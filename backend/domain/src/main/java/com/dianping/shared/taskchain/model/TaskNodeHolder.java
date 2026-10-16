package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import java.util.Objects;

/** 节点处理器与元数据的绑定；名称用于展示，描述用于说明。 */
public record TaskNodeHolder<I, O>(String nodeId, String nodeName, String description,
                                   String group, ProcessPhase phase, int order,
                                   TaskType taskType, boolean required, TaskNodeHandler<I, O> handler) {
    /** 手动注册时可省略展示元数据。 */
    public TaskNodeHolder(String nodeId, String group, ProcessPhase phase, int order,
                          TaskType taskType, boolean required, TaskNodeHandler<I, O> handler) {
        this(nodeId, "", "", group, phase, order, taskType, required, handler);
    }

    public TaskNodeHolder {
        if (nodeId == null || nodeId.isBlank() || group == null || group.isBlank()) {
            throw new IllegalArgumentException("节点 nodeId 和 group 不得为空白");
        }
        nodeName = nodeName == null || nodeName.isBlank() ? nodeId : nodeName;
        description = Objects.requireNonNullElse(description, "");
        Objects.requireNonNull(phase, "phase 不得为空");
        Objects.requireNonNull(taskType, "taskType 不得为空");
        Objects.requireNonNull(handler, "handler 不得为空");
    }
}
