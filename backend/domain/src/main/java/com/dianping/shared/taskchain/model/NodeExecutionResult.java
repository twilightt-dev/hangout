package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.NodeExecutionStatus;
import com.dianping.shared.taskchain.enums.ProcessPhase;


public record NodeExecutionResult(String nodeId, String nodeName, String group, ProcessPhase phase,
                                  boolean required, NodeExecutionStatus status, Throwable cause) { }
