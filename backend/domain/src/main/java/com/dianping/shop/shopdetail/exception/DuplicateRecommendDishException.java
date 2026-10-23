package com.dianping.shop.shopdetail.exception;

/**
 * 唯一约束竞争；应用层须在原事务回滚后判断是否为同一提交的重试。
 */
public class DuplicateRecommendDishException extends RuntimeException {
    /**
     * 创建 DuplicateRecommendDishException，初始化所需依赖及实例状态。
     *
     * @param cause 失败原因
     */
    public DuplicateRecommendDishException(Throwable cause) { super("菜品或提交标识已存在", cause); }
}
