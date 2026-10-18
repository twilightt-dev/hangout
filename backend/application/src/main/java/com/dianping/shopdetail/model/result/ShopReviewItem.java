package com.dianping.shopdetail.model.result;

import com.dianping.shopdetail.model.ShopReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ShopReviewItem(Long id, Long shopId, Long userId, String userName, String userIcon,
                             BigDecimal overallScore, BigDecimal tasteScore, BigDecimal environmentScore,
                             BigDecimal serviceScore, String content, List<String> tags, LocalDateTime createTime) {
    public static ShopReviewItem from(ShopReview review) {
        String name = review.userName() == null || review.userName().isBlank()
                ? "用户资料暂缺" : review.userName();
        return new ShopReviewItem(review.id(), review.shopId(), review.userId(), name,
                review.userIcon(), BigDecimal.valueOf(review.overallScore(), 1),
                BigDecimal.valueOf(review.tasteScore(), 1), BigDecimal.valueOf(review.environmentScore(), 1),
                BigDecimal.valueOf(review.serviceScore(), 1), review.content(), review.tags(), review.createTime());
    }
}
