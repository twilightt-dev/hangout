package com.dianping.shop.shopdetail.model;

import java.util.List;

/**
 * 固定内容套餐，金额以整数分表达。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param name 名称
 * @param coverImage 已上传的封面图片路径
 * @param price 套餐支付金额，单位为分
 * @param originalPrice 套餐原价，单位为分
 * @param people 适用人数
 * @param description 说明文本
 * @param rules 使用规则
 * @param items 套餐包含的固定菜品明细
 */
public record ShopSetmeal(Long id, Long shopId, String name, String coverImage, Long price,
                          Long originalPrice, Integer people, String description, String rules,
                          List<ShopSetmealItem> items) {
    /**
     * 创建并校验ShopSetmeal，拒绝不满足业务约束的数据。
     *
     * @param id 记录标识
     * @param shopId 店铺标识
     * @param name 名称
     * @param coverImage 已上传的封面图片路径
     * @param price 套餐支付金额，单位为分
     * @param originalPrice 套餐原价，单位为分
     * @param people 适用人数
     * @param description 说明文本
     * @param rules 使用规则
     * @param items 套餐包含的固定菜品明细
     * @throws IllegalStateException 套餐或菜品数据不满足领域约束
     */
    public ShopSetmeal {
        if (id == null || id <= 0 || shopId == null || shopId <= 0
                || name == null || name.isBlank() || coverImage == null || coverImage.isBlank()
                || rules == null || rules.isBlank() || price == null || price <= 0
                || originalPrice != null && originalPrice < price
                || people == null || people <= 0 || items == null || items.isEmpty()) {
            throw new IllegalStateException("套餐数据不完整或不合法");
        }
        items = List.copyOf(items);
    }
}
