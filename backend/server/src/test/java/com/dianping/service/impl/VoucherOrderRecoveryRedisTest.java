package com.dianping.service.impl;

import com.dianping.entity.VoucherOrder;
import com.dianping.service.VoucherOrderPersistService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Explicit opt-in: point only to an isolated, disposable local Redis, never the application's Redis.
@EnabledIfSystemProperty(named = "stream.recovery.redis.port", matches = "[0-9]+")
class VoucherOrderRecoveryRedisTest {
    @Test
    void realRedisKeepsFreshPendingAndTransfersStalePendingThenAcknowledgesIt() {
        int port = Integer.parseInt(System.getProperty("stream.recovery.redis.port"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        VoucherOrderServiceImpl service = new VoucherOrderServiceImpl();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            var streams = redis.opsForStream();
            RecordId id = streams.add("seckill.orders", Map.of(
                    "orderId", "10001", "userId", "123", "voucherId", "7"));
            streams.createGroup("seckill.orders", ReadOffset.from("0"), "g1");
            streams.read(Consumer.from("g1", "old-instance"), StreamReadOptions.empty().count(1),
                    StreamOffset.create("seckill.orders", ReadOffset.lastConsumed()));

            List<Long> saved = new ArrayList<>();
            VoucherOrderPersistService persist = mock(VoucherOrderPersistService.class);
            doAnswer(invocation -> {
                VoucherOrder order = invocation.getArgument(0);
                saved.add(order.getId());
                // Ownership really transferred before persisting; it is not ACKed prematurely.
                var pending = streams.pending("seckill.orders", "g1");
                assertThat(pending.getTotalPendingMessages()).isEqualTo(1);
                assertThat(pending.getPendingMessagesPerConsumer()).doesNotContainKey("old-instance");
                return null;
            }).when(persist).createVoucherOrder(any());
            ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
            ReflectionTestUtils.setField(service, "voucherOrderPersistService", persist);

            // The normal 60s threshold must not steal a freshly delivered message.
            ReflectionTestUtils.invokeMethod(service, "handlePendingList");
            assertThat(saved).isEmpty();
            assertThat(streams.pending("seckill.orders", "g1").getPendingMessagesPerConsumer())
                    .containsEntry("old-instance", 1L);

            // Age the fixture without making the test sleep for a minute.
            redis.execute(new DefaultRedisScript<List>(
                    "return redis.call('XCLAIM', KEYS[1], ARGV[1], ARGV[2], '0', ARGV[3], 'IDLE', '120000')",
                    List.class), List.of("seckill.orders"), "g1", "old-instance", id.getValue());
            ReflectionTestUtils.invokeMethod(service, "handlePendingList");

            assertThat(saved).containsExactly(10001L);
            assertThat(streams.pending("seckill.orders", "g1").getTotalPendingMessages()).isZero();
            assertThat(streams.size("seckill.orders")).isEqualTo(1L); // ACK is not XDEL.
        } finally {
            service.destroy();
            factory.destroy();
        }
    }
}
