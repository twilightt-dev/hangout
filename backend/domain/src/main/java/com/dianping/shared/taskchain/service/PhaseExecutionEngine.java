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
 * <p>
 * 负责阶段内部的节点编排，根据 {@link PhaseConfig} 阶段配置控制执行方式和阶段总超时：
 * </p>
 * <ul>
 *    <li>串行执行：按节点顺序逐个执行，后续节点可以读取前面成功节点的结果</li>
 *    <li>并行执行：各节点读取阶段开始时的同一快照，通过进程内完成通知队列收集结果</li>
 *    <li>失败处理：必需节点失败后尽快结束等待；可选节点失败仅保留诊断，丢弃其局部写入</li>
 *    <li>超时控制：所有节点共享阶段总预算；到期时若仍有必需节点未完成，则阶段失败，仅剩可选节点时允许继续</li>
 *    <li>结果合并：检查成功并行节点的同名属性（包括删除）和 output 写入冲突，阶段允许继续时才统一提交结果</li>
 *    <li>提前结束：尽力取消未完成任务，不再接受迟到结果；取消不保证底层任务立即停止</li>
 * </ul>
 * <p>
 * 节点投递由 {@link TaskDispatcher} 负责，阶段结果通过 {@link PhaseExecutionResult} 返回给 {@link TaskChainEngine}。
 * 阶段失败仅丢弃本阶段的上下文写入，不回滚前序阶段结果或节点产生的外部副作用。
 * </p>
 */
public final class PhaseExecutionEngine {
    private final TaskDispatcher dispatcher;
    public PhaseExecutionEngine(TaskDispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "调度器不得为空");
    }

    public <I, O> PhaseExecutionResult execute(PhaseConfig config, List<TaskNodeHolder<?, ?>> nodes,
                                              TaskContext.Execution<I, O> execution) {
        return new StageRun<>(config, List.copyOf(nodes), execution).run();
    }

    private final class StageRun<I, O> {
        private final PhaseConfig config;
        private final List<TaskNodeHolder<?, ?>> nodes;
        private final TaskContext.Execution<I, O> execution;
        private final TaskContext.Snapshot<O> base;
        private TaskContext.Snapshot<O> working;
        private final long started = System.nanoTime();
        private final long budget;
        //完成通知队列
        private final BlockingQueue<TaskDispatcher.Outcome<O>> completed = new LinkedBlockingQueue<>();
        private final List<Future<?>> futures = new ArrayList<>();
        private final NodeExecutionResult[] reports;
        private final Set<String> touched = new HashSet<>();
        private boolean outputWritten;
        private int finished;
        private Throwable fatal;
        private boolean expired;

        StageRun(PhaseConfig config, List<TaskNodeHolder<?, ?>> nodes, TaskContext.Execution<I, O> execution) {
            this.config = config;
            this.nodes = nodes;
            this.execution = execution;
            this.base = execution.snapshot();
            this.working = base;
            this.budget = config.timeout().toNanos();
            this.reports = new NodeExecutionResult[nodes.size()];
        }

        PhaseExecutionResult run() {
            try {
                for (int i = 0; i < nodes.size() && fatal == null; i++) {
                    drainAvailable();
                    if (fatal != null) break;
                    if (remaining() <= 0) { expire(); break; }
                    futures.add(dispatcher.dispatch(i, nodes.get(i), execution,
                            config.mode() == ExecutionMode.PARALLEL ? base : working, completed::add));
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

        private long remaining() { return budget - (System.nanoTime() - started); }

        private void awaitCompletion() throws InterruptedException {
            long left = remaining();
            if (left <= 0) { expire(); return; }
            var outcome = completed.poll(left, TimeUnit.NANOSECONDS);
            if (outcome == null) expire();
            else {
                accept(outcome);
                if (outcome.completedAt() - started > budget && fatal == null) expire();
            }
        }

        private void drainAvailable() {
            TaskDispatcher.Outcome<O> outcome;
            while (fatal == null && (outcome = completed.poll()) != null) accept(outcome);
        }

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

    static NodeExecutionResult report(TaskNodeHolder<?, ?> node, NodeExecutionStatus status, Throwable error) {
        return new NodeExecutionResult(node.nodeId(), node.nodeName(), node.group(), node.phase(), node.required(), status, error);
    }
}
