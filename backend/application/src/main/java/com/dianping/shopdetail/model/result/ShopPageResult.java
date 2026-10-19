package com.dianping.shopdetail.model.result;
import com.dianping.shopdetail.model.ShopPage;
import java.util.List;
import java.util.function.Function;
/** HTTP 业务分页数据，不暴露持久化分页类型。 */
public record ShopPageResult<T>(List<T> records, long total, long current, int size, long pages) {
    public ShopPageResult { records = List.copyOf(records); }
    public static <T, R> ShopPageResult<R> from(ShopPage<T> page, Function<T, R> mapper) {
        return new ShopPageResult<>(page.records().stream().map(mapper).toList(), page.total(), page.current(), page.size(), page.pages());
    }
}
