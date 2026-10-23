package com.dianping.shop.shopdetail.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Set;
/**
 * 商详业务数据组合；任务节点修改前复制，避免修改已经发布的结果。
 */
@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ShopDetailAggregate {
    /**
     * 店铺基础资料。
     */
    private ShopBaseInfo shop;
    /**
     * 优惠券读取结果。
     */
    private ShopPage<ShopVoucher> vouchers;
    /**
     * 套餐读取结果。
     */
    private ShopPage<ShopSetmeal> setmeals;
    /**
     * 推荐菜读取结果。
     */
    private ShopPage<RecommendDish> recommendations;
    /**
     * 当前浏览者已推荐的菜品标识。
     */
    private Set<Long> recommendedIds;
    /**
     * 评价读取结果。
     */
    private ShopPage<ShopReview> reviews;
    /**
     * 全店评价统计。
     */
    private ShopReviewSummary reviewSummary;
    /**
     * 关联当前店铺的探店笔记。
     */
    private ShopPage<ShopVisitBlog> blogs;
    /**
     * 评价与博客合并得到的近期口碑标签。
     */
    private List<ShopDecisionTag> decisionTags;
    /**
     * 仅由近期评价得到的高频标签。
     */
    private List<ShopDecisionTag> reviewTags;
    /**
     * 同类型周边店铺。
     */
    private ShopPage<ShopRelatedShop> relatedShops;
    /**
     * 缺少坐标或类型时无法推荐周边店铺的原因。
     */
    private String relatedUnavailableReason;
}
