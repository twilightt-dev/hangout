package com.dianping.shopdetail.model.result;
import com.dianping.shopdetail.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 商详应用响应；领域组合与引擎诊断不直接作为 HTTP 返回类型。 */
public record ShopDetailResult(BaseInfo shop,
        SectionResult<ShopPageResult<VoucherItem>> vouchers,
        SectionResult<ShopPageResult<ShopSetmealResult>> setmeals,
        SectionResult<ShopPageResult<RecommendDishItem>> recommendations,
        SectionResult<ShopPageResult<ShopReviewItem>> reviews,
        SectionResult<ReviewStatistics> statistics,
        SectionResult<ShopPageResult<BlogItem>> blogs,
        SectionResult<List<TagItem>> decisionTags,
        SectionResult<List<TagItem>> reviewTags,
        SectionResult<ShopPageResult<RelatedItem>> relatedShops,
        boolean recommendationStateAvailable) {
    public record BaseInfo(Long id, String name, Long typeId, String images, String area, String address,
                           Double x, Double y, Long avgPrice, String openHours) {
        public static BaseInfo from(ShopBaseInfo s) { return new BaseInfo(s.id(),s.name(),s.typeId(),s.images(),s.area(),s.address(),s.x(),s.y(),s.avgPrice(),s.openHours()); }
    }
    public record VoucherItem(Long id, Long shopId, String title, String subTitle, String rules, Long payValue,
            Long actualValue, Integer type, Integer stock, LocalDateTime beginTime, LocalDateTime endTime, String availability) {
        public static VoucherItem from(ShopVoucher v) { return new VoucherItem(v.id(),v.shopId(),v.title(),v.subTitle(),v.rules(),v.payValue(),v.actualValue(),v.type(),v.stock(),v.beginTime(),v.endTime(),v.availability()); }
    }
    public record ReviewStatistics(long totalReviews, BigDecimal averageScore) {
        public static ReviewStatistics from(ShopReviewSummary s) { return new ReviewStatistics(s.totalReviews(),s.averageScore()); }
    }
    public record BlogItem(Long id, Long shopId, Long userId, String name, String icon, Boolean isLike,
            String title, String images, String content, Integer liked, Integer comments, LocalDateTime createTime) {
        public static BlogItem from(ShopVisitBlog b) { return new BlogItem(b.getId(),b.getShopId(),b.getUserId(),b.getUserName(),b.getUserIcon(),b.getIsLike(),b.getTitle(),b.getImages(),b.getContent(),b.getLiked(),b.getComments(),b.getCreateTime()); }
    }
    public record TagItem(String code, String name, long userCount) {
        public static TagItem from(ShopDecisionTag t) { return new TagItem(t.code(),t.name(),t.userCount()); }
    }
    public record RelatedItem(Long id, String name, String area, Long avgPrice, Integer score, Double distance, String recommendationReason) {
        public static RelatedItem from(ShopRelatedShop s) { return new RelatedItem(s.getShopId(),s.getShopName(),s.getArea(),s.getAvgPrice(),s.getScore(),s.getDistance(),s.getRecommendationReason()); }
    }
}
