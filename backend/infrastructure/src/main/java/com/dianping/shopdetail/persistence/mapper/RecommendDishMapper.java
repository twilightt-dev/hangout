package com.dianping.shopdetail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.shopdetail.persistence.dataobject.RecommendDish;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 当前业务表的 MyBatis 数据访问契约；写入锁须在调用方事务内使用。
 */
public interface RecommendDishMapper extends BaseMapper<RecommendDish> {
    /**
     * 检查店铺记录是否存在。
     *
     * @param shopId 店铺标识
     * @return 店铺是否存在
     */
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id=#{shopId}")
    boolean shopExists(@Param("shopId") Long shopId);

    /**
     * 锁定店铺行，串行化该店铺的业务写入。
     *
     * @param shopId 店铺标识
     * @return 锁定的店铺标识；店铺不存在时为 {@code null}
     */
    @Select("SELECT id FROM tb_shop WHERE id=#{shopId} FOR UPDATE")
    Long lockShop(@Param("shopId") Long shopId);

    /**
     * 按作者和提交标识查询已持久化内容，用于幂等处理。
     *
     * @param userId 服务端确认的用户标识
     * @param requestId 客户端生成的 UUID 提交标识，用于幂等校验
     * @return 相同用户及提交标识对应的持久化记录；不存在时为空
     */
    @Select("SELECT * FROM tb_recommend_dish WHERE user_id=#{userId} AND request_id=#{requestId}")
    RecommendDish findSubmission(@Param("userId") Long userId, @Param("requestId") String requestId);

    /**
     * 按当前店铺与规范菜名查询已有推荐菜。
     *
     * @param shopId 店铺标识
     * @param nameKey 用于精确去重的 UTF-8 菜名二进制键
     * @return 店内同规范菜名的记录；不存在时为空
     */
    @Select("SELECT * FROM tb_recommend_dish WHERE shop_id=#{shopId} AND name_key=#{nameKey}")
    RecommendDish findByName(@Param("shopId") Long shopId, @Param("nameKey") byte[] nameKey);

    /**
     * 按店铺和菜品标识查询并加行锁，用于推荐关系与人数一致更新。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 已加行锁的指定店内菜品；不存在时为空
     */
    @Select("SELECT * FROM tb_recommend_dish WHERE id=#{dishId} AND shop_id=#{shopId} FOR UPDATE")
    RecommendDish lockDish(@Param("shopId") Long shopId, @Param("dishId") Long dishId);

    /**
     * 按店铺和菜品标识查询推荐菜，避免跨店引用。
     *
     * @param shopId 店铺标识
     * @param dishId 菜品标识
     * @return 指定店内的菜品记录；不存在时为空
     */
    @Select("SELECT * FROM tb_recommend_dish WHERE id=#{dishId} AND shop_id=#{shopId}")
    RecommendDish findById(@Param("shopId") Long shopId, @Param("dishId") Long dishId);

    /**
     * 判断用户是否已推荐指定菜品。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 当前用户是否已经推荐该菜品
     */
    @Select("SELECT COUNT(*) FROM tb_recommend_dish_user WHERE dish_id=#{dishId} AND user_id=#{userId}")
    boolean hasRecommendation(@Param("dishId") Long dishId, @Param("userId") Long userId);

    /**
     * 插入用户与菜品的去重推荐关系。
     *
     * @param dishId 菜品标识
     * @param userId 服务端确认的用户标识
     * @return 实际插入的用户推荐关系行数
     */
    @Insert("INSERT INTO tb_recommend_dish_user(dish_id,user_id,create_time) VALUES(#{dishId},#{userId},CURRENT_TIMESTAMP)")
    int insertRecommendation(@Param("dishId") Long dishId, @Param("userId") Long userId);

    /**
     * 将菜品的不同用户推荐人数增加一。
     *
     * @param dishId 菜品标识
     * @return 实际更新的菜品行数
     */
    @Update("UPDATE tb_recommend_dish SET recommend_count=recommend_count+1 WHERE id=#{dishId}")
    int incrementRecommendationCount(@Param("dishId") Long dishId);

    /**
     * 统计指定店铺的全部业务记录。
     *
     * @param shopId 店铺标识
     * @return 匹配记录数或十倍总体评分总和
     */
    @Select("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=#{shopId}")
    long countByShopId(@Param("shopId") Long shopId);

    /**
     * 分页读取当前店铺的业务记录，返回有序明细及总数。
     *
     * @param shopId 店铺标识
     * @param offset 从 0 开始的记录偏移量
     * @param size 每页数量
     * @return 当前页结果；无匹配数据时为空记录集合
     */
    @Select("SELECT * FROM tb_recommend_dish WHERE shop_id=#{shopId} ORDER BY recommend_count DESC,create_time DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<RecommendDish> page(@Param("shopId") Long shopId, @Param("offset") long offset, @Param("size") int size);

    /**
     * 批量读取当前用户已推荐的菜品标识。
     *
     * @param userId 服务端确认的用户标识
     * @param dishIds 待检查的菜品标识集合
     * @return 当前用户在给定菜品集合中已推荐的标识
     */
    @Select("""
            <script>
            SELECT dish_id FROM tb_recommend_dish_user WHERE user_id=#{userId} AND dish_id IN
            <foreach collection="dishIds" item="dishId" open="(" separator="," close=")">#{dishId}</foreach>
            </script>
            """)
    List<Long> recommendedIds(@Param("userId") Long userId, @Param("dishIds") List<Long> dishIds);
}
