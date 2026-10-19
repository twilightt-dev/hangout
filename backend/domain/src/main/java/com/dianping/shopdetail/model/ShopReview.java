package com.dianping.shopdetail.model;

import com.dianping.shopdetail.enums.ReviewTag;
import com.dianping.shopdetail.exception.ShopReviewException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一条评价事实；评分以十分之一分存储，标签和提交内容规范化后不可变。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param userId 服务端确认的用户标识
 * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
 * @param overallScore 独立总体评分，按实际分数的十倍保存
 * @param tasteScore 口味评分，按实际分数的十倍保存
 * @param environmentScore 环境评分，按实际分数的十倍保存
 * @param serviceScore 服务评分，按实际分数的十倍保存
 * @param content 正文内容
 * @param tags 用户选择的预设标签
 * @param createTime 创建时间
 * @param userName 作者当前昵称
 * @param userIcon 作者当前头像
 */
public record ShopReview(Long id, Long shopId, Long userId, String requestId,
                         int overallScore, int tasteScore, int environmentScore, int serviceScore,
                         String content, List<String> tags, LocalDateTime createTime,
                         String userName, String userIcon) {


    /**
     * 创建并校验ShopReview，拒绝不满足业务约束的数据。
     *
     * @param id 记录标识
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @param overallScore 独立总体评分，按实际分数的十倍保存
     * @param tasteScore 口味评分，按实际分数的十倍保存
     * @param environmentScore 环境评分，按实际分数的十倍保存
     * @param serviceScore 服务评分，按实际分数的十倍保存
     * @param content 正文内容
     * @param tags 用户选择的预设标签
     * @param createTime 创建时间
     * @param userName 作者当前昵称
     * @param userIcon 作者当前头像
     */
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

    /**
     * 为新的评价创建经过规范化及业务校验的领域对象。
     *
     * @param shopId 店铺标识
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @param overallScore 独立总体评分，按实际分数的十倍保存
     * @param tasteScore 口味评分，按实际分数的十倍保存
     * @param environmentScore 环境评分，按实际分数的十倍保存
     * @param serviceScore 服务评分，按实际分数的十倍保存
     * @param content 正文内容
     * @param tags 用户选择的预设标签
     * @param now 本次读取或创建使用的时间基准
     * @return 完成字段规范化与业务校验的新评价
     */
    public static ShopReview create(Long shopId, Long userId, String requestId, int overallScore,
                                    int tasteScore, int environmentScore, int serviceScore,
                                    String content, List<String> tags, LocalDateTime now) {
        return new ShopReview(null, shopId, userId, requestId, overallScore, tasteScore,
                environmentScore, serviceScore, content, tags, now, null, null);
    }

    /**
     * 比较规范化后的业务提交内容，用于判断同提交标识能否安全重放。
     *
     * @param other 用于比较的另一个业务对象
     * @return 两次提交的规范化业务内容是否相同
     */
    public boolean sameSubmission(ShopReview other) {
        return shopId.equals(other.shopId) && overallScore == other.overallScore
                && tasteScore == other.tasteScore && environmentScore == other.environmentScore
                && serviceScore == other.serviceScore && content.equals(other.content) && tags.equals(other.tags);
    }

    /**
     * 校验十倍整数评分是否处于一至五分且满足半分步长。
     *
     * @param score 按实际分数的十倍保存的评分
     */
    private static void validateScore(int score) {
        if (score < 10 || score > 50 || score % 5 != 0) invalid("四项评分均须为 1～5 分，步长 0.5 分");
    }
    /**
     * 抛出当前业务对应的非法请求异常。
     *
     * @param message 对外展示的固定提示
     */
    private static void invalid(String message) { throw new ShopReviewException(400, message); }
}
