package com.dianping.service;

import com.dianping.dto.VisitRequestDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.Shop;
import com.dianping.exception.VisitStatsException;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.ShopMapper;
import com.dianping.service.impl.VisitStatsServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

// 只连接显式指定的独立测试 Redis，不使用应用的 Redis。
@EnabledIfSystemProperty(named = "visits.redis.port", matches = "[0-9]+")
class VisitStatsRedisTest {
    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("visits.redis.port")));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void disconnect() { factory.destroy(); }

    @Test
    void concurrentRetriesIncrementPvOnceButNewDisplaysIncrementAgain() throws Exception {
        long id = resourceId();
        VisitStatsService service = service(id);
        VisitRequestDTO event = event(UUID.randomUUID().toString());
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 16).<java.util.concurrent.Callable<Long>>mapToObj(i ->
                    () -> service.recordBlog(id, event).count()).toList();
            for (var result : executor.invokeAll(tasks, 10, TimeUnit.SECONDS)) assertThat(result.get()).isEqualTo(1);
        }
        assertThat(service.recordBlog(id, event(event.visitorId())).count()).isEqualTo(2);
        assertThat(redis.opsForValue().get("stats:{blog:" + id + "}:pv")).isEqualTo("2");
        assertThat(redis.getExpire("stats:{blog:" + id + "}:pv")).isEqualTo(-1);
        assertThat(redis.getExpire("stats:{blog:" + id + "}:event:" + event.visitorId() + ":" + event.eventId()))
                .isBetween(86390L, 86400L);
    }

    @Test
    void shopUvDeduplicatesVisitorAcrossNewEventsAndSeparatesShops() {
        long id = resourceId();
        VisitStatsService service = service(id);
        String visitor = UUID.randomUUID().toString();
        assertThat(service.recordShop(id, event(visitor)).count()).isEqualTo(1);
        assertThat(service.recordShop(id, event(visitor)).count()).isEqualTo(1);
        assertThat(service.recordShop(id, event(UUID.randomUUID().toString())).count()).isEqualTo(2);
        assertThat(service(id + 1).recordShop(id + 1, event(visitor)).count()).isEqualTo(1);
        assertThat(redis.getExpire("stats:{shop:" + id + "}:uv")).isEqualTo(-1);
    }

    @Test
    void rateLimitRejectsNewEventsButAcknowledgesAnAlreadyCountedRetry() {
        long id = resourceId();
        VisitStatsService service = service(id);
        String visitor = UUID.randomUUID().toString();
        VisitRequestDTO first = event(visitor);
        service.recordBlog(id, first);
        for (int i = 1; i < 30; i++) service.recordBlog(id, event(visitor));
        assertThatThrownBy(() -> service.recordBlog(id, event(visitor)))
                .isInstanceOfSatisfying(VisitStatsException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(service.recordBlog(id, first).count()).isEqualTo(30);
        assertThat(redis.opsForValue().get("stats:{blog:" + id + "}:pv")).isEqualTo("30");
    }

    @Test
    void expiredEventCanBeCountedAgainButCorruptTotalDoesNotConsumeEvent() {
        long id = resourceId();
        VisitStatsService service = service(id);
        VisitRequestDTO event = event(UUID.randomUUID().toString());
        service.recordBlog(id, event);
        String marker = "stats:{blog:" + id + "}:event:" + event.visitorId() + ":" + event.eventId();
        redis.expire(marker, java.time.Duration.ofMillis(1));
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(2))
                .until(() -> !Boolean.TRUE.equals(redis.hasKey(marker)));
        assertThat(service.recordBlog(id, event).count()).isEqualTo(2);
        VisitRequestDTO next = event(event.visitorId());
        String total = "stats:{blog:" + id + "}:pv";
        redis.opsForValue().set(total, "broken");
        assertThatThrownBy(() -> service.recordBlog(id, next)).isInstanceOf(DataAccessException.class);
        assertThat(redis.hasKey("stats:{blog:" + id + "}:event:" + next.visitorId() + ":" + next.eventId())).isFalse();
        redis.opsForValue().set(total, "2");
        assertThat(service.recordBlog(id, next).count()).isEqualTo(3);
    }

    private static VisitStatsService service(long id) {
        BlogMapper blogs = mock(BlogMapper.class);
        ShopMapper shops = mock(ShopMapper.class);
        when(blogs.selectById(id)).thenReturn(new Blog());
        when(shops.selectById(id)).thenReturn(new Shop());
        return new VisitStatsServiceImpl(redis, blogs, shops);
    }

    private static VisitRequestDTO event(String visitor) {
        return new VisitRequestDTO(visitor, UUID.randomUUID().toString());
    }

    private static long resourceId() { return UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE; }
}
