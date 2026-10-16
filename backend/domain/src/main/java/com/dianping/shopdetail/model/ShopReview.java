package com.dianping.shopdetail.model;

import com.dianping.shopdetail.exception.ShopReviewException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 一条评价事实；评分以十分之一分存储，标签和提交内容规范化后不可变。 */
public record ShopReview(Long id, Long shopId, Long userId, String requestId,
                         int overallScore, int tasteScore, int environmentScore, int serviceScore,
                         String content, List<String> tags, LocalDateTime createTime) {
    public ShopReview {
        if (shopId == null || shopId <= 0 || userId == null || userId <= 0) invalid("店铺和作者标识必须为正数");
        try {
            if (requestId == null || !UUID.fromString(requestId).toString().equalsIgnoreCase(requestId))
                invalid("提交标识必须是 UUID");
            requestId = UUID.fromString(requestId).toString();
        } catch (IllegalArgumentException e) { throw new ShopReviewException(400, "提交标识必须是 UUID"); }
        validateScore(overallScore); validateScore(tasteScore);
        validateScore(environmentScore); validateScore(serviceScore);
        content = content == null ? "" : content.trim();
        if (content.isEmpty() || content.length() > 1000) invalid("评价正文须为 1～1000 个字符");
        tags = tags == null ? List.of() : tags;
        for (String tag : tags) if (!ReviewTag.contains(tag)) invalid("评价标签不合法");
        tags = tags.stream().distinct().sorted().toList();
        if (tags.size() > 5) invalid("最多选择 5 个标签");
        Objects.requireNonNull(createTime, "评价时间不得为空");
    }

    public static ShopReview create(Long shopId, Long userId, String requestId, int overallScore,
                                    int tasteScore, int environmentScore, int serviceScore,
                                    String content, List<String> tags, LocalDateTime now) {
        return new ShopReview(null, shopId, userId, requestId, overallScore, tasteScore,
                environmentScore, serviceScore, content, tags, now);
    }

    public boolean sameSubmission(ShopReview other) {
        return shopId.equals(other.shopId) && overallScore == other.overallScore
                && tasteScore == other.tasteScore && environmentScore == other.environmentScore
                && serviceScore == other.serviceScore && content.equals(other.content) && tags.equals(other.tags);
    }

    private static void validateScore(int score) {
        if (score < 10 || score > 50 || score % 5 != 0) invalid("四项评分均须为 1～5 分，步长 0.5 分");
    }
    private static void invalid(String message) { throw new ShopReviewException(400, message); }
}
