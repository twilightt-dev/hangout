package com.dianping.shopdetail.persistence.dataobject;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 套餐明细表的持久化数据。
 */
@Data
public class ShopSetmealItem {
    /**
     * 记录标识。
     */
    private Long id;
    /**
     * 所属套餐标识。
     */
    private Long setmealId;
    /**
     * 名称。
     */
    private String name;
    /**
     * 菜品数量，最多两位小数。
     */
    private BigDecimal quantity;
    /**
     * 数量单位。
     */
    private String unit;
    /**
     * 展示排序值。
     */
    private Integer sort;
}
