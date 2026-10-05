package com.dianping.feed;

import com.dianping.dto.ScrollResult;
import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.User;
import com.dianping.result.Result;
import com.dianping.service.UserService;
import com.dianping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FeedService {
    private final FeedRepository repository;
    private final FeedCache cache;
    private final FeedProperties properties;
    private final UserService userService;

    public FeedService(FeedRepository repository, FeedCache cache, FeedProperties properties, UserService users) {
        this.repository = repository;
        this.cache = cache;
        this.properties = properties;
        this.userService = users;
    }

    //计算feed的时间下界
    public long lower() {
        return Instant.now()
                .minus(Duration.ofDays(properties.retentionDays()))
                .toEpochMilli();
    }


    /**
     *
     * @param lastId 时间游标，表示本次查询的时间上界
     * @param offset 在lastId这个时点已经查询过的博客数量
     * @return
     */
    public Result<ScrollResult> read(long lastId, int offset) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return Result.error("请先登录");
        //非负+防止整数溢出
        if (lastId < 0 || offset < 0 || offset > Integer.MAX_VALUE - properties.pageSize()) {
            return Result.error("分页参数无效");
        }
        try {
            long upper = Math.min(lastId, System.currentTimeMillis());
            long lower = lower();
            List<FeedRepository.Author> authors = repository.authors(user.getId(), properties.threshold());
            //分为两组，一组普通作者一组热点作者
            List<Long> ordinary = authors.stream().filter(a -> !a.hot()).map(FeedRepository.Author::id).toList();
            List<Long> hot = authors.stream().filter(FeedRepository.Author::hot).map(FeedRepository.Author::id).toList();
            //候选数量，需要先跳过offset条博客
            int count = offset + properties.pageSize();
            List<Blog> candidates;
            //查询普通作者
            try {
                ensureCache(user.getId(), ordinary, lower);
                candidates = cachedCandidates(user.getId(), ordinary, lower, upper, count);
            } catch (RuntimeException cacheFailure) {
                log.warn("Feed 缓存不可用，改用数据库，userId={}", user.getId());
                //缓存不可用时降级数据库
                candidates = new ArrayList<>(repository.recent(ordinary, FeedTimeline.time(lower), FeedTimeline.time(upper), count));
            }
            //查询热点作者
            candidates.addAll(repository.recent(hot, FeedTimeline.time(lower), FeedTimeline.time(upper), count));
            ScrollResult result = FeedTimeline.page(candidates, upper, offset, properties.pageSize(), lower);
            //补充作者信息
            enrich(result);
            return Result.success(result);
        } catch (RuntimeException failure) {
            log.error("关注动态读取失败，userId={}", user.getId(), failure);
            return Result.error("关注动态加载失败，请稍后重试。");
        }
    }

    /**
     * 确保缓存存在，不存在就重建
     * @param userId
     * @param ordinary 当前用户关注的普通作者id列表
     * @param lower
     */
    private void ensureCache(long userId, List<Long> ordinary, long lower) {
        String signature = properties.retentionDays() + ":" + ordinary;
        //没准备好就重建缓存
        if (!cache.ready(userId, signature)) {
            String token = cache.beginBuild(userId);
            long after = 0;
            while (true) {
                List<Blog> blogs = repository.history(ordinary, FeedTimeline.time(lower), after, properties.batchSize());
                cache.add(userId, blogs, lower);
                if (blogs.size() < properties.batchSize()) break;
                after = blogs.getLast().getId();
            }
            cache.complete(userId, signature, token);
        }//清理窗口外的数据
        cache.prune(userId, lower);
    }

    /**
     * 读取缓存里的普通作者的博客
     * @param userId 用户id
     * @param ordinary 用户关注的普通作者id
     * @param lower 时间下界
     * @param upper 时间上界
     * @param count 本次最多准备多少篇
     * @return 博客列表
     */
    private List<Blog> cachedCandidates(long userId, List<Long> ordinary, long lower, long upper, int count) {
        Set<Long> allowed = new HashSet<>(ordinary);
        List<Blog> result = new ArrayList<>();
        long scanned = 0;//记录扫描位置
        while (result.size() < count) {
            List<Long> ids = cache.candidates(userId, lower, upper, scanned, properties.batchSize());
            if (ids.isEmpty()) break;
            Map<Long, Blog> blogs = new HashMap<>();
            repository.blogByIds(ids).forEach(blog -> blogs.put(blog.getId(), blog));
            for (Long id : ids) {
                Blog blog = blogs.get(id);
                if (blog != null && allowed.contains(blog.getUserId()) && FeedTimeline.score(blog) >= lower
                        && FeedTimeline.score(blog) <= upper) result.add(blog);
            }
            scanned += ids.size();
            if (ids.size() < properties.batchSize()) break;
        }
        return result;
    }

    //补充作者基本信息
    private void enrich(ScrollResult result) {
        List<?> blogs = result.getList();
        List<Long> authorIds = blogs.stream().map(b -> ((Blog) b).getUserId()).distinct().toList();
        if (authorIds.isEmpty()) return;
        Map<Long, User> authors = new HashMap<>();
        userService.listByIds(authorIds).forEach(user -> authors.put(user.getId(), user));
        for (Object value : blogs) {
            Blog blog = (Blog) value;
            User author = authors.get(blog.getUserId());
            if (author != null) {
                blog.setName(author.getNickName());
                blog.setIcon(author.getIcon());
            }
        }
    }

}
