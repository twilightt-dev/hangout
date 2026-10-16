package com.dianping.shopdetail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.shopdetail.persistence.dataobject.ShopReview;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ShopReviewMapper extends BaseMapper<ShopReview> {
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id=#{shopId}") boolean shopExists(Long shopId);
    @Select("SELECT id FROM tb_shop WHERE id=#{shopId} FOR UPDATE") Long lockShop(Long shopId);
    @Select("SELECT * FROM tb_shop_review WHERE user_id=#{userId} AND request_id=#{requestId}") ShopReview findSubmission(Long userId, String requestId);
    @Select("SELECT COUNT(*) FROM tb_shop_review WHERE shop_id=#{shopId}") long countByShopId(Long shopId);
    @Select("SELECT COALESCE(SUM(overall_score),0) FROM tb_shop_review WHERE shop_id=#{shopId}") long sumOverallScoreByShopId(Long shopId);
    @Update("UPDATE tb_shop SET score=#{score}, comments=#{comments} WHERE id=#{shopId}") int updateStatistics(Long shopId, int score, int comments);
    @Select("SELECT r.*, u.nick_name AS user_name, u.icon AS user_icon FROM tb_shop_review r LEFT JOIN tb_user u ON u.id=r.user_id WHERE r.shop_id=#{shopId} ORDER BY r.create_time DESC,r.id DESC LIMIT #{offset},#{size}") List<ShopReview> page(Long shopId,long offset,int size);
}
