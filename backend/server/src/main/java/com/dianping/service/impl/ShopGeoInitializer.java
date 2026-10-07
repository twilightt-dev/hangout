package com.dianping.service.impl;

import com.dianping.constant.RedisConstants;
import com.dianping.entity.Shop;
import com.dianping.service.ShopService;
import com.dianping.service.ShopGeoValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * One-off, opt-in backfill for existing shop locations.
 * Enable with {@code shop.geo.init=true}; the marker makes subsequent runs idempotent.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shop.geo.init", havingValue = "true")
public class ShopGeoInitializer implements org.springframework.boot.CommandLineRunner {

    private final ShopService shopService;
    private final StringRedisTemplate redisTemplate;

    public ShopGeoInitializer(ShopService shopService, StringRedisTemplate redisTemplate) {
        this.shopService = shopService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisConstants.SHOP_GEO_INIT_KEY))) {
            return;
        }

        try {
            GeoOperations<String, String> geo = redisTemplate.opsForGeo();
            for (Shop shop : shopService.list()) {
                if (shop.getId() != null && shop.getTypeId() != null
                        && ShopGeoValidator.isValidCoordinate(shop.getX(), shop.getY())) {
                    geo.add(RedisConstants.SHOP_GEO_KEY + shop.getTypeId(),
                            new Point(shop.getX(), shop.getY()), shop.getId().toString());
                }
            }
            redisTemplate.opsForValue().set(RedisConstants.SHOP_GEO_INIT_KEY, "1");
        } catch (DataAccessException e) {
            log.warn("初始化店铺 GEO 索引失败", e);
        }
    }
}
