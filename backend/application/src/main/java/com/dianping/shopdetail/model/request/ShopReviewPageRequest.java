package com.dianping.shopdetail.model.request;

import com.dianping.shopdetail.exception.ShopReviewException;

public record ShopReviewPageRequest(Long shopId, Long current) {
    public static final int SIZE = 10;
    public ShopReviewPageRequest {
        if (shopId == null || shopId <= 0 || current == null || current <= 0
                || current > Long.MAX_VALUE / SIZE) throw new ShopReviewException(400, "店铺或页码不合法");
    }
    public long offset() { return (current - 1) * SIZE; }
}
