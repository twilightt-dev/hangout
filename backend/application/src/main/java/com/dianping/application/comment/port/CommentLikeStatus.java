package com.dianping.application.comment.port;

public record CommentLikeStatus(Boolean isLike, boolean available) {
    public static CommentLikeStatus unavailable(Long userId) { return new CommentLikeStatus(userId == null ? false : null, false); }
}
