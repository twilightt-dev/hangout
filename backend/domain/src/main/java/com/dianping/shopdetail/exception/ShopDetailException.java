package com.dianping.shopdetail.exception;
/**
 * 应用入口可映射的错误；对外消息不包含底层异常详情。
 */
public class ShopDetailException extends RuntimeException {
    /**
     * 业务或节点状态。
     */
    private final int status;
    /**
     * 创建 ShopDetailException，初始化所需依赖及实例状态。
     *
     * @param status 业务或节点状态
     * @param message 对外展示的固定提示
     */
    public ShopDetailException(int status, String message) { super(message); this.status = status; }
    /**
     * 创建 ShopDetailException，初始化所需依赖及实例状态。
     *
     * @param status 业务或节点状态
     * @param message 对外展示的固定提示
     * @param cause 失败原因
     */
    public ShopDetailException(int status, String message, Throwable cause) { super(message, cause); this.status = status; }
    /**
     * 取得供外层统一异常处理使用的状态码。
     *
     * @return 外层异常处理使用的状态码
     */
    public int status() { return status; }
}
