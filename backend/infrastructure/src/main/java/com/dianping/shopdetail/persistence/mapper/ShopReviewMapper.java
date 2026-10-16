package com.dianping.shopdetail.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.shopdetail.persistence.dataobject.ShopReview;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

public interface ShopReviewMapper extends BaseMapper<ShopReview> {
    @Select("SELECT COUNT(*) FROM tb_shop WHERE id=#{shopId}") boolean shopExists(Long shopId);
    @Select("SELECT id FROM tb_shop WHERE id=#{shopId} FOR UPDATE") Long lockShop(Long shopId);
    @Select("SELECT * FROM tb_shop_review WHERE user_id=#{userId} AND request_id=#{requestId}") ShopReview findSubmission(Long userId, String requestId);
    @Select("SELECT COUNT(*) total, COALESCE(SUM(overall_score),0) scoreSum FROM tb_shop_review WHERE shop_id=#{shopId}") Map<String,Object> statistics(Long shopId);
    @Update("UPDATE tb_shop SET score=#{score}, comments=#{comments} WHERE id=#{shopId}") int updateStatistics(Long shopId, int score, int comments);
    @Select("SELECT * FROM tb_shop_review WHERE shop_id=#{shopId} ORDER BY create_time DESC,id DESC LIMIT #{offset},#{size}") List<ShopReview> page(Long shopId,long offset,int size);
    @Select("<script>SELECT id,nick_name,icon FROM tb_user WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>") List<Map<String,Object>> authors(List<Long> ids);
}
