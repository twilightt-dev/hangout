package com.dianping.shopdetail.service;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.PhaseConfig;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shopdetail.exception.ShopDetailException;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.model.request.ShopDetailPageRequest;
import com.dianping.shopdetail.model.result.*;
import com.dianping.shopdetail.model.result.ShopDetailResult.*;
import com.dianping.shopdetail.task.ShopDetailTaskContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** 商详用例入口；任务编排与 HTTP 业务数据转换集中在应用层。 */
@Service
public class ShopDetailApplicationService {
    private final TaskChainEngine engine;
    private final ShopDetailQueryService queries;
    private final List<PhaseConfig> phases;
    public ShopDetailApplicationService(TaskChainEngine engine, ShopDetailQueryService queries,
            @Value("${shop-detail.query-timeout-ms:2000}") long queryTimeout,
            @Value("${shop-detail.post-timeout-ms:1000}") long postTimeout) {
        this.engine=engine; this.queries=queries;
        phases=List.of(new PhaseConfig(ProcessPhase.DATA_PROCESSING,ExecutionMode.PARALLEL,Duration.ofMillis(queryTimeout)),
                new PhaseConfig(ProcessPhase.POST_PROCESSING,ExecutionMode.SERIAL,Duration.ofMillis(postTimeout)));
    }
    public ShopDetailResult getShopDetail(Long id, Long viewerId) {
        new ShopDetailPageRequest(id,1,1);
        var context=engine.executePhaseChain(ShopDetailTaskContext.GROUP,new ShopDetailTaskContext(id,viewerId),phases);
        if (!context.isSuccess()) {
            Throwable error=context.getError();
            org.apache.commons.logging.LogFactory.getLog(getClass()).error("商详必需任务失败，shopId="+id,error);
            if (error instanceof ShopDetailException known) throw known;
            throw new ShopDetailException(error instanceof TimeoutException ? 504 : 503,"商详暂时不可用，请稍后重试",error);
        }
        if (context.getOutput()==null) throw new ShopDetailException(503,"商详未生成有效结果");
        var personal=context.<Set<Long>>section("recommendationState","recommendations");
        return new ShopDetailResult(BaseInfo.from(context.getOutput().getShop()),
                context.<ShopPage<ShopVoucher>>section("vouchers","vouchers").map(page -> ShopPageResult.from(page,VoucherItem::from)),
                context.<ShopPage<ShopSetmeal>>section("setmeals","setmeals").map(page -> ShopPageResult.from(page,ShopSetmealResult::from)),
                context.<ShopPage<RecommendDish>>section("recommendations","recommendations").map(page ->
                        ShopPageResult.from(page,dish -> RecommendDishItem.from(dish,context.getRecommendedIds().contains(dish.id())))),
                context.<ShopPage<ShopReview>>section("reviews","reviews").map(page -> ShopPageResult.from(page,ShopReviewItem::from)),
                context.<ShopReviewSummary>section("statistics","reviews").map(ReviewStatistics::from),
                context.<ShopPage<ShopVisitBlog>>section("blogs","blogs").map(page -> ShopPageResult.from(page,BlogItem::from)),
                context.<List<ShopDecisionTag>>section("decisionTags","enrich").map(tags -> tags.stream().map(TagItem::from).toList()),
                context.<List<ShopDecisionTag>>section("reviewTags","enrich").map(tags -> tags.stream().map(TagItem::from).toList()),
                context.<ShopPage<ShopRelatedShop>>section("relatedShops","enrich").map(page -> ShopPageResult.from(page,RelatedItem::from)),
                personal.available());
    }
    public ShopPageResult<VoucherItem> vouchers(ShopDetailPageRequest request) {
        queries.base(request.shopId()); return ShopPageResult.from(queries.vouchers(request),VoucherItem::from);
    }
    public ShopPageResult<BlogItem> blogs(ShopDetailPageRequest request,Long viewerId) {
        queries.base(request.shopId()); return ShopPageResult.from(queries.blogs(request,viewerId),BlogItem::from);
    }
    public SectionResult<ShopPageResult<RelatedItem>> related(ShopDetailPageRequest request) {
        var center=queries.base(request.shopId());
        if (!queries.hasCoordinates(center))
            return SectionResult.unavailable("UNAVAILABLE","缺少位置信息，暂无法推荐周边店铺");
        if (center.typeId() == null) return SectionResult.unavailable("UNAVAILABLE","缺少店铺类型，暂无法推荐周边店铺");
        var page = queries.related(center, request);
        return SectionResult.success(ShopPageResult.from(page,RelatedItem::from),page.records().isEmpty());
    }
}
