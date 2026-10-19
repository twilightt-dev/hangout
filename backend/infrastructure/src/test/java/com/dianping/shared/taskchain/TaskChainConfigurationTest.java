package com.dianping.shared.taskchain;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.config.TaskChainConfiguration;
import com.dianping.shared.taskchain.config.TaskChainExecutors;
import com.dianping.shared.taskchain.config.TaskChainProperties;
import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过公开入口验证当前模块的业务约束和执行边界。
 */
class TaskChainConfigurationTest {
    /**
     * Spring 测试上下文启动器。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TaskChainConfiguration.class);

    /**
     * 验证注解省略展示元数据时的名称和描述默认行为。
     */
    @Test
    void omittedAnnotationNameUsesNodeIdAndEmptyDescription() {
        runner.withBean(DefaultNameNode.class).run(context -> {
            assertNull(context.getStartupFailure());
            var node = context.getBean(TaskNodeRegistry.class).nodes("shop", ProcessPhase.DATA_PROCESSING).getFirst();
            assertEquals("unnamed", node.nodeName());
            assertEquals("", node.description());
            var result = context.getBean(TaskChainEngine.class)
                    .executeChain("shop", new TaskContext<Integer, String>(7));
            assertTrue(result.isSuccess());
            assertEquals("unnamed", result.getNodeResults().getFirst().nodeName());
        });
    }

    /**
     * 测试使用的任务处理器或容器装配对象。
     */
    @TaskNode(nodeId = "unnamed", group = "shop", phase = ProcessPhase.DATA_PROCESSING, taskType = TaskType.CPU_BOUND)
    static class DefaultNameNode implements TaskNodeHandler<Integer, String> {
        /**
         * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
         *
         * @param context 当前调用独立使用的任务上下文
         */
        @Override public void execute(TaskContext<Integer, String> context) {
            context.setOutput("店铺-" + context.getInput());
        }
    }

    /**
     * 验证工作线程不会隐式继承请求线程身份。
     */
    @Test
    void workerThreadsDoNotImplicitlyInheritCallerIdentity() {
        var identity = new InheritableThreadLocal<String>();
        identity.set("调用方身份");
        try {
            runner.withPropertyValues("taskchain.cpu-threads=1", "taskchain.io-threads=1").run(context -> {
                assertNull(context.getStartupFailure());
                var executors = context.getBean(TaskChainExecutors.class);
                assertNull(executors.cpuExecutor().submit(identity::get).get(2, TimeUnit.SECONDS));
                assertNull(executors.ioExecutor().submit(identity::get).get(2, TimeUnit.SECONDS));
            });
        } finally { identity.remove(); }
    }

