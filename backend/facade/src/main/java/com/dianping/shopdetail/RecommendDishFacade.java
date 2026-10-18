package com.dianping.shopdetail;

import com.dianping.shopdetail.model.request.PublishRecommendDishRequest;
import com.dianping.shopdetail.model.request.RecommendDishPageRequest;
import com.dianping.shopdetail.model.result.RecommendDishItem;
import com.dianping.shopdetail.model.result.RecommendDishPageResult;

public interface RecommendDishFacade {
    RecommendDishItem publish(Long shopId, Long userId, PublishRecommendDishRequest request);
    RecommendDishItem recommend(Long shopId, Long dishId, Long userId);
    RecommendDishPageResult page(RecommendDishPageRequest request, Long userId);
}
