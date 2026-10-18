package com.dianping.shopdetail.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 菜品表映射；名称二进制键避免数据库排序规则误合并不同名称。 */
@Data
@TableName("tb_recommend_dish")
public class RecommendDish {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private String requestId;
    private String name;
    private byte[] nameKey;
    private String coverImage;
    private Long recommendCount;
    private LocalDateTime createTime;
}
