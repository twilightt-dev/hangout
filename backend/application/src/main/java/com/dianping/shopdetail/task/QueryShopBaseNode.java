package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shopdetail.model.ShopDetailAggregate;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.service.ShopDetailQueryService;
import org.springframework.stereotype.Component;
/** 独立取得base事实，不依赖同阶段其他节点的输出。 */
@Component
@TaskNode(nodeId="base", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=10, taskType=TaskType.IO_BOUND, required=true)
public class QueryShopBaseNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    private final ShopDetailQueryService queries;
    public QueryShopBaseNode(ShopDetailQueryService queries) { this.queries = queries; }
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.setBase(queries.base(context.getShopId()));
    }
}
