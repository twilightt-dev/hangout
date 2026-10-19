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

/**
 * 业务子类必须在真实引擎执行中保留类型，并沿用局部写入及阶段提交语义。
 */
class TaskContextInheritanceTest {
    /**
     * 验证节点保留业务上下文子类型且串行属性能合并回根。
     */
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

    /**
     * 验证业务子上下文并行读取一致快照并合并不同属性。
     */
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

    /**
     * 验证业务属性并行冲突拒绝整个阶段提交。
     */
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

    /**
     * 验证失败业务节点的属性不会泄漏给后续节点。
     */
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

    /**
     * 验证无效业务子类型工厂转为节点失败报告。
     *
     * @param required 是否为失败后阻断链路的必需节点
     */
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

    /**
     * 验证节点工厂不能跨执行返回上下文或复用节点视图。
     */
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

    /**
     * 创建带指定元数据的测试任务节点。
     *
     * @param id 记录标识
     * @param order 阶段内排序值；数值越小越先执行
     * @param required 是否为失败后阻断链路的必需节点
     * @param handler 实际执行节点业务的处理器
     * @return 具有指定元数据及执行逻辑的测试节点
     */
    private static TaskNodeHolder<Long, String> node(String id, int order, boolean required, TaskNodeHandler<Long, String> handler) {
        return new TaskNodeHolder<>(id, "shop", ProcessPhase.DATA_PROCESSING, order, TaskType.CPU_BOUND, required, handler);
    }

    /**
     * 创建测试用的并行阶段配置。
     *
     * @return 测试使用的并行阶段配置列表
     */
    private static List<PhaseConfig> parallel() {
        return List.of(new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.PARALLEL, Duration.ofSeconds(3)));
    }

    /**
     * 测试使用的业务上下文子类型。
     */
    private static final class BusinessContext extends TaskContext<Long, String> {
        /**
         * 创建 BusinessContext，初始化所需依赖及实例状态。
         *
         * @param shopId 店铺标识
         */
        BusinessContext(Long shopId) { super(shopId); }
        /**
         * 创建 BusinessContext，初始化所需依赖及实例状态。
         *
         * @param parent 创建当前节点视图的父上下文
         * @param snapshot 节点可见或准备提交的数据快照
         */
        private BusinessContext(BusinessContext parent, Snapshot<String> snapshot) { super(parent, snapshot); }
        /**
         * 基于引擎提供的快照创建同类型节点视图，业务属性继续通过父类写集传递。
         *
         * @param snapshot 节点可见或准备提交的数据快照
         * @return 与根上下文具有相同业务类型的全新节点视图
         */
        @Override protected BusinessContext createNodeContext(Snapshot<String> snapshot) { return new BusinessContext(this, snapshot); }
        /**
         * 取得本次商详查询的店铺标识。
         *
         * @return 本次查询的店铺标识
         */
        Long getShopId() { return getInput(); }
        /**
         * 读取测试业务上下文中通过写集维护的属性。
         *
         * @return 当前视图可见的测试名称属性
         */
        String getName() { return (String) getAttribute("name"); }
        /**
         * 通过上下文写集更新测试业务属性。
         *
         * @param name 名称
         */
        void setName(String name) { setAttribute("name", name); }
        /**
         * 读取测试业务上下文中通过写集维护的属性。
         *
         * @return 当前视图可见的测试计数属性
         */
        Integer getCount() { return (Integer) getAttribute("count"); }
        /**
         * 通过上下文写集更新测试业务属性。
         *
         * @param count 记录数量或测试计数值
         */
        void setCount(Integer count) { setAttribute("count", count); }
    }
}
