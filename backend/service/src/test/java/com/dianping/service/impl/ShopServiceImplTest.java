package com.dianping.service.impl;

import com.dianping.cache.CacheClient;
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
