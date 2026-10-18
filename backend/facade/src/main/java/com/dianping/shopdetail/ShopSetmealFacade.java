package com.dianping.shopdetail;

import com.dianping.shopdetail.model.result.ShopSetmealResult;
import java.util.List;

/** 店铺套餐公开查询门面。 */
public interface ShopSetmealFacade {
    List<ShopSetmealResult> list(Long shopId);
}
