package com.dianping.shopdetail.cache;

import com.dianping.shop.cache.ShopCacheProtection;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

//@Configuration表示这是一个配置类，@Bean注解的方法返回值需要交给IOC容器管理
@Configuration(proxyBeanMethods = false)//proxyBeanMethods默认是true，这里因为用的是参数注入，所以关闭Bean方法之间的代理
public class ShopDetailCacheConfiguration {
    /**
     * 创建有界的商详异步重建线程池。
     * @return 核心 2 线程、最大 2 线程、队列容量 100 的执行器
     */
    @Bean("shopDetailRefreshExecutor")
    public ThreadPoolTaskExecutor shopDetailRefreshExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("shop-detail-refresh-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    /**
     * 创建带逻辑过期、物理 TTL 和冷启动等待配置的协调器，锁由看门狗续期。
     * @param cache Redis 快照读写适配器
     * @param protection 与旧详情入口及写操作共享的缓存保护端口
     * @param executor 有界后台刷新执行器
     * @param logicalTtl 逻辑有效期，默认 35 分钟
     * @param physicalTtl 物理 TTL 上限，默认 60 分钟
     * @param physicalTtlJitter 物理 TTL 最大提前量，默认 5 分钟
     * @param waitTimeout 冷启动等待上限，默认 2 秒
     * @param waitInterval 冷启动重读间隔，默认 50 毫秒
     * @return 配置完成的公共商详缓存协调器
     */
    @Bean
    public ShopDetailCacheCoordinator<ShopDetailResult> shopDetailCacheCoordinator(RedisShopDetailCache cache,
            ShopCacheProtection protection,
            @Qualifier("shopDetailRefreshExecutor") Executor executor,
            @Value("${shop-detail.cache-logical-ttl-ms:2100000}") long logicalTtl,
            @Value("${shop-detail.cache-physical-ttl-ms:3600000}") long physicalTtl,
            @Value("${shop-detail.cache-physical-ttl-jitter-ms:300000}") long physicalTtlJitter,
            @Value("${shop-cache.lock-wait-ms:2000}") long waitTimeout,
            @Value("${shop-cache.lock-retry-ms:50}") long waitInterval) {
        return new ShopDetailCacheCoordinator<>(cache, protection, executor, Clock.systemUTC(), Duration.ofMillis(logicalTtl),
                Duration.ofMillis(physicalTtl), Duration.ofMillis(physicalTtlJitter),
                Duration.ofMillis(waitTimeout), Duration.ofMillis(waitInterval));
    }
}
