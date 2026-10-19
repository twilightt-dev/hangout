package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.model.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * 任务链入口，按调用方配置的顺序推进阶段，并封存链路结果及执行报告。
 * <ul>
 *   <li>{@link #executeChain(String, TaskContext)}：按默认五阶段串行执行。</li>
 *   <li>{@link #executePhaseChain(String, TaskContext, List)}：自定义阶段顺序与阶段内执行方式。</li>
 * </ul>
 */
public final class TaskChainEngine {
    /**
     * 按任务组与阶段组织的节点注册表。
     */
    private final TaskNodeRegistry registry;
    /**
     * 负责单阶段节点执行与结果合并的引擎。
     */
    private final PhaseExecutionEngine phaseEngine;
    /**
     * 五个固定阶段的默认串行配置。
     */
    private final List<PhaseConfig> defaultPhases;

    /**
     * 创建 TaskChainEngine，初始化所需依赖及实例状态。
     *
     * @param registry 按任务组与阶段组织的节点注册表
     * @param cpuExecutor CPU 密集任务执行器
     * @param ioExecutor IO 密集任务执行器
     * @param defaultPhaseTimeout 默认单阶段执行预算
     */
    public TaskChainEngine(TaskNodeRegistry registry, Executor cpuExecutor,
                           Executor ioExecutor, Duration defaultPhaseTimeout) {
        this.registry = Objects.requireNonNull(registry, "注册表不得为空");
        this.phaseEngine = new PhaseExecutionEngine(new TaskDispatcher(cpuExecutor, ioExecutor));
        this.defaultPhases = Arrays.stream(ProcessPhase.values())
                .map(phase -> new PhaseConfig(phase, ExecutionMode.SERIAL, defaultPhaseTimeout)).toList();
    }

    /**
     * 按默认五阶段串行配置执行任务组，保留输入上下文的实际子类型。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     * @param <C> 实际业务上下文类型
     * @param group 任务组标识
     * @param context 当前调用独立使用的任务上下文
     * @return 已封存执行状态与报告的原始业务上下文
     * @throws IllegalArgumentException 任务组、阶段配置或节点选择不合法
     * @throws IllegalStateException 注册表尚未冻结，或上下文已被使用
     */
    public <I, O, C extends TaskContext<I, O>> C executeChain(String group, C context) {
        return executePhaseChain(group, context, defaultPhases);
    }

    /**
     * 按调用方给定的阶段顺序执行任务组，并将失败及诊断信息封存到上下文。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     * @param <C> 实际业务上下文类型
     * @param group 任务组标识
     * @param context 当前调用独立使用的任务上下文
     * @param phaseConfigs 按执行顺序排列且阶段不重复的配置
     * @return 已封存执行状态与报告的原始业务上下文
     * @throws IllegalArgumentException 任务组、阶段配置或节点选择不合法
     * @throws IllegalStateException 注册表尚未冻结，或上下文已被使用
     */
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
