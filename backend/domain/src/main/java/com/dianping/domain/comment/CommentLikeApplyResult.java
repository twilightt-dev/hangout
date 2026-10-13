package com.dianping.domain.comment;

/** applied=false 仅表示状态竞争且确定没有执行写入。 */
public record CommentLikeApplyResult(boolean applied, boolean isLike, long liked) {}
