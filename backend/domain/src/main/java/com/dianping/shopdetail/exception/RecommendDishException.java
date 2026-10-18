package com.dianping.shopdetail.exception;

/** 推荐菜用例的业务错误，由 HTTP 入口转换为相应状态。 */
public class RecommendDishException extends RuntimeException {
    private final int status;
    public RecommendDishException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
