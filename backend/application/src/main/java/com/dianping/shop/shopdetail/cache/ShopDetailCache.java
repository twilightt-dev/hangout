package com.dianping.shop.shopdetail.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 公共商详快照的读写端口，存在性判断和同店互斥由共享缓存保护端口负责。
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
     * 写入快照并设置 Redis 物理过期时间；调用方必须持有同店锁。
     * 实现须在发布快照时原子校验锁所有权，拒绝失锁后的旧任务回写。
     * @param shopId 店铺标识
     * @param value 公共快照
     * @param logicalTtl 逻辑过期时间
     * @param physicalTtl Redis 物理过期时间
     */
    void put(Long shopId, T value, Instant logicalTtl, Duration physicalTtl);
}
