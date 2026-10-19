package com.dianping.shopdetail.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 菜品表映射；名称二进制键避免数据库排序规则误合并不同名称。
 */
@Data
@TableName("tb_recommend_dish")
public class RecommendDish {
    /**
     * 记录标识。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 店铺标识。
     */
    private Long shopId;
    /**
     * 服务端确认的用户标识。
     */
    private Long userId;
    /**
     * 客户端生成的 UUID 提交标识，用于幂等校验。
     */
    private String requestId;
    /**
     * 名称。
     */
    private String name;
    /**
     * 用于精确去重的 UTF-8 菜名二进制键。
     */
    private byte[] nameKey;
    /**
     * 已上传的封面图片路径。
     */
    private String coverImage;
    /**
     * 不同用户的推荐人数。
     */
    private Long recommendCount;
    /**
     * 创建时间。
     */
    private LocalDateTime createTime;
}
