package com.dianping.mapper;

import com.dianping.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dianping.feed.FeedRepository;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {
    List<FeedRepository.Author> selectFeedAuthors(@Param("userId") long userId, @Param("threshold") int threshold);

    List<Long> selectFeedFans(@Param("authorId") long authorId,
            @Param("afterUserId") long afterUserId, @Param("limit") int limit);
}
