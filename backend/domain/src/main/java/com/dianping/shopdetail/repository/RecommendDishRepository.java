package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.RecommendDish;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 推荐菜与推荐关系的持久化契约；写操作由应用层组织为一个事务。 */
public interface RecommendDishRepository {
    boolean shopExists(Long shopId);
    boolean lockShop(Long shopId);
    Optional<RecommendDish> findSubmission(Long userId, String requestId);
    Optional<RecommendDish> findByName(Long shopId, String name);
    Long insert(RecommendDish dish);
    Optional<RecommendDish> lockDish(Long shopId, Long dishId);
    Optional<RecommendDish> findById(Long shopId, Long dishId);
    boolean hasRecommendation(Long dishId, Long userId);
    void insertRecommendation(Long dishId, Long userId);
    void incrementRecommendationCount(Long dishId);
    long countByShopId(Long shopId);
    List<RecommendDish> page(Long shopId, long offset, int size);
    Set<Long> recommendedIds(Long userId, List<Long> dishIds);
}
