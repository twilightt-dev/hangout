package com.dianping.shop.shopdetail.exception;

/**
 * 唯一提交身份已存在；应用层须在失败事务回滚后重新判断内容。
 */
public final class DuplicateReviewSubmissionException extends RuntimeException {
    /**
     * 创建 DuplicateReviewSubmissionException，初始化所需依赖及实例状态。
     *
     * @param cause 失败原因
     */
    public DuplicateReviewSubmissionException(Throwable cause) { super("评价提交身份重复", cause); }
}
