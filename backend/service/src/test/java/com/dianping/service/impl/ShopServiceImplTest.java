package com.dianping.service.impl;

import com.dianping.cache.CacheClient;
import com.dianping.shared.cache.ShopCacheProtection;
import java.time.Duration;
import com.dianping.cache.CacheResult;
import com.dianping.constant.RedisConstants;
import com.dianping.entity.Shop;
import com.dianping.mapper.ShopMapper;
import com.dianping.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Distance;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShopServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private GeoOperations<String, String> geoOperations;
    private CacheClient cacheClient;
    private ShopServiceImpl service;
    private ShopCacheProtection protection;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        geoOperations = mock(GeoOperations.class);
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);

        service = spy(new ShopServiceImpl());
        service.stringRedisTemplate = redisTemplate;
        service.objectMapper = new ObjectMapper();
        cacheClient = mock(CacheClient.class);
        service.cacheClient = cacheClient;
        protection = mock(ShopCacheProtection.class);
        service.protection = protection;
        when(protection.mayExist(anyLong())).thenReturn(true);
        when(protection.tryLock(anyLong(), any(Duration.class))).thenAnswer(call -> mock(ShopCacheProtection.Lease.class));
    }

    @Test
    void cachedNotFoundMarkerDoesNotQueryDatabase() {
        Long shopId = 999L;
        when(cacheClient.get(RedisConstants.SHOP_CACHE_KEY + shopId, Shop.class))
                .thenReturn(CacheResult.notFound());

        Result<Shop> result = service.queryById(shopId);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getMsg()).isEqualTo("商户不存在");
        verify(service, never()).getById(shopId);
    }

    /** 过滤器否定时，旧详情入口不再查询数据库。 */
    @Test void bloomNegativeSkipsDatabase() {
        when(cacheClient.get(anyString(), eq(Shop.class))).thenReturn(CacheResult.miss());
        when(protection.mayExist(999L)).thenReturn(false);
        assertThat(service.queryById(999L).getMsg()).isEqualTo("商户不存在");
        verify(service, never()).getById(999L);
    }

    /** 一个入口写入的共享空值标记可直接阻断旧入口回源。 */
    @Test void sharedAbsentMarkerSkipsDatabase() {
        when(cacheClient.get(anyString(), eq(Shop.class))).thenReturn(CacheResult.miss());
        when(protection.isNotFound(999L)).thenReturn(true);
        assertThat(service.queryById(999L).getMsg()).isEqualTo("商户不存在");
        verify(service, never()).getById(999L);
    }

    /** 过滤器写入失败向外抛异常，使外层新增事务能够回滚。 */
    @Test void failedBloomWriteFailsCreationAndReleasesLock() {
        ShopMapper mapper = mock(ShopMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.insert(any(Shop.class))).thenReturn(1);
        var lease = mock(ShopCacheProtection.Lease.class);
        when(protection.tryLock(eq(301L), any())).thenReturn(lease);
        doThrow(new com.dianping.shared.cache.ShopCacheAccessException("Redis 不可用", null))
                .when(protection).add(301L);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.save(new Shop().setId(301L)))
                .isInstanceOf(com.dianping.shared.cache.ShopCacheAccessException.class);
        verify(protection, never()).evict(301L);
        verify(lease).close();
    }

    /** 业务方法返回后仍持锁，事务真正完成后才释放。 */
    @Test void creationKeepsLockUntilTransactionCompletion() {
        ShopMapper mapper = mock(ShopMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.insert(any(Shop.class))).thenReturn(1);
        var lease = mock(ShopCacheProtection.Lease.class);
        when(protection.tryLock(eq(302L), any())).thenReturn(lease);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.save(new Shop().setId(302L))).isTrue();
            verify(lease, never()).close();
            var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            callbacks.forEach(callback -> callback.beforeCommit(false));
            callbacks.forEach(callback -> callback.afterCompletion(0));
            verify(lease).close();
            verify(protection).add(302L);
            verify(protection).evict(302L);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void distanceQueryReturnsNearestShopsWithKilometres() {
        Shop nearest = new Shop().setId(101L).setName("近店");
        Shop farther = new Shop().setId(102L).setName("远店");
        doReturn(List.of(farther, nearest)).when(service).listByIds(anyCollection());
        when(geoOperations.search(eq("shop:geo:1"), any(), any(org.springframework.data.redis.domain.geo.GeoShape.class), any()))
                .thenReturn(new GeoResults<>(List.of(
                        new GeoResult<>(new RedisGeoCommands.GeoLocation<>("101", new Point(120.155, 30.274)),
                                new Distance(0.456, Metrics.KILOMETERS)),
                        new GeoResult<>(new RedisGeoCommands.GeoLocation<>("102", new Point(120.16, 30.28)),
                                new Distance(1.234, Metrics.KILOMETERS))
                )));

        Result<List<Shop>> result = service.queryByType(1, 1, "distance", 120.155, 30.274);

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).extracting(Shop::getId).containsExactly(101L, 102L);
        assertThat(result.getData()).extracting(Shop::getDistance).containsExactly(0.46, 1.23);
        verify(geoOperations).search(eq("shop:geo:1"), any(),
                any(org.springframework.data.redis.domain.geo.GeoShape.class), any());
    }

    @Test
    void distanceQueryRejectsMissingOrInvalidCoordinates() {
        Result<List<Shop>> missing = service.queryByType(1, 1, "distance", null, 30.274);
        Result<List<Shop>> invalid = service.queryByType(1, 1, "distance", 181D, 30.274);

        assertThat(missing.getCode()).isZero();
        assertThat(invalid.getCode()).isZero();
        verifyNoInteractions(geoOperations);
    }

    @Test
    void updatingShopLocationAndTypeMovesItsGeoMember() {
        Shop before = new Shop().setId(101L).setTypeId(1L).setX(120.1).setY(30.1);
        Shop after = new Shop().setId(101L).setTypeId(2L).setX(120.2).setY(30.2);
        doReturn(before, after).when(service).getById(101L);
        doReturn(true).when(service).updateById(any(Shop.class));

        Result<Void> result = service.update(new Shop().setId(101L).setTypeId(2L));

        assertThat(result.getCode()).isEqualTo(1);
        verify(geoOperations).remove("shop:geo:1", "101");
        verify(geoOperations).add("shop:geo:2", new Point(120.2, 30.2), "101");
    }

    @Test
    void savingValidShopAddsGeoMemberAndInvalidShopIsSkipped() {
        ShopMapper mapper = mock(ShopMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        Shop valid = new Shop().setId(201L).setTypeId(3L).setX(120.2).setY(30.2);
        Shop invalid = new Shop().setId(202L).setTypeId(3L).setX(181D).setY(30.2);
        when(mapper.insert(any(Shop.class))).thenReturn(1);

        assertThat(service.save(valid)).isTrue();
        assertThat(service.save(invalid)).isTrue();

        verify(geoOperations).add("shop:geo:3", new Point(120.2, 30.2), "201");
        verify(geoOperations, never()).add("shop:geo:3", new Point(181D, 30.2), "202");
    }

    @Test
    void removingShopRemovesItsGeoMember() {
        ShopMapper mapper = mock(ShopMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        Shop existing = new Shop().setId(203L).setTypeId(4L).setX(120.2).setY(30.2);
        when(mapper.selectById(203L)).thenReturn(existing);
        when(mapper.deleteById(203L)).thenReturn(1);

        assertThat(service.removeById(203L)).isTrue();

        verify(geoOperations).remove("shop:geo:4", "203");
    }
}
