package com.dianping.service;

import com.dianping.dto.UserDTO;
import com.dianping.service.impl.SignServiceImpl;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// Opt in only with an isolated local Redis, never the application's Redis.
@EnabledIfSystemProperty(named = "signin.redis.port", matches = "[0-9]+")
class SignServiceRedisTest {
    @Test
    void realRedisPreservesBitOrderMonthLengthsAndConcurrentIdempotency() throws Exception {
        int port = Integer.parseInt(System.getProperty("signin.redis.port"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        UserDTO user = new UserDTO();
        user.setId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        UserHolder.saveUser(user);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            String prefix = "sign:" + user.getId() + ":";
            var service = serviceAt(redis, "2026-10-08T04:00:00Z");
            assertThat(service.stats().monthlyDays()).isZero();
            for (long offset : new long[]{0, 2, 5, 6, 7}) {
                redis.opsForValue().setBit(prefix + "202610", offset, true);
            }
            for (long offset : new long[]{0, 28, 29}) {
                redis.opsForValue().setBit(prefix + "202609", offset, true);
            }
            var stats = service.stats();
            assertThat(stats.monthlyDays()).isEqualTo(5);
            assertThat(stats.continuousDays()).isEqualTo(3);
            assertThat(stats.previousMonthDays()).isEqualTo(3);
            assertThat(stats.monthDifference()).isEqualTo(2);
            assertThat(service.sign().alreadySigned()).isTrue();
            assertThat(redis.getExpire(prefix + "202610")).isEqualTo(-1);
            assertThat(redis.opsForValue().size(prefix + "202610")).isEqualTo(1L);

            var ninthDay = serviceAt(redis, "2026-10-09T04:00:00Z");
            try (var executor = Executors.newFixedThreadPool(8)) {
                var tasks = IntStream.range(0, 16)
                        .<java.util.concurrent.Callable<Boolean>>mapToObj(index -> () -> {
                            UserHolder.saveUser(user);
                            try { return ninthDay.sign().alreadySigned(); }
                            finally { UserHolder.removeUser(); }
                        }).toList();
                int newlySigned = 0;
                for (var future : executor.invokeAll(tasks, 10, TimeUnit.SECONDS)) {
                    if (!future.get()) newlySigned++;
                }
                assertThat(newlySigned).isEqualTo(1);
            }
            assertThat(ninthDay.stats().monthlyDays()).isEqualTo(6);
            assertThat(ninthDay.stats().continuousDays()).isEqualTo(4);

            for (int offset = 0; offset < 31; offset++) redis.opsForValue().setBit(prefix + "202612", offset, true);
            assertThat(serviceAt(redis, "2026-12-31T04:00:00Z").stats().continuousDays()).isEqualTo(31);
            var january = serviceAt(redis, "2026-12-31T16:00:00Z");
            january.sign();
            assertThat(january.stats().continuousDays()).isEqualTo(1);
            assertThat(january.stats().previousMonthDays()).isEqualTo(31);

            for (int offset = 0; offset < 29; offset++) redis.opsForValue().setBit(prefix + "202802", offset, true);
            var march = serviceAt(redis, "2028-03-01T04:00:00Z");
            assertThat(march.stats().previousMonthDays()).isEqualTo(29);
            assertThat(march.stats().continuousDays()).isZero();
            UserDTO other = new UserDTO();
            other.setId(user.getId() ^ 1L);
            UserHolder.saveUser(other);
            assertThat(service.stats().monthlyDays()).isZero();
        } finally {
            UserHolder.removeUser();
            factory.destroy();
        }
    }

    private static SignService serviceAt(StringRedisTemplate redis, String instant) {
        return new SignServiceImpl(redis, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
