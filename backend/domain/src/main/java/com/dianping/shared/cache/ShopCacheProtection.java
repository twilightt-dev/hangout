package com.dianping.shared.cache;

import java.time.Duration;

/**
 * 两个店铺详情入口共享的存在性判断、空值缓存和同店互斥端口。
 * 技术实现必须在发布缓存时原子检查锁所有权，故障读取允许放行。
 */
public interface ShopCacheProtection {
    /** 判断 ID 是否可能存在；未就绪或故障时返回 true。 */
    boolean mayExist(Long shopId);
    /** 读取共享不存在标记；故障时按未命中处理。 */
    boolean isNotFound(Long shopId);
    /** 有界尝试获取同店锁；竞争超时返回 null，连接故障抛出异常。 */
    Lease tryLock(Long shopId, Duration wait);
    /** 在新增事务提交前把 ID 追加至过滤器；失败必须抛出异常。 */
    void add(Long shopId);
    /** 仅持锁线程可发布正常缓存，同时清除共享不存在标记。 */
    void publish(Long shopId, String key, String value, Duration ttl);
    /** 仅持锁线程可发布不存在标记，并移除两个入口的旧快照。 */
    void markNotFound(Long shopId);
    /** 仅持锁线程可清理两个入口的旧值及不存在标记；失败抛出异常。 */
    void evict(Long shopId);

    /** 同店锁的线程所有权句柄，由取得锁的线程关闭。 */
    interface Lease extends AutoCloseable {
        /** 提交写事务前确认当前线程仍持有锁。 */
        void verifyOwnership();
        /** 释放仍属于本线程的锁；不得释放其他线程或新所有者的锁。 */
        @Override void close();
    }
}
