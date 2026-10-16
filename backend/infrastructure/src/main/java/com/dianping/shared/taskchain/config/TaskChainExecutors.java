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
    //应用关闭时，等待线程池中已有任务完成的最长等待时间，超时后对线程池调用shutDownNow尝试中断
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
        //配置工作线程的参数
        ThreadFactory factory = Thread.ofPlatform()
                .name(prefix, 1)
                .inheritInheritableThreadLocals(false)
                .daemon(false)
                .factory();
        return new ThreadPoolExecutor(
                threads, //核心线程数
                threads, //最大线程数
                0,//空闲存活时间
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),//有界等待队列，限制挤压量，防止任务无限增长
                factory,
                new ThreadPoolExecutor.AbortPolicy()//拒绝策略，在线程池无法接收业务时直接抛出异常
        );
    }

    @Override
    public void close() {
        //防止重复修改
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
            //两个池共享等待时间
            if (!cpuExecutor.isTerminated()) cpuExecutor.shutdownNow();
            if (!ioExecutor.isTerminated()) ioExecutor.shutdownNow();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void await(ThreadPoolExecutor executor, long remaining) throws InterruptedException {
        if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }
}
