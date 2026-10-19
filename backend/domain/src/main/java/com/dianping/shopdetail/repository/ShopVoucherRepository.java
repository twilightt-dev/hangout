package com.dianping.shopdetail.repository;
import com.dianping.shopdetail.model.*;
import java.time.LocalDateTime;
/** 首屏与查看更多使用同一筛选、排序和时间基准。 */
public interface ShopVoucherRepository {
    ShopPage<ShopVoucher> page(Long shopId, long current, int size, LocalDateTime now);
}
