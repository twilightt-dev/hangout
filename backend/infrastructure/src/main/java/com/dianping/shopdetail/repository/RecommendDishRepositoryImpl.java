package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.exception.DuplicateRecommendDishException;
import com.dianping.shopdetail.model.RecommendDish;
import com.dianping.shopdetail.persistence.mapper.RecommendDishMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class RecommendDishRepositoryImpl implements RecommendDishRepository {
    private final RecommendDishMapper mapper;
    public RecommendDishRepositoryImpl(RecommendDishMapper mapper) { this.mapper = mapper; }

    @Override public boolean shopExists(Long shopId) { return mapper.shopExists(shopId); }
    @Override public boolean lockShop(Long shopId) { return mapper.lockShop(shopId) != null; }
    @Override public Optional<RecommendDish> findSubmission(Long userId, String requestId) {
        return Optional.ofNullable(mapper.findSubmission(userId, requestId)).map(this::toDomain);
    }
    @Override public Optional<RecommendDish> findByName(Long shopId, String name) {
        return Optional.ofNullable(mapper.findByName(shopId, name.getBytes(StandardCharsets.UTF_8))).map(this::toDomain);
    }
    @Override public Long insert(RecommendDish dish) {
        var row = new com.dianping.shopdetail.persistence.dataobject.RecommendDish();
        row.setShopId(dish.shopId()); row.setUserId(dish.userId()); row.setRequestId(dish.requestId());
        row.setName(dish.name()); row.setNameKey(dish.name().getBytes(StandardCharsets.UTF_8));
        row.setCoverImage(dish.coverImage()); row.setRecommendCount(dish.recommendCount()); row.setCreateTime(dish.createTime());
        try { mapper.insert(row); return row.getId(); }
        catch (DuplicateKeyException error) { throw new DuplicateRecommendDishException(error); }
    }
    @Override public Optional<RecommendDish> lockDish(Long shopId, Long dishId) {
        return Optional.ofNullable(mapper.lockDish(shopId, dishId)).map(this::toDomain);
    }
    @Override public Optional<RecommendDish> findById(Long shopId, Long dishId) {
        return Optional.ofNullable(mapper.findById(shopId, dishId)).map(this::toDomain);
    }
    @Override public boolean hasRecommendation(Long dishId, Long userId) { return mapper.hasRecommendation(dishId, userId); }
    @Override public void insertRecommendation(Long dishId, Long userId) {
        if (mapper.insertRecommendation(dishId, userId) != 1) throw new IllegalStateException("推荐关系写入失败");
    }
    @Override public void incrementRecommendationCount(Long dishId) {
        if (mapper.incrementRecommendationCount(dishId) != 1) throw new IllegalStateException("推荐人数更新失败");
    }
    @Override public long countByShopId(Long shopId) { return mapper.countByShopId(shopId); }
    @Override public List<RecommendDish> page(Long shopId, long offset, int size) {
        return mapper.page(shopId, offset, size).stream().map(this::toDomain).toList();
    }
    @Override public Set<Long> recommendedIds(Long userId, List<Long> dishIds) {
        return dishIds.isEmpty() ? Set.of() : Set.copyOf(mapper.recommendedIds(userId, dishIds));
    }
    private RecommendDish toDomain(com.dianping.shopdetail.persistence.dataobject.RecommendDish row) {
        return new RecommendDish(row.getId(), row.getShopId(), row.getUserId(), row.getRequestId(),
                row.getName(), row.getCoverImage(), row.getRecommendCount(), row.getCreateTime());
    }
}
