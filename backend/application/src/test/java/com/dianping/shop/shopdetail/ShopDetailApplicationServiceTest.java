package com.dianping.shop.shopdetail;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shop.shopdetail.service.ShopDetailApplicationService;
import com.dianping.shop.shopdetail.task.AssembleShopDetailNode;
import com.dianping.shop.shopdetail.task.EnrichShopDetailNode;
import com.dianping.shop.shopdetail.task.QueryRecommendationNode;
import com.dianping.shop.shopdetail.task.QueryShopBaseNode;
import com.dianping.shop.shopdetail.task.QueryShopBlogNode;
import com.dianping.shop.shopdetail.task.QueryShopReviewNode;
import com.dianping.shop.shopdetail.task.QueryShopSetmealNode;
import com.dianping.shop.shopdetail.task.QueryShopVoucherNode;
import com.dianping.shopdetail.exception.ShopDetailException;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.repository.*;
import com.dianping.shopdetail.service.ShopDetailDomainService;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 从已确认的应用入口验证真实任务引擎及降级边界，替换的仅是外部查询能力。
 */
class ShopDetailApplicationServiceTest {
    /**
     * 测试使用的 CPU 执行器。
     */
    /**
     * 测试使用的 IO 执行器。
     */
    ExecutorService cpu, io;
    /**
     * 店铺基础资料、标签语料和周边查询仓储。
     */
    ShopDetailRepository details;
    /**
     * 优惠券仓储。
     */
    ShopVoucherRepository vouchers;
    /**
     * 店铺套餐仓储。
     */
    ShopSetmealRepository meals;
    /**
     * 店铺评价仓储。
     */
    ShopReviewRepository reviews;
    /**
     * 推荐菜与用户推荐关系仓储。
     */
    RecommendDishRepository dishes;
    /**
     * 商详领域服务。
     */
    ShopDetailDomainService domain;
    /**
     * 应用用例服务。
     */
    ShopDetailApplicationService service;
    /**
     * 店铺基础资料。
     */
    final ShopBaseInfo shop=new ShopBaseInfo(8L,"巷口咖啡",1L,"","商圈","地址",120.0,30.0,50L,"全天");

    /**
     * 通过上下文写集更新测试业务属性。
     */
    @BeforeEach void setup() {
        cpu=Executors.newFixedThreadPool(2); io=Executors.newFixedThreadPool(8);
        details=mock(ShopDetailRepository.class);
        vouchers=mock(ShopVoucherRepository.class);
        meals=mock(ShopSetmealRepository.class);
        reviews=mock(ShopReviewRepository.class);
        dishes=mock(RecommendDishRepository.class);
        domain=new ShopDetailDomainService(details,vouchers,meals,reviews,dishes);
        when(details.findShop(8L)).thenReturn(Optional.of(shop));
        when(vouchers.page(eq(8L),eq(1L),eq(2),any())).thenReturn(empty(2));
        when(meals.pageWithTotal(8L,1,4)).thenReturn(empty(4));
        when(dishes.pageWithTotal(8L,1,6)).thenReturn(empty(6));
        when(reviews.pageWithTotal(8L,1,3)).thenReturn(empty(3));
        when(reviews.summarize(8L)).thenReturn(new ShopReviewSummary(0,null));
        when(details.blogs(eq(8L),eq(1L),eq(4),any())).thenReturn(empty(4));
        when(details.reviewEvidence(eq(8L),any(),eq(200))).thenReturn(List.of());
        when(details.blogEvidence(eq(8L),any(),eq(50))).thenReturn(List.of());
        when(details.related(shop,1,6)).thenReturn(empty(6));
        service=service(1000);
    }
    /**
     * 创建测试用的成功空分页数据。
     *
     * @param <T> 当前业务数据类型
     * @param size 每页数量
     * @return 无记录且总数为零的成功分页结果
     */
    private <T> ShopPage<T> empty(int size) { return new ShopPage<>(List.of(),0,1,size); }
    /**
     * 按指定阶段预算装配测试应用入口与真实任务引擎。
     *
     * @param postBudget 测试使用的后处理预算，单位为毫秒
     * @return 已按测试预算装配的商详应用服务
     */
    private ShopDetailApplicationService service(long postBudget) {
        return service(2000, postBudget);
    }
    /**
     * 按指定阶段预算装配测试应用入口与真实任务引擎。
     *
     * @param queryBudget 测试使用的查询预算，单位为毫秒
     * @param postBudget 测试使用的后处理预算，单位为毫秒
     * @return 已按测试预算装配的商详应用服务
     */
    private ShopDetailApplicationService service(long queryBudget, long postBudget) {
        var registry=new TaskNodeRegistry();
        List<TaskNodeHandler<Long,ShopDetailAggregate>> handlers=List.of(
                new QueryShopBaseNode(domain),new QueryShopVoucherNode(domain),new QueryShopSetmealNode(domain),
                new QueryRecommendationNode(domain),new QueryShopReviewNode(domain),new QueryShopBlogNode(domain),
                new AssembleShopDetailNode(),new EnrichShopDetailNode(domain));
        for (var handler:handlers) {
            var node=handler.getClass().getAnnotation(TaskNode.class);
            registry.register(new TaskNodeHolder<>(node.nodeId(),node.group(),node.phase(),node.order(),node.taskType(),node.required(),handler));
        }
        registry.freeze();
        return new ShopDetailApplicationService(new TaskChainEngine(registry,cpu,io,java.time.Duration.ofSeconds(2)),domain,queryBudget,postBudget);
    }
    /**
     * 共享一次关闭预算等待两个线程池退出，超时后尽力取消，并恢复调用线程的中断标记。
     */
    @AfterEach void close() { cpu.shutdownNow(); io.shutdownNow(); }

