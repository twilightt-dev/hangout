package com.dianping.shop.shopdetail.exception;

/**
 * 推荐菜用例的业务错误，由 HTTP 入口转换为相应状态。
 */
public class RecommendDishException extends RuntimeException {
    /**
     * 业务或节点状态。
     */
    private final int status;
    /**
     * 创建 RecommendDishException，初始化所需依赖及实例状态。
     *
     * @param status 业务或节点状态
     * @param message 对外展示的固定提示
     */
    public RecommendDishException(int status, String message) { super(message); this.status = status; }
    /**
     * 取得供外层统一异常处理使用的状态码。
     *
     * @return 外层异常处理使用的状态码
     */
    public int status() { return status; }
}
