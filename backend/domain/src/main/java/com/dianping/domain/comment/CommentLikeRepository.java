package com.dianping.domain.comment;

public interface CommentLikeRepository {
    CommentLike findState(CommentLikeId id, long epoch, boolean allowInitialize);
    CommentLikeApplyResult tryApply(CommentLikeChange change, long epoch);
}
