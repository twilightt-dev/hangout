package com.dianping.shared.taskchain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * 任务链的线程池容量及时间预算配置，绑定 taskchain 前缀。
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "taskchain")
public class TaskChainProperties {
    //线程数
    /**
     * CPU 密集任务线程数量。
     */
    private int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    /**
     * IO 密集任务线程数量。
     */
    private int ioThreads = Math.max(4, 2 * Runtime.getRuntime().availableProcessors());
    //队列数量
    /**
     * 每个线程池的有界等待队列容量。
     */
    private int queueCapacity = 128;
    /**
     * 默认单阶段执行预算。
     */
    private Duration defaultPhaseTimeout = Duration.ofMillis(3000);
    //两个线程池关闭时共享的等待预算
    /**
     * 两个线程池共享的关闭等待预算。
     */
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    //参数校验
    /**
     * 校验线程数、队列容量和时间预算，非法配置在启动时拒绝。
     */
    public void validate() {
        if (cpuThreads <= 0 || ioThreads <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException("任务链线程数与队列容量必须为正数");
        }
        validateDuration(defaultPhaseTimeout, "默认阶段预算");
        validateDuration(shutdownTimeout, "关闭等待预算");
    }

    /**
     * 校验正时间间隔及其向纳秒换算是否溢出。
     *
     * @param duration 需要校验的正时间间隔
     * @param name 名称
     */
    private static void validateDuration(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        try { duration.toNanos(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(name + "超出纳秒计时范围", error); }
    }
}
