package com.dianping.shop.recommend.model.request;

import com.dianping.shop.shopdetail.exception.RecommendDishException;

/**
 * 商详预览可取 6 条，独立浏览默认每页 12 条。
 *
 * @param shopId 店铺标识
 * @param current 从 1 开始的页码
 * @param size 每页数量
 */
public record RecommendDishPageRequest(Long shopId, Long current, Integer size) {
    /**
     * 创建 RecommendDishPageRequest，初始化所需依赖及实例状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     */
    public RecommendDishPageRequest {
        if (shopId == null || shopId <= 0) throw new RecommendDishException(400, "店铺标识必须为正数");
        current = current == null ? 1L : current;
        size = size == null ? 12 : size;
        if (current <= 0 || size <= 0 || size > 50) throw new RecommendDishException(400, "页码须为正数，每页数量须为 1～50");
        try { Math.multiplyExact(current - 1, size.longValue()); }
        catch (ArithmeticException error) { throw new RecommendDishException(400, "页码超出支持范围"); }
    }
    /**
     * 计算从零开始的分页偏移量。
     *
     * @return 分页偏移量
     */
    public long offset() { return (current - 1) * size; }
}
