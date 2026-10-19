package com.dianping.shared.taskchain.enums;

/**
 * 阶段内部节点的执行方式。
 */
public enum ExecutionMode {
    /**
     * 阶段内逐个执行，后续节点可读取已接受的前序结果。
     */
    SERIAL,
    /**
     * 阶段内并行执行，各节点读取同一初始快照。
     */
    PARALLEL }
