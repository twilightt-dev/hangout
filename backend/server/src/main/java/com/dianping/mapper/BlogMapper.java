package com.dianping.mapper;

import com.dianping.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {
    List<Blog> selectFeedRecent(@Param("authors") Collection<Long> authors,
            @Param("lower") LocalDateTime lower, @Param("upper") LocalDateTime upper, @Param("limit") int limit);

    List<Blog> selectFeedHistory(@Param("authors") Collection<Long> authors,
            @Param("lower") LocalDateTime lower, @Param("afterBlogId") long afterBlogId, @Param("limit") int limit);
}
