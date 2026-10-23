package com.dianping.shop.shopdetail.model;
/**
 * 近期样本内明确提及该标签的不同作者人数。
 *
 * @param code 标签代码
 * @param name 名称
 * @param userCount 去重后的提及用户数
 */
public record ShopDecisionTag(String code, String name, long userCount) {}
