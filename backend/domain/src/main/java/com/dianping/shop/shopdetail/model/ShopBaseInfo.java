package com.dianping.shop.shopdetail.model;
/**
 * 店铺自身资料，不包含旧缓存中的评分或评价数量。
 *
 * @param id 记录标识
 * @param name 名称
 * @param typeId 店铺类型标识
 * @param images 图片路径集合的存储文本
 * @param area 所属商圈
 * @param address 详细地址
 * @param x 经度
 * @param y 纬度
 * @param avgPrice 店铺人均价格
 * @param openHours 营业时间
 */
public record ShopBaseInfo(Long id, String name, Long typeId, String images, String area,
                           String address, Double x, Double y, Long avgPrice, String openHours) {}
