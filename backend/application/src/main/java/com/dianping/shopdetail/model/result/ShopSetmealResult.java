package com.dianping.shopdetail.model.result;

import com.dianping.shopdetail.model.ShopSetmeal;
import com.dianping.shopdetail.model.ShopSetmealItem;
import java.util.List;

/** 套餐展示结果，不暴露上下架状态等内部管理信息。 */
public record ShopSetmealResult(Long id, Long shopId, String name, String coverImage, Long price,
                                Long originalPrice, Integer people, String description, String rules,
                                List<ShopSetmealItem> items) {
    public static ShopSetmealResult from(ShopSetmeal setmeal) {
        return new ShopSetmealResult(setmeal.id(), setmeal.shopId(), setmeal.name(), setmeal.coverImage(),
                setmeal.price(), setmeal.originalPrice(), setmeal.people(), setmeal.description(),
                setmeal.rules(), setmeal.items());
    }
}
