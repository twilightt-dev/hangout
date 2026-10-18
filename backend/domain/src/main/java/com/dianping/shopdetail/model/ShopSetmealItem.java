package com.dianping.shopdetail.model;

import java.math.BigDecimal;

/** 套餐内固定菜品，不与其他套餐共享菜品身份。 */
public record ShopSetmealItem(String name, BigDecimal quantity, String unit) {
    public ShopSetmealItem {
        if (name == null || name.isBlank() || unit == null || unit.isBlank()
                || quantity == null || quantity.signum() <= 0
                || quantity.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("套餐菜品数据不完整或不合法");
        }
    }
}
