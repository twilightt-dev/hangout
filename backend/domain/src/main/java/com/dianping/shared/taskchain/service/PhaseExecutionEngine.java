package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.model.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 阶段执行引擎
 *  <p>
 *  负责阶段内部的节点编排，根据 {@link PhaseConfig} 阶段配置控制执行方式和阶段总超时：
 *  </p>
 *  <ul>
 *     <li>串行执行：按节点顺序逐个执行，后续节点可以读取前面成功节点的结果</li>
 *     <li>并行执行：各节点读取阶段开始时的同一快照，通过进程内完成通知队列收集结果</li>
 *     <li>失败处理：必需节点失败后尽快结束等待；可选节点失败仅保留诊断，丢弃其局部写入</li>
 *     <li>超时控制：所有节点共享阶段总预算；到期时若仍有必需节点未完成，则阶段失败，仅剩可选节点时允许继续</li>
 *     <li>结果合并：检查成功并行节点的同名属性（包括删除）和 output 写入冲突，阶段允许继续时才统一提交结果</li>
 *     <li>提前结束：尽力取消未完成任务，不再接受迟到结果；取消不保证底层任务立即停止</li>
 *  </ul>
 *  <p>
 *  节点投递由 {@link TaskDispatcher} 负责，阶段结果通过 {@link PhaseExecutionResult} 返回给 {@link TaskChainEngine}。
 *  阶段失败仅丢弃本阶段的上下文写入，不回滚前序阶段结果或节点产生的外部副作用。
 *  </p>
 */
