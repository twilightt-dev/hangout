package com.dianping.shopdetail.model;

import java.math.BigDecimal;

/**
 * 套餐内固定菜品，不与其他套餐共享菜品身份。
 *
 * @param name 名称
 * @param quantity 菜品数量，最多两位小数
 * @param unit 数量单位
 */
public record ShopSetmealItem(String name, BigDecimal quantity, String unit) {
    /**
     * 创建并校验ShopSetmealItem，拒绝不满足业务约束的数据。
     *
     * @param name 名称
     * @param quantity 菜品数量，最多两位小数
     * @param unit 数量单位
     * @throws IllegalStateException 套餐或菜品数据不满足领域约束
     */
    public ShopSetmealItem {
        if (name == null || name.isBlank() || unit == null || unit.isBlank()
                || quantity == null || quantity.signum() <= 0
                || quantity.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException("套餐菜品数据不完整或不合法");
        }
    }
}
