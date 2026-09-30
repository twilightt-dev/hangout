package com.dianping.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long blogId;
    private Long userId;
    private String userName;
    private String userIcon;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
