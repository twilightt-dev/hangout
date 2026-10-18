package com.dianping.shopdetail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.shopdetail.persistence.dataobject.RecommendDish;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface RecommendDishMapper extends BaseMapper<RecommendDish> {
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id=#{shopId}")
    boolean shopExists(@Param("shopId") Long shopId);

    @Select("SELECT id FROM tb_shop WHERE id=#{shopId} FOR UPDATE")
    Long lockShop(@Param("shopId") Long shopId);

    @Select("SELECT * FROM tb_recommend_dish WHERE user_id=#{userId} AND request_id=#{requestId}")
    RecommendDish findSubmission(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("SELECT * FROM tb_recommend_dish WHERE shop_id=#{shopId} AND name_key=#{nameKey}")
    RecommendDish findByName(@Param("shopId") Long shopId, @Param("nameKey") byte[] nameKey);

    @Select("SELECT * FROM tb_recommend_dish WHERE id=#{dishId} AND shop_id=#{shopId} FOR UPDATE")
    RecommendDish lockDish(@Param("shopId") Long shopId, @Param("dishId") Long dishId);

    @Select("SELECT * FROM tb_recommend_dish WHERE id=#{dishId} AND shop_id=#{shopId}")
    RecommendDish findById(@Param("shopId") Long shopId, @Param("dishId") Long dishId);

    @Select("SELECT COUNT(*) FROM tb_recommend_dish_user WHERE dish_id=#{dishId} AND user_id=#{userId}")
    boolean hasRecommendation(@Param("dishId") Long dishId, @Param("userId") Long userId);

    @Insert("INSERT INTO tb_recommend_dish_user(dish_id,user_id,create_time) VALUES(#{dishId},#{userId},CURRENT_TIMESTAMP)")
    int insertRecommendation(@Param("dishId") Long dishId, @Param("userId") Long userId);

    @Update("UPDATE tb_recommend_dish SET recommend_count=recommend_count+1 WHERE id=#{dishId}")
    int incrementRecommendationCount(@Param("dishId") Long dishId);

    @Select("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=#{shopId}")
    long countByShopId(@Param("shopId") Long shopId);

    @Select("SELECT * FROM tb_recommend_dish WHERE shop_id=#{shopId} ORDER BY recommend_count DESC,create_time DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<RecommendDish> page(@Param("shopId") Long shopId, @Param("offset") long offset, @Param("size") int size);

    @Select("""
            <script>
            SELECT dish_id FROM tb_recommend_dish_user WHERE user_id=#{userId} AND dish_id IN
            <foreach collection="dishIds" item="dishId" open="(" separator="," close=")">#{dishId}</foreach>
            </script>
            """)
    List<Long> recommendedIds(@Param("userId") Long userId, @Param("dishIds") List<Long> dishIds);
}
