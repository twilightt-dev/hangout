package com.dianping.shop.review.model.request;

import com.dianping.shop.shopdetail.exception.ShopReviewException;
import com.dianping.shop.shopdetail.model.ShopReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 请求不包含可信作者身份，评分在此精确转换为内部整数。
 *
 * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
 * @param overallScore 用户独立填写的总体评分，使用五分制小数
 * @param tasteScore 口味评分，使用五分制小数
 * @param environmentScore 环境评分，使用五分制小数
 * @param serviceScore 服务评分，使用五分制小数
 * @param content 正文内容
 * @param tags 用户选择的预设标签
 */
public record PublishShopReviewRequest(String requestId, BigDecimal overallScore, BigDecimal tasteScore,
                                       BigDecimal environmentScore, BigDecimal serviceScore,
                                       String content, List<String> tags) {
    /**
     * 将发布请求转换为经过领域校验的评价，保留四项独立评分。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @return 尚未持久化的合法评价领域对象
     */
    public ShopReview toReview(Long shopId, Long userId) {
        return ShopReview.create(shopId, userId, requestId, score(overallScore), score(tasteScore),
                score(environmentScore), score(serviceScore), content, tags, LocalDateTime.now());
    }
    /**
     * 将五分制请求评分精确转换为十倍保存的整数。
     *
     * @param value 用户传入的五分制小数评分
     * @return 精确放大十倍后的整数评分
     */
    private static int score(BigDecimal value) {
        if (value == null) throw new ShopReviewException(400, "四项评分均为必填");
        try { return value.movePointRight(1).intValueExact(); }
        catch (ArithmeticException error) { throw new ShopReviewException(400, "评分格式不合法"); }
    }
}
