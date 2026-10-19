package com.dianping.shopdetail.model;
import java.util.List;
/** 单次读取内的有序分页事实。 */
public record ShopPage<T>(List<T> records, long total, long current, int size) {
    public ShopPage { records = List.copyOf(records); }
    public long pages() { return total == 0 ? 0 : 1 + (total - 1) / size; }
}
