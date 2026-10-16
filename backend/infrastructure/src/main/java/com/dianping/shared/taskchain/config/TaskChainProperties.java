package com.dianping.shared.taskchain.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "taskchain")
public class TaskChainProperties {
    //线程数
    private int cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
    private int ioThreads = Math.max(4, 2 * Runtime.getRuntime().availableProcessors());
    //队列数量
    private int queueCapacity = 128;
    private Duration defaultPhaseTimeout = Duration.ofMillis(3000);
    //阶段执行的超时时间
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    //参数校验
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
