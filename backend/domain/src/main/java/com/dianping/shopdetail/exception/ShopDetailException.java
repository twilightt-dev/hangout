package com.dianping.shopdetail.exception;
/** 应用入口可映射的错误；对外消息不包含底层异常详情。 */
public class ShopDetailException extends RuntimeException {
    private final int status;
    public ShopDetailException(int status, String message) { super(message); this.status = status; }
    public ShopDetailException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    public int status() { return status; }
}
