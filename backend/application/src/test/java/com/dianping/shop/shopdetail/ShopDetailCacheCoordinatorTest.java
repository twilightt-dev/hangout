package com.dianping.shop.shopdetail;

import com.dianping.shop.shopdetail.cache.ShopDetailCache;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheCoordinator;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheEntry;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证公共商详快照逻辑过期刷新和冷启动互斥回源行为。
 */
class ShopDetailCacheCoordinatorTest {
    private final Instant now = Instant.parse("2026-10-22T12:00:00Z");

    /**
     * 逻辑过期但物理仍存活时返回旧值，并把重建移交后台执行。
     */
    @Test void returnsStaleValueBeforeRunningBackgroundRefresh() {
        var cache = new MemoryCache();
        cache.entries.put(7L, new ShopDetailCacheEntry<>("old", now.minusSeconds(1)));
        Runnable[] queued = new Runnable[1];
        var loads = new AtomicInteger();
        var coordinator = coordinator(cache, command -> queued[0] = command);

        assertThat(coordinator.getOrLoad(7L, () -> {
            loads.incrementAndGet();
            return "new";
        })).isEqualTo("old");
        assertThat(loads).hasValue(0);

        queued[0].run();

        assertThat(loads).hasValue(1);
        assertThat(cache.entries.get(7L).value()).isEqualTo("new");
    }

    /**
     * 物理过期后不返回旧快照，重新同步查询并写入。
     */
    @Test void physicallyExpiredValueFallsBackToSynchronousRebuild() {
        var cache = new MemoryCache();
        cache.entries.put(7L, new ShopDetailCacheEntry<>("old", now.minusSeconds(3601)));
        cache.physicallyExpired = true;
        var coordinator = coordinator(cache, Runnable::run);

        assertThat(coordinator.getOrLoad(7L, () -> "new")).isEqualTo("new");
        assertThat(cache.entries.get(7L).value()).isEqualTo("new");
    }

    /**
     * Redis 读取故障时跳过缓存协调，直接执行实时查询。
     */
    @Test void cacheReadFailureFallsBackToLiveLoader() {
        var cache = new MemoryCache();
        cache.readFailure = true;
        var loads = new AtomicInteger();
        var coordinator = coordinator(cache, Runnable::run);

        assertThat(coordinator.getOrLoad(7L, () -> {
            loads.incrementAndGet();
            return "live";
        })).isEqualTo("live");
        assertThat(loads).hasValue(1);
        assertThat(cache.entries).isEmpty();
    }

    /**
     * 冷启动期间其他请求已持有锁时，等待者重读并使用刚写入的快照。
     */
    @Test void lockContenderUsesSnapshotWrittenByRebuilder() {
        var cache = new MemoryCache();
        cache.locked = true;
        var reads = new AtomicInteger();
        cache.onGet = () -> {
            if (reads.incrementAndGet() == 2)
                cache.entries.put(7L, new ShopDetailCacheEntry<>("rebuilt", now.plusSeconds(60)));
        };
        var coordinator = new ShopDetailCacheCoordinator<>(cache, Runnable::run,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(35), Duration.ofMinutes(60),
                Duration.ofMinutes(2), Duration.ofMillis(100), Duration.ofMillis(1));

        assertThat(coordinator.getOrLoad(7L, () -> "unexpected")).isEqualTo("rebuilt");
    }

    /**
     * 物理 TTL 抖动只会提前失效，且始终保持在逻辑有效期之后和配置上限以内。
     */
    @Test void physicalTtlJitterStaysWithinConfiguredBounds() {
        var cache = new MemoryCache();
        var coordinator = new ShopDetailCacheCoordinator<>(cache, Runnable::run,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(35), Duration.ofMinutes(60),
                Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(1), Duration.ofMillis(1));

        for (int i = 0; i < 64; i++) coordinator.refresh(7L, () -> "value");

        assertThat(cache.physicalTtls).allSatisfy(ttl -> {
            assertThat(ttl).isGreaterThanOrEqualTo(Duration.ofMinutes(55));
            assertThat(ttl).isLessThanOrEqualTo(Duration.ofMinutes(60));
        });
    }

    private ShopDetailCacheCoordinator<String> coordinator(ShopDetailCache<String> cache, Executor executor) {
        return new ShopDetailCacheCoordinator<>(cache, executor, Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(35), Duration.ofMinutes(60), Duration.ofMinutes(2),
                Duration.ofSeconds(5), Duration.ofMillis(5));
    }

    private static final class MemoryCache implements ShopDetailCache<String> {
        private final Map<Long, ShopDetailCacheEntry<String>> entries = new HashMap<>();
        private final List<Duration> physicalTtls = new ArrayList<>();
        private boolean physicallyExpired;
        private boolean locked;
        private boolean readFailure;
        private Runnable onGet;

        @Override public Optional<ShopDetailCacheEntry<String>> get(Long shopId) {
            if (readFailure) throw new ShopDetailCacheException("redis unavailable", null);
            if (onGet != null) onGet.run();
            if (physicallyExpired) return Optional.empty();
            return Optional.ofNullable(entries.get(shopId));
        }
        @Override public void put(Long shopId, String value, Instant logicalTtl, Duration physicalTtl) {
            physicalTtls.add(physicalTtl);
            entries.put(shopId, new ShopDetailCacheEntry<>(value, logicalTtl));
        }
        @Override public boolean tryLock(Long shopId, String token, Duration lease) {
            if (locked) return false;
            locked = true;
            return true;
        }
        @Override public void unlock(Long shopId, String token) { locked = false; }
    }
}
