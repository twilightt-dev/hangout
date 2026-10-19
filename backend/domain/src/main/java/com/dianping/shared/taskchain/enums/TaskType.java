package com.dianping.shared.taskchain.enums;

/**
 * 节点任务类型，用于 CPU 与 IO 线程池路由。
 */
public enum TaskType {
    /**
     * CPU 密集任务，使用 CPU 线程池。
     */
    CPU_BOUND,
    /**
     * IO 密集任务，使用 IO 线程池。
     */
    IO_BOUND
}
