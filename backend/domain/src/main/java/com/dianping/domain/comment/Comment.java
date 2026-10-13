package com.dianping.domain.comment;

public final class Comment {
    private final Long id;
    private final Long blogId;
    private final Long authorId;
    private final CommentContent content;
    private final Long parentId;
    private final Long answerId;
    private final Integer status;

    private Comment(Long id, Long blogId, Long authorId, CommentContent content,
                    Long parentId, Long answerId, Integer status) {
        if (blogId == null || blogId <= 0) {
            throw new CommentRuleViolation("非法博客Id");
        }
        if (authorId == null) {
            throw new CommentRuleViolation("请先登录");
        }
        if (content == null) {
            throw new CommentRuleViolation("评论内容不能为空");
        }
        this.id = id;
        this.blogId = blogId;
        this.authorId = authorId;
        this.content = content;
        this.parentId = parentId;
        this.answerId = answerId;
        this.status = status;
    }

    public static Comment create(Long blogId, Long authorId, CommentContent content) {
        return new Comment(null, blogId, authorId, content, 0L, 0L, 0);
    }

    public static Comment restore(Long id, Long blogId, Long authorId, CommentContent content,
                                  Long parentId, Long answerId, Integer status) {
        if (id == null || id <= 0) throw new CommentRuleViolation("非法评论Id");
        return new Comment(id, blogId, authorId, content, parentId, answerId, status);
    }
    public void ensureLikeable() {
        if (id == null || !Long.valueOf(0).equals(parentId) || (status != null && status != 0)) {
            throw new CommentRuleViolation("评论不允许点赞");
        }
    }
    public Long id() { return id; }
    public Long blogId() { return blogId; }
    public Long authorId() { return authorId; }
    public CommentContent content() { return content; }
    public Long parentId() { return parentId; }
    public Long answerId() { return answerId; }
    public Integer status() { return status; }
    @Override public boolean equals(Object other) {
        return this == other || (id != null && other instanceof Comment that && id.equals(that.id));
    }
    @Override public int hashCode() { return id == null ? System.identityHashCode(this) : id.hashCode(); }
}
