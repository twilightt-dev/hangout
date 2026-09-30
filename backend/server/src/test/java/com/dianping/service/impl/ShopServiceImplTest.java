package com.dianping.service.impl;

import com.dianping.cache.CacheClient;
import com.dianping.cache.CacheResult;
import com.dianping.constant.RedisConstants;
import com.dianping.entity.Shop;
import com.dianping.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShopServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private CacheClient cacheClient;
    private ShopServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);

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
}
