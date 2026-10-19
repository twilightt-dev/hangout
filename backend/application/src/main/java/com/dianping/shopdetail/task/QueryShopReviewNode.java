package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shopdetail.model.ShopDetailAggregate;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.service.ShopDetailQueryService;
import org.springframework.stereotype.Component;
/** 独立取得reviews事实，不依赖同阶段其他节点的输出。 */
@Component
@TaskNode(nodeId="reviews", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=50, taskType=TaskType.IO_BOUND, required=false)
public class QueryShopReviewNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    private final ShopDetailQueryService queries;
    public QueryShopReviewNode(ShopDetailQueryService queries) { this.queries = queries; }
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.put("reviews", ShopDetailQueryService.attempt(() -> queries.reviews(new ShopDetailPageRequest(context.getShopId(), 1, 3)), page -> page.records().isEmpty()));
        context.put("statistics", ShopDetailQueryService.attempt(() -> queries.statistics(context.getShopId()), data -> data.totalReviews() == 0));
    }
}
