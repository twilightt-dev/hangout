package com.dianping.shop.review.model.result;

import com.dianping.shopdetail.model.ShopReview;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价展示响应，将内部十倍整数评分转换为五分制小数并补齐作者资料。
 *
 * @param id 记录标识
 * @param shopId 店铺标识
 * @param userId 服务端确认的用户标识
 * @param userName 作者当前昵称
 * @param userIcon 作者当前头像
 * @param overallScore 用户独立填写的总体评分，使用五分制小数
 * @param tasteScore 口味评分，使用五分制小数
 * @param environmentScore 环境评分，使用五分制小数
 * @param serviceScore 服务评分，使用五分制小数
 * @param content 正文内容
 * @param tags 用户选择的预设标签
 * @param createTime 创建时间
 */
public record ShopReviewItem(Long id, Long shopId, Long userId, String userName, String userIcon,
                             BigDecimal overallScore, BigDecimal tasteScore, BigDecimal environmentScore,
                             BigDecimal serviceScore, String content, List<String> tags, LocalDateTime createTime) {
    /**
     * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
     *
     * @param review 评价业务数据
     * @return 转换或执行后的业务响应，保留对应分项状态
     */
    public static ShopReviewItem from(ShopReview review) {
        String name = review.userName() == null || review.userName().isBlank()
                ? "用户资料暂缺" : review.userName();
        return new ShopReviewItem(review.id(), review.shopId(), review.userId(), name,
                review.userIcon(), BigDecimal.valueOf(review.overallScore(), 1),
                BigDecimal.valueOf(review.tasteScore(), 1), BigDecimal.valueOf(review.environmentScore(), 1),
                BigDecimal.valueOf(review.serviceScore(), 1), review.content(), review.tags(), review.createTime());
    }
}
