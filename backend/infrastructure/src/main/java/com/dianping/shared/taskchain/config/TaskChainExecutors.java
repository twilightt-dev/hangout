package com.dianping.shared.taskchain.config;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 两个有界池共享一次关闭预算，拒绝时绝不在协调线程运行节点。
 */
public final class TaskChainExecutors implements AutoCloseable {

    /**
     * CPU 密集任务执行器。
     */
    private final ThreadPoolExecutor cpuExecutor;
    /**
     * IO 密集任务执行器。
     */
    private final ThreadPoolExecutor ioExecutor;
    //应用关闭时，等待线程池中已有任务完成的最长等待时间，超时后对线程池调用shutDownNow尝试中断
    /**
     * 两个线程池共享的关闭等待预算。
     */
    private final Duration shutdownTimeout;
    /**
     * 关闭操作是否已启动的原子标记。
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 TaskChainExecutors，初始化所需依赖及实例状态。
     *
     * @param properties 任务链线程池与超时配置
     */
    public TaskChainExecutors(TaskChainProperties properties) {
        properties.validate();
        shutdownTimeout = properties.getShutdownTimeout();
        cpuExecutor = create(properties.getCpuThreads(), properties.getQueueCapacity(), "taskchain-cpu-");
        ioExecutor = create(properties.getIoThreads(), properties.getQueueCapacity(), "taskchain-io-");
    }

    /**
     * 取得供 CPU 密集节点使用的有界线程池。
     *
     * @return CPU 密集任务专用的有界线程池
     */
    public ThreadPoolExecutor cpuExecutor() { return cpuExecutor; }
    /**
     * 取得供 IO 密集节点使用的有界线程池。
     *
     * @return IO 密集任务专用的有界线程池
     */
    public ThreadPoolExecutor ioExecutor() { return ioExecutor; }


    /**
     * 创建固定大小的有界线程池，禁止继承调用线程上下文，并使用直接拒绝策略。
     *
     * @param threads 固定工作线程数量
     * @param capacity 有界队列容量
     * @param prefix 工作线程名称前缀
     * @return 使用有界队列、命名工作线程及拒绝策略的固定线程池
     */
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

    /**
     * 共享一次关闭预算等待两个线程池退出，超时后尽力取消，并恢复调用线程的中断标记。
     */
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

    /**
     * 仅在剩余预算为正时等待指定线程池终止。
     *
     * @param executor 需要等待结束的线程池
     * @param remaining 剩余等待时间，单位为纳秒
     * @throws InterruptedException 关闭等待期间当前线程被中断
     */
    private static void await(ThreadPoolExecutor executor, long remaining) throws InterruptedException {
        if (remaining > 0) executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }
}
