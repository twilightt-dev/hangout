package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shopdetail.model.ShopDetailAggregate;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.service.ShopDetailQueryService;
import org.springframework.stereotype.Component;
/** 独立取得recommendations事实，不依赖同阶段其他节点的输出。 */
@Component
@TaskNode(nodeId="recommendations", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=40, taskType=TaskType.IO_BOUND, required=false)
public class QueryRecommendationNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    private final ShopDetailQueryService queries;
    public QueryRecommendationNode(ShopDetailQueryService queries) { this.queries = queries; }
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        var result = ShopDetailQueryService.attempt(() -> queries.recommendations(new ShopDetailPageRequest(context.getShopId(), 1, 6)), page -> page.records().isEmpty());
        context.put("recommendations", result);
        if (result.available()) {
            var personal = ShopDetailQueryService.attempt(() -> queries.recommended(context.getViewerId(), result.data()), java.util.Set::isEmpty);
            context.put("recommendationState", personal);
            if (personal.available()) context.setAttribute("recommendedIds", personal.data());
        }
    }
}
