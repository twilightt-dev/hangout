package com.dianping.shopdetail.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
/** 商详读取的组合模型；以新实例替换，不是跨六区写入的事务聚合根。 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class ShopDetailAggregate {
    private final ShopBaseInfo shop;
    private final ShopPage<ShopVoucher> vouchers;
    private final ShopPage<ShopSetmeal> setmeals;
    private final ShopPage<RecommendDish> recommendations;
    private final ShopPage<ShopReview> reviews;
    private final ShopReviewSummary reviewSummary;
    private final ShopPage<ShopVisitBlog> blogs;
    private final List<ShopDecisionTag> decisionTags;
    private final List<ShopDecisionTag> reviewTags;
    private final ShopPage<ShopRelatedShop> relatedShops;
}
