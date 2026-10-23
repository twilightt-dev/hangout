package com.dianping.shop.shopdetail.model;
import java.math.BigDecimal;
import java.math.RoundingMode;
/**
 * 全部店铺评价的统计，与近期标签样本口径分开。
 *
 * @param totalReviews 全店评价数量
 * @param averageScore 五分制均分；无评价时为 {@code null}
 */
public record ShopReviewSummary(long totalReviews, BigDecimal averageScore) {
    /**
     * 持久化评分放大十倍保存，五分制均分保留一位小数。
     *
     * @param total 全部匹配记录数
     * @param sum 按十倍保存的总体评分总和
     * @return 评价数量与五分制均分；无评价时均分为 {@code null}
     */
    public static ShopReviewSummary fromTotals(long total, long sum) {
        return new ShopReviewSummary(total, total == 0 ? null : BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(total).multiply(BigDecimal.TEN), 1, RoundingMode.HALF_UP));
    }
}
