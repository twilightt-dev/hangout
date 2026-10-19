package com.dianping.shopdetail.model.request;
import com.dianping.shopdetail.exception.ShopDetailException;
/** 新增列表的分页请求；溢出页码在进入仓储前拒绝。 */
public record ShopDetailPageRequest(Long shopId, long current, int size) {
    public ShopDetailPageRequest {
        if (shopId == null || shopId <= 0) throw new ShopDetailException(400, "店铺 ID 必须为正整数");
        if (current < 1 || size < 1 || size > 50) throw new ShopDetailException(400, "页码须为正数，每页数量须为 1～50");
        try { Math.multiplyExact(current - 1, (long) size); }
        catch (ArithmeticException error) { throw new ShopDetailException(400, "页码超出支持范围"); }
    }
    public long offset() { return (current - 1) * size; }
}
