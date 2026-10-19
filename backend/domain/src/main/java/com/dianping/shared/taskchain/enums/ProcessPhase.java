package com.dianping.shared.taskchain.enums;

/**
 * 任务链支持的固定处理阶段，调用方只配置所需阶段。
 */
public enum ProcessPhase {
    /**
     * 业务执行前的校验阶段。
     */
    PRE_VALIDATION,
    /**
     * 正式处理前的准备阶段。
     */
    PRE_PROCESSING,
    /**
     * 业务事实读取与处理阶段。
     */
    DATA_PROCESSING,
    /**
     * 需要持久化写入的业务阶段。
     */
    DATA_INSERTION,
    /**
     * 组装与衍生信息生成阶段。
     */
    POST_PROCESSING
}
