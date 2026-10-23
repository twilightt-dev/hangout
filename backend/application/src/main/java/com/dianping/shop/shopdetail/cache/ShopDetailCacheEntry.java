package com.dianping.shop.shopdetail.cache;

import java.time.Instant;

/**
 * 公共商详快照及其逻辑过期时间。
 *
 * @param <T> 快照类型
 * @param value 公共快照
 * @param logicalTtl 逻辑过期时间
 */
public record ShopDetailCacheEntry<T>(T value, Instant logicalTtl) { }