public final class PhaseExecutionEngine {
    /**
     * 任务节点投递器。
     */
    private final TaskDispatcher dispatcher;
    /**
     * 创建 PhaseExecutionEngine，初始化所需依赖及实例状态。
     *
     * @param dispatcher 任务节点投递器
     */
    public PhaseExecutionEngine(TaskDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "调度器不得为空");
    }

    /**
     * 执行一个阶段；节点共享阶段总预算，成功结果在阶段收尾时统一决定是否提交。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     * @param config 当前阶段配置
     * @param nodes 本阶段按顺序排列的任务节点
     * @param execution 引擎持有的本次链路协调权限
     * @return 当前阶段能否继续及各节点诊断报告
     */
    public <I, O> PhaseExecutionResult execute(PhaseConfig config, List<TaskNodeHolder<?, ?>> nodes,
                                              TaskContext.Execution<I, O> execution) {
        return new StageRun<>(config, List.copyOf(nodes), execution).run();
    }

    /**
     * 单次阶段执行状态，集中维护共享预算、完成队列、合并冲突与节点报告。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     */
    private final class StageRun<I, O> {
        /**
         * 当前阶段配置。
         */
        private final PhaseConfig config;
        /**
         * 本阶段按顺序排列的任务节点。
         */
        private final List<TaskNodeHolder<?, ?>> nodes;
        /**
         * 引擎持有的本次链路协调权限。
         */
        private final TaskContext.Execution<I, O> execution;
        /**
         * 阶段起始快照或本次查询的店铺基本资料。
         */
        private final TaskContext.Snapshot<O> base;
        /**
         * 累积已接受写集后的待提交快照。
         */
        private TaskContext.Snapshot<O> working;
        /**
         * 阶段开始时的单调时钟纳秒值。
         */
        private final long started = System.nanoTime();
        /**
         * 本阶段允许消耗的总纳秒数。
         */
        private final long budget;
        //完成通知队列
        /**
         * 工作线程向协调线程发送完成通知的队列。
         */
        private final BlockingQueue<TaskDispatcher.Outcome<O>> completionQueue = new LinkedBlockingQueue<>();
        /**
         * 已投递任务的句柄，用于尽力取消。
         */
        private final List<Future<?>> futures = new ArrayList<>();
        /**
         * 按阶段或节点顺序保存的执行报告。
         */
        private final NodeExecutionResult[] reports;
        /**
         * 并行阶段成功节点已写入或删除的属性名，用于检测冲突。
         */
        private final Set<String> touched = new HashSet<>();
        /**
         * 当前节点或当前阶段是否显式写入输出。
         */
        private boolean outputWritten;
        /**
         * 已接收完成通知的节点数量。
         */
        private int finished;
        /**
         * 当前首先观察到的阻断性错误。
         */
        private Throwable fatal;
        /**
         * 阶段总预算是否已经耗尽。
         */
        private boolean expired;

        /**
         * 创建 StageRun，初始化所需依赖及实例状态。
         *
         * @param config 当前阶段配置
         * @param nodes 本阶段按顺序排列的任务节点
         * @param execution 引擎持有的本次链路协调权限
         */
        StageRun(PhaseConfig config, List<TaskNodeHolder<?, ?>> nodes, TaskContext.Execution<I, O> execution) {
            this.config = config;
            this.nodes = nodes;
            this.execution = execution;
            this.base = execution.snapshot();
            this.working = base;
            this.budget = config.timeout().toNanos();
            this.reports = new NodeExecutionResult[nodes.size()];
        }

        /**
         * 执行一次阶段运行，收集节点通知，决定是否提交结果并生成完整报告。
         *
         * @return 当前阶段能否继续及各节点诊断报告
         */
        PhaseExecutionResult run() {
            try {
                for (int i = 0; i < nodes.size() && fatal == null; i++) {
                    drainAvailable();
                    if (fatal != null) break;
                    if (remaining() <= 0) { expire(); break; }
                    futures.add(dispatcher.dispatch(i, nodes.get(i), execution,
                            config.mode() == ExecutionMode.PARALLEL ? base : working, completionQueue::add));
                    if (config.mode() == ExecutionMode.SERIAL) {
                        while (reports[i] == null && fatal == null && !expired) awaitCompletion();
                    }
                    if (expired) break;
                }
                while (finished < nodes.size() && fatal == null && !expired) awaitCompletion();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fatal = error;
            }
            for (int i = 0; i < nodes.size(); i++) {
                if (reports[i] == null) reports[i] = report(nodes.get(i),
                        i < futures.size() ? NodeExecutionStatus.ABANDONED : NodeExecutionStatus.NOT_EXECUTED, null);
            }
            if (fatal != null || expired) futures.forEach(future -> future.cancel(true));
            if (fatal == null) execution.commit(working);
            return new PhaseExecutionResult(config.phase(), fatal == null, fatal, Arrays.asList(reports));
        }

        /**
         * 计算共享阶段总预算的剩余纳秒数。
         *
         * @return 剩余纳秒数；非正数表示阶段预算耗尽
         */
        private long remaining() { return budget - (System.nanoTime() - started); }

        /**
         * 在剩余阶段预算内等待并接收一个完成通知。
         *
         * @throws InterruptedException 协调线程等待节点完成时被中断
         */
        private void awaitCompletion() throws InterruptedException {
            long left = remaining();
            if (left <= 0) { expire(); return; }
            var outcome = completionQueue.poll(left, TimeUnit.NANOSECONDS);
            if (outcome == null) expire();
            else {
                accept(outcome);
                if (outcome.completedAt() - started > budget && fatal == null) expire();
            }
        }

        /**
         * 立即接收已经到达的完成通知，不额外等待。
         */
        private void drainAvailable() {
            TaskDispatcher.Outcome<O> outcome;
            while (fatal == null && (outcome = completionQueue.poll()) != null) accept(outcome);
        }

        /**
         * 接收预算内的节点结果，处理必需失败及并行写入冲突。
         *
         * @param outcome 待接收的节点完成通知
         */
        private void accept(TaskDispatcher.Outcome<O> outcome) {
            if (reports[outcome.index()] != null || outcome.completedAt() - started > budget) return;
            var node = nodes.get(outcome.index());
            reports[outcome.index()] = report(node, outcome.failure() == null
                    ? NodeExecutionStatus.SUCCESS : NodeExecutionStatus.FAILED, outcome.failure());
            finished++;
            if (outcome.failure() != null) {
                if (node.required()) fatal = outcome.failure();
                return;
            }
            if (config.mode() == ExecutionMode.PARALLEL) {
                var keys = new HashSet<>(outcome.writes().attributes().keySet());
                keys.addAll(outcome.writes().removals());
                if (keys.stream().anyMatch(touched::contains) || (outputWritten && outcome.writes().outputWritten())) {
                    fatal = new IllegalStateException("并行节点结果存在同名属性或 output 冲突：" + node.nodeId());
                    return;
                }
                touched.addAll(keys);
                outputWritten |= outcome.writes().outputWritten();
            }
            working = working.apply(outcome.writes());
        }

        /**
         * 结束阶段等待，保留截止前完成的结果并标记未完成节点超时。
         */
        private void expire() {
            // 截止前已投递的完成通知仍可接受，完成时间超过预算的结果永不回写。
            drainAvailable();
            if (fatal != null) return;
            expired = true;
            for (int i = 0; i < nodes.size(); i++) {
                if (reports[i] != null) continue;
                var timeout = new TimeoutException("阶段预算耗尽，节点未及时完成：" + nodes.get(i).nodeId());
                reports[i] = report(nodes.get(i), NodeExecutionStatus.TIMED_OUT, timeout);
                if (nodes.get(i).required() && fatal == null) fatal = timeout;
            }
        }
    }

    /**
     * 将节点元数据、执行状态与异常组合为诊断报告。
     *
     * @param node 任务节点或本次执行创建的节点上下文
     * @param status 业务或节点状态
     * @param error 导致失败的异常；没有异常时为 {@code null}
     * @return 包含节点标识、必需性、执行状态及失败原因的诊断报告
     */
    static NodeExecutionResult report(TaskNodeHolder<?, ?> node, NodeExecutionStatus status, Throwable error) {
        return new NodeExecutionResult(node.nodeId(), node.nodeName(), node.group(), node.phase(), node.required(), status, error);
    }
}
