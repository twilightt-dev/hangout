package com.dianping.application.comment.query;

import java.time.LocalDateTime;

public record CommentView(Long id, Long blogId, Long userId, String userName, String userIcon,
                          String content, LocalDateTime createTime, LocalDateTime updateTime,
                          long liked, Boolean isLike, boolean likeAvailable) {}
