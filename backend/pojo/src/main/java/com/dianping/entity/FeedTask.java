package com.dianping.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Feed 异步任务实体，对应 tb_feed_task。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_feed_task")
public class FeedTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String kind;

    private Long blogId;

    private Long authorId;

    private Long userId;

    private String status;

    private Long cursorId;

    private Integer attempts;

    private LocalDateTime nextAttempt;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private String lastError;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
