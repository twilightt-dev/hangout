package com.dianping.application.comment.like;

public class CommentLikeUnavailableException extends RuntimeException {
    public CommentLikeUnavailableException() { super("点赞服务暂时不可用"); }
    public CommentLikeUnavailableException(Throwable cause) { super("点赞服务暂时不可用", cause); }
}
