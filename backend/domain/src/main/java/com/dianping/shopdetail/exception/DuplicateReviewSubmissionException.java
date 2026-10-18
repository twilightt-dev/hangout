package com.dianping.shopdetail.exception;

/** 唯一提交身份已存在；应用层须在失败事务回滚后重新判断内容。 */
public final class DuplicateReviewSubmissionException extends RuntimeException {
    public DuplicateReviewSubmissionException(Throwable cause) { super("评价提交身份重复", cause); }
}
