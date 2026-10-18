package com.dianping.shopdetail.service;


import com.dianping.shopdetail.model.ShopDetailAggregate;

/**
 * 店铺详情领域服务
 * <p>
 * 封装跨实体的核心业务逻辑，包括：
 * <ul>
 *   <li>聚合查询：组合多个仓储查询，构建完整的 {@link ShopDetailAggregate}</li>
 *   <li>数据丰富：合并决策标签、构建推荐卡片、周边店铺和扩展信息</li>
 *   <li>申诉管理：创建、更新、查询 {@link GuaranteeClaim}</li>
 * </ul>
 * 注意：该服务不感知任务链引擎，并行编排由应用层负责。
 * 查询节点只搬运原始事实，真正的标签合并、推荐卡片生成都在这里完成。
 * </p>
 */
public class ShopDetailDomainService {
}
