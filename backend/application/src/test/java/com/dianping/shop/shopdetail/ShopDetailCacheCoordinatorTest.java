package com.dianping.shop.shopdetail;

import com.dianping.shop.cache.ShopCacheAccessException;
import com.dianping.shop.cache.ShopCacheProtection;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.dianping.shop.shopdetail.exception.ShopDetailException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 验证公共商详快照逻辑过期刷新和冷启动互斥回源行为。
 */
class ShopDetailCacheCoordinatorTest {
    private final Instant now = Instant.parse("2026-10-22T12:00:00Z");
    private final ShopCacheProtection protection = mock(ShopCacheProtection.class);
    private final ShopCacheProtection.Lease lease = mock(ShopCacheProtection.Lease.class);
    private final Set<Long> absent = new HashSet<>();

    /** 独立提供共享保护端口，快照测试适配器仅负责存取数据。 */
    @BeforeEach void configureProtection() {
        when(protection.mayExist(anyLong())).thenReturn(true);
        when(protection.tryLock(anyLong(), eq(Duration.ZERO))).thenReturn(lease);
        when(protection.isNotFound(anyLong())).thenAnswer(call -> absent.contains(call.getArgument(0)));
        doAnswer(call -> { absent.add(call.getArgument(0)); return null; })
                .when(protection).markNotFound(anyLong());
    }

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
        when(protection.tryLock(7L, Duration.ZERO)).thenReturn(null);
        var reads = new AtomicInteger();
        cache.onGet = () -> {
            if (reads.incrementAndGet() == 2)
                cache.entries.put(7L, new ShopDetailCacheEntry<>("rebuilt", now.plusSeconds(60)));
        };
        var coordinator = new ShopDetailCacheCoordinator<>(cache, protection, Runnable::run,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(35), Duration.ofMinutes(60),
                Duration.ofMillis(100), Duration.ofMillis(1));

