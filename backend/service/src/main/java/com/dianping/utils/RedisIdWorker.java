package com.dianping.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     * 对应2026-9-15 00:00:00 UTC
     */
    private static final long BEGIN_TIMESTAMP = 1789430400L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳  距离开始时间过去了多久
        long nowSecond = Instant.now().getEpochSecond() ;
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长  redis自增命令的原子性保证了id不重复
        long count = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + date);

        // 3.拼接并返回  左移 + 位或
        return (timestamp << COUNT_BITS) | count;
    }
}
