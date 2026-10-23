package com.dianping.shop.shopdetail.cache;

import com.dianping.shopdetail.exception.ShopDetailException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 缓存流程协调器，负责把读取缓存、获取重建锁回源数据库、异步刷新、失败降级组织成一套流程
 * @param <T> 缓存值的类型
 */
public class ShopDetailCacheCoordinator<T> {
    private static final Log log = LogFactory.getLog(ShopDetailCacheCoordinator.class);
    private final ShopDetailCache<T> cache;
    //后台执行异步刷新的执行器
    private final Executor refreshExecutor;
    //判断逻辑过期，计算新的逻辑过期时间
    private final Clock clock;
    private final Duration logicalTtl;
    private final Duration physicalTtl;
    //给物理ttl添加随机抖动，防止大量key同时失效导致的缓存雪崩
    private final Duration physicalTtlJitter;
    //重建锁的租约时间
    private final Duration lockLease;
    //没拿到重建锁最多等多久
    private final Duration waitTimeout;
    //等待期间的重试间隔
    private final Duration waitInterval;


    public ShopDetailCacheCoordinator(ShopDetailCache<T> cache, Executor refreshExecutor, Clock clock,
            Duration logicalTtl, Duration physicalTtl, Duration lockLease, Duration waitTimeout,
            Duration waitInterval) {
        this(cache, refreshExecutor, clock, logicalTtl, physicalTtl, Duration.ZERO, lockLease, waitTimeout,
                waitInterval);
    }

    /**
     * 创建带物理 TTL 单向抖动的缓存协调器。
     * @param cache 缓存和锁适配器
     * @param refreshExecutor 有界后台刷新执行器
     * @param clock 当前时间源
     * @param logicalTtl 逻辑有效期
     * @param physicalTtl Redis 物理 TTL 上限
     * @param physicalTtlJitter 物理 TTL 最大提前量，实际 TTL 在
     *        {@code physicalTtl - physicalTtlJitter} 到 {@code physicalTtl} 之间随机取值
     * @param lockLease 重建锁租约
     * @param waitTimeout 冷启动等待上限
     * @param waitInterval 冷启动重读间隔
     */
    public ShopDetailCacheCoordinator(ShopDetailCache<T> cache, Executor refreshExecutor, Clock clock,
            Duration logicalTtl, Duration physicalTtl, Duration physicalTtlJitter, Duration lockLease,
            Duration waitTimeout, Duration waitInterval) {
        this.cache = cache;
        this.refreshExecutor = refreshExecutor;
        this.clock = clock;
        this.logicalTtl = positive(logicalTtl, "逻辑有效期");
        this.physicalTtl = positive(physicalTtl, "物理 TTL");
        this.physicalTtlJitter = nonNegative(physicalTtlJitter, "物理 TTL 抖动");
        this.lockLease = positive(lockLease, "重建锁租约");
        this.waitTimeout = positive(waitTimeout, "冷启动等待上限");
        this.waitInterval = positive(waitInterval, "冷启动重读间隔");
        if (physicalTtl.minus(physicalTtlJitter).compareTo(logicalTtl) <= 0)
            throw new IllegalArgumentException("物理 TTL 抖动后的最小值必须大于逻辑ttl");
    }

    /**
     * 缓存优先读取快照，逻辑过期时尝试异步刷新，物理过期后重建缓存
     * @param shopId 店铺标识
     * @param loader 重建公共快照的函数
     * @return 缓存或回源取得的快照
     */
    public T getOrLoad(Long shopId, Supplier<T> loader) {
        ShopDetailCacheEntry<T> entry;
        try {
            entry = cache.get(shopId).orElse(null);
        } catch (ShopDetailCacheException cacheFailure) {
            log.warn("商详缓存读取失败，回退实时查询，shopId=" + shopId, cacheFailure);
            return loader.get();
        }
        //缓存命中
        if (entry != null) {
            //没到期就返回
            if (!isExpired(entry)) return entry.value();
            //到期了就异步刷新
            scheduleRefresh(shopId, loader);
            //返回旧数据
            return entry.value();
        }
        //缓存未命中就回源数据库
        return loadOnCacheMiss(shopId, loader);
    }

    /**
     * 同步刷新缓存，适用于定时预热、主动更新缓存的场景
     * 先获取重建锁，获取成功再写缓存
     * @param shopId 店铺标识
     * @param loader 调用方提供的查询逻辑，获取新的公共快照
     * @return 成功取得锁并完成重建时返回 true
     */
    public boolean refresh(Long shopId, Supplier<T> loader) {
        String token = UUID.randomUUID().toString();
        if (!cache.tryLock(shopId, token, lockLease)) return false;
        try {
            T value = loader.get();
            cache.put(shopId, value, clock.instant().plus(logicalTtl), jitteredPhysicalTtl());
            return true;
        } finally {
            cache.unlock(shopId, token);
        }
    }

