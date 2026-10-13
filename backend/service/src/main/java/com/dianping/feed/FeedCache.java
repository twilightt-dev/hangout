package com.dianping.feed;

import com.dianping.entity.Blog;
import com.dianping.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class FeedCache {
    private final StringRedisTemplate redis;
    private final FeedProperties properties;
    //token用于标识这是哪一次重建，防止旧请求覆盖新状态
    //signature用于标识“缓存是按什么条件构建的”，比如30:[10,20]表示展示30天，作者列表为10，20
    //这段Lua脚本用于完成缓存重建
    /**
     * 3个key分别是：
     * feed:state:{userId}表示缓存重建状态 存的是token
     * feed:state:{userId}:empty 空结果标记  存的是0或1
     * feed:{userId} 存的是时间线zset
     * 3个参数分别是：本次重建的token、重建完成后的签名、ttl
     * 三个功能：
     * 1.先校验token，判断本轮构建是否仍然有效，无效就提前返回
     * 2.更新empty标记
     * 3.更新state，从token换为signature
     */
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local empty = '1' 
            if redis.call('ZCARD', KEYS[3]) > 0 then empty = '0' end
            redis.call('SET', KEYS[2], empty, 'EX', ARGV[3])
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            return 1
            """, Long.class);

    public FeedCache(StringRedisTemplate redis, FeedProperties properties) {
        this.redis = redis; this.properties = properties;
    }
    //辅助方法
    private String key(long userId) { return RedisConstants.FEED_KEY + userId; }
    //stateKey在重建期间存token，重建完成后存签名
    private String stateKey(long userId) { return RedisConstants.FEED_STATE_KEY + userId; }

    /**
     * 判断当前用户的缓存是否可以使用
     * @param userId 用户id
     * @param signature 签名
     * @return 布尔值
     */
    public boolean ready(long userId, String signature) {
        //判断缓存是否重建完成（如果存的还是token就说明没完成），并且是否符合当前条件（作者列表或者保留天数变了就需要重建新的缓存）
        if (!signature.equals(redis.opsForValue().get(stateKey(userId)))) return false;
        //判空
        String empty = redis.opsForValue().get(stateKey(userId) + ":empty");
        //1就是确实为空，0的话就要保证zset的key必须存在，注意空结果也是可用缓存
        return "1".equals(empty) || ("0".equals(empty) && redis.hasKey(key(userId)));
    }


    //生成本轮构建的唯一标识token，写入redis状态key
    public String beginBuild(long userId) {
        String token = "building:" + UUID.randomUUID();
        redis.opsForValue().set(stateKey(userId), token, Duration.ofDays(properties.retentionDays() + 1L));
        return token;
    }

    //让其他类在写完缓存之后调用Lua脚本，完成本轮构建的收尾工作
    public void complete(long userId, String signature, String token) {
        redis.execute(COMPLETE, List.of(stateKey(userId), stateKey(userId) + ":empty", key(userId)),
                token, signature, String.valueOf(Duration.ofDays(properties.retentionDays() + 1L).toSeconds()));
    }

    //清除构建状态
    public void invalidate(long userId) { redis.delete(stateKey(userId)); }

    //向zset中添加元素
    public void add(long userId, List<Blog> blogs, long lower) {
        for (Blog blog : blogs) {
            long score = FeedTimeline.score(blog);
            if (score >= lower) {
                redis.opsForZSet().add(key(userId), blog.getId().toString(), score);
                redis.opsForValue().set(stateKey(userId) + ":empty", "0", Duration.ofDays(properties.retentionDays() + 1L));
            }
        }
        prune(userId, lower);
        redis.expire(key(userId), Duration.ofDays(properties.retentionDays() + 1L));
    }

    //清理过期博客，负无穷到lower-1
    public void prune(long userId, long lower) {
        redis.opsForZSet().removeRangeByScore(key(userId), Double.NEGATIVE_INFINITY, lower - 1);
    }

    //按时间倒序读取候选博客ID
    public List<Long> candidates(long userId, long lower, long upper, long offset, int limit) {
        Set<ZSetOperations.TypedTuple<String>> rows = redis.opsForZSet()
                .reverseRangeByScoreWithScores(key(userId), lower, upper, offset, limit);
        if (rows == null) throw new IllegalStateException("Feed 缓存读取失败");
        return rows.stream().map(row -> Long.parseLong(row.getValue())).toList();
    }

    //删除指定博客
    public void remove(long userId, Collection<Long> blogIds) {
        if (!blogIds.isEmpty()) redis.opsForZSet().remove(key(userId), blogIds.stream().map(String::valueOf).toArray());
    }
}
