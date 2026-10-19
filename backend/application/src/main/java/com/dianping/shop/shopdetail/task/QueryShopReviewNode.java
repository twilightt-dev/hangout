package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Component;

/**
 * 并行查询首屏评价与全店评分统计，并分别记录读取状态。
 */
@Component
@TaskNode(nodeId="reviews", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=50, taskType=TaskType.IO_BOUND, required=false)
public class QueryShopReviewNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 QueryShopReviewNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public QueryShopReviewNode(ShopDetailDomainService domain) { this.domain = domain; }
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.put("reviews", SectionResult.attempt(() -> domain.reviews(context.getShopId(), 1, ShopDetailDomainService.REVIEW_PREVIEW_SIZE),
                page -> page.records().isEmpty()));
        context.put("statistics", SectionResult.attempt(() -> domain.statistics(context.getShopId()),
                summary -> summary.totalReviews() == 0));
    }
}
