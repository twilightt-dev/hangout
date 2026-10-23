package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import org.springframework.stereotype.Component;

/**
 * 独立读取事实，查询规则与同步聚合路径共用。
 */
@Component
@TaskNode(nodeId="blogs", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.DATA_PROCESSING,
        order=60, taskType=TaskType.IO_BOUND, required=false)
public class QueryShopBlogNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 QueryShopBlogNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public QueryShopBlogNode(ShopDetailDomainService domain) { this.domain = domain; }
    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        context.put("blogs", SectionResult.attempt(() -> domain.blogs(context.getShopId(), 1, ShopDetailDomainService.BLOG_PREVIEW_SIZE, context.getViewerId()),
                page -> page.records().isEmpty()));
    }
}
