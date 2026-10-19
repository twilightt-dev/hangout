package com.dianping.shopdetail.persistence.dataobject;

import lombok.Data;

/**
 * 套餐主表的持久化数据。
 */
@Data
public class ShopSetmeal {
    /**
     * 记录标识。
     */
    private Long id;
    /**
     * 店铺标识。
     */
    private Long shopId;
    /**
     * 名称。
     */
    private String name;
    /**
     * 已上传的封面图片路径。
     */
    private String coverImage;
    /**
     * 套餐支付金额，单位为分。
     */
    private Long price;
    /**
     * 套餐原价，单位为分。
     */
    private Long originalPrice;
    /**
     * 适用人数。
     */
    private Integer people;
    /**
     * 说明文本。
     */
    private String description;
    /**
     * 使用规则。
     */
    private String rules;
    /**
     * 套餐上架状态，参与公开查询的过滤。
     */
    private Integer status;
    /**
     * 展示排序值。
     */
    private Integer sort;
}
