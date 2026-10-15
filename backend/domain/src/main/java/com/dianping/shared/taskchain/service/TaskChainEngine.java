package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;

public final class TaskChainEngine {
    private final TaskNodeRegistry registry;
    private final PhaseExecutionEngine phaseEngine;
    private final List<PhaseConfig> defaultPhases;

    public TaskChainEngine(TaskNodeRegistry registry, Executor cpuExecutor,
                           Executor ioExecutor, Duration defaultPhaseTimeout) {
        this.registry = Objects.requireNonNull(registry, "注册表不得为空");
        this.phaseEngine = new PhaseExecutionEngine(new TaskDispatcher(cpuExecutor, ioExecutor));
        this.defaultPhases = Arrays.stream(ProcessPhase.values())
                .map(phase -> new PhaseConfig(phase, ExecutionMode.SERIAL, defaultPhaseTimeout)).toList();
    }

    public <I, O> TaskContext<I, O> executeChain(String group, TaskContext<I, O> context) {
        return executePhaseChain(group, context, defaultPhases);
    }

    public <I, O> TaskContext<I, O> executePhaseChain(String group, TaskContext<I, O> context,
                                                    List<PhaseConfig> phaseConfigs) {
        Objects.requireNonNull(context, "上下文不得为空");
        if (group == null || group.isBlank()) throw new IllegalArgumentException("任务组不得为空白");
        if (phaseConfigs == null || phaseConfigs.isEmpty()) throw new IllegalArgumentException("阶段列表不得为空");
        var phases = List.copyOf(phaseConfigs);
        var seen = EnumSet.noneOf(ProcessPhase.class);
        var selected = new ArrayList<List<TaskNodeHolder<?, ?>>>();
        for (var phase : phases) {
            if (!seen.add(phase.phase())) throw new IllegalArgumentException("阶段不得重复：" + phase.phase());
            selected.add(registry.nodes(group, phase.phase()));
        }
        var execution = context.beginExecution();
        var reports = new ArrayList<PhaseExecutionResult>();
        Throwable fatal = null;
        for (int i = 0; i < phases.size(); i++) {
            if (fatal == null) {
                var result = phaseEngine.execute(phases.get(i), selected.get(i), execution);
                reports.add(result);
                if (!result.canContinue()) fatal = result.cause();
            } else {
                var skipped = selected.get(i).stream().map(node -> PhaseExecutionEngine.report(node,
                        NodeExecutionStatus.NOT_EXECUTED, null)).toList();
                reports.add(new PhaseExecutionResult(phases.get(i).phase(), false, null, skipped));
            }
        }
        execution.finish(fatal == null, fatal, reports);
        return context;
    }
}
