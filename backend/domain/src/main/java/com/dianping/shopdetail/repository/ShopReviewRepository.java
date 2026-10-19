package com.dianping.shopdetail.repository;

import com.dianping.shopdetail.model.*;
import java.util.List;
import java.util.Optional;

/**
 * 评价用例的持久化边界，锁与写入须在同一事务中调用。
 */
public interface ShopReviewRepository {

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
    Optional<ShopReview> findSubmission(Long userId, String requestId);
    /**
     * 插入经过领域校验的业务记录，并返回生成标识。
     *
     * @param review 评价业务数据
     * @return 新记录标识
     */
    Long insert(ShopReview review);
    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    long countByShopId(Long shopId);
    /**
     * 汇总店铺全部评价的十倍总体评分。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    long sumOverallScoreByShopId(Long shopId);
    /**
     * 更新店铺评分与评价数；须与评价写入处于同一事务。
     *
     * @param shopId 店铺标识
     * @param score 按实际分数的十倍保存的评分
     * @param total 全部匹配记录数
     */
    void updateStatistics(Long shopId, int score, int total);
    /**
     * 同时查询作者当前昵称和头像；作者不存在时仍保留评价。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    List<ShopReview> page(Long shopId, long offset, int size);
    /**
     * 在同一读取快照内取得分页明细和总数。
     *
     * @param shopId 店铺标识
     * @param current 从 1 开始的页码
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    ShopPage<ShopReview> pageWithTotal(Long shopId, long current, int size);
    /**
     * 在同一读取快照内汇总全店评价数量及均分。
     *
     * @param shopId 店铺标识
     * @return 全店评价数量和五分制总体均分；无评价时均分为空
     */
    ShopReviewSummary summarize(Long shopId);
}
