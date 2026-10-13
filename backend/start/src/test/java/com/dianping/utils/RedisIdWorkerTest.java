package com.dianping.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

// 沿用现有 Redis 测试的端口属性开关，只连接显式指定的隔离实例。
@EnabledIfSystemProperty(named = "legacy.redis.port", matches = "[0-9]+")
@SpringJUnitConfig(RedisIdWorkerTest.TestConfig.class)
public class RedisIdWorkerTest {

    @Autowired
    private RedisIdWorker redisIdWorker;


    @Test
    void testIdWorker() throws Exception {
        String keyPrefix = "test:order:" + UUID.randomUUID();
        var ids = ConcurrentHashMap.<Long>newKeySet();
        List<Callable<Void>> tasks = IntStream.range(0, 300).mapToObj(index -> (Callable<Void>) () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId(keyPrefix);
                assertTrue(ids.add(id), "并发生成的 ID 必须唯一");
            }
            return null;
        }).toList();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (var future : executor.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                assertFalse(future.isCancelled(), "并发生成不能超时");
                future.get();
            }
        }
        assertEquals(30_000, ids.size());
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("legacy.redis.port")));
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
            return new StringRedisTemplate(factory);
        }

        @Bean
        RedisIdWorker redisIdWorker(StringRedisTemplate template) {
            return new RedisIdWorker(template);
        }
    }
}
