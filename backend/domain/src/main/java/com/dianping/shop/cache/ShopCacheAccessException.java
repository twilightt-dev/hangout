package com.dianping.shop.cache;

/** 缓存协调无法可靠完成时的技术异常，读取可降级，写事务应回滚。 */
public class ShopCacheAccessException extends RuntimeException {
    /** 创建带原因的缓存协调异常。 */
    public ShopCacheAccessException(String message, Throwable cause) { super(message, cause); }
}
