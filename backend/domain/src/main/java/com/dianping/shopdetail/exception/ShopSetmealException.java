package com.dianping.shopdetail.exception;

/** 套餐查询用例中可明确反馈的业务错误。 */
public class ShopSetmealException extends RuntimeException {
    private final int status;

    public ShopSetmealException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
