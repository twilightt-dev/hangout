package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;


/**
 * 当前业务的数据传输模型。
 *
 * @param nodeId 任务组内唯一的节点标识
 * @param nodeName 节点展示名称；空白时回退为节点标识
 * @param group 任务组标识
 * @param phase 节点所属的处理阶段
 * @param required 是否为失败后阻断链路的必需节点
 * @param status 业务或节点状态
 * @param cause 失败原因
 */
public record NodeExecutionResult(String nodeId, String nodeName, String group, ProcessPhase phase,
                                  boolean required, NodeExecutionStatus status, Throwable cause) { }
