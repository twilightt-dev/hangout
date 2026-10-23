package com.dianping.shopdetail.cache;

import com.dianping.constant.RedisConstants;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheEntry;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheException;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.cache.ShopDetailCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 公共商详快照与 token 所有权重建锁适配器。
 */
@Component
public class RedisShopDetailCache implements ShopDetailCache<ShopDetailResult> {
    //Lua脚本，把比锁和删锁合并成一个原子操作
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    /**
     * 创建商详缓存适配器。
     *
     * @param redis Redis 字符串模板
     * @param mapper JSON 序列化器
     */
    public RedisShopDetailCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * 读取尚未物理过期的缓存
     * @param shopId 店铺标识
     * @return 快照及逻辑过期时间；无快照时为空
     */
    @Override public Optional<ShopDetailCacheEntry<ShopDetailResult>> get(Long shopId) {
        String key = cacheKey(shopId);
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) return Optional.empty();
            //把redis里存的json字符串反序列化为对象
            var stored = mapper.readValue(value, StoredSnapshot.class);
            //非法的直接删掉
            if (stored == null || stored.data() == null || stored.logicalExpiresAt() == null) {
                redis.delete(key);
                return Optional.empty();
            }
            //返回
            return Optional.of(new ShopDetailCacheEntry<>(stored.data(), stored.logicalExpiresAt()));
        } catch (JsonProcessingException malformed) {
            try {
                redis.delete(key);
            } catch (DataAccessException failure) {
                throw unavailable("商详缓存脏数据清理失败", failure);
            }
            return Optional.empty();
        } catch (DataAccessException failure) {
            throw unavailable("商详缓存读取失败", failure);
        }
    }

    /**
     * 写入缓存
     * @param shopId 店铺标识
     * @param value 公共快照
     * @param logicalTtl 逻辑过期时间
     * @param physicalTtl Redis 物理 TTL
     */
    @Override public void put(Long shopId, ShopDetailResult value, Instant logicalTtl, Duration physicalTtl) {
        try {
            redis.opsForValue().set(cacheKey(shopId), mapper.writeValueAsString(new StoredSnapshot(value, logicalTtl)), physicalTtl);
        } catch (JsonProcessingException failure) {
            throw unavailable("商详缓存序列化失败", failure);
        } catch (DataAccessException failure) {
            throw unavailable("商详缓存写入失败", failure);
        }
    }

    /**
     * 原子取得当前店铺的重建锁。
     * @param shopId 店铺标识
     * @param token 锁所有者标记
     * @param lease 锁租约
     * @return 是否成功取得锁
     */
    @Override
    public boolean tryLock(Long shopId, String token, Duration lease) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey(shopId), token, lease));
        } catch (DataAccessException failure) {
            throw unavailable("商详重建锁不可用", failure);
        }
    }

    /**
     * 仅释放仍属于指定 token 的重建锁。
     *
     * @param shopId 店铺标识
     * @param token 锁所有者标记
     */
    @Override public void unlock(Long shopId, String token) {
        try {
            redis.execute(UNLOCK, List.of(lockKey(shopId)), token);
        } catch (DataAccessException failure) {
            throw unavailable("商详重建锁释放失败", failure);
        }
    }

    //缓存key cache:shop-detail:id
    private static String cacheKey(Long shopId) { return RedisConstants.SHOP_DETAIL_CACHE_KEY + shopId; }
    //锁key lock:shop-detail:id
    private static String lockKey(Long shopId) { return RedisConstants.SHOP_DETAIL_CACHE_LOCK_KEY + shopId; }

    //缓存不可用的异常
    private static ShopDetailCacheException unavailable(String message, Throwable cause) {
        return new ShopDetailCacheException(message, cause);
    }

    /**
     * Redis里存的value
     * @param data 公共商详快照
     * @param logicalExpiresAt 逻辑过期时间
     */
    private record StoredSnapshot(ShopDetailResult data, Instant logicalExpiresAt) { }
}
