package com.dianping.shop.shopdetail.cache;

import java.time.Duration;
import com.dianping.shared.cache.ShopCacheProtection;
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

    /** 判断过滤器是否允许当前 ID 回源，故障时放行。 */
    boolean mayExist(Long shopId);
    /** 读取共享不存在标记。 */
    boolean isNotFound(Long shopId);
    /** 明确查无店铺后发布短期不存在标记，必须持有同店锁。 */
    void markNotFound(Long shopId);
    /** 尝试获取同店锁，使用调用线程的所有权。 */
    ShopCacheProtection.Lease tryLock(Long shopId);
}
