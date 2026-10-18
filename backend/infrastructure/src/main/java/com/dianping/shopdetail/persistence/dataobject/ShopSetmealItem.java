package com.dianping.shopdetail.persistence.dataobject;

import lombok.Data;
import java.math.BigDecimal;

/** 套餐明细表的持久化数据。 */
@Data
public class ShopSetmealItem {
    private Long id;
    private Long setmealId;
    private String name;
    private BigDecimal quantity;
    private String unit;
    private Integer sort;
}
