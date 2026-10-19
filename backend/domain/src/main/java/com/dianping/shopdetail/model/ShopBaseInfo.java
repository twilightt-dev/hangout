package com.dianping.shopdetail.model;
/** 店铺自身资料，不包含旧缓存中的评分或评价数量。 */
public record ShopBaseInfo(Long id, String name, Long typeId, String images, String area,
                           String address, Double x, Double y, Long avgPrice, String openHours) {}
