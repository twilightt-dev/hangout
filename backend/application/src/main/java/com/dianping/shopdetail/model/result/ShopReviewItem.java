package com.dianping.shopdetail.model.result;

import com.dianping.shopdetail.model.ReviewAuthor;
import com.dianping.shopdetail.model.ShopReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShopReviewItem(Long id, Long shopId, Long userId, String userName, String userIcon,
                             BigDecimal overallScore, BigDecimal tasteScore, BigDecimal environmentScore,
                             BigDecimal serviceScore, String content, List<String> tags, LocalDateTime createTime) {
    public static ShopReviewItem from(ShopReview review, ReviewAuthor author) {
        String name = author == null || author.name() == null || author.name().isBlank()
                ? "用户资料暂缺" : author.name();
        return new ShopReviewItem(review.id(), review.shopId(), review.userId(), name,
                author == null ? null : author.icon(), BigDecimal.valueOf(review.overallScore(), 1),
                BigDecimal.valueOf(review.tasteScore(), 1), BigDecimal.valueOf(review.environmentScore(), 1),
                BigDecimal.valueOf(review.serviceScore(), 1), review.content(), review.tags(), review.createTime());
    }
}