    /**
     * 适用于请求读到逻辑过期的数据，返回旧的，异步刷新的场景
     * @param shopId 店铺标识
     * @param loader 外部传入的查询逻辑
     */
    private void scheduleRefresh(Long shopId, Supplier<T> loader) {
        String token = UUID.randomUUID().toString();
        try {
            if (!cache.tryLock(shopId, token, lockLease)) return;
            try {
                refreshExecutor.execute(() -> {
                    try {
                        T value = loader.get();
                        cache.put(shopId, value, clock.instant().plus(logicalTtl), physicalTtl);
                    } catch (RuntimeException failure) {
                        log.warn("商详异步刷新失败，保留旧快照，shopId=" + shopId, failure);
                    } finally {
                        unlockAfterRequest(shopId, token);
                    }
                });
            } catch (RuntimeException rejected) {
                unlockAfterRequest(shopId, token);
                log.warn("商详异步刷新未能提交，保留旧快照，shopId=" + shopId, rejected);
            }
        } catch (ShopDetailCacheException cacheFailure) {
            log.warn("商详异步刷新锁不可用，继续使用旧快照，shopId=" + shopId, cacheFailure);
        }
    }

    /**
     * 缓存未命中时，通过单店重建锁控制回源，避免大量请求同时查询数据库
     * @param shopId 店铺标识
     * @param loader 调用方传入的查询逻辑
     * @return T类型的缓存数据
     */
    private T loadOnCacheMiss(Long shopId, Supplier<T> loader) {
        String token = UUID.randomUUID().toString();
        try {
            if (cache.tryLock(shopId, token, lockLease)) {
                try {
                    //进行double check，因为读取缓存和拿到锁之间有空隙，别的线程可能完成了重建
                    ShopDetailCacheEntry<T> secondRead = cache.get(shopId).orElse(null);
                    //缓存已重建就返回
                    if (secondRead != null && isExpired(secondRead)) return secondRead.value();
                    //没重建就回源数据库
                    T value = loader.get();
                    try {
                        //写入缓存
                        cache.put(shopId, value, clock.instant().plus(logicalTtl), jitteredPhysicalTtl());
                    } catch (ShopDetailCacheException cacheFailure) {
                        //没有抛异常所以会继续执行下面的return value
                        log.warn("商详缓存写入失败，返回实时查询结果，shopId=" + shopId, cacheFailure);
                    }
                    return value;
                } finally {
                    unlockAfterRequest(shopId, token);
                }
            }
            //没抢到锁的进入等待重建的方法
            return awaitRebuild(shopId);
        } catch (ShopDetailCacheException cacheFailure) {
            log.warn("商详缓存协调失败，回退实时查询，shopId=" + shopId, cacheFailure);
            return loader.get();
        }
    }

    /**
     * 对于没拿到重建锁的请求，进行等待
     * @param shopId  店铺标识
     * @return T类型的缓存数据
     */
    private T awaitRebuild(Long shopId) {
        //计算等待截止时间
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        //在截止时间前周期性的读取缓存
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(waitInterval.toMillis());
            } catch (InterruptedException interrupted) {
                //sleep被中断抛出InterruptedException并清除interrupt标记，需要手动复位
                Thread.currentThread().interrupt();
                throw unavailable();
            }
            ShopDetailCacheEntry<T> entry = cache.get(shopId).orElse(null);
            if (entry != null) {
                return entry.value() ;
            }
        }
        throw unavailable();
    }



    //工具方法，判断是否逻辑过期
    private boolean isExpired(ShopDetailCacheEntry<T> entry) {
        return !clock.instant().isBefore(entry.logicalTtl());
    }

    /**
     * 计算本次写入使用的物理 TTL
     * 抖动的目的是防止大量key同时失效导致的缓存雪崩
     * @return 本次写入的 Redis 物理 TTL
     */
    private Duration jitteredPhysicalTtl() {
        long jitterMillis = physicalTtlJitter.toMillis();
        if (jitterMillis == 0) return physicalTtl;
        long reduction = ThreadLocalRandom.current().nextLong(jitterMillis + 1);
        return physicalTtl.minusMillis(reduction);
    }

    //工具方法，释放重建锁
    private void unlockAfterRequest(Long shopId, String token) {
        try {
            cache.unlock(shopId, token);
        } catch (ShopDetailCacheException failure) {
            log.warn("商详重建锁释放失败，等待锁租约自动释放，shopId=" + shopId, failure);
        }
    }

    //返回异常对象
    private static ShopDetailException unavailable() {
        return new ShopDetailException(503, "商详暂时不可用，请稍后重试");
    }

    //工具方法，保证value为正数
    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + "必须为正数");
        return value;
    }

    //工具方法，保证value>=0,不是负数
    private static Duration nonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) throw new IllegalArgumentException(name + "不能为负数");
        return value;
    }
}
