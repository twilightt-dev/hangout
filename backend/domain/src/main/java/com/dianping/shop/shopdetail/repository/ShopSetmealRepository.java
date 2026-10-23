package com.dianping.shop.shopdetail.repository;

import com.dianping.shop.shopdetail.model.ShopSetmeal;
import com.dianping.shop.shopdetail.model.ShopPage;
import java.util.List;

/**
 * 套餐查询持久化边界；返回按展示顺序排列的上架套餐及固定明细。
 */
public interface ShopSetmealRepository {
    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    boolean shopExists(Long shopId);
    /**
     * 查询当前店铺的上架套餐并批量补齐固定菜品明细。
     *
     * @param shopId 店铺标识
     * @return 有序上架套餐列表，每项包含固定菜品明细
     */
    List<ShopSetmeal> findPublishedByShopId(Long shopId);
    /**
     * 统计当前店铺上架套餐总数。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    long countPublished(Long shopId);
    /**
     * 按展示顺序分页读取上架套餐。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    List<ShopSetmeal> pagePublished(Long shopId, long offset, int size);
    /**
     * 同一只读快照内返回套餐、明细和总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopPage<ShopSetmeal> pageWithTotal(Long shopId, long current, int size);
}
