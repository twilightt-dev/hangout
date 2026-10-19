package com.dianping.shop.shopdetail.service;

import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.*;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Service;

/**
 * 独立“查看更多”用例；首屏任务链直接调用领域服务。
 */
@Service
public class ShopDetailQueryService {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 ShopDetailQueryService，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public ShopDetailQueryService(ShopDetailDomainService domain) { this.domain = domain; }

    /**
     * 读取优惠券，沿用上架、有效期和可购买优先的展示规则。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<VoucherItem> vouchers(ShopListPageRequest request) {
        domain.base(request.shopId());
        return ShopPageResult.from(domain.vouchers(request.shopId(), request.current(), request.size()), VoucherItem::from);
    }

    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param request 已经定义字段约束的业务请求
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<BlogItem> blogs(ShopListPageRequest request, Long viewerId) {
        domain.base(request.shopId());
        return ShopPageResult.from(domain.blogs(request.shopId(), request.current(), request.size(), viewerId), BlogItem::from);
    }

    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 带可用状态的周边分页结果
     */
    public SectionResult<ShopPageResult<RelatedItem>> related(ShopListPageRequest request) {
        var center = domain.base(request.shopId());
        var reason = domain.relatedUnavailableReason(center);
        if (reason != null) return SectionResult.unavailable("UNAVAILABLE", reason);
        return SectionResult.attempt(() -> domain.related(center, request.current(), request.size()),
                page -> page.records().isEmpty()).map(page -> ShopPageResult.from(page, RelatedItem::from));
    }
}
