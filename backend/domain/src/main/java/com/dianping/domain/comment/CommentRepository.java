package com.dianping.domain.comment;

public interface CommentRepository {
    /** 插入一级评论并返回生成的 ID；返回 null 表示保存失败。 */
    Long insert(Comment comment);
    Comment findById(Long id);
}
