package com.dianping.service.impl;

import com.dianping.cache.CacheClient;
import com.dianping.shop.cache.ShopCacheProtection;
import com.dianping.shop.cache.ShopCacheAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.dianping.cache.CacheResult;
import com.dianping.cache.CacheState;
import com.dianping.constant.RedisConstants;
import com.dianping.constant.SystemConstants;
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
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    @Autowired
    ShopCacheProtection protection;
    @Value("${shop-cache.lock-wait-ms:2000}")
    long cacheWaitMillis = 2000;
    @Value("${shop-cache.lock-retry-ms:50}")
    long cacheRetryMillis = 50;



    /**
     * 根据id查询商铺
     * 缓存穿透：共享布隆过滤器与空值标记，未就绪或故障时放行
     * 缓存雪崩：ttl抖动
     * 缓存击穿：Redisson 同店锁，缓存发布原子校验所有权
     */
    @Override
    public Result<Shop> queryById(Long id) {
        if (id == null || id <= 0) {
            return Result.error("商户ID非法");
        }
        String cacheKey = RedisConstants.SHOP_CACHE_KEY + id;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cacheWaitMillis);
        while (true) {
            CacheResult<Shop> cached = cacheClient.get(cacheKey, Shop.class);
            if (cached.state() == CacheState.HIT) return Result.success(cached.value());
            if (cached.state() == CacheState.NOT_FOUND || protection.isNotFound(id)
                    || !protection.mayExist(id)) return Result.error("商户不存在");
            ShopCacheProtection.Lease lease;
            try { lease = protection.tryLock(id, Duration.ZERO); }
            catch (ShopCacheAccessException unavailable) {
                // 无法可靠持锁时仅实时读取，不发布可能与新增或删除冲突的缓存。
                Shop live = getById(id);
                return live == null ? Result.error("商户不存在") : Result.success(live);
            }
            if (lease != null) {
                try (lease) {
                    CacheResult<Shop> second = cacheClient.get(cacheKey, Shop.class);
                    if (second.state() == CacheState.HIT) return Result.success(second.value());
                    if (second.state() == CacheState.NOT_FOUND || protection.isNotFound(id))
                        return Result.error("商户不存在");
                    Shop shop = getById(id);
                    try {
                        if (shop == null) protection.markNotFound(id);
                        else protection.publish(id, cacheKey, objectMapper.writeValueAsString(shop),
                                RedisConstants.SHOP_CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(-180, 181)));
                    } catch (ShopCacheAccessException | com.fasterxml.jackson.core.JsonProcessingException failure) {
                        log.warn("店铺缓存写入失败，保留实时结果，shopId={}", id, failure);
                    }
                    return shop == null ? Result.error("商户不存在") : Result.success(shop);
                }
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return Result.error("系统繁忙，请稍后重试");
            try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(Math.max(1, cacheRetryMillis)))); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Result.error("请求被中断");
            }
        }
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
        if (before != null) {
            // 评分和评价数由店铺评价用例维护，普通店铺修改不能接收客户端覆盖。
            shop.setScore(before.getScore());
            shop.setComments(before.getComments());
        }
        //为了保持数据库与缓存一致性，先操作数据库再删除缓存
        if (!updateById(shop)) {
            return Result.error("店铺不存在");
        }
        stringRedisTemplate.delete(RedisConstants.SHOP_CACHE_KEY + id);
        syncGeo(before, getById(id));
        return Result.success() ;
    }

    /** 新增店铺，在事务提交前追加过滤器并清理两个入口的空值标记。 */
    @Override
    @Transactional
    public boolean save(Shop shop) {
        shop.setScore(0);
        shop.setComments(0);
        if (!super.save(shop)) return false;
        ShopCacheProtection.Lease lease = requireWriteLock(shop.getId());
        boolean deferred = releaseAfterTransaction(lease);
        try {
            protection.add(shop.getId());
            protection.evict(shop.getId());
            syncGeo(null, shop);
            lease.verifyOwnership();
            return true;
        } finally {
            if (!deferred) lease.close();
        }
    }

    /** 删除与回源共用同店锁，避免旧查询在清理后重新写回缓存。 */
    @Override
    @Transactional
    public boolean removeById(Serializable id) {
        Long shopId = Long.valueOf(id.toString());
        ShopCacheProtection.Lease lease = requireWriteLock(shopId);
        boolean deferred = releaseAfterTransaction(lease);
        try {
            Shop existing = getById(id);
            boolean removed = super.removeById(id);
            if (removed) {
                protection.evict(shopId);
                syncGeo(existing, null);
            }
            lease.verifyOwnership();
            return removed;
        } finally {
            if (!deferred) lease.close();
        }
    }

    /** 写操作等待预算耗尽或 Redis 故障时抛异常，使数据库事务回滚。 */
    private ShopCacheProtection.Lease requireWriteLock(Long shopId) {
        var lease = protection.tryLock(shopId, Duration.ofMillis(Math.max(0, cacheWaitMillis)));
        if (lease == null) throw new ShopCacheAccessException("店铺正在更新，请稍后重试", null);
        return lease;
    }

    /** 事务提交前复核所有权，实际提交或回滚完成后才由持锁线程释放。 */
    private boolean releaseAfterTransaction(ShopCacheProtection.Lease lease) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 在数据库提交前拒绝已失去锁的写事务。 */
            @Override public void beforeCommit(boolean readOnly) { lease.verifyOwnership(); }
            /** 数据库事务结束后释放，不在业务方法返回时提前释放。 */
            @Override public void afterCompletion(int status) { lease.close(); }
        });
        return true;
    }

    /**
     * 根据种类查询店铺
     * @param typeId 店铺种类id
     * @param current 当前页码
     * @param sort 排序方式
     * @param longitude 经度
     * @param latitude 纬度
     * @return 店铺列表
     */
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
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.success(page.getRecords());
    }

    /**
     * 根据距离查询店铺
     * @param typeId 店铺种类id
     * @param current 页码
     * @param longitude 经度
     * @param latitude 纬度
     * @return 店铺列表
     */
    private Result<List<Shop>> queryByDistance(Integer typeId, Integer current,
                                                Double longitude, Double latitude) {
        if (!ShopGeoValidator.isValidCoordinate(longitude, latitude)) {
            return Result.error("距离排序需要有效的经纬度");
        }
        //目前距离查询只返回一页，最多50家，页码＞1返回空
        if (current > 1) {
            return Result.success(List.of());
        }
        //GEO操作对象，第一个表示key，第二个表示member
        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
        //表示从哪个位置开始计算距离
        GeoReference<String> reference = GeoReference.fromCoordinate(longitude, latitude);
        //定义范围
        GeoShape shape = GeoShape.byRadius(
                new Distance(RedisConstants.SHOP_GEO_RADIUS_KM, Metrics.KILOMETERS));
        //设置GEOSEARCH命令的距离、排序、数量
        RedisGeoCommands.GeoSearchCommandArgs args =
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(RedisConstants.SHOP_GEO_MAX_RESULTS);
        //GeoResults：整批查询结果
        //  └─ GeoResult：某一个成员的查询结果
        //       ├─ content：GeoLocation，包含成员名称
        //       │    └─ name："101"，也就是店铺 ID
        //       └─ distance：0.456 km
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = geo.search(
                RedisConstants.SHOP_GEO_KEY + typeId, reference, shape, args);
        if (results == null || results.getContent().isEmpty()) {
            return Result.success(List.of());
        }
        //获取店铺id，批量查询店铺详情，存成一个方便查询的哈希表
        List<Long> ids = results.getContent().stream()
                .map(GeoResult::getContent)
                .map(RedisGeoCommands.GeoLocation::getName)
                .map(Long::valueOf)
                .toList();
        Map<Long, Shop> shopsById = new HashMap<>();
        listByIds(ids).forEach(shop -> shopsById.put(shop.getId(), shop));

        //遍历redis，因为前面的查询没有维护距离顺序
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

    /**
     * 同步修改GEO（比如修改店铺数据时）
     * @param before 改之前
     * @param after 改之后
     */
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

    


}
