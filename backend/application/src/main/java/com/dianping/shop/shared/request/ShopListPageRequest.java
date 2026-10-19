package com.dianping.shop.shared.request;

import com.dianping.shopdetail.exception.ShopDetailException;

/**
 * 店铺各业务列表共享的分页请求，用于独立查看更多接口，不作为商详首屏请求。
 * 构造时校验店铺标识、分页范围和偏移量溢出。
 *
 * @param shopId 店铺标识
 * @param current 从 1 开始的页码
 * @param size 每页数量
 */
public record ShopListPageRequest(Long shopId, long current, int size) {
    /**
     * 创建并校验店铺列表分页条件。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @throws ShopDetailException 店铺标识、分页范围非法，或分页偏移量溢出
     */
    public ShopListPageRequest {
        if (shopId == null || shopId <= 0) throw new ShopDetailException(400, "店铺 ID 必须为正整数");
        if (current < 1 || size < 1 || size > 50) throw new ShopDetailException(400, "页码须为正数，每页数量须为 1～50");
        try { Math.multiplyExact(current - 1, (long) size); }
        catch (ArithmeticException error) { throw new ShopDetailException(400, "页码超出支持范围"); }
    }
    /**
     * 计算从零开始的分页偏移量。
     *
     * @return 分页偏移量
     */
    public long offset() { return (current - 1) * size; }
}
