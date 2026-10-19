package com.dianping.shop.review.model.result;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评价分页与全店评分摘要响应，明细集合保持只读。
 *
 * @param records 当前页有序记录
 * @param total 全部匹配记录数
 * @param current 从 1 开始的页码
 * @param size 每页数量
 * @param pages 总页数
 * @param averageScore 五分制均分；无评价时为 {@code null}
 */
public record ShopReviewPageResult(List<ShopReviewItem> records, long total, long current, int size,
                                   long pages, BigDecimal averageScore) {
    /**
     * 复制记录集合建立只读分页结果。
     *
     * @param records 当前页有序记录
     * @param total 全部匹配记录数
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param pages 总页数
     * @param averageScore 五分制均分；无评价时为 {@code null}
     */
    public ShopReviewPageResult { records = List.copyOf(records); }
}
