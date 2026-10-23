package com.dianping.shop.cache;

import java.time.Duration;

/**
 *
 */
public interface ShopCacheProtection {
    /** 用布隆过滤器判断是否存在，未就绪或故障时返回 true。 */
    boolean mayExist(Long shopId);


    /** 读取空值标记，空值标记存在返回true，不存在返回false，故障时按未命中处理。 */
    boolean isNotFound(Long shopId);

    //获取同店分布式锁，成功返回Lease，失败返回null，故障抛异常
    Lease tryLock(Long shopId, Duration wait);


    //把新增店铺id追加到过滤器，失败抛异常
    void add(Long shopId);


    //安全写缓存
    void publish(Long shopId, String key, String value, Duration ttl);


    //写空值标记
    void markNotFound(Long shopId);


    //安全清除缓存
    void evict(Long shopId);

    //成功获取同店锁之后交给调用方管理的锁句柄
    interface Lease extends AutoCloseable {
        /** 提交写事务前确认当前线程仍持有锁。 */
        void verifyOwnership();
        /** 释放仍属于本线程的锁；不得释放其他线程或新所有者的锁。 */
        @Override void close();
    }
}
