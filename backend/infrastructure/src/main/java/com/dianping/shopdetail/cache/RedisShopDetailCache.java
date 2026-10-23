package com.dianping.shopdetail.cache;

import com.dianping.shop.cache.ShopCacheProtection;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheEntry;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheException;
import com.dianping.shop.shopdetail.model.result.ShopDetailResult;
import com.dianping.shop.shopdetail.cache.ShopDetailCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 公共商详快照读写适配器，负责序列化和逻辑过期时间存储。
 * 发布仍通过共享保护端口原子校验锁所有权，锁的获取由应用协调器负责。
 */
@Component
public class RedisShopDetailCache implements ShopDetailCache<ShopDetailResult> {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ShopCacheProtection protection;

    /**
     * 创建商详缓存适配器。
     *
     * @param redis Redis 字符串模板
     * @param mapper JSON 序列化器
     * @param protection 发布时原子校验同店锁所有权，并清理共享不存在标记
     */
    public RedisShopDetailCache(StringRedisTemplate redis, ObjectMapper mapper, ShopCacheProtection protection) {
        this.redis = redis;
        this.mapper = mapper;
        this.protection = protection;
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
            // 脏数据按未命中处理，由持锁回源覆盖，避免误删并发发布的新快照。
            if (stored == null || stored.data() == null || stored.logicalExpiresAt() == null) {
                return Optional.empty();
            }
            //返回
            return Optional.of(new ShopDetailCacheEntry<>(stored.data(), stored.logicalExpiresAt()));
        } catch (JsonProcessingException malformed) {
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
            protection.publish(shopId, cacheKey(shopId), mapper.writeValueAsString(new StoredSnapshot(value, logicalTtl)), physicalTtl);
        } catch (JsonProcessingException failure) {
            throw unavailable("商详缓存序列化失败", failure);
        } catch (DataAccessException failure) {
            throw unavailable("商详缓存写入失败", failure);
        }
    }

    /** 公共快照的稳定缓存键。 */
    private static String cacheKey(Long shopId) { return "cache:shop-detail:" + shopId; }
    /** 包装缓存访问故障，供应用层回退实时查询。 */
    private static ShopDetailCacheException unavailable(String message, Throwable cause) {
        return new ShopDetailCacheException(message, cause);
    }
    /** 与 Redis value 一起存储的逻辑有效期和公共数据。 */
    public record StoredSnapshot(ShopDetailResult data, Instant logicalExpiresAt) { }
}
