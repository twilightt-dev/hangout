package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.dianping.cache.CacheClient;
import com.dianping.cache.CacheResult;
import com.dianping.entity.ShopType;
import com.dianping.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopTypeServiceImplTest {

    private FakeCacheClient cacheClient;
    private TestableShopTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        cacheClient = new FakeCacheClient();
        service = new TestableShopTypeServiceImpl();
        service.cacheClient = cacheClient;
    }

    @Test
    void cacheHitReturnsTypesWithoutQueryingDatabase() {
        ShopType food = new ShopType().setId(1L).setName("美食").setSort(1);
        ShopType ktv = new ShopType().setId(2L).setName("KTV").setSort(2);
        cacheClient.nextResult = CacheResult.hit(new ShopType[]{food, ktv});

        Result<List<ShopType>> result = service.queryTypeList();

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).containsExactly(food, ktv);
        assertThat(service.databaseQueryCount).isZero();
    }

    @Test
    void cacheMissQueriesDatabaseAndCachesReturnedTypes() {
        ShopType food = new ShopType().setId(1L).setName("美食").setSort(1);
        ShopType ktv = new ShopType().setId(2L).setName("KTV").setSort(2);
        List<ShopType> databaseTypes = List.of(food, ktv);
        cacheClient.nextResult = CacheResult.miss();
        service.databaseTypes = databaseTypes;

        Result<List<ShopType>> result = service.queryTypeList();

        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getData()).containsExactly(food, ktv);
        assertThat(service.databaseQueryCount).isEqualTo(1);
        assertThat(service.lastQuery.getSqlSegment()).containsIgnoringCase("ORDER BY sort ASC");
        assertThat(cacheClient.writtenKey).isEqualTo("cache:shop-type:list");
        assertThat(cacheClient.writtenValue).isSameAs(databaseTypes);
        assertThat(cacheClient.writtenTtl).isPositive();
    }

    private static final class TestableShopTypeServiceImpl extends ShopTypeServiceImpl {
        private List<ShopType> databaseTypes = List.of();
        private int databaseQueryCount;
        private Wrapper<ShopType> lastQuery;

        @Override
        public List<ShopType> list(Wrapper<ShopType> queryWrapper) {
            databaseQueryCount++;
            lastQuery = queryWrapper;
            return databaseTypes;
        }
    }

    private static final class FakeCacheClient extends CacheClient {
        private CacheResult<?> nextResult = CacheResult.miss();
        private String writtenKey;
        private Object writtenValue;
        private Duration writtenTtl;

        @Override
        @SuppressWarnings("unchecked")
        public <T> CacheResult<T> get(String key, Class<T> type) {
            return (CacheResult<T>) nextResult;
        }

        @Override
        public boolean set(String key, Object value, Duration ttl) {
            writtenKey = key;
            writtenValue = value;
            writtenTtl = ttl;
            return true;
        }
    }
}
