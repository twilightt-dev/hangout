package com.dianping.shopdetail.cache;

import com.dianping.shop.shopdetail.cache.ShopDetailCacheException;import com.dianping.shop.shopdetail.service.ShopDetailApplicationService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每 30 分钟遍历全部店铺并主动重建公共商详快照。
 */
@Component
public class ShopDetailCacheWarmWorker {

    private static final Log log = LogFactory.getLog(ShopDetailCacheWarmWorker.class);
    private final ShopDetailApplicationService shopDetails;

    /**
     * 创建商详缓存预热 Worker。
     * @param shopDetails 商详聚合与缓存重建用例
     */
    public ShopDetailCacheWarmWorker(ShopDetailApplicationService shopDetails) {
        this.shopDetails = shopDetails;
    }


    /**
     * 启动 60 秒后首轮预热，此后每轮结束 30 分钟再执行。
     */
    @Scheduled(fixedDelayString = "${shop-detail.cache-warm-interval-ms:1800000}",
            initialDelayString = "${shop-detail.cache-warm-initial-delay-ms:60000}")
    public void warmAll() {
        for (Long shopId : shopDetails.allShopIds()) {
            try {
                shopDetails.warmShop(shopId);
            } catch (ShopDetailCacheException cacheFailure) {
                log.error("商详缓存预热因 Redis 故障提前结束，shopId=" + shopId, cacheFailure);
                return;
            } catch (RuntimeException failure) {
                log.warn("单店商详缓存预热失败，继续处理后续店铺，shopId=" + shopId, failure);
            }
        }
    }
}
