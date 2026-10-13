package com.dianping.feed;

import com.dianping.entity.Blog;
import com.dianping.entity.Follow;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.FollowMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Feed 的业务数据查询入口；通用查询使用 MyBatis-Plus，定制查询定义于 Mapper XML。 */
@Repository
public class FeedRepository {
    private final BlogMapper blogMapper;
    private final FollowMapper followMapper;

    public FeedRepository(BlogMapper blogs, FollowMapper follows) {
        this.blogMapper = blogs;
        this.followMapper = follows;
    }
    //一个记录内部类，完整名称就是FeedRepository.Author,嵌套的record类隐式是static类型
    //相当于一个摘要，不需要查询完整的数据
    public record Author(long id, boolean hot) {}

    /**
     * 查询关注的作者
     * @param userId 用户id
     * @param threshold 热点作者阈值
     * @return 作者列表
     */
    public List<Author> authors(long userId, int threshold) {
        return followMapper.selectFeedAuthors(userId, threshold);
    }

    /**
     * 根据作者id和阈值判断是否是热点作者
     * @param authorId 作者id
     * @param threshold 热点作者阈值
     * @return
     */
    public boolean hot(long authorId, int threshold) {
        // 关系表是持久化来源，避免历史用户统计未初始化造成漏推或错误分类。
        Long fans = followMapper.selectCount(Wrappers.<Follow>lambdaQuery().eq(Follow::getFollowUserId, authorId));
        return fans != null && fans >= threshold;
    }
    //判断是否有关注关系
    public boolean follows(long userId, long authorId) {
        Long count = followMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, authorId));
        return count != null && count > 0;
    }

    /**
     * 用于分批查询某个作者的粉丝ID
     * @param authorId 作者Id
     * @param afterUserId 游标，只查询id大于它的用户
     * @param limit 本次最多查询多少个粉丝
     * @return
     */
    public List<Long> fans(long authorId, long afterUserId, int limit) {
        return followMapper.selectFeedFans(authorId, afterUserId, limit);
    }

    //根据id查询博客
    public Blog blog(long id) {
        return blogMapper.selectById(id);
    }

    //根据id批量查询博客
    public List<Blog> blogByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return blogMapper.selectByIds(ids);
    }

    //查询一段时间内的博客
    public List<Blog> recent(Collection<Long> authors, LocalDateTime lower, LocalDateTime upper, int limit) {
        if (authors.isEmpty()) return List.of();
        return blogMapper.selectFeedRecent(authors, lower, upper, limit);
    }

    //查询历史博客，通常就是新关注了一个用户，获得一定范围的历史博客
    public List<Blog> history(Collection<Long> authors, LocalDateTime lower, long afterBlogId, int limit) {
        if (authors.isEmpty()) return List.of();
        return blogMapper.selectFeedHistory(authors, lower, afterBlogId, limit);
    }

}
