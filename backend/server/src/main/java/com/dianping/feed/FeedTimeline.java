package com.dianping.feed;

import com.dianping.dto.ScrollResult;
import com.dianping.entity.Blog;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;


public final class FeedTimeline {
    private FeedTimeline() {}

    //把博客发布时间转换为毫秒时间戳
    public static long score(Blog blog) {
        return blog.getCreateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }
    //反向转换
    public static LocalDateTime time(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    /**
     *
     * @param candidates 待处理的候选博客
     * @param upper 本次查询的时间上界
     * @param offset 在upper这个时点已经查询过多少篇
     * @param size 每页最多多少篇
     * @param lower 本次查询的时间下界
     * @return 统一的scrollResult
     * upper+offset就是总的游标，表示已经查询过的部分
     */
    public static ScrollResult page(List<Blog> candidates, long upper, int offset, int size, long lower) {
        //过滤并去重
        Map<Long, Blog> unique = new LinkedHashMap<>();
        for (Blog blog : candidates) {
            if (blog.getCreateTime() != null && score(blog) >= lower && score(blog) <= upper) unique.put(blog.getId(), blog);
        }
        //统一排序
        List<Blog> sorted = unique.values().stream()
                .sorted(Comparator //先按发布时间倒序，新的在前
                        .comparingLong(FeedTimeline::score).reversed()
                        //发布时间相同的再按博客ID的字符串顺序倒序，和redis zset保持一致
                        .thenComparing(blog -> blog.getId().toString(), Comparator.reverseOrder()))
                .toList();

        int skip = offset;
        List<Blog> page = new ArrayList<>() ;
        for (Blog blog : sorted) {
            if (score(blog) == upper && skip > 0) { skip--; continue; }
            page.add(blog);
            if (page.size() == size) break;
        }

        ScrollResult result = new ScrollResult();
        //计算本页最后一篇博客的时间，就是下一次查询的upper
        long minTime = page.isEmpty() ? upper : score(page.getLast());
        //计算minTime这个时点上有多少篇博客，就是下一次查询的offset
        int sameTime = (int) page.stream().filter(blog -> score(blog) == minTime).count();

        result.setList(page);
        result.setMinTime(minTime);
        result.setOffset(page.isEmpty() ? offset : sameTime + (minTime == upper ? offset : 0));
        return result;
    }
}
