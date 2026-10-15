package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import java.util.Objects;

public record TaskNodeHolder<I, O>(String id, String group, ProcessPhase phase, int order,
                                   TaskType taskType, boolean required, TaskNodeHandler<I, O> handler) {
    public TaskNodeHolder {
        if (id == null || id.isBlank() || group == null || group.isBlank()) {
            throw new IllegalArgumentException("节点 id 和 group 不得为空白");
        }
        Objects.requireNonNull(phase, "phase 不得为空");
        Objects.requireNonNull(taskType, "taskType 不得为空");
        Objects.requireNonNull(handler, "handler 不得为空");
    }
}
