package com.dianping.shared.taskchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "taskchain")
public class TaskChainProperties {
    private int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private int ioThreads = Math.max(4, 2 * Runtime.getRuntime().availableProcessors());
    private int queueCapacity = 128;
    private Duration defaultPhaseTimeout = Duration.ofMillis(3000);
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    public int getCpuThreads() { return cpuThreads; }
    public void setCpuThreads(int cpuThreads) { this.cpuThreads = cpuThreads; }
    public int getIoThreads() { return ioThreads; }
    public void setIoThreads(int ioThreads) { this.ioThreads = ioThreads; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public Duration getDefaultPhaseTimeout() { return defaultPhaseTimeout; }
    public void setDefaultPhaseTimeout(Duration defaultPhaseTimeout) { this.defaultPhaseTimeout = defaultPhaseTimeout; }
    public Duration getShutdownTimeout() { return shutdownTimeout; }
    public void setShutdownTimeout(Duration shutdownTimeout) { this.shutdownTimeout = shutdownTimeout; }

    public void validate() {
        if (cpuThreads <= 0 || ioThreads <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("任务链线程数与队列容量必须为正数");
        }
        validateDuration(defaultPhaseTimeout, "默认阶段预算");
        validateDuration(shutdownTimeout, "关闭等待预算");
    }

    private static void validateDuration(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        try { duration.toNanos(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(name + "超出纳秒计时范围", error); }
    }
}
