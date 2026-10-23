package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import org.springframework.stereotype.Component;

/**
 * 汇集已完成的业务数据，执行状态保留在上下文中。
 */
@Component
@TaskNode(nodeId="assemble", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.POST_PROCESSING,
        order=10, taskType=TaskType.CPU_BOUND, required=true)
public class AssembleShopDetailNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        if (context.getBase() == null) throw new IllegalStateException("基础查询未生成店铺资料");
        context.setOutput(ShopDetailAggregate.builder().shop(context.getBase())
                .vouchers(context.data("vouchers")).setmeals(context.data("setmeals"))
                .recommendations(context.data("recommendations"))
                .recommendedIds(context.data("recommendationState"))
                .reviews(context.data("reviews")).reviewSummary(context.data("statistics"))
                .blogs(context.data("blogs")).build());
    }
}
