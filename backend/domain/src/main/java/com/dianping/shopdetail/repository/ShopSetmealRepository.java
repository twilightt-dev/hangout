package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.ShopSetmeal;
import java.util.List;

/** 套餐查询持久化边界；返回按展示顺序排列的上架套餐及固定明细。 */
public interface ShopSetmealRepository {
    boolean shopExists(Long shopId);
    List<ShopSetmeal> findPublishedByShopId(Long shopId);
}
