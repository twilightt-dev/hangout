package com.dianping.service.impl;

import com.dianping.entity.VoucherOrder;
import com.dianping.service.VoucherOrderPersistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoucherOrderRecoveryTest {
    private VoucherOrderServiceImpl service;
    private RecoveryRedis redis;
    private VoucherOrderPersistService persist;
    private List<String> effects;

    @BeforeEach
    void setUp() {
        effects = new ArrayList<>();
        redis = new RecoveryRedis(effects);
        persist = mock(VoucherOrderPersistService.class);
        service = new VoucherOrderServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "voucherOrderPersistService", persist);
        doAnswer(invocation -> {
            VoucherOrder order = invocation.getArgument(0);
            effects.add("saved:" + order.getId() + ":" + order.getUserId() + ":" + order.getVoucherId());
            return null;
        }).when(persist).createVoucherOrder(any());
    }

    @AfterEach
    void tearDown() {
        service.destroy();
    }

    @Test
    void claimedOrderIsMappedAndAcknowledgedOnlyAfterPersistenceReturns() {
        // Redis 6.2 returns cursor + entries, with no deleted-IDs element.
        redis.pages.add(List.of("0-0", List.of(entry("100-0", "10001"))));

        recover();

        assertThat(effects).containsExactly("saved:10001:123:7", "ack:seckill.orders:g1:100-0");
        assertThat(redis.keys).containsExactly(List.of("seckill.orders"));
        assertThat(redis.arguments.getFirst().get(0)).isEqualTo("g1");
        assertThat(redis.arguments.getFirst().get(1)).isNotEqualTo("c1");
        assertThat(Long.parseLong(redis.arguments.getFirst().get(2))).isPositive();
    }

    @Test
    void emptyPageWithNonzeroCursorDoesNotHideLaterPendingOrders() {
        redis.pages.add(List.of("200-0", List.of(), List.of()));
        redis.pages.add(List.of("0-0", List.of(entry("201-0", "10002")), List.of()));

        recover();

        assertThat(redis.arguments).extracting(args -> args.get(3)).containsExactly("0-0", "200-0");
        assertThat(effects).containsExactly("saved:10002:123:7", "ack:seckill.orders:g1:201-0");
    }

    @Test
    void failedOrderRemainsUnackedWhileNextClaimedOrderCanComplete() {
        doAnswer(invocation -> {
            VoucherOrder order = invocation.getArgument(0);
            if (order.getId() == 10001L) {
                throw new IllegalStateException("database temporarily unavailable");
            }
            effects.add("saved:" + order.getId());
            return null;
        }).when(persist).createVoucherOrder(any());
        redis.pages.add(List.of("0-0", List.of(entry("100-0", "10001"), entry("101-0", "10002"))));

        recover();

        assertThat(effects).containsExactly("saved:10002", "ack:seckill.orders:g1:101-0");
    }

    @Test
    void malformedMessageDoesNotPreventOtherMessagesFromBeingProcessed() {
        redis.pages.add(List.of("0-0", List.of(
                List.of("100-0", List.of("userId", "123")), entry("101-0", "10002"))));

        recover();

        assertThat(effects).containsExactly("saved:10002:123:7", "ack:seckill.orders:g1:101-0");
    }

    @Test
    void deletedPendingEntriesDoNotCauseParsingFailure() {
        // Redis 7 additionally reports deleted entries cleaned from the PEL.
        redis.pages.add(List.of("0-0", List.of(), List.of("99-0")));

        recover();

        assertThat(effects).isEmpty();
    }

    @Test
    void boundedScanResumesItsCursorOnNextPass() {
        for (int i = 1; i <= 5; i++) {
            redis.pages.add(List.of(i + "-0", List.of()));
        }
        redis.pages.add(List.of("0-0", List.of(entry("6-0", "10002"))));

        recover();
        assertThat(effects).isEmpty();
        recover();

        assertThat(redis.arguments.getLast().get(3)).isEqualTo("5-0");
        assertThat(effects).containsExactly("saved:10002:123:7", "ack:seckill.orders:g1:6-0");
    }

    @Test
    void stoppedConsumerDoesNotClaimMoreMessages() {
        ReflectionTestUtils.setField(service, "running", false);

        recover();

        assertThat(redis.arguments).isEmpty();
    }

    private void recover() {
        ReflectionTestUtils.invokeMethod(service, "handlePendingList");
    }

    @Test
    void startupAndIdleLoopBothRecoverPendingWithoutANewMessageOrException() throws Exception {
        ReflectionTestUtils.setField(service, "recoveryIntervalMillis", 1L);
        redis.pages.add(List.of("0-0", List.of(entry("100-0", "10001"))));
        redis.pages.add(List.of("0-0", List.of(entry("101-0", "10002"))));
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            Consumer consumer = invocation.getArgument(0);
            assertThat(consumer.getName()).isEqualTo(redis.arguments.getFirst().get(1));
            if (reads.incrementAndGet() == 2) {
                ReflectionTestUtils.setField(service, "running", false);
            }
            Thread.sleep(5);
            return List.of();
        }).when(redis.streams).read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class));

        var constructor = Class.forName(VoucherOrderServiceImpl.class.getName() + "$VoucherOrderHandler")
                .getDeclaredConstructor(VoucherOrderServiceImpl.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(service)).run();

        assertThat(effects).containsExactly("saved:10001:123:7", "ack:seckill.orders:g1:100-0",
                "saved:10002:123:7", "ack:seckill.orders:g1:101-0");
    }

    private static List<Object> entry(String messageId, String orderId) {
        return List.of(messageId, List.of("orderId", orderId, "userId", "123", "voucherId", "7"));
    }

    // Redis and MySQL are external boundaries; consumer parsing, control flow and ACK ordering remain real.
    private static class RecoveryRedis extends StringRedisTemplate {
        private final Deque<List<?>> pages = new ArrayDeque<>();
        private final List<List<String>> keys = new ArrayList<>();
        private final List<List<String>> arguments = new ArrayList<>();
        private final StreamOperations<String, Object, Object> streams;

        @SuppressWarnings("unchecked")
        RecoveryRedis(List<String> effects) {
            streams = mock(StreamOperations.class);
            doAnswer(invocation -> {
                effects.add("ack:" + invocation.getArgument(0) + ":" + invocation.getArgument(1)
                        + ":" + invocation.getArgument(2));
                return 1L;
            }).when(streams).acknowledge(anyString(), anyString(), any(org.springframework.data.redis.connection.stream.RecordId.class));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <HK, HV> StreamOperations<String, HK, HV> opsForStream() {
            return (StreamOperations<String, HK, HV>) (StreamOperations<?, ?, ?>) streams;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> scriptKeys, Object... args) {
            keys.add(List.copyOf(scriptKeys));
            arguments.add(java.util.Arrays.stream(args).map(Object::toString).toList());
            return (T) (pages.isEmpty() ? List.of("0-0", List.of()) : pages.removeFirst());
        }
    }
}
