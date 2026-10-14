package com.dianping.mapper;

import com.dianping.entity.BlogComments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {
    @Update("UPDATE tb_blog_comments SET liked=COALESCE(liked,0)+1 WHERE id=#{id} AND blog_id=#{blogId} AND parent_id=0 AND (status=0 OR status IS NULL) AND COALESCE(liked,0)<4294967295")
    int incrementLiked(@Param("id") Long id, @Param("blogId") Long blogId);

    @Update("UPDATE tb_blog_comments SET liked=liked-1 WHERE id=#{id} AND blog_id=#{blogId} AND parent_id=0 AND (status=0 OR status IS NULL) AND liked>0")
    int decrementLiked(@Param("id") Long id, @Param("blogId") Long blogId);

}
