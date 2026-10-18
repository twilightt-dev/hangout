package com.dianping.shopdetail.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_shop_review")
public class ShopReview {
    @TableId private Long id;
    private Long shopId, userId;
    private String requestId, content, tags;
    private Integer overallScore, tasteScore, environmentScore, serviceScore;
    private LocalDateTime createTime;
    /** 关联查询得到的当前资料，不写入评价表。 */
    @TableField(exist = false)
    private String userName;
    @TableField(exist = false)
    private String userIcon;
}
