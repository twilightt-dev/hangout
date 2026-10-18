package com.dianping.shopdetail.exception;

/** 唯一约束竞争；应用层须在原事务回滚后判断是否为同一提交的重试。 */
public class DuplicateRecommendDishException extends RuntimeException {
    public DuplicateRecommendDishException(Throwable cause) { super("菜品或提交标识已存在", cause); }
}
