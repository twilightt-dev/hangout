package com.dianping.shopdetail.repository;
import com.dianping.shopdetail.model.*;
import java.time.LocalDateTime;
/**
 * 首屏与查看更多使用同一筛选、排序和时间基准。
 */
public interface ShopVoucherRepository {
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @param now 本次读取或创建使用的时间基准
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopPage<ShopVoucher> page(Long shopId, long current, int size, LocalDateTime now);
}
