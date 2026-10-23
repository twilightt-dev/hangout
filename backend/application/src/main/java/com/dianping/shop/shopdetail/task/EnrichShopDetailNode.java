package com.dianping.shop.shopdetail.task;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shop.shopdetail.model.ShopDecisionTag;
import com.dianping.shop.shopdetail.model.ShopDetailAggregate;
import com.dianping.shop.shopdetail.model.ShopPage;
import com.dianping.shop.shopdetail.model.ShopRelatedShop;
import com.dianping.shop.shopdetail.model.result.SectionResult;
import com.dianping.shop.shopdetail.service.ShopDetailDomainService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 领域服务生成衍生数据，节点只维护各项执行状态及提交边界。
 */
@Component
@TaskNode(nodeId="enrich",
        group=ShopDetailTaskContext.GROUP,
        phase=ProcessPhase.POST_PROCESSING,
        order=20,
        taskType=TaskType.IO_BOUND,
        required=false)
public class EnrichShopDetailNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    /**
     * 商详领域服务。
     */
    private final ShopDetailDomainService domain;
    /**
     * 创建 EnrichShopDetailNode，初始化所需依赖及实例状态。
     *
     * @param domain 商详领域服务
     */
    public EnrichShopDetailNode(ShopDetailDomainService domain) { this.domain = domain; }

    /**
     * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
     *
     * @param value 待读取或转换的值
     */
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        var review = SectionResult.attempt(() -> domain.reviewEvidence(context.getShopId(), context.getSince()), List::isEmpty);
        var blog = SectionResult.attempt(() -> domain.blogEvidence(context.getShopId(), context.getSince()), List::isEmpty);
        SectionResult<List<ShopDecisionTag>> tags;
        if (!review.available() && !blog.available()) {
            tags = SectionResult.unavailable("FAILED", "近期口碑标签暂不可用");
        } else {
            tags = SectionResult.attempt(() -> domain.mergeDecisionTags(
                    review.available() ? review.data() : List.of(), blog.available() ? blog.data() : List.of()), List::isEmpty);
            if (tags.available() && (!review.available() || !blog.available()))
                tags = new SectionResult<>("PARTIAL", tags.data(), "部分来源不可用，统计不完整");
        }
        var reviewTags = review.available()
                ? SectionResult.attempt(() -> domain.mergeDecisionTags(review.data(), List.of()), List::isEmpty)
                : SectionResult.<List<ShopDecisionTag>>unavailable("FAILED", "近期评价标签暂不可用");
        // 可选节点必须复制后再赋值，超时后才能安全丢弃整个节点的写入。
        var aggregate = context.getOutput().toBuilder().build();
        var reason = domain.relatedUnavailableReason(aggregate.getShop());
        var related = reason == null
                ? SectionResult.attempt(() -> domain.related(aggregate.getShop(), 1, 6), page -> page.records().isEmpty())
                : SectionResult.<ShopPage<ShopRelatedShop>>unavailable("UNAVAILABLE", reason);
        context.put("decisionTags", tags);
        context.put("reviewTags", reviewTags);
        context.put("relatedShops", related);
        aggregate.setDecisionTags(tags.data());
        aggregate.setReviewTags(reviewTags.data());
        aggregate.setRelatedShops(related.data());
        aggregate.setRelatedUnavailableReason(reason);
        context.setOutput(aggregate);
    }
}
