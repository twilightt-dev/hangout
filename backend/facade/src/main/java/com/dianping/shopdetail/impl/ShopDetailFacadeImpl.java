package com.dianping.shopdetail.impl;

import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.*;
import com.dianping.shop.shopdetail.service.ShopDetailApplicationService;
import com.dianping.shop.shopdetail.service.ShopDetailQueryService;
import com.dianping.shopdetail.ShopDetailFacade;
import org.springframework.stereotype.Component;

/**
 * 业务门面实现，转调应用用例，不维护额外业务规则。
 */
@Component
public class ShopDetailFacadeImpl implements ShopDetailFacade {
    /**
     * 应用用例服务。
     */
    private final ShopDetailApplicationService service;
    /**
     * 独立查看更多查询用例。
     */
    private final ShopDetailQueryService queries;
    /**
     * 创建 ShopDetailFacadeImpl，初始化所需依赖及实例状态。
     *
     * @param service 应用用例服务
     * @param queries 独立查看更多查询用例
     */
    public ShopDetailFacadeImpl(ShopDetailApplicationService service, ShopDetailQueryService queries) {
        this.service=service; this.queries=queries;
    }
    /**
     * 读取商详首次展示的六区组合及当前浏览者个人状态。
     *
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 包含六区数据与分项状态的商详响应
     */
    public ShopDetailResult detail(Long shopId, Long viewerId) { return service.getShopDetail(shopId,viewerId); }
    /**
     * 读取优惠券，沿用上架、有效期和可购买优先的展示规则。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<VoucherItem> vouchers(ShopListPageRequest request) { return queries.vouchers(request); }
    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param request 已经定义字段约束的业务请求
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<BlogItem> blogs(ShopListPageRequest request,Long viewerId) { return queries.blogs(request,viewerId); }
    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 带可用状态的周边分页结果
     */
    public SectionResult<ShopPageResult<RelatedItem>> related(ShopListPageRequest request) { return queries.related(request); }
}
