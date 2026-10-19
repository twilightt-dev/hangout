package com.dianping.shopdetail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.shopdetail.persistence.dataobject.ShopReview;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 当前业务表的 MyBatis 数据访问契约；写入锁须在调用方事务内使用。
 */
public interface ShopReviewMapper extends BaseMapper<ShopReview> {
    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id=#{shopId}") boolean shopExists(Long shopId);
    /**
     * 锁定店铺行，串行化该店铺的业务写入。
     *
     * @param shopId 店铺标识
     * @return 锁定的店铺标识；店铺不存在时为 {@code null}
     */
    @Select("SELECT id FROM tb_shop WHERE id=#{shopId} FOR UPDATE") Long lockShop(Long shopId);
    /**
     * 按作者和提交标识查询已持久化内容，用于幂等处理。
     *
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @return 相同用户及提交标识对应的持久化记录；不存在时为空
     */
    @Select("SELECT * FROM tb_shop_review WHERE user_id=#{userId} AND request_id=#{requestId}") ShopReview findSubmission(Long userId, String requestId);
    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    @Select("SELECT COUNT(*) FROM tb_shop_review WHERE shop_id=#{shopId}") long countByShopId(Long shopId);
    /**
     * 汇总店铺全部评价的十倍总体评分。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    @Select("SELECT COALESCE(SUM(overall_score),0) FROM tb_shop_review WHERE shop_id=#{shopId}") long sumOverallScoreByShopId(Long shopId);
    /**
     * 更新店铺评分与评价数；须与评价写入处于同一事务。
     *
     * @param shopId 店铺标识
     * @param score 按实际分数的十倍保存的评分
     * @param comments 评价或评论数量
     * @return 实际更新的店铺行数
     */
    @Update("UPDATE tb_shop SET score=#{score}, comments=#{comments} WHERE id=#{shopId}") int updateStatistics(Long shopId, int score, int comments);
    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Select("SELECT r.*, u.nick_name AS user_name, u.icon AS user_icon FROM tb_shop_review r LEFT JOIN tb_user u ON u.id=r.user_id WHERE r.shop_id=#{shopId} ORDER BY r.create_time DESC,r.id DESC LIMIT #{offset},#{size}") List<ShopReview> page(Long shopId,long offset,int size);
}
