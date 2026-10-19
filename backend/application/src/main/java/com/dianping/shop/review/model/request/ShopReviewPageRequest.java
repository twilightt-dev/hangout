package com.dianping.shop.review.model.request;

import com.dianping.shopdetail.exception.ShopReviewException;

/**
 * 当前业务的数据传输模型。
 *
 * @param shopId 店铺标识
 * @param current 从 1 开始的页码
 */
public record ShopReviewPageRequest(Long shopId, Long current) {
    /**
     * 评价独立分页的固定每页数量。
     */
    public static final int SIZE = 10;
    /**
     * 创建 ShopReviewPageRequest，初始化所需依赖及实例状态。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     */
    public ShopReviewPageRequest {
        if (shopId == null || shopId <= 0 || current == null || current <= 0
                || current > Long.MAX_VALUE / SIZE) throw new ShopReviewException(400, "店铺或页码不合法");
    }
    /**
     * 计算从零开始的分页偏移量。
     *
     * @return 分页偏移量
     */
    public long offset() { return (current - 1) * SIZE; }
}
