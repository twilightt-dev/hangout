package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Component;

/**
 * 查询必须存在的店铺基本资料，失败时阻断商详链路。
 */
@Component
@TaskNode(nodeId="base", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=10, taskType=TaskType.IO_BOUND, required=true)
public class QueryShopBaseNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 QueryShopBaseNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public QueryShopBaseNode(ShopDetailDomainService domain) { this.domain = domain; }
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.setBase(domain.base(context.getShopId()));
    }
}
