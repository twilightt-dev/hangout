package com.dianping.shopdetail.model;
import java.util.List;
/**
 * 单次读取内的有序分页事实。
 *
 * @param <T> 当前业务数据类型
 * @param records 当前页有序记录
 * @param total 全部匹配记录数
 * @param current 从 1 开始的页码
 * @param size 每页数量
 */
public record ShopPage<T>(List<T> records, long total, long current, int size) {
    /**
     * 复制记录集合建立只读分页结果。
     *
     * @param records 当前页有序记录
     * @param total 全部匹配记录数
     * @param current 从 1 开始的页码
     * @param size 每页数量
     */
    public ShopPage { records = List.copyOf(records); }
    /**
     * 根据全部记录数和每页数量计算总页数。
     *
     * @return 总页数
     */
    public long pages() { return total == 0 ? 0 : 1 + (total - 1) / size; }
}
