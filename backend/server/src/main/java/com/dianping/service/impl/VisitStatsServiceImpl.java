package com.dianping.service.impl;

import com.dianping.VO.VisitStatsVO;
import com.dianping.dto.VisitRequestDTO;
import com.dianping.exception.VisitStatsException;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.ShopMapper;
import com.dianping.service.VisitStatsService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class VisitStatsServiceImpl implements VisitStatsService {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final DefaultRedisScript<Long> RECORD = new DefaultRedisScript<>();
    static {
        RECORD.setLocation(new ClassPathResource("lua/record-visit.lua"));
        RECORD.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final BlogMapper blogs;
    private final ShopMapper shops;

    public VisitStatsServiceImpl(StringRedisTemplate redis, BlogMapper blogs, ShopMapper shops) {
        this.redis = redis;
        this.blogs = blogs;
        this.shops = shops;
    }

    @Override
    public VisitStatsVO recordBlog(Long id, VisitRequestDTO event) {
        validate(id, event);
        if (blogs.selectById(id) == null) throw new VisitStatsException(HttpStatus.NOT_FOUND, "博客不存在");
        return record("blog", id, "pv", event);
    }

    @Override
    public VisitStatsVO recordShop(Long id, VisitRequestDTO event) {
        validate(id, event);
        if (shops.selectById(id) == null) throw new VisitStatsException(HttpStatus.NOT_FOUND, "店铺不存在");
        return record("shop", id, "uv", event);
    }

    private VisitStatsVO record(String resource, long id, String metric, VisitRequestDTO event) {
        String prefix = "stats:{" + resource + ":" + id + "}:";
        String visitor = event.visitorId().toLowerCase(Locale.ROOT);
        String eventId = event.eventId().toLowerCase(Locale.ROOT);
        // 同资源的键放入同一 Redis Cluster 槽，计数与事件去重由脚本原子执行。
        Long count = redis.execute(RECORD, List.of(prefix + metric,
                prefix + "event:" + visitor + ":" + eventId, prefix + "rate:" + visitor),
                metric, visitor, "30", "60", "86400");
        if (count == null) throw new DataAccessResourceFailureException("Redis 未确认访问统计结果");
        if (count == -1) throw new VisitStatsException(HttpStatus.TOO_MANY_REQUESTS, "访问上报过于频繁，请稍后再试");
        if (count < 0) throw new DataAccessResourceFailureException("Redis 返回无效访问统计结果");
        return new VisitStatsVO(count);
    }

    private static void validate(Long id, VisitRequestDTO event) {
        if (id == null || id <= 0 || event == null || !isUuid(event.visitorId()) || !isUuid(event.eventId())) {
            throw new VisitStatsException(HttpStatus.BAD_REQUEST, "资源 ID 或访客、事件标识无效");
        }
    }

    private static boolean isUuid(String value) { return value != null && UUID_PATTERN.matcher(value).matches(); }
}
