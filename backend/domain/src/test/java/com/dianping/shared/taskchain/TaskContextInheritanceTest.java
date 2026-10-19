package com.dianping.shared.taskchain;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 业务子类必须在真实引擎执行中保留类型，并沿用局部写入及阶段提交语义。 */
class TaskContextInheritanceTest {
    @Test
    void serialNodesReceiveBusinessSubtypeAndPassBusinessPropertiesToTheRoot() {
        var first = new AtomicReference<BusinessContext>();
        var registry = new TaskNodeRegistry();
        registry.register(node("load", 0, true, context -> {
            var business = assertInstanceOf(BusinessContext.class, context);
            first.set(business);
            assertEquals(7L, business.getShopId());
            business.setName("店铺-7");
        }));
        registry.register(node("assemble", 1, true, context -> {
            var business = assertInstanceOf(BusinessContext.class, context);
            assertNotSame(first.get(), business);
            business.setOutput(business.getName() + "-详情");
        }));
        registry.freeze();
        var root = new BusinessContext(7L);
        try (var pool = Executors.newFixedThreadPool(2)) {
            BusinessContext result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3))
                    .executeChain("shop", root);
            assertSame(root, result);
            assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
            assertEquals("店铺-7", result.getName());
            assertEquals("店铺-7-详情", result.getOutput());
            assertThrows(IllegalStateException.class, () -> first.get().setName("迟到结果"));
            assertThrows(IllegalStateException.class, () -> root.setName("修改已返回结果"));
            assertThrows(IllegalStateException.class, () -> first.get().beginExecution());
        }
    }

    @Test
    void parallelBusinessContextsReadTheSameSnapshotAndMergeDistinctProperties() {
        var written = new CountDownLatch(1);
        var observed = new CountDownLatch(1);
        var registry = new TaskNodeRegistry();
        registry.register(node("name", 0, true, context -> {
            var business = (BusinessContext) context;
            business.setName("新名称");
            written.countDown();
            assertTrue(observed.await(2, TimeUnit.SECONDS));
        }));
        registry.register(node("count", 1, true, context -> {
            assertTrue(written.await(2, TimeUnit.SECONDS));
            var business = (BusinessContext) context;
            try {
                assertEquals("初始名称", business.getName());
                business.setCount(3);
            } finally { observed.countDown(); }
        }));
        registry.freeze();
        var root = new BusinessContext(7L);
        root.setName("初始名称");
        try (var pool = Executors.newFixedThreadPool(2)) {
            BusinessContext result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3))
                    .executePhaseChain("shop", root, parallel());
            assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
            assertEquals("新名称", result.getName());
            assertEquals(3, result.getCount());
        }
    }

    @Test
    void parallelBusinessPropertyConflictRejectsTheWholeStage() {
        var registry = new TaskNodeRegistry();
        registry.register(node("first", 0, true, c -> ((BusinessContext) c).setName("甲")));
        registry.register(node("second", 1, true, c -> ((BusinessContext) c).setName("乙")));
        registry.freeze();
        var root = new BusinessContext(7L);
        root.setName("原始名称");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3))
                    .executePhaseChain("shop", root, parallel());
            assertFalse(result.isSuccess());
            assertInstanceOf(IllegalStateException.class, result.getError());
            assertEquals("原始名称", result.getName());
        }
    }

    @Test
    void failedBusinessNodeCannotLeakItsPropertiesIntoTheNextNode() {
        var registry = new TaskNodeRegistry();
        registry.register(node("optional", 0, false, c -> {
            ((BusinessContext) c).setName("无效名称");
            c.fail(new IllegalStateException("可选节点失败"));
        }));
        registry.register(node("assemble", 1, true, c -> c.setOutput(((BusinessContext) c).getName())));
        registry.freeze();
        var root = new BusinessContext(7L);
        root.setName("原始名称");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3)).executeChain("shop", root);
            assertTrue(result.isSuccess());
            assertEquals("原始名称", result.getOutput());
            assertEquals(NodeExecutionStatus.FAILED, result.getNodeResults().getFirst().status());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidSubtypeFactoryIsReportedAsNodeFailure(boolean required) {
        // 未覆盖工厂时不能悄悄给业务节点一个基类上下文。
        var root = new TaskContext<Long, String>(7L) { };
        var registry = new TaskNodeRegistry();
        registry.register(node("invalid", 0, required, c -> fail("非法上下文不应交给业务节点")));
        registry.freeze();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(3)).executeChain("shop", root);
            assertEquals(!required, result.isSuccess());
            assertEquals(NodeExecutionStatus.FAILED, result.getNodeResults().getFirst().status());
            assertInstanceOf(IllegalStateException.class, result.getNodeResults().getFirst().cause());
            assertThrows(IllegalStateException.class, () -> root.setOutput("执行已结束"));
        }
    }

    @Test
    void nodeFactoryCannotReturnAnotherExecutionOrReuseANode() {
        class InvalidContext extends TaskContext<Long, String> {
            private TaskContext<Long, String> next;
            InvalidContext() { super(7L); }
            InvalidContext(InvalidContext parent, Snapshot<String> snapshot) { super(parent, snapshot); }
            @Override protected TaskContext<Long, String> createNodeContext(Snapshot<String> snapshot) { return next; }
        }
        var root = new InvalidContext();
        var execution = root.beginExecution();
        var snapshot = execution.snapshot();
        root.next = root;
        assertThrows(IllegalStateException.class, () -> execution.nodeContext(snapshot));
        root.next = new InvalidContext(new InvalidContext(), snapshot);
        assertThrows(IllegalStateException.class, () -> execution.nodeContext(snapshot));
        root.next = new InvalidContext(root, snapshot);
        var node = execution.nodeContext(snapshot);
        assertThrows(IllegalStateException.class, () -> execution.nodeContext(snapshot));
        execution.finishNode(node);
        assertThrows(IllegalStateException.class, () -> execution.nodeContext(snapshot));
    }

    private static TaskNodeHolder<Long, String> node(String id, int order, boolean required, TaskNodeHandler<Long, String> handler) {
        return new TaskNodeHolder<>(id, "shop", ProcessPhase.DATA_PROCESSING, order, TaskType.CPU_BOUND, required, handler);
    }

    private static List<PhaseConfig> parallel() {
        return List.of(new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.PARALLEL, Duration.ofSeconds(3)));
    }

    private static final class BusinessContext extends TaskContext<Long, String> {
        BusinessContext(Long shopId) { super(shopId); }
        private BusinessContext(BusinessContext parent, Snapshot<String> snapshot) { super(parent, snapshot); }
        @Override protected BusinessContext createNodeContext(Snapshot<String> snapshot) { return new BusinessContext(this, snapshot); }
        Long getShopId() { return getInput(); }
        String getName() { return (String) getAttribute("name"); }
        void setName(String name) { setAttribute("name", name); }
        Integer getCount() { return (Integer) getAttribute("count"); }
        void setCount(Integer count) { setAttribute("count", count); }
    }
}