    /**
     * 验证空节点集合不影响启动，但调用未知组时失败。
     */
    @Test
    void emptyNodeSetAllowsStartupButUnknownGroupFailsAtInvocation() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(TaskNodeRegistry.class).isFrozen());
            assertThrows(IllegalArgumentException.class, () -> context.getBean(TaskChainEngine.class)
                    .executeChain("unknown", new TaskContext<>(7)));
        });
    }

    /**
     * 验证同组节点重复在启动期间直接失败。
     */
    @Test
    void duplicateNodeIdsFailDuringStartup() {
        runner.withUserConfiguration(Nodes.class).withBean("duplicate", ShopNode.class, ShopNode::new)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    /**
     * 验证非法线程池和预算配置导致启动失败。
     *
     * @param property 测试业务上下文使用的属性名称
     */
    @ParameterizedTest
    @ValueSource(strings = {"taskchain.cpu-threads=0", "taskchain.io-threads=-1", "taskchain.queue-capacity=0",
            "taskchain.default-phase-timeout=0ms", "taskchain.shutdown-timeout=-1s",
            "taskchain.default-phase-timeout=bad-duration"})
    void invalidPoolAndBudgetConfigurationFailsStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertNotNull(context.getStartupFailure()));
    }

    /**
     * 验证双线程池有界、具备名称且拒绝任务不在调用线程执行。
     */
    @Test
    void bothPoolsAreBoundedNamedAndRejectWithoutRunningOnCaller() {
        runner.withPropertyValues("taskchain.cpu-threads=1", "taskchain.io-threads=1", "taskchain.queue-capacity=1")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var executors = context.getBean(TaskChainExecutors.class);
                    for (var pool : new ThreadPoolExecutor[]{executors.cpuExecutor(), executors.ioExecutor()}) {
                        assertEquals(1, pool.getMaximumPoolSize());
                        assertEquals(1, pool.getQueue().remainingCapacity());
                        assertTrue(pool.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS).startsWith("taskchain-"));
                        var entered = new CountDownLatch(1);
                        var release = new CountDownLatch(1);
                        try {
                            pool.execute(() -> { entered.countDown(); awaitIgnoringInterrupts(release); });
                            assertTrue(entered.await(2, TimeUnit.SECONDS));
                            var queued = pool.submit(() -> "队列任务");
                            var callerRan = new AtomicBoolean();
                            assertThrows(RejectedExecutionException.class, () -> pool.execute(() -> callerRan.set(true)));
                            assertFalse(callerRan.get());
                            release.countDown();
                            assertEquals("队列任务", queued.get(2, TimeUnit.SECONDS));
                        } finally { release.countDown(); }
                    }
                    context.close();
                    assertTrue(executors.cpuExecutor().isShutdown());
                    assertTrue(executors.ioExecutor().isShutdown());
                });
    }

    /**
     * 验证两个线程池共享关闭预算，超时后尝试取消。
     */
    @Test
    void poolsShareOneShutdownBudgetAndAttemptCancellationAfterIt() {
        runner.withPropertyValues("taskchain.cpu-threads=1", "taskchain.io-threads=1", "taskchain.shutdown-timeout=600ms")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var executors = context.getBean(TaskChainExecutors.class);
                    var entered = new CountDownLatch(2);
                    var release = new CountDownLatch(1);
                    var interrupted = new CountDownLatch(2);
                    Runnable blocking = () -> {
                        entered.countDown();
                        boolean done = false;
                        while (!done) {
                            try { release.await(); done = true; }
                            catch (InterruptedException ignored) { interrupted.countDown(); }
                        }
                    };
                    try {
                        executors.cpuExecutor().execute(blocking);
                        executors.ioExecutor().execute(blocking);
                        assertTrue(entered.await(2, TimeUnit.SECONDS));
                        long started = System.nanoTime();
                        context.close();
                        long elapsed = System.nanoTime() - started;
                        assertTrue(elapsed < Duration.ofMillis(1050).toNanos(), "两个线程池不能分别消耗完整预算");
                        assertTrue(interrupted.await(2, TimeUnit.SECONDS));
                    } finally {
                        release.countDown();
                        assertTrue(executors.cpuExecutor().awaitTermination(2, TimeUnit.SECONDS));
                        assertTrue(executors.ioExecutor().awaitTermination(2, TimeUnit.SECONDS));
                    }
                });
    }

    /**
     * 验证默认线程池与预算符合确认的开发配置。
     */
    @Test
    void defaultsMatchApprovedDevelopmentValues() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var properties = context.getBean(TaskChainProperties.class);
            assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors()), properties.getCpuThreads());
            assertEquals(Math.max(4, 2 * Runtime.getRuntime().availableProcessors()), properties.getIoThreads());
            assertEquals(128, properties.getQueueCapacity());
            assertEquals(Duration.ofSeconds(3), properties.getDefaultPhaseTimeout());
            assertEquals(Duration.ofSeconds(5), properties.getShutdownTimeout());
        });
    }

    /**
     * 模拟无法立即中断的外部任务，用于验证迟到写入隔离。
     *
     * @param latch 协调测试线程执行时机的计数门闩
     */
    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) {
            try { latch.await(); break; }
            catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /**
     * 验证从目标类型发现注解，但实际调用容器中的代理处理器。
     */
    @Test
    void discoversTargetAnnotationButInvokesActualJdkProxyBean() {
        runner.withUserConfiguration(ProxyNodes.class).run(context -> {
            assertNull(context.getStartupFailure());
            var node = context.getBean(TaskNodeRegistry.class).nodes("shop", ProcessPhase.DATA_PROCESSING).getFirst();
            assertEquals("shop", node.nodeId());
            assertEquals("加载店铺基础信息", node.nodeName());
            assertEquals("根据店铺编号生成示例详情", node.description());
            var result = context.getBean(TaskChainEngine.class)
                    .executeChain("shop", new TaskContext<Integer, String>(7));
            assertTrue(result.isSuccess());
            assertEquals("店铺-7", result.getOutput());
            assertEquals(Boolean.TRUE, result.getAttribute("advice"));
            assertEquals("shop", result.getNodeResults().getFirst().nodeId());
            assertEquals("加载店铺基础信息", result.getNodeResults().getFirst().nodeName());
        });
    }

    /**
     * 测试使用的任务处理器或容器装配对象。
     */
    @Configuration(proxyBeanMethods = false)
    static class ProxyNodes {
        /**
         * 创建用于验证代理注解发现的测试处理器。
         *
         * @return 用于验证代理发现与调用的任务处理器
         */
        @Bean
        @SuppressWarnings("unchecked")
        TaskNodeHandler<Integer, String> proxiedNode() {
            var factory = new ProxyFactory(new ShopNode());
            factory.setProxyTargetClass(false);
            factory.addAdvice((MethodInterceptor) invocation -> {
                ((TaskContext<?, ?>) invocation.getArguments()[0]).setAttribute("advice", true);
                return invocation.proceed();
            });
            return (TaskNodeHandler<Integer, String>) factory.getProxy();
        }
    }

    /**
     * 验证注解节点被发现、注册表冻结及默认链路可执行。
     */
    @Test
    void discoversAnnotatedBeansFreezesRegistryAndRunsDefaultChain() {
        runner.withUserConfiguration(Nodes.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBean(TaskNodeRegistry.class).isFrozen());
            var result = context.getBean(TaskChainEngine.class)
                    .executeChain("shop", new TaskContext<Integer, String>(7));
            assertTrue(result.isSuccess());
            assertEquals("店铺-7", result.getOutput());
        });
    }

    /**
     * 测试使用的任务处理器或容器装配对象。
     */
    @Configuration(proxyBeanMethods = false)
    static class Nodes {
        /**
         * 创建商详业务上下文测试节点。
         *
         * @return 测试用商详处理器
         */
        @Bean ShopNode shopNode() { return new ShopNode(); }
    }

    /**
     * 测试使用的任务处理器或容器装配对象。
     */
    @TaskNode(nodeId = "shop", nodeName = "加载店铺基础信息", description = "根据店铺编号生成示例详情",
            group = "shop", phase = ProcessPhase.DATA_PROCESSING, taskType = TaskType.CPU_BOUND)
    static class ShopNode implements TaskNodeHandler<Integer, String> {
        /**
         * 执行当前节点，只通过节点上下文提交结果；失败或超时的写集由引擎丢弃。
         *
         * @param context 当前调用独立使用的任务上下文
         */
        @Override public void execute(TaskContext<Integer, String> context) {
            context.setOutput("店铺-" + context.getInput());
        }
    }
}