        assertThat(coordinator.getOrLoad(7L, () -> "unexpected")).isEqualTo("rebuilt");
    }

    /**
     * 物理 TTL 抖动只会提前失效，且始终保持在逻辑有效期之后和配置上限以内。
     */
    @Test void physicalTtlJitterStaysWithinConfiguredBounds() {
        var cache = new MemoryCache();
        var coordinator = new ShopDetailCacheCoordinator<>(cache, protection, Runnable::run,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(35), Duration.ofMinutes(60),
                Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofMillis(1));

        for (int i = 0; i < 64; i++) coordinator.refresh(7L, () -> "value");

        assertThat(cache.physicalTtls).allSatisfy(ttl -> {
            assertThat(ttl).isGreaterThanOrEqualTo(Duration.ofMinutes(55));
            assertThat(ttl).isLessThanOrEqualTo(Duration.ofMinutes(60));
        });
    }

    /**
     * 过滤器确定不存在时，不启动聚合查询，也不写入普通快照。
     */
    @Test void bloomNegativeDoesNotLoadDatabase() {
        var cache = new MemoryCache();
        when(protection.mayExist(99L)).thenReturn(false);
        var coordinator = coordinator(cache, Runnable::run);
        var loads = new AtomicInteger();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.getOrLoad(99L, () -> {
            loads.incrementAndGet();
            return "unexpected";
        })).isInstanceOf(ShopDetailException.class)
          .satisfies(error -> assertThat(((ShopDetailException) error).status()).isEqualTo(404));
        assertThat(loads).hasValue(0);
        assertThat(cache.entries).isEmpty();
    }

    /** 明确查无店铺后写共享标记，下一次请求不再回源。 */
    @Test void databaseNotFoundIsCachedButDependencyFailureIsNot() {
        var cache = new MemoryCache();
        var coordinator = coordinator(cache, Runnable::run);
        var loads = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.getOrLoad(7L, () -> {
                loads.incrementAndGet();
                throw new ShopDetailException(404, "店铺不存在");
            })).isInstanceOf(ShopDetailException.class);
        }
        assertThat(loads).hasValue(1);
        assertThat(absent).contains(7L);
        var failedCache = new MemoryCache();
        var failed = coordinator(failedCache, Runnable::run);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> failed.getOrLoad(8L, () -> {
            throw new ShopDetailException(503, "数据库故障");
        })).isInstanceOf(ShopDetailException.class);
        assertThat(absent).doesNotContain(8L);
    }

    /** 异步任务排队期间不持锁，防止跨线程解锁并避免占用数据库更新资格。 */
    @Test void queuedRefreshDoesNotHoldShopLock() {
        var cache = new MemoryCache();
        cache.entries.put(7L, new ShopDetailCacheEntry<>("old", now.minusSeconds(1)));
        Runnable[] queued = new Runnable[1];
        var coordinator = coordinator(cache, command -> queued[0] = command);
        assertThat(coordinator.getOrLoad(7L, () -> "new")).isEqualTo("old");
        verifyNoInteractions(protection);
        when(protection.tryLock(7L, Duration.ZERO)).thenReturn(null);
        queued[0].run();
        assertThat(cache.entries.get(7L).value()).isEqualTo("old");
    }

    /** 等待其他持锁者期间 Redis 故障，仍应实时回源且不能发布缓存。 */
    @Test void redisFailureWhileWaitingFallsBackWithoutPublication() {
        var cache = new MemoryCache();
        when(protection.tryLock(7L, Duration.ZERO)).thenReturn(null);
        cache.onGet = () -> cache.readFailure = true;
        var loads = new AtomicInteger();
        assertThat(coordinator(cache, Runnable::run).getOrLoad(7L, () -> {
            loads.incrementAndGet();
            return "live";
        })).isEqualTo("live");
        assertThat(loads).hasValue(1);
        assertThat(cache.entries).isEmpty();
    }

    /** 前台保留已经取得的结果，预热写失败必须传播以便调度器终止故障轮次。 */
    @Test void publicationFailureStopsWarmupButKeepsForegroundResult() {
        var cache = new MemoryCache();
        cache.writeFailure = true;
        var coordinator = coordinator(cache, Runnable::run);
        assertThat(coordinator.getOrLoad(7L, () -> "live")).isEqualTo("live");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.refresh(7L, () -> "live"))
                .isInstanceOf(ShopDetailCacheException.class);
        verify(lease, times(2)).close();
    }

    /** 新鲜快照直接返回，不能被过滤器或空值判断影响，也无需争抢重建锁。 */
    @Test void freshSnapshotBypassesProtectionAndDatabase() {
        var cache = new MemoryCache();
        cache.entries.put(7L, new ShopDetailCacheEntry<>("cached", now.plusSeconds(60)));
        assertThat(coordinator(cache, Runnable::run).getOrLoad(7L, () -> {
            throw new AssertionError("命中新鲜快照不应回源");
        })).isEqualTo("cached");
        verifyNoInteractions(protection);
    }

    /** 共享锁服务不可用时只回源，不发布快照或不存在标记。 */
    @Test void protectionLockFailureFallsBackWithoutPublication() {
        var cache = new MemoryCache();
        when(protection.tryLock(7L, Duration.ZERO))
                .thenThrow(new ShopCacheAccessException("测试锁服务不可用", null));
        assertThat(coordinator(cache, Runnable::run).getOrLoad(7L, () -> "live")).isEqualTo("live");
        assertThat(cache.entries).isEmpty();
        assertThat(absent).isEmpty();
        verifyNoInteractions(lease);
    }

    /** 创建带固定时钟的应用层测试入口。 */
    private ShopDetailCacheCoordinator<String> coordinator(ShopDetailCache<String> cache, Executor executor) {
        return new ShopDetailCacheCoordinator<>(cache, protection, executor, Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(35), Duration.ofMinutes(60),
                Duration.ofSeconds(5), Duration.ofMillis(5));
    }

    /** 只模拟快照存储及其读写故障，不承担过滤器、空值或锁职责。 */
    private static final class MemoryCache implements ShopDetailCache<String> {
        private final Map<Long, ShopDetailCacheEntry<String>> entries = new HashMap<>();
        private final List<Duration> physicalTtls = new ArrayList<>();
        private boolean physicallyExpired;
        private boolean readFailure;
        private boolean writeFailure;
        private Runnable onGet;

        /** 按测试设定模拟快照命中、物理过期和读取失败。 */
        @Override public Optional<ShopDetailCacheEntry<String>> get(Long shopId) {
            if (readFailure) throw new ShopDetailCacheException("redis unavailable", null);
            if (onGet != null) onGet.run();
            if (physicallyExpired) return Optional.empty();
            return Optional.ofNullable(entries.get(shopId));
        }
        /** 保存快照及实际物理 TTL，允许测试注入写入故障。 */
        @Override public void put(Long shopId, String value, Instant logicalTtl, Duration physicalTtl) {
            if (writeFailure) throw new ShopDetailCacheException("测试写入故障", null);
            physicalTtls.add(physicalTtl);
            entries.put(shopId, new ShopDetailCacheEntry<>(value, logicalTtl));
        }
    }
}
