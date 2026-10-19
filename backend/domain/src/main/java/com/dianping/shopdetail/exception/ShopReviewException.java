package com.dianping.shopdetail.exception;

/** 可对外解释的评价用例错误，不携带数据库信息。 */
public final class ShopReviewException extends RuntimeException {
    private final int status;
    public ShopReviewException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
