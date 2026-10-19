package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Component;

/**
 * 独立读取事实，查询规则与同步聚合路径共用。
 */
@Component
@TaskNode(nodeId="setmeals", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=30, taskType=TaskType.IO_BOUND, required=false)
public class QueryShopSetmealNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 QueryShopSetmealNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public QueryShopSetmealNode(ShopDetailDomainService domain) { this.domain = domain; }
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.put("setmeals", SectionResult.attempt(() -> domain.setmeals(context.getShopId(), 1, ShopDetailDomainService.SETMEAL_PREVIEW_SIZE),
                page -> page.records().isEmpty()));
    }
}
