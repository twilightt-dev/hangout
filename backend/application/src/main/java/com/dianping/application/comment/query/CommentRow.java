package com.dianping.application.comment.query;

import java.time.LocalDateTime;

public record CommentRow(Long id, Long blogId, Long userId, String content,
                         LocalDateTime createTime, LocalDateTime updateTime, long liked) {
    public CommentRow(Long id, Long blogId, Long userId, String content, LocalDateTime createTime, LocalDateTime updateTime) {
        this(id, blogId, userId, content, createTime, updateTime, 0);
    }
}
