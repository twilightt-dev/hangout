package com.dianping.shopdetail.persistence.dataobject;

import lombok.Data;

/** 套餐主表的持久化数据。 */
@Data
public class ShopSetmeal {
    private Long id;
    private Long shopId;
    private String name;
    private String coverImage;
    private Long price;
    private Long originalPrice;
    private Integer people;
    private String description;
    private String rules;
    private Integer status;
    private Integer sort;
}
