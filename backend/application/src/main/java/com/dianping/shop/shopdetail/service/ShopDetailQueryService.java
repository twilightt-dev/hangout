package com.dianping.shop.shopdetail.service;

import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.*;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 负责实现“查看更多”的功能
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
     * 优惠券分页查询
     * @param request 已经定义字段约束的业务请求
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<VoucherItem> vouchers(ShopListPageRequest request) {
        domain.base(request.shopId());
        return ShopPageResult.from(domain.vouchers(request.shopId(), request.current(), request.size()), VoucherItem::from);
    }

    /**
     * 探店笔记分页查询
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     * @param request 已经定义字段约束的业务请求
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    public ShopPageResult<BlogItem> blogs(ShopListPageRequest request, Long viewerId) {
        domain.base(request.shopId());
        return ShopPageResult.from(domain.blogs(request.shopId(), request.current(), request.size(), viewerId), BlogItem::from);
    }

    /**
     * 补齐缓存推荐菜品的个人推荐状态
     * @param viewerId 当前浏览者标识
     * @param dishIds 当前商详快照中的推荐菜标识
     * @return 当前用户已推荐的菜品id的集合
     */
    public Set<Long> recommendedIds(Long viewerId, List<Long> dishIds) {
        return domain.recommendedIds(viewerId, dishIds);
    }

    /**
     * 批量读取当前用户对缓存博客的点赞状态。
     * 补齐缓存博客的个人点赞状态
     * @param viewerId 当前浏览者标识
     * @param blogIds 当前商详快照中的博客标识
     * @return 当前用户已点赞的博客标识
     */
    public Set<Long> likedBlogIds(Long viewerId, List<Long> blogIds) {
        return domain.likedBlogIds(viewerId, blogIds);
    }

    /**
     * 周边店铺分页查询
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
