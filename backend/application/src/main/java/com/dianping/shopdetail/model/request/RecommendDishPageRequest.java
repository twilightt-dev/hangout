package com.dianping.shopdetail.model.request;

import com.dianping.shopdetail.exception.RecommendDishException;

/** 商详预览可取 6 条，独立浏览默认每页 12 条。 */
public record RecommendDishPageRequest(Long shopId, Long current, Integer size) {
    public RecommendDishPageRequest {
        if (shopId == null || shopId <= 0) throw new RecommendDishException(400, "店铺标识必须为正数");
        current = current == null ? 1L : current;
        size = size == null ? 12 : size;
        if (current <= 0 || size <= 0 || size > 50) throw new RecommendDishException(400, "页码须为正数，每页数量须为 1～50");
        try { Math.multiplyExact(current - 1, size.longValue()); }
        catch (ArithmeticException error) { throw new RecommendDishException(400, "页码超出支持范围"); }
    }
    public long offset() { return (current - 1) * size; }
}
