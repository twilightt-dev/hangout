package com.dianping.shared.taskchain.service;

import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.model.TaskContext;
import com.dianping.shared.taskchain.model.TaskNodeHandler;
import com.dianping.shared.taskchain.model.TaskNodeHolder;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * 只负责投递和归一化单节点结果，链路是否继续由阶段协调方决定。
 * 外部执行器必须异步且不阻塞提交，不得在协调线程执行节点；拒绝时须抛出 RejectedExecutionException。
 * 直接执行器或 CallerRunsPolicy 不满足阶段预算契约。
 */
public final class TaskDispatcher {
    private final Executor cpuExecutor;
    private final Executor ioExecutor;

    public TaskDispatcher(Executor cpuExecutor, Executor ioExecutor) {
        this.cpuExecutor = Objects.requireNonNull(cpuExecutor, "CPU 执行器不得为空");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "IO 执行器不得为空");
    }

    @SuppressWarnings("unchecked")
    <I, O> Future<?> dispatch(int index, TaskNodeHolder<?, ?> node, TaskContext.Execution<I, O> execution,
                             TaskContext.Snapshot<O> snapshot, Consumer<Outcome<O>> completed) {
        var local = execution.nodeContext(snapshot);
        var task = new FutureTask<Void>(() -> {
            Throwable failure = null;
            try { ((TaskNodeHandler<I, O>) node.handler()).execute(local); }
            catch (Throwable error) { failure = error; }
            var writes = execution.finishNode(local);
            if (failure == null) failure = writes.failure();
            completed.accept(new Outcome<>(index, writes, failure, System.nanoTime()));
            return null;
        });
        try { (node.taskType() == TaskType.CPU_BOUND ? cpuExecutor : ioExecutor).execute(task); }
        catch (RejectedExecutionException error) {
            task.cancel(false);
            completed.accept(new Outcome<>(index, execution.finishNode(local), error, System.nanoTime()));
        }
        return task;
    }

    record Outcome<O>(int index, TaskContext.WriteSet<O> writes, Throwable failure, long completedAt) { }
}
