package com.dianping.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redisson 与 Spring Redis 模板使用相同的地址、认证和逻辑数据库。 */
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(RedisProperties.class)
public class RedissonConfig {
    /** 复用 Spring Boot Redis 配置，避免同店锁与缓存写入连接不同数据库。 */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redis) {
        Config config = new Config();
        var server = config.useSingleServer()
                .setAddress((redis.getSsl().isEnabled() ? "rediss://" : "redis://") + redis.getHost() + ":" + redis.getPort())
                .setDatabase(redis.getDatabase());
        if (redis.getUsername() != null && !redis.getUsername().isBlank()) server.setUsername(redis.getUsername());
        if (redis.getPassword() != null && !redis.getPassword().isBlank()) server.setPassword(redis.getPassword());
        return Redisson.create(config);
    }
}
