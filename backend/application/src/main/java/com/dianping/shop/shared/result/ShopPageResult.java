package com.dianping.shop.shared.result;

import com.dianping.shop.shopdetail.model.ShopPage;
import java.util.List;
import java.util.function.Function;

/**
 * 店铺各业务共享的应用层分页响应，保存当前页记录与分页统计。
 *
 * @param <T> 当前业务数据类型
 * @param records 当前页有序记录
 * @param total 全部匹配记录数
 * @param current 从 1 开始的页码
 * @param size 每页数量
 * @param pages 总页数
 */
public record ShopPageResult<T>(List<T> records, long total, long current, int size, long pages) {
    /**
     * 复制记录集合建立只读分页结果。
     *
     * @param records 当前页有序记录
     * @param total 全部匹配记录数
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param pages 总页数
     */
    public ShopPageResult { records = List.copyOf(records); }
    /**
     * 将领域业务数据转换为当前响应模型，不暴露持久化对象。
     *
     * @param <T> 当前业务数据类型
     * @param <R> 转换后的业务数据类型
     * @param page 仓储或领域服务返回的分页数据
     * @param mapper 将领域记录转换为应用响应项的函数
     * @return 转换列表项后保留原分页统计的响应
     */
    public static <T, R> ShopPageResult<R> from(ShopPage<T> page, Function<T, R> mapper) {
        return new ShopPageResult<>(page.records().stream().map(mapper).toList(), page.total(), page.current(), page.size(), page.pages());
    }
}
