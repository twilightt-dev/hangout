package com.dianping.shopdetail.model;

import java.util.List;

/** 固定内容套餐，金额以整数分表达。 */
public record ShopSetmeal(Long id, Long shopId, String name, String coverImage, Long price,
                          Long originalPrice, Integer people, String description, String rules,
                          List<ShopSetmealItem> items) {
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
