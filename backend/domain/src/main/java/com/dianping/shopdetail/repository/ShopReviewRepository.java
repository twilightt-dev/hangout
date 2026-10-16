package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 评价用例的持久化边界，锁与写入须在同一事务中调用。 */
public interface ShopReviewRepository {
    boolean shopExists(Long shopId);
    boolean lockShop(Long shopId);
    Optional<ShopReview> findSubmission(Long userId, String requestId);
    Long insert(ShopReview review);
    ReviewStatistics statistics(Long shopId);
    void updateStatistics(Long shopId, ReviewStatistics statistics);
    List<ShopReview> page(Long shopId, long offset, int size);
    Map<Long, ReviewAuthor> authors(Collection<Long> userIds);
}