    /**
     * 验证没有引擎时同步聚合仍能返回相同的成功业务结果及个人状态。
     */
    @Test void missingEngineUsesSynchronousDomainAggregation() {
        var dish = new RecommendDish(10L,8L,7L,UUID.randomUUID().toString(),"烤鱼",
                "/imgs/blogs/a/b/550e8400-e29b-41d4-a716-446655440000.png",3,java.time.LocalDateTime.now());
        when(dishes.pageWithTotal(8L,1,6)).thenReturn(new ShopPage<>(List.of(dish),1,1,6));
        when(dishes.recommendedIds(99L,List.of(10L))).thenReturn(Set.of(10L));
        when(details.reviewEvidence(eq(8L),any(),eq(200))).thenReturn(List.of(
                new TagEvidence(1L,List.of(com.dianping.shopdetail.enums.ReviewTag.TASTY),null)));
        when(details.blogEvidence(eq(8L),any(),eq(50))).thenReturn(List.of(new TagEvidence(2L,List.of(),"味道好")));
        var synchronous = new ShopDetailApplicationService(null, domain, 2000, 1000);
        var result = synchronous.getShopDetail(8L, 99L);
        assertThat(result.shop().id()).isEqualTo(8L);
        assertThat(result.vouchers().status()).isEqualTo("EMPTY");
        assertThat(result.relatedShops().status()).isEqualTo("EMPTY");
        assertThat(result.recommendations().data().records().getFirst().recommended()).isTrue();
        assertThat(result.decisionTags().data()).extracting(tag -> tag.code()).containsExactly("TASTY");
        assertThat(result).isEqualTo(service.getShopDetail(8L,99L));
    }

    /**
     * 验证可选查询超时在最终区域响应中保留为超时状态。
     */
    @Test void optionalQueryTimeoutIsPreservedInCompletedOutput() {
        when(vouchers.page(eq(8L),eq(1L),eq(2),any()))
                .thenAnswer(call -> { Thread.sleep(2000); return empty(2); });
        var result = service(300,1000).getShopDetail(8L,null);
        assertThat(result.vouchers().status()).isEqualTo("TIMED_OUT");
        assertThat(result.vouchers().data()).isNull();
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
        assertThat(result.relatedShops().status()).isEqualTo("EMPTY");
    }

    /**
     * 验证同步与任务链的必需查询故障使用同一对外异常边界。
     */
    @Test void synchronousRequiredFailureUsesSameErrorBoundaryAsTaskChain() {
        var synchronous = new ShopDetailApplicationService(null,domain,2000,1000);
        when(details.findShop(8L)).thenThrow(new IllegalStateException("仓储映射错误"));
        for (var entry : List.of(synchronous,service)) {
            assertThatThrownBy(() -> entry.getShopDetail(8L,null))
                    .isInstanceOfSatisfying(ShopDetailException.class, error -> {
                        assertThat(error.status()).isEqualTo(503);
                        assertThat(error.getMessage()).doesNotContain("仓储映射错误");
                    });
        }
        doReturn(Optional.empty()).when(details).findShop(8L);
        assertThatThrownBy(() -> synchronous.getShopDetail(8L,null))
                .isInstanceOfSatisfying(ShopDetailException.class, error -> assertThat(error.status()).isEqualTo(404));
    }

