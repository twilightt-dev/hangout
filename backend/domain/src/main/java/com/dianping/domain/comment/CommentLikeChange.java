package com.dianping.domain.comment;

import java.util.Objects;

public record CommentLikeChange(CommentLikeId id, boolean expectedLiked, boolean targetLiked) {
    public CommentLikeChange {
        Objects.requireNonNull(id);
        if (expectedLiked == targetLiked) throw new IllegalArgumentException("点赞切换必须改变状态");
    }
}
