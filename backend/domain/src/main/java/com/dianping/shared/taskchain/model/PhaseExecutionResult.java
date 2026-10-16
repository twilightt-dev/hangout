package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.util.List;

public record PhaseExecutionResult(ProcessPhase phase, boolean canContinue,
                                   Throwable cause, List<NodeExecutionResult> nodeResults) {
    public PhaseExecutionResult {
        nodeResults = List.copyOf(nodeResults);
    }
}
