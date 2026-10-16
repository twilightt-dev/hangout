package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.*;
import java.util.List;
import java.util.Optional;

/** 评价用例的持久化边界，锁与写入须在同一事务中调用。 */
public interface ShopReviewRepository {

    boolean shopExists(Long shopId);
    boolean lockShop(Long shopId);
    Optional<ShopReview> findSubmission(Long userId, String requestId);
    Long insert(ShopReview review);
    long countByShopId(Long shopId);
    long sumOverallScoreByShopId(Long shopId);
    void updateStatistics(Long shopId, int score, int total);
    /** 同时查询作者当前昵称和头像；作者不存在时仍保留评价。 */
    List<ShopReview> page(Long shopId, long offset, int size);
}
