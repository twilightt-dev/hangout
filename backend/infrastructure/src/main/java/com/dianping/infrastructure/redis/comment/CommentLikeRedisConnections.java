package com.dianping.infrastructure.redis.comment;

import io.lettuce.core.*;
import io.lettuce.core.resource.ClientResources;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.*;
import java.time.Duration;
import java.util.Optional;

/** 点赞使用独立连接，禁止 Lettuce 在断线后重放结果未知的切换命令。 */
final class CommentLikeRedisConnections {
    private CommentLikeRedisConnections() {}
    static LettuceConnectionFactory create(RedisConnectionFactory source) {
        if (!(source instanceof LettuceConnectionFactory original)) {
            throw new IllegalArgumentException("评论点赞需要支持禁止命令重放的 Lettuce 连接");
        }
        LettuceClientConfiguration originalConfig = original.getClientConfiguration();
        ClientOptions safeOptions = originalConfig.getClientOptions().orElse(ClientOptions.create()).mutate()
                .autoReconnect(false).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build();
        LettuceClientConfiguration safeConfig = new LettuceClientConfiguration() {
            public boolean isUseSsl() { return originalConfig.isUseSsl(); }
            @SuppressWarnings("deprecation") public boolean isVerifyPeer() { return originalConfig.isVerifyPeer(); }
            public SslVerifyMode getVerifyMode() { return originalConfig.getVerifyMode(); }
            public boolean isStartTls() { return originalConfig.isStartTls(); }
            public Optional<ClientResources> getClientResources() { return originalConfig.getClientResources(); }
            public Optional<ClientOptions> getClientOptions() { return Optional.of(safeOptions); }
            public Optional<String> getClientName() { return originalConfig.getClientName(); }
            public Optional<ReadFrom> getReadFrom() { return Optional.of(ReadFrom.MASTER); }
            public Optional<RedisCredentialsProviderFactory> getRedisCredentialsProviderFactory() { return originalConfig.getRedisCredentialsProviderFactory(); }
            public Duration getCommandTimeout() { return originalConfig.getCommandTimeout(); }
            public Duration getShutdownTimeout() { return originalConfig.getShutdownTimeout(); }
            public Duration getShutdownQuietPeriod() { return originalConfig.getShutdownQuietPeriod(); }
        };
        LettuceConnectionFactory factory;
        if (original.getClusterConfiguration() != null) factory = new LettuceConnectionFactory(original.getClusterConfiguration(), safeConfig);
        else if (original.getSentinelConfiguration() != null) factory = new LettuceConnectionFactory(original.getSentinelConfiguration(), safeConfig);
        else factory = new LettuceConnectionFactory(original.getStandaloneConfiguration(), safeConfig);
        // 每次调用取得独立物理连接；失败后下一次读取可重新连接，不重用已经断开的共享连接。
        factory.setShareNativeConnection(false);
        factory.afterPropertiesSet(); factory.start();
        return factory;
    }
}
