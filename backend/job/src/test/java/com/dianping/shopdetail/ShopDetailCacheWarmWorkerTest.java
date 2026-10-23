package com.dianping.shopdetail;

import com.dianping.shop.shopdetail.service.ShopDetailApplicationService;
import com.dianping.shop.shopdetail.cache.ShopDetailCacheException;
import com.dianping.shopdetail.cache.ShopDetailCacheWarmWorker;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/**
 * 验证商详缓存预热遇到单店业务故障和 Redis 故障时的遍历边界。
 */
class ShopDetailCacheWarmWorkerTest {
    /**
     * 单店聚合故障不会阻止本轮处理后续店铺。
     */
    @Test
    void continuesAfterSingleShopFailure() {
        var application = mock(ShopDetailApplicationService.class);
        when(application.allShopIds()).thenReturn(List.of(1L, 2L, 3L));
        when(application.warmShop(2L)).thenThrow(new IllegalStateException("database unavailable"));
        var worker = new ShopDetailCacheWarmWorker(application);

        worker.warmAll();

        verify(application).warmShop(1L);
        verify(application).warmShop(2L);
        verify(application).warmShop(3L);
    }

    /**
     * Redis 协调故障会提前终止本轮，避免对后续店铺持续施压。
     */
    @Test
    void stopsCurrentRoundAfterRedisFailure() {
        var application = mock(ShopDetailApplicationService.class);
        when(application.allShopIds()).thenReturn(List.of(1L, 2L, 3L));
        when(application.warmShop(2L)).thenThrow(new ShopDetailCacheException("redis unavailable", null));
        var worker = new ShopDetailCacheWarmWorker(application);

        worker.warmAll();

        verify(application).warmShop(1L);
        verify(application).warmShop(2L);
        verify(application, never()).warmShop(3L);
    }
}
