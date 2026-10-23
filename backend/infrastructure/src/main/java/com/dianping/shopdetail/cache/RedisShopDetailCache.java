package com.dianping.shopdetail.cache;

import com.dianping.shared.cache.ShopCacheProtection;
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
 * Redis 公共商详快照与 Redisson 同店锁适配器。
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
     * @param protection 两入口共享的布隆过滤器、空值标记和同店协调器
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

    /** 两入口共享同一份已完整加载的布隆过滤器。 */
    @Override public boolean mayExist(Long shopId) { return protection.mayExist(shopId); }
    /** 读取共享空值标记，读取故障按未命中处理。 */
    @Override public boolean isNotFound(Long shopId) { return protection.isNotFound(shopId); }
    /** 明确查无店铺后，在同店锁内写入共享空值标记。 */
    @Override public void markNotFound(Long shopId) { protection.markNotFound(shopId); }
    /** 冷启动和后台刷新均尝试立即获取，等待由协调器控制。 */
    @Override public ShopCacheProtection.Lease tryLock(Long shopId) {
        return protection.tryLock(shopId, Duration.ZERO);
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
