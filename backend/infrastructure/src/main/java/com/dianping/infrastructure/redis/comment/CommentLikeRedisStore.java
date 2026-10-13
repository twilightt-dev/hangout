package com.dianping.infrastructure.redis.comment;

import com.dianping.application.comment.like.CommentLikeUnavailableException;
import com.dianping.application.comment.like.sync.CommentLikeCountSnapshot;
import com.dianping.application.comment.port.*;
import com.dianping.domain.comment.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 四个稳定同步分区；每条评论的关系、元数据与待同步集合位于同一个 Cluster 槽。 */
@Component
public class CommentLikeRedisStore implements DisposableBean {
    private static final int PARTITIONS = 4;
    private static final Logger LOG = Logger.getLogger(CommentLikeRedisStore.class.getName());
    private static final DefaultRedisScript<List> READ = script("read");
    private static final DefaultRedisScript<List> APPLY = script("apply");
    private static final DefaultRedisScript<List> BATCH = script("batch-read");
    private static final DefaultRedisScript<List> SNAPSHOT = script("snapshot");
    private static final DefaultRedisScript<Long> ACK = numberScript("ack");
    private static final DefaultRedisScript<Long> DEFER = numberScript("defer");
    private final StringRedisTemplate redis;
    private final String namespace;
    private final LettuceConnectionFactory connections;
    private final AtomicInteger nextPartition = new AtomicInteger();
    @Autowired public CommentLikeRedisStore(StringRedisTemplate redis) { this(redis, "comment:likes"); }
    /** 独立命名空间用于隔离测试，生产实例固定使用默认命名。 */
    public CommentLikeRedisStore(StringRedisTemplate redis, String namespace) {
        if (!namespace.matches("[a-zA-Z0-9:-]+")) throw new IllegalArgumentException("非法命名空间");
        this.connections = CommentLikeRedisConnections.create(redis.getConnectionFactory());
        this.redis = new StringRedisTemplate(connections); this.namespace = namespace;
    }
    private static DefaultRedisScript<List> script(String name) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/comment-like-" + name + ".lua"));
        script.setResultType(List.class);
        return script;
    }
    private static DefaultRedisScript<Long> numberScript(String name) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/comment-like-" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }
    private String prefix(int partition) { return "{" + namespace + ":" + partition + "}:"; }
    private int partition(Long id) { return Math.floorMod(id, PARTITIONS); }
    private List<String> keys(Long id) {
        String prefix = prefix(partition(id));
        return List.of(prefix + id + ":users", prefix + id + ":meta", prefix + "pending");
    }
    public String pendingKey(Long id) { return keys(id).get(2); }
    private List<?> execute(DefaultRedisScript<List> script, List<String> keys, String... args) {
        try {
            List<?> result = redis.execute(script, keys, (Object[]) args);
            if (result == null || result.isEmpty() || "-1".equals(result.get(0).toString())) throw new CommentLikeUnavailableException();
            return result;
        } catch (CommentLikeUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) { throw new CommentLikeUnavailableException(failure); }
    }
    private static long number(List<?> result, int index) { return Long.parseLong(result.get(index).toString()); }
    public CommentLike read(CommentLikeId id, long epoch, boolean initialize) {
        List<?> result = execute(READ, keys(id.commentId()), Long.toString(epoch), id.userId().toString(), initialize ? "1" : "0");
        return CommentLike.restore(id, number(result, 1) == 1);
    }
    public void initialize(Long commentId, long epoch, boolean allowInitialize) {
        execute(READ, keys(commentId), Long.toString(epoch), "", allowInitialize ? "1" : "0");
    }
    public CommentLikeApplyResult apply(CommentLikeChange change, long epoch) {
        List<?> result = execute(APPLY, keys(change.id().commentId()), Long.toString(epoch), change.id().userId().toString(),
                change.expectedLiked() ? "1" : "0", change.targetLiked() ? "1" : "0", change.id().commentId().toString());
        return new CommentLikeApplyResult(number(result, 0) == 1, number(result, 1) == 1, number(result, 2));
    }
    public Map<Long, CommentLikeStatus> readBatch(Map<Long, CommentLikeMetadata> metadata, Long userId) {
        Map<Long, CommentLikeStatus> result = new HashMap<>();
        for (int partition = 0; partition < PARTITIONS; partition++) {
            int current = partition;
            List<Long> ids = metadata.keySet().stream().filter(id -> partition(id) == current).toList();
            if (ids.isEmpty()) continue;
            List<String> keys = new ArrayList<>(); List<String> args = new ArrayList<>();
            for (Long id : ids) { keys.addAll(keys(id).subList(0, 2)); args.add(Long.toString(metadata.get(id).epoch())); args.add(userId == null ? "" : userId.toString()); }
            List<?> values = execute(BATCH, keys, args.toArray(String[]::new));
            for (int i = 0; i < ids.size(); i++) result.put(ids.get(i), number(values, i * 2) == 1
                    ? new CommentLikeStatus(number(values, i * 2 + 1) == 1, true) : CommentLikeStatus.unavailable(userId));
        }
        return result;
    }
    public List<CommentLikeCountSnapshot> pending(int limit) {
        List<CommentLikeCountSnapshot> result = new ArrayList<>();
        int start = Math.floorMod(nextPartition.getAndIncrement(), PARTITIONS);
        try {
            for (int offset = 0; offset < PARTITIONS && result.size() < limit; offset++) {
                String pending = prefix((start + offset) % PARTITIONS) + "pending";
                Set<String> ids = redis.opsForZSet().rangeByScore(pending, 0, System.currentTimeMillis(), 0, limit - result.size());
                if (ids == null) continue;
                for (String value : ids) {
                    try {
                        Long id = Long.valueOf(value);
                        List<?> snapshot = execute(SNAPSHOT, keys(id));
                        result.add(new CommentLikeCountSnapshot(id, number(snapshot, 0), number(snapshot, 1), number(snapshot, 2)));
                    } catch (RuntimeException failure) {
                        LOG.log(Level.WARNING, "待同步点赞元数据缺失或损坏，保留记录并暂停本条", failure);
                        redis.opsForZSet().add(pending, value, System.currentTimeMillis() + 1000);
                    }
                }
            }
            return result;
        } catch (RuntimeException failure) { throw new CommentLikeUnavailableException(failure); }
    }
    public void acknowledge(CommentLikeCountSnapshot snapshot) { finish(ACK, snapshot); }
    public void defer(CommentLikeCountSnapshot snapshot) { finish(DEFER, snapshot); }
    private void finish(DefaultRedisScript<Long> script, CommentLikeCountSnapshot snapshot) {
        try { redis.execute(script, keys(snapshot.commentId()), Long.toString(snapshot.epoch()), Long.toString(snapshot.version()), snapshot.commentId().toString()); }
        catch (RuntimeException failure) { throw new CommentLikeUnavailableException(failure); }
    }
    @Override public void destroy() { connections.destroy(); }
}
