package com.dianping.comment;

import com.dianping.application.comment.like.CommentLikeUnavailableException;
import com.dianping.domain.comment.*;
import com.dianping.infrastructure.redis.comment.CommentLikeRedisStore;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 仅连接本轮专用的无持久化 Redis；必须显式提供隔离端口。 */
class CommentLikeRedisIntegrationTest {
    static LettuceConnectionFactory factory;
    static CommentLikeRedisStore store;
    static StringRedisTemplate redis;
    @BeforeAll static void connect() {
        assumeTrue(Boolean.getBoolean("commentLikeRedisTest"), "需要显式启动隔离 Redis");
        int port = Integer.getInteger("commentLikeRedisPort", 0);
        if (port < 1024 || port == 6379) throw new IllegalArgumentException("必须提供专用隔离端口");
        factory = new LettuceConnectionFactory("127.0.0.1", port); factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory);
    }
    @BeforeEach void isolateKeys() { store = new CommentLikeRedisStore(redis, "test:" + UUID.randomUUID()); }
    @AfterEach void closeStore() { if (store != null) store.destroy(); }
    @AfterAll static void close() { if (factory != null) factory.destroy(); }
    @Test void switchesAndKeepsNewChangesWhenOldSnapshotIsAcknowledged() {
        long comment = 10001;
        CommentLikeId id = new CommentLikeId(comment, 7L);
        assertThat(store.read(id, 1, true).isLike()).isFalse();
        assertThat(store.apply(new CommentLikeChange(id, false, true), 1).liked()).isEqualTo(1);
        var first = store.pending(100).stream().filter(s -> s.commentId() == comment).findFirst().orElseThrow();
        assertThat(first.version()).isEqualTo(1);
        assertThat(store.apply(new CommentLikeChange(id, true, false), 1).liked()).isZero();
        store.acknowledge(first);
        store.defer(first);
        var second = store.pending(100).stream().filter(s -> s.commentId() == comment).findFirst().orElseThrow();
        assertThat(second.version()).isEqualTo(2); assertThat(second.liked()).isZero();
        store.acknowledge(second);
        assertThat(store.pending(100)).noneMatch(s -> s.commentId() == comment);
    }
    @Test void knownCompareFailureDoesNotChangeVersionAndOldEpochCannotWrite() {
        var id = new CommentLikeId(10002L, 7L); store.read(id, 2, true);
        assertThat(store.apply(new CommentLikeChange(id, true, false), 2).applied()).isFalse();
        assertThat(store.pending(100)).noneMatch(s -> s.commentId().equals(id.commentId()));
        assertThatThrownBy(() -> store.apply(new CommentLikeChange(id, false, true), 1)).isInstanceOf(CommentLikeUnavailableException.class);
        assertThat(store.read(id, 2, false).isLike()).isFalse();
    }
    @Test void concurrentDifferentUsersProduceExactCountAndVersions() throws Exception {
        long comment = 10003; store.read(new CommentLikeId(comment, 1L), 1, true);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (long user = 1; user <= 40; user++) {
                long current = user;
                calls.add(() -> store.apply(new CommentLikeChange(new CommentLikeId(comment, current), false, true), 1).applied());
            }
            for (Future<Boolean> done : executor.invokeAll(calls)) assertThat(done.get()).isTrue();
        }
        var snapshot = store.pending(100).stream().filter(s -> s.commentId() == comment).findFirst().orElseThrow();
        assertThat(snapshot.liked()).isEqualTo(40); assertThat(snapshot.version()).isEqualTo(40);
    }
    @Test void missingAlreadyUsedMetadataAndWrongKeyTypesFailBeforeMutation() {
        var id = new CommentLikeId(10004L, 7L);
        assertThatThrownBy(() -> store.read(id, 1, false)).isInstanceOf(CommentLikeUnavailableException.class);
        store.read(id, 1, true);
        redis.opsForValue().set(store.pendingKey(id.commentId()), "损坏类型");
        assertThatThrownBy(() -> store.apply(new CommentLikeChange(id, false, true), 1)).isInstanceOf(CommentLikeUnavailableException.class);
        redis.rename(store.pendingKey(id.commentId()), store.pendingKey(id.commentId()) + ":corrupt");
        assertThat(store.read(id, 1, false).isLike()).isFalse();
    }

    @Test void invalidIntegerVersionCannotPartiallyChangeTheRelation() {
        var id = new CommentLikeId(10005L, 7L); store.read(id, 1, true);
        String meta = store.pendingKey(id.commentId()).replace("pending", id.commentId() + ":meta");
        redis.opsForHash().put(meta, "version", "01");
        assertThatThrownBy(() -> store.apply(new CommentLikeChange(id, false, true), 1)).isInstanceOf(CommentLikeUnavailableException.class);
        redis.opsForHash().put(meta, "version", "0");
        assertThat(store.read(id, 1, false).isLike()).isFalse();
    }

    @Test void missingRelationSetWithPositiveMetadataCountIsUnavailableRatherThanZero() {
        var id = new CommentLikeId(10006L, 7L); store.read(id, 1, true);
        store.apply(new CommentLikeChange(id, false, true), 1);
        String users = store.pendingKey(id.commentId()).replace("pending", id.commentId() + ":users");
        redis.rename(users, users + ":lost");
        assertThatThrownBy(() -> store.read(id, 1, false)).isInstanceOf(CommentLikeUnavailableException.class);
        assertThat(store.readBatch(Map.of(id.commentId(), new com.dianping.application.comment.port.CommentLikeMetadata(1, 0, 0, true)), 7L).get(id.commentId()).available()).isFalse();
    }

    @Test void concurrentSameUserRequestsAreTwoDistinctSwitches() throws Exception {
        var id = new CommentLikeId(10007L, 7L); store.read(id, 1, true);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var futures = executor.invokeAll(List.of(
                    (Callable<CommentLikeApplyResult>) () -> store.apply(new CommentLikeChange(id, false, true), 1),
                    (Callable<CommentLikeApplyResult>) () -> store.apply(new CommentLikeChange(id, false, true), 1)));
            assertThat(List.of(futures.get(0).get().applied(), futures.get(1).get().applied())).containsExactlyInAnyOrder(true, false);
            // 未执行的请求基于真实状态再计算一次，完成自己的第二次切换。
            assertThat(store.apply(store.read(id, 1, false).toggle(), 1).isLike()).isFalse();
        }
        var result = store.pending(100).stream().filter(s -> s.commentId().equals(id.commentId())).findFirst().orElseThrow();
        assertThat(result.version()).isEqualTo(2); assertThat(result.liked()).isZero();
    }

    @Test void lostWriteReplyDoesNotReplayAndANewReadRecoversRealState() throws Exception {
        byte[] body;
        try (InputStream source = new ClassPathResource("lua/comment-like-apply.lua").getInputStream()) { body = source.readAllBytes(); }
        String sha = redis.execute((RedisCallback<String>) connection -> connection.scriptingCommands().scriptLoad(body));
        try (LostReplyGateway gateway = new LostReplyGateway(Integer.getInteger("commentLikeRedisPort"), sha)) {
            var proxiedFactory = new LettuceConnectionFactory("127.0.0.1", gateway.port());
            proxiedFactory.afterPropertiesSet(); proxiedFactory.start();
            var proxied = new CommentLikeRedisStore(new StringRedisTemplate(proxiedFactory), "lostreply:" + UUID.randomUUID());
            try {
                var id = new CommentLikeId(10008L, 7L); proxied.read(id, 1, true);
                assertThatThrownBy(() -> proxied.apply(new CommentLikeChange(id, false, true), 1)).isInstanceOf(CommentLikeUnavailableException.class);
                assertThat(proxied.read(id, 1, false).isLike()).isTrue();
                assertThat(proxied.pending(100).getFirst().version()).isEqualTo(1);
                assertThat(gateway.attempts.get()).isEqualTo(1);
            } finally { proxied.destroy(); proxiedFactory.destroy(); }
        }
    }

    /** 透传真实 Redis，仅丢弃第一次切换响应，用于模拟服务端已执行但客户端未知。 */
    static class LostReplyGateway implements AutoCloseable {
        final ServerSocket listener;
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicBoolean lost = new AtomicBoolean();
        LostReplyGateway(int upstreamPort, String sha) throws IOException {
            listener = new ServerSocket(0, 20, InetAddress.getLoopbackAddress());
            workers.submit(() -> {
                while (!listener.isClosed()) {
                    try {
                        Socket client = listener.accept(); Socket upstream = new Socket("127.0.0.1", upstreamPort);
                        sockets.add(client); sockets.add(upstream);
                        AtomicBoolean drop = new AtomicBoolean();
                        workers.submit(() -> {
                            ByteArrayOutputStream captured = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
                            try {
                                int size;
                                while ((size = client.getInputStream().read(buffer)) >= 0) {
                                    captured.write(buffer, 0, size);
                                    if (!drop.get() && captured.toString(StandardCharsets.UTF_8).contains(sha)) { attempts.incrementAndGet(); drop.set(true); }
                                    upstream.getOutputStream().write(buffer, 0, size); upstream.getOutputStream().flush();
                                }
                            } catch (IOException closed) { /* 预期断线。 */ }
                            finally { closeSocket(upstream); closeSocket(client); }
                        });
                        workers.submit(() -> {
                            byte[] buffer = new byte[8192];
                            try {
                                int size;
                                while ((size = upstream.getInputStream().read(buffer)) >= 0) {
                                    if (drop.get() && lost.compareAndSet(false, true)) { closeSocket(client); closeSocket(upstream); return; }
                                    client.getOutputStream().write(buffer, 0, size); client.getOutputStream().flush();
                                }
                            } catch (IOException closed) { /* 预期断线。 */ }
                            finally { closeSocket(upstream); closeSocket(client); }
                        });
                    } catch (IOException closed) { if (!listener.isClosed()) throw new UncheckedIOException(closed); }
                }
            });
        }
        int port() { return listener.getLocalPort(); }
        static void closeSocket(Socket socket) { try { socket.close(); } catch (IOException ignored) { /* 测试清理。 */ } }
        public void close() throws IOException { listener.close(); sockets.forEach(LostReplyGateway::closeSocket); workers.shutdownNow(); workers.close(); }
    }
}
