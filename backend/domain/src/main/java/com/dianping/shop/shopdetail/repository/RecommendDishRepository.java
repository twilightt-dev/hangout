package com.dianping.shop.shopdetail.repository;

import com.dianping.shop.shopdetail.model.RecommendDish;
import com.dianping.shop.shopdetail.model.ShopPage;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 推荐菜与推荐关系的持久化契约；写操作由应用层组织为一个事务。
 */
public interface RecommendDishRepository {
    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    boolean shopExists(Long shopId);
    /**
     * 锁定店铺行，串行化该店铺的业务写入。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在且已成功取得行锁
     */
    boolean lockShop(Long shopId);
    /**
     * 按作者和提交标识查询已持久化内容，用于幂等处理。
     *
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @return 相同用户及提交标识对应的持久化记录；不存在时为空
     */
    Optional<RecommendDish> findSubmission(Long userId, String requestId);
    /**
     * 按当前店铺与规范菜名查询已有推荐菜。
     *
     * @param shopId 店铺标识
     * @param name 名称
     * @return 店内同规范菜名的记录；不存在时为空
     */
    Optional<RecommendDish> findByName(Long shopId, String name);
    /**
     * 插入经过领域校验的业务记录，并返回生成标识。
     *
     * @param dish 推荐菜业务数据
     * @return 新记录标识
     */
    Long insert(RecommendDish dish);
    /**
     * 按店铺和菜品标识查询并加行锁，用于推荐关系与人数一致更新。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 已加行锁的指定店内菜品；不存在时为空
     */
    Optional<RecommendDish> lockDish(Long shopId, Long dishId);
    /**
     * 按店铺和菜品标识查询推荐菜，避免跨店引用。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 指定店内的菜品记录；不存在时为空
     */
    Optional<RecommendDish> findById(Long shopId, Long dishId);
    /**
     * 判断用户是否已推荐指定菜品。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 当前用户是否已经推荐该菜品
     */
    boolean hasRecommendation(Long dishId, Long userId);
    /**
     * 插入用户与菜品的去重推荐关系。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     */
    void insertRecommendation(Long dishId, Long userId);
    /**
     * 将菜品的不同用户推荐人数增加一。
     *
     * @param dishId 菜品标识
     */
    void incrementRecommendationCount(Long dishId);
    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    long countByShopId(Long shopId);
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    List<RecommendDish> page(Long shopId, long offset, int size);
    /**
     * 批量读取当前用户已推荐的菜品标识。
     *
     * @param userId 服务端确认的用户标识
     * @param dishIds 待检查的菜品标识集合
     * @return 当前用户在给定菜品集合中已推荐的标识
     */
    Set<Long> recommendedIds(Long userId, List<Long> dishIds);
    /**
     * 在同一读取快照内取得分页明细和总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopPage<RecommendDish> pageWithTotal(Long shopId, long current, int size);
}
