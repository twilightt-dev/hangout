package com.dianping.shopdetail;

import com.dianping.shop.recommend.model.request.PublishRecommendDishRequest;
import com.dianping.shop.recommend.model.request.RecommendDishPageRequest;
import com.dianping.shop.recommend.model.result.RecommendDishItem;
import com.dianping.shop.recommend.model.result.RecommendDishPageResult;

/**
 * 面向控制器的业务门面，隐藏应用层编排并返回业务响应。
 */
public interface RecommendDishFacade {
    /**
     * 发布推荐菜并原子维护作者首次推荐关系与人数。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    RecommendDishItem publish(Long shopId, Long userId, PublishRecommendDishRequest request);
    /**
     * 为当前用户增加一次去重推荐，重复请求不重复增加推荐人数。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 包含最新推荐人数及当前用户推荐状态的菜品响应
     */
    RecommendDishItem recommend(Long shopId, Long dishId, Long userId);
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @param userId 服务端确认的用户标识
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    RecommendDishPageResult page(RecommendDishPageRequest request, Long userId);
}
