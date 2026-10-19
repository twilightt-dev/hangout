package com.dianping.shopdetail.impl;

import com.dianping.shop.review.model.request.PublishShopReviewRequest;
import com.dianping.shop.review.model.request.ShopReviewPageRequest;
import com.dianping.shop.review.model.result.ShopReviewPageResult;
import com.dianping.shop.review.model.result.ShopReviewTagOption;
import com.dianping.shop.review.service.ShopReviewApplicationService;
import com.dianping.shopdetail.ShopReviewFacade;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 业务门面实现，转调应用用例，不维护额外业务规则。
 */
@Component
public class ShopReviewFacadeImpl implements ShopReviewFacade {
    /**
     * 应用用例服务。
     */
    private final ShopReviewApplicationService service;
    /**
     * 创建 ShopReviewFacadeImpl，初始化所需依赖及实例状态。
     *
     * @param service 应用用例服务
     */
    public ShopReviewFacadeImpl(ShopReviewApplicationService service) { this.service = service; }
    /**
     * 发布店铺评价，幂等记录、评价写入与店铺统计在同一事务完成。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param request 已经定义字段约束的业务请求
     * @return 新建或幂等重放的发布结果
     */
    public Long publish(Long shopId, Long userId, PublishShopReviewRequest request) { return service.publish(shopId,userId,request); }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    public ShopReviewPageResult page(ShopReviewPageRequest request) { return service.page(request); }
    /**
     * 读取供用户选择的固定评价标签词典。
     *
     * @return 系统预设评价标签的代码与中文名称列表
     */
    public List<ShopReviewTagOption> tags() { return service.tags(); }
}
