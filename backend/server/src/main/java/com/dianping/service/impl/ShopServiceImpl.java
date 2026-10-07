package com.dianping.service.impl;

import com.dianping.cache.CacheClient;
import com.dianping.cache.CacheResult;
import com.dianping.cache.CacheState;
import com.dianping.constant.RedisConstants;
import com.dianping.entity.Shop;
import com.dianping.mapper.ShopMapper;
import com.dianping.result.Result;
import com.dianping.service.ShopService;
import com.dianping.service.ShopGeoValidator;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    CacheClient cacheClient ;



    /**
     * 根据id查询商铺
     * 缓存穿透：空值标记
     * 缓存雪崩：ttl抖动
     * 缓存击穿：互斥锁
     */
    @Override
    public Result<Shop> queryById(Long id) {
        if (id == null || id <= 0) {
            return Result.error("商户ID非法");
        }
        //构造缓存key和锁key
        String cacheKey =
                RedisConstants.SHOP_CACHE_KEY + id;

        String lockKey =
                RedisConstants.LOCK_SHOP_KEY + id;

        // 第一次查缓存
        CacheResult<Shop> cached =
                cacheClient.get(cacheKey, Shop.class);
        //命中了就直接返回
        if (cached.state() == CacheState.HIT) {
            return Result.success(cached.value());
        }

        if (cached.state() == CacheState.NOT_FOUND) {
            return Result.error("商户不存在");
        }
        //未命中就开始等待并重试，这里设置最大重试次数为5
        for (int attempt = 0; attempt < 5; attempt++) {
            String token = tryLock(lockKey);

            if (token == null) {
                if (!sleepBeforeRetry(attempt)) {
                    return Result.error("请求被中断");
                }

                // 等待期间其他线程可能已经写好缓存
                CacheResult<Shop> retryCache =
                        cacheClient.get(cacheKey, Shop.class);

                if (retryCache.state() == CacheState.HIT) {
                    return Result.success(retryCache.value());
                }

                if (retryCache.state() == CacheState.NOT_FOUND) {
                    return Result.error("商户不存在");
                }

                continue;
            }

            try {
                // 获得锁后的 double-check
                CacheResult<Shop> secondCache =
                        cacheClient.get(cacheKey, Shop.class);

                if (secondCache.state() == CacheState.HIT) {
                    return Result.success(secondCache.value());
                }

                if (secondCache.state() == CacheState.NOT_FOUND) {
                    return Result.error("商户不存在");
                }

                Shop shop = getById(id);

                if (shop == null) {
                    cacheClient.setNotFound(
                            cacheKey,
                            RedisConstants.CACHE_NULL_TTL
                    );
                    return Result.error("商户不存在");
                }

                cacheClient.set(
                        cacheKey,
                        shop,
                        RedisConstants.SHOP_CACHE_TTL
                );

                return Result.success(shop);
            } finally {
                unlock(lockKey, token);
            }
        }

        //直接返回降级结果
        return Result.error("系统繁忙，请稍后重试");
    }


    /**
     * 修改商户
     * @param shop
     * @return
     */
    @Transactional
    public Result<Void> update(Shop shop){
        Long id = shop.getId();
        if(id == null){
            return Result.error("店铺id不能为空");
        }

        Shop before = getById(id);
        //为了保持数据库与缓存一致性，先操作数据库再删除缓存
        if (!updateById(shop)) {
            return Result.error("店铺不存在");
        }
        stringRedisTemplate.delete(RedisConstants.SHOP_CACHE_KEY + id);
        syncGeo(before, getById(id));
        return Result.success() ;
    }

    @Override
    @Transactional
    public boolean save(Shop shop) {
        boolean saved = super.save(shop);
        if (saved) {
            syncGeo(null, shop);
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        Shop existing = getById(id);
        boolean removed = super.removeById(id);
        if (removed) {
            syncGeo(existing, null);
        }
        return removed;
    }

    @Override
    public Result<List<Shop>> queryByType(Integer typeId, Integer current, String sort,
                                           Double longitude, Double latitude) {
        if (typeId == null || typeId <= 0) {
            return Result.error("商铺类型非法");
        }
        if (current == null || current <= 0) {
            return Result.error("页码非法");
        }

        if ("distance".equals(sort)) {
            return queryByDistance(typeId, current, longitude, latitude);
        }
        if (!"comments".equals(sort) && !"score".equals(sort)) {
            return Result.error("排序方式非法");
        }

        Page<Shop> page = query()
                .eq("type_id", typeId)
                .orderByDesc(sort)
                .page(new Page<>(current, com.dianping.constant.SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.success(page.getRecords());
    }

    private Result<List<Shop>> queryByDistance(Integer typeId, Integer current,
                                                Double longitude, Double latitude) {
        if (!ShopGeoValidator.isValidCoordinate(longitude, latitude)) {
            return Result.error("距离排序需要有效的经纬度");
        }
        if (current > 1) {
            return Result.success(List.of());
        }

        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
        GeoReference<String> reference = GeoReference.fromCoordinate(longitude, latitude);
        GeoShape shape = GeoShape.byRadius(
                new Distance(RedisConstants.SHOP_GEO_RADIUS_KM, Metrics.KILOMETERS));
        RedisGeoCommands.GeoSearchCommandArgs args =
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(RedisConstants.SHOP_GEO_MAX_RESULTS);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geo.search(
                RedisConstants.SHOP_GEO_KEY + typeId, reference, shape, args);
        if (results == null || results.getContent().isEmpty()) {
            return Result.success(List.of());
        }

        List<Long> ids = results.getContent().stream()
                .map(GeoResult::getContent)
                .map(RedisGeoCommands.GeoLocation::getName)
                .map(Long::valueOf)
                .toList();
        Map<Long, Shop> shopsById = new HashMap<>();
        listByIds(ids).forEach(shop -> shopsById.put(shop.getId(), shop));

        List<Shop> shops = results.getContent().stream()
                .map(result -> {
                    Long id = Long.valueOf(result.getContent().getName());
                    Shop shop = shopsById.get(id);
                    if (shop != null && result.getDistance() != null) {
                        shop.setDistance(roundKilometres(result.getDistance().getValue()));
                    }
                    return shop;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return Result.success(shops);
    }

    private static double roundKilometres(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private void syncGeo(Shop before, Shop after) {
        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
        removeFromGeo(geo, before);
        addToGeo(geo, after);
    }

    private void removeFromGeo(GeoOperations<String, String> geo, Shop shop) {
        if (shop != null && shop.getId() != null && shop.getTypeId() != null) {
            geo.remove(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(), shop.getId().toString());
        }
    }

    private void addToGeo(GeoOperations<String, String> geo, Shop shop) {
        if (shop != null && shop.getId() != null && shop.getTypeId() != null
                && ShopGeoValidator.isValidCoordinate(shop.getX(), shop.getY())) {
            geo.add(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(),
                    new Point(shop.getX(), shop.getY()), shop.getId().toString());
        }
    }

    


    //尝试获取锁
    //TODO 后续补充LUA脚本或者成熟的分布式锁实现比如Redisson
    private String tryLock(String lockKey){
        //给锁加一个唯一的标识防止误删
        String token = UUID.randomUUID().toString() ;

        try{
            boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey , token ,
                    Duration.ofSeconds(RedisConstants.LOCK_SHOP_TTL));
            return Boolean.TRUE.equals(acquired) ? token : null ;
        }catch(DataAccessException e){
            log.warn("获取分布式锁失败,lockKey = {}" , lockKey , e) ;
            return null ;
        }
    }
    //释放锁
    //Lua脚本执行原子删除
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class
            );
    private boolean unlock(String lockKey, String token) {
        if (token == null) {
            return false;
        }

        try {
            Long result = stringRedisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey),
                    token
            );

            return Long.valueOf(1L).equals(result);
        } catch (DataAccessException e) {
            log.warn(
                    "释放分布式锁失败，lockKey={}",
                    lockKey,
                    e
            );
            return false;
        }
    }

    //没抢到锁时线程休眠，加入指数退避和随机抖动
    private boolean sleepBeforeRetry(int attempt) {
        try {
            // 20、40、80、160、320 毫秒附近随机等待
            long maxDelay = Math.min(
                    300L,
                    20L * (1L << attempt)
            );

            long minDelay = Math.max(10L, maxDelay / 2);

            long delay = ThreadLocalRandom.current()
                    .nextLong(minDelay, maxDelay + 1);

            Thread.sleep(delay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("缓存重建等待被中断");
            return false;
        }
    }
}