    /**
     * 验证六区响应区分查询失败与成功空数据，且不暴露底层错误。
     */
    @Test void returnsSixSectionsAndDistinguishesEmptyFromFailure() {
        when(vouchers.page(eq(8L),eq(1L),eq(2),any())).thenThrow(new IllegalStateException("数据库地址不应外泄"));
        var result=service.getShopDetail(8L,null);
        assertThat(result.shop().name()).isEqualTo("巷口咖啡");
        assertThat(result.vouchers().status()).isEqualTo("FAILED");
        assertThat(result.vouchers().data()).isNull();
        assertThat(result.vouchers().message()).doesNotContain("数据库地址");
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
        assertThat(result.setmeals().data().records()).isEmpty();
        assertThat(result.statistics().data().averageScore()).isNull();
    }

    /**
     * 验证同步直接传播仓储异常，而任务链保留其他成功区域。
     */
    @Test void synchronousAggregationPropagatesRepositoryFailureWhileTaskChainDegrades() {
        var failure = new IllegalStateException("优惠券查询失败");
        when(vouchers.page(eq(8L),eq(1L),eq(2),any())).thenThrow(failure);
        assertThatThrownBy(() -> domain.getShopDetail(8L,null)).isSameAs(failure);
        var result = service.getShopDetail(8L,null);
        assertThat(result.vouchers().status()).isEqualTo("FAILED");
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
    }
    /**
     * 验证单一语料故障不隐藏成功列表和统计，并标记口碑来源不完整。
     */
    @Test void failedTagEvidenceDoesNotHideSuccessfulListsAndStatistics() {
        when(details.reviewEvidence(eq(8L),any(),eq(200))).thenThrow(new IllegalStateException("语料故障"));
        var result=service.getShopDetail(8L,99L);
        assertThat(result.reviews().status()).isEqualTo("EMPTY");
        assertThat(result.statistics().status()).isEqualTo("EMPTY");
        assertThat(result.decisionTags().status()).isEqualTo("PARTIAL");
        assertThat(result.reviewTags().status()).isEqualTo("FAILED");
        verify(details).blogs(8L,1,4,99L);
    }
    /**
     * 验证增强超时后保留先前输出，并拒绝迟到节点写入。
     *
     * @throws Exception 测试请求、并发等待或隔离环境初始化失败
     */
    @Test void wholeEnrichmentTimeoutKeepsAssembledOutputAndRejectsLateWrites() throws Exception {
        when(details.related(any(),anyLong(),anyInt())).thenAnswer(call -> { Thread.sleep(500); return empty(6); });
        var result=service(100).getShopDetail(8L,null);
        assertThat(result.shop().id()).isEqualTo(8L);
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
        assertThat(result.relatedShops().status()).isEqualTo("TIMED_OUT");
        assertThat(result.decisionTags().status()).isEqualTo("TIMED_OUT");
    }
    /**
     * 验证缺失店铺使链路失败，非法标识在开始查询前被拒绝。
     */
    @Test void missingShopIsFatalAndInvalidInputNeverStartsQuery() {
        when(details.findShop(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getShopDetail(8L,null)).isInstanceOfSatisfying(ShopDetailException.class,e -> assertThat(e.status()).isEqualTo(404));
        assertThatThrownBy(() -> service.getShopDetail(0L,null)).isInstanceOfSatisfying(ShopDetailException.class,e -> assertThat(e.status()).isEqualTo(400));
        verify(details,never()).findShop(0L);
    }
    /**
     * 验证缺坐标时返回明确不可用状态并跳过周边查询。
     */
    @Test void missingCoordinatesHaveAnExplicitStateAndDoNotQueryNearby() {
        when(details.findShop(8L)).thenReturn(Optional.of(new ShopBaseInfo(8L,"无坐标店",1L,null,null,null,null,null,null,null)));
        var result=service.getShopDetail(8L,null);
        assertThat(result.relatedShops().status()).isEqualTo("UNAVAILABLE");
        assertThat(result.relatedShops().message()).contains("缺少位置信息");
        verify(details,never()).related(any(),anyLong(),anyInt());
    }
}
