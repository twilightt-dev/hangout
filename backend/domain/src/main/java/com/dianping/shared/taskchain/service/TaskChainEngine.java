package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * 任务链引擎
 * <p>
 * 负责阶段之间的推进，支持两种入口：
 * <ul>
 *    <li>{@link TaskChainEngine#executeChain(String, TaskContext)}使用默认阶段配置(全部串行)执行group的所有节点</li>
 *    <li>{@link TaskChainEngine#executePhaseChain(String, TaskContext, List)}使用自定义阶段配置，可以传入自定义的阶段配置PhaseConfig</li>
 * </ul>
 * </p>
 */
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

    public <I, O, C extends TaskContext<I, O>> C executeChain(String group, C context) {
        return executePhaseChain(group, context, defaultPhases);
    }

    public <I, O, C extends TaskContext<I, O>> C executePhaseChain(String group, C context,
                                                    List<PhaseConfig> phaseConfigs) {
        //校验任务链配置
        Objects.requireNonNull(context, "上下文不得为空");
        if (group == null || group.isBlank()) throw new IllegalArgumentException("任务组不得为空白");
        if (phaseConfigs == null || phaseConfigs.isEmpty()) throw new IllegalArgumentException("阶段列表不得为空");
        //按执行顺序排列的阶段配置列表
        var phases = List.copyOf(phaseConfigs);
        //每个阶段对应的任务节点列表
        var selected = new ArrayList<List<TaskNodeHolder<?, ?>>>();
        var seen = EnumSet.noneOf(ProcessPhase.class);

        for (var phase : phases) {
            if (!seen.add(phase.phase())) throw new IllegalArgumentException("阶段不得重复：" + phase.phase());
            selected.add(registry.nodes(group, phase.phase()));
        }
        //校验通过之后领取本次上下文的执行权限，防止同一个上下文被复用
        var execution = context.beginExecution();
        //收集每个阶段的执行报告
        var reports = new ArrayList<PhaseExecutionResult>();
        //标志变量，标志是否已经出现阻断性错误
        Throwable fatal = null;
        //依次执行每个阶段
        for (int i = 0; i < phases.size(); i++) {
            //没有阻断错误就继续执行
            if (fatal == null) {
                var result = phaseEngine.execute(phases.get(i), selected.get(i), execution);
                reports.add(result);//记录报告
                if (!result.canContinue()) fatal = result.cause();//阶段无法继续时及时记录原因
            }//已经出现阻断错误，后续阶段不执行，只记录
            else {
                var skipped = selected.get(i).stream().map(node -> PhaseExecutionEngine.report(node,
                        NodeExecutionStatus.NOT_EXECUTED, null)).toList();
                reports.add(new PhaseExecutionResult(phases.get(i).phase(), false, null, skipped));
            }
        }
        execution.finish(fatal == null, fatal, reports);
        return context;
    }
}
