package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.time.Duration;
import java.util.Objects;

public record PhaseConfig(ProcessPhase phase, ExecutionMode mode, Duration timeout) {
    public PhaseConfig {
        Objects.requireNonNull(phase, "phase 不得为空");
        Objects.requireNonNull(mode, "mode 不得为空");
        Objects.requireNonNull(timeout, "timeout 不得为空");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("阶段预算必须为正数");
        try { timeout.toNanos(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("阶段预算超出纳秒计时范围", error); }
    }
}
