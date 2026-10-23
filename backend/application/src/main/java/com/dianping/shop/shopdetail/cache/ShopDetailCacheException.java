package com.dianping.shop.shopdetail.cache;

/**
 * 商详缓存或重建锁适配器不可用。
 */
public class ShopDetailCacheException extends RuntimeException {
    /**
     * 创建缓存适配器故障。
     *
     * @param message 固定故障说明
     * @param cause 底层故障
     */
    public ShopDetailCacheException(String message, Throwable cause) { super(message, cause); }
}
