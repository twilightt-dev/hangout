package com.dianping.shopdetail.repository;

import com.dianping.shop.shopdetail.exception.DuplicateRecommendDishException;
import com.dianping.shop.shopdetail.model.RecommendDish;
import com.dianping.shop.shopdetail.model.ShopPage;
import com.dianping.shop.shopdetail.repository.RecommendDishRepository;
import org.springframework.transaction.annotation.*;
import com.dianping.shopdetail.persistence.mapper.RecommendDishMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 实现RecommendDish仓储，负责数据库查询及持久化模型转换。
 */
@Repository
public class RecommendDishRepositoryImpl implements RecommendDishRepository {
    /**
     * 数据映射器。
     */
    private final RecommendDishMapper mapper;
    /**
     * 创建 RecommendDishRepositoryImpl，初始化所需依赖及实例状态。
     *
     * @param mapper 当前业务的 MyBatis 数据访问接口
     */
    public RecommendDishRepositoryImpl(RecommendDishMapper mapper) { this.mapper = mapper; }

    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    @Override public boolean shopExists(Long shopId) { return mapper.shopExists(shopId); }
    /**
     * 锁定店铺行，串行化该店铺的业务写入。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在且已成功取得行锁
     */
    @Override public boolean lockShop(Long shopId) { return mapper.lockShop(shopId) != null; }
    /**
     * 按作者和提交标识查询已持久化内容，用于幂等处理。
     *
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @return 相同用户及提交标识对应的持久化记录；不存在时为空
     */
    @Override public Optional<RecommendDish> findSubmission(Long userId, String requestId) {
        return Optional.ofNullable(mapper.findSubmission(userId, requestId)).map(this::toDomain);
    }
    /**
     * 按当前店铺与规范菜名查询已有推荐菜。
     *
     * @param shopId 店铺标识
     * @param name 名称
     * @return 店内同规范菜名的记录；不存在时为空
     */
    @Override public Optional<RecommendDish> findByName(Long shopId, String name) {
        return Optional.ofNullable(mapper.findByName(shopId, name.getBytes(StandardCharsets.UTF_8))).map(this::toDomain);
    }
    /**
     * 插入经过领域校验的业务记录，并返回生成标识。
     *
     * @param dish 推荐菜业务数据
     * @return 新记录标识
     */
    @Override public Long insert(RecommendDish dish) {
        var row = new com.dianping.shopdetail.persistence.dataobject.RecommendDish();
        row.setShopId(dish.shopId()); row.setUserId(dish.userId()); row.setRequestId(dish.requestId());
        row.setName(dish.name()); row.setNameKey(dish.name().getBytes(StandardCharsets.UTF_8));
        row.setCoverImage(dish.coverImage()); row.setRecommendCount(dish.recommendCount()); row.setCreateTime(dish.createTime());
        try { mapper.insert(row); return row.getId(); }
        catch (DuplicateKeyException error) { throw new DuplicateRecommendDishException(error); }
    }
    /**
     * 按店铺和菜品标识查询并加行锁，用于推荐关系与人数一致更新。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 已加行锁的指定店内菜品；不存在时为空
     */
    @Override public Optional<RecommendDish> lockDish(Long shopId, Long dishId) {
        return Optional.ofNullable(mapper.lockDish(shopId, dishId)).map(this::toDomain);
    }
    /**
     * 按店铺和菜品标识查询推荐菜，避免跨店引用。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 指定店内的菜品记录；不存在时为空
     */
    @Override public Optional<RecommendDish> findById(Long shopId, Long dishId) {
        return Optional.ofNullable(mapper.findById(shopId, dishId)).map(this::toDomain);
    }
    /**
     * 判断用户是否已推荐指定菜品。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 当前用户是否已经推荐该菜品
     */
    @Override public boolean hasRecommendation(Long dishId, Long userId) { return mapper.hasRecommendation(dishId, userId); }
    /**
     * 插入用户与菜品的去重推荐关系。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     */
    @Override public void insertRecommendation(Long dishId, Long userId) {
        if (mapper.insertRecommendation(dishId, userId) != 1) throw new IllegalStateException("推荐关系写入失败");
    }
    /**
     * 将菜品的不同用户推荐人数增加一。
     *
     * @param dishId 菜品标识
     */
    @Override public void incrementRecommendationCount(Long dishId) {
        if (mapper.incrementRecommendationCount(dishId) != 1) throw new IllegalStateException("推荐人数更新失败");
    }
    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    @Override public long countByShopId(Long shopId) { return mapper.countByShopId(shopId); }
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Override public List<RecommendDish> page(Long shopId, long offset, int size) {
        return mapper.page(shopId, offset, size).stream().map(this::toDomain).toList();
    }
    /**
     * 批量读取当前用户已推荐的菜品标识。
     *
     * @param userId 服务端确认的用户标识
     * @param dishIds 待检查的菜品标识集合
     * @return 当前用户在给定菜品集合中已推荐的标识
     */
    @Override public Set<Long> recommendedIds(Long userId, List<Long> dishIds) {
        return dishIds.isEmpty() ? Set.of() : Set.copyOf(mapper.recommendedIds(userId, dishIds));
    }
    /**
     * 将数据库记录映射为领域业务数据。
     *
     * @param row 数据库记录
     * @return 由持久化字段恢复的领域业务对象
     */
    private RecommendDish toDomain(com.dianping.shopdetail.persistence.dataobject.RecommendDish row) {
        return new RecommendDish(row.getId(), row.getShopId(), row.getUserId(), row.getRequestId(),
                row.getName(), row.getCoverImage(), row.getRecommendCount(), row.getCreateTime());
    }
    /**
     * 在同一读取快照内取得分页明细和总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public ShopPage<RecommendDish> pageWithTotal(Long shopId, long current, int size) {
        return new ShopPage<>(page(shopId, Math.multiplyExact(current - 1, (long) size), size),
                countByShopId(shopId), current, size);
    }
}
