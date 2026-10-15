package com.dianping.shared.taskchain;

import com.dianping.shared.taskchain.enums.*;
import com.dianping.shared.taskchain.model.*;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class TaskNodeRegistryTest {
    @Test
    void idsAreUniqueWithinGroupAndOrderingIsStableAfterFreeze() {
        var registry = new TaskNodeRegistry();
        registry.register(node("b", "shop", 1));
        registry.register(node("z", "shop", 0));
        registry.register(node("a", "shop", 1));
        assertThrows(IllegalArgumentException.class, () -> registry.register(node("a", "shop", 9)));
        registry.register(node("a", "other", 0));
        assertThrows(IllegalStateException.class, () -> registry.nodes("shop", ProcessPhase.DATA_PROCESSING));
        registry.freeze();
        assertEquals(List.of("z", "a", "b"), registry.nodes("shop", ProcessPhase.DATA_PROCESSING)
                .stream().map(TaskNodeHolder::id).toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.nodes("shop", ProcessPhase.DATA_PROCESSING).clear());
        assertThrows(IllegalStateException.class, () -> registry.register(node("new", "shop", 0)));
        assertThrows(NullPointerException.class, () -> registry.nodes("shop", null));
    }

    @Test
    void invalidInvocationsFailBeforeNodesRunOrContextIsClaimed() {
        var registry = new TaskNodeRegistry();
        registry.register(node("node", "shop", 0));
        registry.freeze();
        var config = new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL, Duration.ofSeconds(1));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var engine = new TaskChainEngine(registry, pool, pool, Duration.ofSeconds(1));
            var context = new TaskContext<Integer, String>(7);
            assertThrows(IllegalArgumentException.class, () -> engine.executePhaseChain("shop", context, List.of()));
            assertThrows(IllegalArgumentException.class, () -> engine.executePhaseChain("shop", context, List.of(config, config)));
            assertThrows(NullPointerException.class, () -> engine.executePhaseChain("shop", context, Arrays.asList(config, null)));
            assertThrows(IllegalArgumentException.class, () -> engine.executeChain("unknown", context));
            assertThrows(IllegalArgumentException.class, () -> engine.executeChain(" ", context));
            assertThrows(NullPointerException.class, () -> engine.executeChain("shop", null));
            assertTrue(engine.executeChain("shop", context).isSuccess());
        }
    }

    @Test
    void emptyRegistryFreezesButUnknownGroupCannotExecute() {
        var registry = new TaskNodeRegistry();
        registry.freeze();
        assertTrue(registry.isFrozen());
        assertThrows(IllegalArgumentException.class, () -> registry.nodes("unknown", ProcessPhase.DATA_PROCESSING));
    }

    @Test
    void nodeMetadataAndPhaseBudgetsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> node(" ", "shop", 0));
        assertThrows(IllegalArgumentException.class, () -> node("id", " ", 0));
        assertThrows(IllegalArgumentException.class, () -> new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL, Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new PhaseConfig(ProcessPhase.DATA_PROCESSING, ExecutionMode.SERIAL, Duration.ofSeconds(Long.MAX_VALUE)));
        assertThrows(NullPointerException.class, () -> new PhaseConfig(null, ExecutionMode.SERIAL, Duration.ofSeconds(1)));
    }

    private TaskNodeHolder<Integer, String> node(String id, String group, int order) {
        return new TaskNodeHolder<>(id, group, ProcessPhase.DATA_PROCESSING, order, TaskType.CPU_BOUND, true,
                context -> context.setOutput("正常"));
    }
}
