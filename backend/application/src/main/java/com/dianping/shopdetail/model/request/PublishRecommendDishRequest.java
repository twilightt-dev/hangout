package com.dianping.shopdetail.model.request;

import com.dianping.shopdetail.model.RecommendDish;
import java.time.LocalDateTime;

/** 作者与店铺从可信调用参数取得，不接受请求体覆盖。 */
public record PublishRecommendDishRequest(String requestId, String name, String coverImage) {
    public RecommendDish toDish(Long shopId, Long userId) {
        return new RecommendDish(null, shopId, userId, requestId, name, coverImage, 0, LocalDateTime.now());
    }
}
