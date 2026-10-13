package com.dianping.domain.comment;

import java.util.Objects;

/** 一个用户对一条评论的关系快照，持久化时由仓储验证并发变化。 */
public final class CommentLike {
    private final CommentLikeId id;
    private final boolean liked;
    private CommentLike(CommentLikeId id, boolean liked) { this.id = Objects.requireNonNull(id); this.liked = liked; }
    public static CommentLike restore(CommentLikeId id, boolean liked) { return new CommentLike(id, liked); }
    public CommentLikeId id() { return id; }
    public boolean isLike() { return liked; }
    public CommentLikeChange toggle() { return new CommentLikeChange(id, liked, !liked); }
    @Override public boolean equals(Object other) { return this == other || other instanceof CommentLike that && id.equals(that.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
