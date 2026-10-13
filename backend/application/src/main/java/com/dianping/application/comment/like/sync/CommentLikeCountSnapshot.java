package com.dianping.application.comment.like.sync;

public record CommentLikeCountSnapshot(Long commentId, long epoch, long version, long liked) {}
