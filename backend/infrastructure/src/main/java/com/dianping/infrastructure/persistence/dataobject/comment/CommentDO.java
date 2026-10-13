package com.dianping.infrastructure.persistence.dataobject.comment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 评论表映射，仅用于持久化；评论业务规则由领域模型维护。 */
@Data
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class CommentDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 数据库自增主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 评论作者标识。 */
    private Long userId;

    /** 评论所属博客标识。 */
    private Long blogId;

    /** 关联的一级评论标识，一级评论自身取 0。 */
    private Long parentId;

    /** 被回复的评论标识，一级评论取 0。 */
    private Long answerId;

    /** 评论内容。 */
    private String content;

    /** 点赞计数投影，使用 Long 覆盖 MySQL unsigned INT 的完整范围。 */
    private Long liked;
    /** 0 表示历史未核对；正数表示已启用的恢复代次。 */
    private Long likeEpoch;
    private Long likeVersion;
    /** 已完成 Redis 初始化；数据缺失后不得自动从零重建。 */
    private Boolean likeInitialized;

    /** 状态：0 正常，1 被举报，2 禁止查看；保留历史 NULL 状态。 */
    private Integer status;

    /** 创建时间，创建时沿用数据库默认值。 */
    private LocalDateTime createTime;

    /** 更新时间，创建时沿用数据库默认值。 */
    private LocalDateTime updateTime;
}
