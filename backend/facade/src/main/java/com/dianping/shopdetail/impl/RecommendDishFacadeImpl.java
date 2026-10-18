package com.dianping.shopdetail.impl;

import com.dianping.shopdetail.RecommendDishFacade;
import com.dianping.shopdetail.model.request.PublishRecommendDishRequest;
import com.dianping.shopdetail.model.request.RecommendDishPageRequest;
import com.dianping.shopdetail.model.result.RecommendDishItem;
import com.dianping.shopdetail.model.result.RecommendDishPageResult;
import com.dianping.shopdetail.service.RecommendDishApplicationService;
import org.springframework.stereotype.Service;

@Service
public class RecommendDishFacadeImpl implements RecommendDishFacade {
    private final RecommendDishApplicationService service;
    public RecommendDishFacadeImpl(RecommendDishApplicationService service) { this.service = service; }
    @Override public RecommendDishItem publish(Long shopId, Long userId, PublishRecommendDishRequest request) {
        return service.publish(shopId, userId, request);
    }
    @Override public RecommendDishItem recommend(Long shopId, Long dishId, Long userId) {
        return service.recommend(shopId, dishId, userId);
    }
    @Override public RecommendDishPageResult page(RecommendDishPageRequest request, Long userId) {
        return service.page(request, userId);
    }
}
