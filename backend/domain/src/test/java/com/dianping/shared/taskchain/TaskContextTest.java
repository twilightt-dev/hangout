package com.dianping.shared.taskchain;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskContextTest {
    @Test
    void visibleAttributesAndReportsAreReadOnlyContainerSnapshots() {
        var input = new HashMap<String, String>();
        input.put("shop", "店铺-7");
        var context = new TaskContext<Map<String, String>, String>(input);
        context.setAttribute("seed", "初始属性");
        var registry = new TaskNodeRegistry();
        registry.register(new TaskNodeHolder<Map<String, String>, String>("read", "shop", ProcessPhase.DATA_PROCESSING,
                0, TaskType.CPU_BOUND, true, c -> {
                    assertThrows(UnsupportedOperationException.class, () -> c.getAttributes().clear());
                    assertThrows(IllegalStateException.class, c::beginExecution);
                    c.setOutput(c.getInput().get("shop"));
                }));
        registry.freeze();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(2)).executeChain("shop", context);
            assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
            assertEquals("店铺-7", result.getOutput());
            assertEquals("初始属性", result.getAttribute("seed"));
            assertThrows(UnsupportedOperationException.class, () -> result.getAttributes().clear());
            assertThrows(UnsupportedOperationException.class, () -> result.getNodeResults().clear());
            assertThrows(IllegalStateException.class, () -> result.setOutput("覆盖已返回响应"));
        }
    }

    @Test
    void concreteCollectionInputsOutputsAndAttributeValuesKeepTheirDeclaredTypes() {
        var input = new HashMap<String, String>();
        input.put("shop", "店铺-7");
        var output = new ArrayList<String>();
        output.add("店铺-7");
        var value = new TreeMap<String, String>();
        value.put("name", "店铺-7");
        var registry = new TaskNodeRegistry();
        registry.register(new TaskNodeHolder<HashMap<String, String>, ArrayList<String>>("node", "shop", ProcessPhase.DATA_PROCESSING,
                0, TaskType.CPU_BOUND, true, c -> {
                    assertEquals("店铺-7", c.getInput().get("shop"));
                    c.setAttribute("value", value);
                    c.setOutput(output);
                }));
        registry.freeze();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(2))
                    .executeChain("shop", new TaskContext<HashMap<String, String>, ArrayList<String>>(input));
            assertTrue(result.isSuccess(), () -> String.valueOf(result.getError()));
            assertSame(input, result.getInput());
            assertSame(output, result.getOutput());
            assertSame(value, result.getAttribute("value"));
        }
    }

    @Test
    void oneContextCannotRunConcurrentlyOrBeExecutedAgain() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var registry = new TaskNodeRegistry();
        registry.register(new TaskNodeHolder<Integer, String>("node", "shop", ProcessPhase.DATA_PROCESSING,
                0, TaskType.CPU_BOUND, true, c -> { started.countDown(); release.await(); c.setOutput("完成"); }));
        registry.freeze();
        try (var workers = Executors.newFixedThreadPool(2); var caller = Executors.newSingleThreadExecutor()) {
            var engine = new TaskChainEngine(registry, workers, workers, Duration.ofSeconds(3));
            var context = new TaskContext<Integer, String>(7);
            try {
                var first = caller.submit(() -> engine.executeChain("shop", context));
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> engine.executeChain("shop", context));
                release.countDown();
                assertTrue(first.get(2, TimeUnit.SECONDS).isSuccess());
                assertThrows(IllegalStateException.class, () -> engine.executeChain("shop", context));
            } finally { release.countDown(); }
        }
    }

    @Test
    void explicitNodeFailureDiscardsLocalWritesWithoutPoisoningOptionalChain() {
        var cause = new IllegalArgumentException("主动标记失败");
        var registry = new TaskNodeRegistry();
        registry.register(new TaskNodeHolder<Integer, String>("optional", "shop", ProcessPhase.DATA_PROCESSING,
                0, TaskType.CPU_BOUND, false, c -> { c.setOutput("无效"); c.fail(cause); }));
        registry.freeze();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var result = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(2))
                    .executeChain("shop", new TaskContext<Integer, String>(7));
            assertTrue(result.isSuccess());
            assertNull(result.getError());
            assertNull(result.getOutput());
            assertSame(cause, result.getNodeResults().getFirst().cause());
        }
    }
}
