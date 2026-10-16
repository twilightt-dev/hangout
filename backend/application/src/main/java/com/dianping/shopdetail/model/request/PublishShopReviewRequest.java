package com.dianping.shopdetail.model.request;

import com.dianping.shopdetail.exception.ShopReviewException;
import com.dianping.shopdetail.model.ShopReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 请求不包含可信作者身份，评分在此精确转换为内部整数。 */
public record PublishShopReviewRequest(String requestId, BigDecimal overallScore, BigDecimal tasteScore,
                                       BigDecimal environmentScore, BigDecimal serviceScore,
                                       String content, List<String> tags) {
    public ShopReview toReview(Long shopId, Long userId) {
        return ShopReview.create(shopId, userId, requestId, score(overallScore), score(tasteScore),
                score(environmentScore), score(serviceScore), content, tags, LocalDateTime.now());
    }
    private static int score(BigDecimal value) {
        if (value == null) throw new ShopReviewException(400, "四项评分均为必填");
        try { return value.movePointRight(1).intValueExact(); }
        catch (ArithmeticException error) { throw new ShopReviewException(400, "评分格式不合法"); }
    }
}
