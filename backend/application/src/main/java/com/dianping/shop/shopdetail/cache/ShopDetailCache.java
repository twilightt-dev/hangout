package com.dianping.shop.shopdetail.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 公共商详快照与单店重建锁的应用层端口。
 *
 * @param <T> 缓存快照类型
 */
public interface ShopDetailCache<T> {
    /**
     * 读取尚未物理过期的商详快照。
     * @param shopId 店铺标识
     * @return 快照及逻辑过期时间；无快照时为空，Optional安全处理null
     */
    Optional<ShopDetailCacheEntry<T>> get(Long shopId);

    /**
     * 写入快照并设置 Redis 物理过期时间。
     * @param shopId 店铺标识
     * @param value 公共快照
     * @param logicalTtl 逻辑过期时间
     * @param physicalTtl Redis 物理过期时间
     */
    void put(Long shopId, T value, Instant logicalTtl, Duration physicalTtl);

    /**
     * 原子取得当前店铺的重建锁。
     * @param shopId 店铺标识
     * @param token 锁所有者标记
     * @param lease 锁租约
     * @return 是否成功取得锁
     */
    boolean tryLock(Long shopId, String token, Duration lease);

    /**
     * 仅释放仍属于指定 token 的重建锁。
     * @param shopId 店铺标识
     * @param token 锁所有者标记
     */
    void unlock(Long shopId, String token);
}
