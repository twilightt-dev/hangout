package com.dianping.shopdetail;

import com.dianping.shop.shared.request.ShopListPageRequest;
import com.dianping.shop.shared.result.ShopPageResult;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult.*;

/**
 * 商详读取门面；不包装 HTTP 外壳。
 */
public interface ShopDetailFacade {
    /**
     * 读取商详首次展示的六区组合及当前浏览者个人状态。
     *
     * @param shopId 店铺标识
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 包含六区数据与分项状态的商详响应
     */
    ShopDetailResult detail(Long shopId, Long viewerId);
    /**
     * 读取优惠券，沿用上架、有效期和可购买优先的展示规则。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 优惠券的分页结果，包含总数与当前页记录
     */
    ShopPageResult<VoucherItem> vouchers(ShopListPageRequest request);
    /**
     * 读取当前店铺的探店笔记，并按显式传入的身份补齐点赞状态。
     *
     * @param request 已经定义字段约束的业务请求
     * @param viewerId 当前浏览者标识；游客传入 {@code null}
     * @return 探店笔记及当前浏览者点赞状态的分页结果，包含总数与当前页记录
     */
    ShopPageResult<BlogItem> blogs(ShopListPageRequest request, Long viewerId);
    /**
     * 以当前店铺为中心读取五公里内同类型店铺，排除自身并按距离、标识排序。
     *
     * @param request 已经定义字段约束的业务请求
     * @return 带可用状态的周边分页结果
     */
    SectionResult<ShopPageResult<RelatedItem>> related(ShopListPageRequest request);
}
