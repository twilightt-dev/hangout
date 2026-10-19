package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.time.Duration;
import java.util.Objects;

/**
 * 一个阶段的执行方式与共享超时预算，构造时拒绝无效配置。
 *
 * @param phase 节点所属的处理阶段
 * @param mode 阶段内串行或并行执行方式
 * @param timeout 整个阶段共享的超时预算
 */
public record PhaseConfig(ProcessPhase phase, ExecutionMode mode, Duration timeout) {
    /**
     * 创建并校验PhaseConfig，拒绝不满足业务约束的数据。
     *
     * @param phase 节点所属的处理阶段
     * @param mode 阶段内串行或并行执行方式
     * @param timeout 整个阶段共享的超时预算
     * @throws IllegalArgumentException 阶段预算不是正数或超出纳秒计时范围
     * @throws NullPointerException 阶段、执行方式或预算为空
     */
    public PhaseConfig {
        Objects.requireNonNull(phase, "phase 不得为空");
        Objects.requireNonNull(mode, "mode 不得为空");
        Objects.requireNonNull(timeout, "timeout 不得为空");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("阶段预算必须为正数");
        try { timeout.toNanos(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("阶段预算超出纳秒计时范围", error); }
    }
}
