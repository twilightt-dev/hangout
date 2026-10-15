package com.dianping.shared.taskchain.config;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 两个有界池共享一次关闭预算，拒绝时绝不在协调线程运行节点。 */
public final class TaskChainExecutors implements AutoCloseable {
    private final ThreadPoolExecutor cpuExecutor;
    private final ThreadPoolExecutor ioExecutor;
    private final Duration shutdownTimeout;
    private final AtomicBoolean closed = new AtomicBoolean();

    public TaskChainExecutors(TaskChainProperties properties) {
        properties.validate();
        shutdownTimeout = properties.getShutdownTimeout();
        cpuExecutor = create(properties.getCpuThreads(), properties.getQueueCapacity(), "taskchain-cpu-");
        ioExecutor = create(properties.getIoThreads(), properties.getQueueCapacity(), "taskchain-io-");
    }

    public ThreadPoolExecutor cpuExecutor() { return cpuExecutor; }
    public ThreadPoolExecutor ioExecutor() { return ioExecutor; }

    private static ThreadPoolExecutor create(int threads, int capacity, String prefix) {
        ThreadFactory factory = Thread.ofPlatform().name(prefix, 1)
                .inheritInheritableThreadLocals(false).daemon(false).factory();
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        long start = System.nanoTime();
        long budget = shutdownTimeout.toNanos();
        cpuExecutor.shutdown();
        ioExecutor.shutdown();
        boolean interrupted = false;
        try {
            await(cpuExecutor, budget - (System.nanoTime() - start));
            await(ioExecutor, budget - (System.nanoTime() - start));
        } catch (InterruptedException error) {
            interrupted = true;
        } finally {
            if (!cpuExecutor.isTerminated()) cpuExecutor.shutdownNow();
            if (!ioExecutor.isTerminated()) ioExecutor.shutdownNow();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void await(ThreadPoolExecutor executor, long remaining) throws InterruptedException {
        if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }
}
