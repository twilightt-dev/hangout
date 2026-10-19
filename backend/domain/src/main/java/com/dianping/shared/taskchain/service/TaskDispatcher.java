package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.model.TaskContext;
import com.dianping.shared.taskchain.model.TaskNodeHandler;
import com.dianping.shared.taskchain.model.TaskNodeHolder;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;


/**
 * 节点投递器，隔离节点上下文并按 CPU 或 IO 类型路由线程池，通过完成通知交回写集。
 */
public final class TaskDispatcher {
    /**
     * CPU 密集任务执行器。
     */
    private final Executor cpuExecutor;
    /**
     * IO 密集任务执行器。
     */
    private final Executor ioExecutor;

    /**
     * 创建 TaskDispatcher，初始化所需依赖及实例状态。
     *
     * @param cpuExecutor CPU 密集任务执行器
     * @param ioExecutor IO 密集任务执行器
     */
    public TaskDispatcher(Executor cpuExecutor, Executor ioExecutor) {
        this.cpuExecutor = Objects.requireNonNull(cpuExecutor, "CPU 执行器不得为空");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "IO 执行器不得为空");
    }

    /**
     * 核心方法dispatch，如何路由任务节点
     *  @param index 节点在当前阶段列表中的下标
     *  @param node 任务节点
     *  @param execution 本次任务链的执行权限对象
     *  @param snapshot 这个节点允许的数据快照
     *  @param completed 调用方传入一个能接收{@code Outcome<O>}类型参数的规则，这里就是PhaseExecutionEngine传入的{@code completionQueue::add}
     *  @return  future，作为任务句柄，封装任务结果
     *  @param <I> 泛型  入参
     *  @param <O> 泛型  出参
     */
    @SuppressWarnings("unchecked")
    <I, O> Future<?> dispatch(int index, TaskNodeHolder<?, ?> node, TaskContext.Execution<I, O> execution,
                             TaskContext.Snapshot<O> snapshot, Consumer<Outcome<O>> completed) {
        //给节点创建独立的节点上下文
        TaskContext<I, O> local;
        try {
            local = execution.nodeContext(snapshot);
        } catch (RuntimeException error) {
            //上下文创建失败就转化成节点失败通知
            completed.accept(new Outcome<>(index, new TaskContext.WriteSet<>(java.util.Map.of(),
                    java.util.Set.of(), false, null, error), error, System.nanoTime()));
            return CompletableFuture.completedFuture(null);
        }
        //开始定义任务
        var task = new FutureTask<Void>(() -> {
            Throwable failure = null;
            try { ((TaskNodeHandler<I, O>) node.handler()).execute(local); }
            //节点兼容两种失败表达方式，一种是throw的异常，一种是context.fail(cause)在写集里记录异常
            catch (Throwable error) {
                failure = error;
            }
            //成功就封存节点上下文，提供写集
            var writes = execution.finishNode(local);
            if (failure == null) failure = writes.failure();
            //把结果写入完成通知队列
            completed.accept(new Outcome<>(index, writes, failure, System.nanoTime()));
            return null;
        });
        //根据任务类型路由到对应的线程池
        try { (node.taskType() == TaskType.CPU_BOUND ? cpuExecutor : ioExecutor).execute(task); }
        catch (RejectedExecutionException error) {
            task.cancel(false);
            completed.accept(new Outcome<>(index, execution.finishNode(local), error, System.nanoTime()));
        }
        return task;
    }

    /**
     * 记录节点的执行结果
     *  @param index 节点在当前阶段列表中的下标
     *  @param writes 节点产生的局部修改
     *  @param failure 节点的失败原因
     *  @param completedAt 节点完成时的单调时钟纳秒值
     *  @param <O> 泛型信息 出参
     */
    record Outcome<O>(int index, TaskContext.WriteSet<O> writes, Throwable failure, long completedAt) { }
}
