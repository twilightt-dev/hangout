package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.util.List;

/**
 * 当前业务的数据传输模型。
 *
 * @param phase 节点所属的处理阶段
 * @param canContinue 本阶段结束后是否允许继续后续阶段
 * @param cause 失败原因
 * @param nodeResults 本阶段各节点的有序诊断报告
 */
public record PhaseExecutionResult(ProcessPhase phase, boolean canContinue,
                                   Throwable cause, List<NodeExecutionResult> nodeResults) {
    /**
     * 创建 PhaseExecutionResult，初始化所需依赖及实例状态。
     *
     * @param phase 节点所属的处理阶段
     * @param canContinue 本阶段结束后是否允许继续后续阶段
     * @param cause 失败原因
     * @param nodeResults 本阶段各节点的有序诊断报告
     */
    public PhaseExecutionResult {
        nodeResults = List.copyOf(nodeResults);
    }
}
