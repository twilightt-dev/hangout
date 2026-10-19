package com.dianping.shopdetail.impl;

import com.dianping.shop.recommend.model.request.PublishRecommendDishRequest;
import com.dianping.shop.recommend.model.request.RecommendDishPageRequest;
import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.recommend.model.result.RecommendDishPageResult;
import com.dianping.shop.recommend.service.RecommendDishApplicationService;
import com.dianping.shopdetail.RecommendDishFacade;
import org.springframework.stereotype.Service;

/**
 * 业务门面实现，转调应用用例，不维护额外业务规则。
 */
@Service
public class RecommendDishFacadeImpl implements RecommendDishFacade {
    /**
     * 应用用例服务。
     */
    private final RecommendDishApplicationService service;
    /**
     * 创建 RecommendDishFacadeImpl，初始化所需依赖及实例状态。
     *
     * @param service 应用用例服务
     */
    public RecommendDishFacadeImpl(RecommendDishApplicationService service) { this.service = service; }
    /**
     * 发布推荐菜并原子维护作者首次推荐关系与人数。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    @Override public RecommendDishItem publish(Long shopId, Long userId, PublishRecommendDishRequest request) {
        return service.publish(shopId, userId, request);
    }
    /**
     * 为当前用户增加一次去重推荐，重复请求不重复增加推荐人数。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 包含最新推荐人数及当前用户推荐状态的菜品响应
     */
    @Override public RecommendDishItem recommend(Long shopId, Long dishId, Long userId) {
        return service.recommend(shopId, dishId, userId);
    }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @param userId 服务端确认的用户标识
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Override public RecommendDishPageResult page(RecommendDishPageRequest request, Long userId) {
        return service.page(request, userId);
    }
}
