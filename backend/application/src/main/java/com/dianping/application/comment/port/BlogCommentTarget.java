package com.dianping.application.comment.port;

//判断博客是否存在，增加评论数
public interface BlogCommentTarget {
    boolean exists(Long blogId);
    boolean incrementCount(Long blogId);
}
