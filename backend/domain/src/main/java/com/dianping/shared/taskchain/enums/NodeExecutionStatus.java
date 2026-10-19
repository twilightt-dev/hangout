package com.dianping.shared.taskchain.enums;

/**
 * 节点执行诊断状态，不等同于页面业务数据是否为空。
 */
public enum NodeExecutionStatus {
    /**
     * 节点成功完成。
     */
    SUCCESS,
    /**
     * 节点执行失败。
     */
    FAILED,
    /**
     * 节点在阶段预算内未完成。
     */
    TIMED_OUT,
    /**
     * 节点尚未执行，链路已经停止推进。
     */
    NOT_EXECUTED,
    /**
     * 节点已投递但因链路提前结束而不再接收结果。
     */
    ABANDONED
}
