package com.dianping.shopdetail.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺评价表的持久化映射，字段形态对应数据库存储。
 */
@Data
@TableName("tb_shop_review")
public class ShopReview {
    /**
     * 记录标识。
     */
    @TableId private Long id;
    /**
     * 店铺标识。
     */
    /**
     * 服务端确认的用户标识。
     */
    private Long shopId, userId;
    /**
     * 客户端生成的 UUID 提交标识，用于幂等校验。
     */
    /**
     * 正文内容。
     */
    /**
     * 预设评价标签的 JSON 存储文本。
     */
    private String requestId, content, tags;
    /**
     * 口味评分，按实际分数的十倍保存。
     */
    /**
     * 服务评分，按实际分数的十倍保存。
     */
    /**
     * 独立总体评分，按实际分数的十倍保存。
     */
    /**
     * 环境评分，按实际分数的十倍保存。
     */
    private Integer overallScore, tasteScore, environmentScore, serviceScore;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
    /**
     * 关联查询得到的当前资料，不写入评价表。
     */
    @TableField(exist = false)
    private String userName;
    /**
     * 作者当前头像。
     */
    @TableField(exist = false)
    private String userIcon;
}
