package com.dianping.domain.comment;

public record CommentLikeId(Long commentId, Long userId) {
    public CommentLikeId {
        if (commentId == null || commentId <= 0) throw new CommentRuleViolation("非法评论Id");
        if (userId == null || userId <= 0) throw new CommentRuleViolation("请先登录");
    }
}
