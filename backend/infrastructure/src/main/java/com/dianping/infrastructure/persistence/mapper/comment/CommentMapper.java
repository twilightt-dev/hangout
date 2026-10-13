package com.dianping.infrastructure.persistence.mapper.comment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import org.apache.ibatis.annotations.*;

/** 新评论链路的数据访问接口，由启动模块统一扫描装配。 */
public interface CommentMapper extends BaseMapper<CommentDO> {
    @Select("SELECT * FROM tb_blog_comments WHERE id=#{id} FOR UPDATE")
    CommentDO lockForLikeInitialization(@Param("id") Long id);

    @Update("UPDATE tb_blog_comments SET like_initialized=TRUE WHERE id=#{id} AND like_epoch=#{epoch} AND like_initialized=FALSE AND like_version=0 AND COALESCE(liked,0)=0")
    int markLikeInitialized(@Param("id") Long id, @Param("epoch") long epoch);

    @Update("UPDATE tb_blog_comments SET liked=#{liked},like_version=#{version} WHERE id=#{id} AND like_epoch=#{epoch} AND like_epoch>0 AND like_initialized=TRUE AND like_version<#{version}")
    int projectLikeCount(@Param("id") Long id, @Param("epoch") long epoch, @Param("version") long version, @Param("liked") long liked);
}
