package com.dianping.shopdetail.task;
import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.SectionResult;
import com.dianping.shopdetail.service.*;
import org.springframework.stereotype.Component;
import java.util.List;
/** 增强内部也隔离故障；周边查询包含 IO，因此使用 IO 池。 */
@Component
@TaskNode(nodeId="enrich", group=ShopDetailTaskContext.GROUP, phase=ProcessPhase.POST_PROCESSING,
        order=20, taskType=TaskType.IO_BOUND, required=false)
public class EnrichShopDetailNode implements TaskNodeHandler<Long, ShopDetailAggregate> {
    private final ShopDetailDomainService domain;
    private final ShopDetailQueryService queries;
    public EnrichShopDetailNode(ShopDetailDomainService domain, ShopDetailQueryService queries) {
        this.domain = domain; this.queries = queries;
    }
    @Override public void execute(TaskContext<Long, ShopDetailAggregate> value) {
        var context = (ShopDetailTaskContext) value;
        // 语料读取留在可选增强中，即使阻塞至阶段超时，也不能丢弃已组装的评价与博客。
        var review = ShopDetailQueryService.attempt(() -> queries.reviewEvidence(context.getShopId(), context.getSince()), List::isEmpty);
        var blog = ShopDetailQueryService.attempt(() -> queries.blogEvidence(context.getShopId(), context.getSince()), List::isEmpty);
        SectionResult<List<ShopDecisionTag>> tags;
        if (!review.available() && !blog.available()) tags = SectionResult.unavailable("FAILED", "近期口碑标签暂不可用");
        else {
            var result = ShopDetailQueryService.attempt(() -> domain.mergeDecisionTags(
                    review.available() ? review.data() : List.of(), blog.available() ? blog.data() : List.of()), List::isEmpty);
            tags = result.available() && (!review.available() || !blog.available())
                    ? new SectionResult<>("PARTIAL", result.data(), "部分来源不可用，统计不完整") : result;
        }
        var reviewTags = review.available()
                ? ShopDetailQueryService.attempt(() -> domain.mergeDecisionTags(review.data(), List.of()), List::isEmpty)
                : SectionResult.<List<ShopDecisionTag>>unavailable("FAILED", "近期评价标签暂不可用");
        context.put("decisionTags", tags); context.put("reviewTags", reviewTags);
        SectionResult<ShopPage<ShopRelatedShop>> related;
        if (!domain.hasCoordinates(context.getBase())) related = SectionResult.unavailable("UNAVAILABLE", "缺少位置信息，暂无法推荐周边店铺");
        else if (context.getBase().typeId() == null) related = SectionResult.unavailable("UNAVAILABLE", "缺少店铺类型，暂无法推荐周边店铺");
        else related = ShopDetailQueryService.attempt(() -> queries.related(context.getBase(),
                new ShopDetailPageRequest(context.getShopId(), 1, 6)), page -> page.records().isEmpty());
        context.put("relatedShops", related);
        context.setOutput(context.getOutput().toBuilder().decisionTags(tags.data()).reviewTags(reviewTags.data())
                .relatedShops(related.data()).build());
    }
}
