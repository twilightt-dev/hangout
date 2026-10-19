package com.dianping.shopdetail;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import com.dianping.shopdetail.exception.ShopDetailException;
import com.dianping.shopdetail.model.*;
import com.dianping.shopdetail.service.*;
import com.dianping.shopdetail.task.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 从已确认的应用入口验证真实任务引擎及降级边界，替换的仅是外部查询能力。 */
class ShopDetailApplicationServiceTest {
    ExecutorService cpu, io;
    ShopDetailQueryService queries;
    ShopDetailApplicationService service;
    final ShopBaseInfo shop=new ShopBaseInfo(8L,"巷口咖啡",1L,"","商圈","地址",120.0,30.0,50L,"全天");

    @BeforeEach void setup() {
        cpu=Executors.newFixedThreadPool(2); io=Executors.newFixedThreadPool(8);
        queries=mock(ShopDetailQueryService.class);
        when(queries.base(8L)).thenReturn(shop);
        when(queries.vouchers(any())).thenReturn(empty(2));
        when(queries.setmeals(any())).thenReturn(empty(4));
        when(queries.recommendations(any())).thenReturn(empty(6));
        when(queries.recommended(any(),any())).thenReturn(Set.of());
        when(queries.reviews(any())).thenReturn(empty(3));
        when(queries.statistics(8L)).thenReturn(new ShopReviewSummary(0,null));
        when(queries.blogs(any(),any())).thenReturn(empty(4));
        when(queries.reviewEvidence(eq(8L),any())).thenReturn(List.of());
        when(queries.blogEvidence(eq(8L),any())).thenReturn(List.of());
        when(queries.related(any(),any())).thenReturn(empty(6));
        service=service(1000);
    }
    private <T> ShopPage<T> empty(int size) { return new ShopPage<>(List.of(),0,1,size); }
    private ShopDetailApplicationService service(long postBudget) {
        var registry=new TaskNodeRegistry();
        List<TaskNodeHandler<Long,ShopDetailAggregate>> handlers=List.of(
                new QueryShopBaseNode(queries),new QueryShopVoucherNode(queries),new QueryShopSetmealNode(queries),
                new QueryRecommendationNode(queries),new QueryShopReviewNode(queries),new QueryShopBlogNode(queries),
                new AssembleShopDetailNode(),new EnrichShopDetailNode(new ShopDetailDomainService(),queries));
        for (var handler:handlers) {
            var node=handler.getClass().getAnnotation(TaskNode.class);
            registry.register(new TaskNodeHolder<>(node.nodeId(),node.group(),node.phase(),node.order(),node.taskType(),node.required(),handler));
        }
        registry.freeze();
        return new ShopDetailApplicationService(new TaskChainEngine(registry,cpu,io,java.time.Duration.ofSeconds(2)),queries,2000,postBudget);
    }
    @AfterEach void close() { cpu.shutdownNow(); io.shutdownNow(); }

    @Test void returnsSixSectionsAndDistinguishesEmptyFromFailure() {
        when(queries.vouchers(any())).thenThrow(new IllegalStateException("数据库地址不应外泄"));
        var result=service.getShopDetail(8L,null);
        assertThat(result.shop().name()).isEqualTo("巷口咖啡");
        assertThat(result.vouchers().status()).isEqualTo("FAILED");
        assertThat(result.vouchers().data()).isNull();
        assertThat(result.vouchers().message()).doesNotContain("数据库地址");
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
        assertThat(result.setmeals().data().records()).isEmpty();
        assertThat(result.statistics().data().averageScore()).isNull();
    }
    @Test void failedTagEvidenceDoesNotHideSuccessfulListsAndStatistics() {
        when(queries.reviewEvidence(eq(8L),any())).thenThrow(new IllegalStateException("语料故障"));
        var result=service.getShopDetail(8L,99L);
        assertThat(result.reviews().status()).isEqualTo("EMPTY");
        assertThat(result.statistics().status()).isEqualTo("EMPTY");
        assertThat(result.decisionTags().status()).isEqualTo("PARTIAL");
        assertThat(result.reviewTags().status()).isEqualTo("FAILED");
        verify(queries).blogs(any(),eq(99L));
    }
    @Test void wholeEnrichmentTimeoutKeepsAssembledOutputAndRejectsLateWrites() throws Exception {
        when(queries.related(any(),any())).thenAnswer(call -> { Thread.sleep(500); return empty(6); });
        var result=service(100).getShopDetail(8L,null);
        assertThat(result.shop().id()).isEqualTo(8L);
        assertThat(result.setmeals().status()).isEqualTo("EMPTY");
        assertThat(result.relatedShops().status()).isEqualTo("TIMED_OUT");
        assertThat(result.decisionTags().status()).isEqualTo("TIMED_OUT");
    }
    @Test void missingShopIsFatalAndInvalidInputNeverStartsQuery() {
        when(queries.base(8L)).thenThrow(new ShopDetailException(404,"店铺不存在"));
        assertThatThrownBy(() -> service.getShopDetail(8L,null)).isInstanceOfSatisfying(ShopDetailException.class,e -> assertThat(e.status()).isEqualTo(404));
        assertThatThrownBy(() -> service.getShopDetail(0L,null)).isInstanceOfSatisfying(ShopDetailException.class,e -> assertThat(e.status()).isEqualTo(400));
        verify(queries,never()).base(0L);
    }
    @Test void missingCoordinatesHaveAnExplicitStateAndDoNotQueryNearby() {
        when(queries.base(8L)).thenReturn(new ShopBaseInfo(8L,"无坐标店",1L,null,null,null,null,null,null,null));
        var result=service.getShopDetail(8L,null);
        assertThat(result.relatedShops().status()).isEqualTo("UNAVAILABLE");
        assertThat(result.relatedShops().message()).contains("缺少位置信息");
        verify(queries,never()).related(any(),any());
    }
}
