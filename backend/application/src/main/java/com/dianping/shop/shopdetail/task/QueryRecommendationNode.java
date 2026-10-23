package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Component;

/**
 * 并行查询推荐菜预览及当前用户的去重推荐状态。
 */
@Component
@TaskNode(nodeId="recommendations", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=40, taskType=TaskType.IO_BOUND, required=false)
public class QueryRecommendationNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 QueryRecommendationNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public QueryRecommendationNode(ShopDetailDomainService domain) { this.domain = domain; }
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        var page = SectionResult.attempt(() -> domain.recommendations(context.getShopId(), 1, ShopDetailDomainService.DISH_PREVIEW_SIZE),
                result -> result.records().isEmpty());
        context.put("recommendations", page);
        if (page.available()) context.put("recommendationState", SectionResult.attempt(
                () -> domain.recommendedIds(context.getViewerId(), page.data()), java.util.Set::isEmpty));
    }
}
