package com.dianping.service.impl;

import com.dianping.constant.RedisConstants;
import com.dianping.entity.Shop;
import com.dianping.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShopGeoInitializerTest {

    @Test
    void backfillsOnlyValidCoordinatesAndMarksInitialization() {
        ShopService shops = mock(ShopService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GeoOperations<String, String> geo = mock(GeoOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.hasKey(RedisConstants.SHOP_GEO_INIT_KEY)).thenReturn(false);
        when(redis.opsForGeo()).thenReturn(geo);
        when(redis.opsForValue()).thenReturn(values);
        when(shops.list()).thenReturn(List.of(
                new Shop().setId(1L).setTypeId(2L).setX(120.1).setY(30.2),
                new Shop().setId(2L).setTypeId(2L).setX(181D).setY(30.2),
                new Shop().setId(3L).setTypeId(2L).setX(null).setY(30.2)));

        new ShopGeoInitializer(shops, redis).run();

        verify(geo).add(eq("shop:geo:2"), eq(new Point(120.1, 30.2)), eq("1"));
        verify(geo, never()).add(eq("shop:geo:2"), any(Point.class), eq("2"));
        verify(geo, never()).add(eq("shop:geo:2"), any(Point.class), eq("3"));
        verify(values).set(RedisConstants.SHOP_GEO_INIT_KEY, "1");
    }

    @Test
    void initializedMarkerMakesBackfillIdempotent() {
        ShopService shops = mock(ShopService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(RedisConstants.SHOP_GEO_INIT_KEY)).thenReturn(true);

        new ShopGeoInitializer(shops, redis).run();

        verifyNoInteractions(shops);
        verify(redis, never()).opsForGeo();
    }
}
