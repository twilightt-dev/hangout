package com.dianping.shop.recommend.model.result;

import java.util.List;

/**
 * 推荐菜独立分页响应，记录集合通过防御性复制保持只读。
 *
 * @param records 当前页有序记录
 * @param total 全部匹配记录数
 * @param current 从 1 开始的页码
 * @param size 每页数量
 * @param pages 总页数
 */
public record RecommendDishPageResult(List<RecommendDishItem> records, long total, long current, int size, long pages) {
    /**
     * 复制记录集合建立只读分页结果。
     *
     * @param records 当前页有序记录
     * @param total 全部匹配记录数
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param pages 总页数
     */
    public RecommendDishPageResult { records = List.copyOf(records); }
}
