package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;

/** ABANDONED 仅表示不再接受该结果，不保证底层任务已经停止。 */
public record NodeExecutionResult(String nodeId, String group, ProcessPhase phase,
                                  boolean required, NodeExecutionStatus status, Throwable cause) { }
