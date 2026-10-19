package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import java.util.Objects;

/**
 * 节点处理器与元数据的绑定；名称用于展示，描述用于说明。
 *
 * @param <I> 链路输入类型
 * @param <O> 链路输出类型
 * @param nodeId 任务组内唯一的节点标识
 * @param nodeName 节点展示名称；空白时回退为节点标识
 * @param description 说明文本
 * @param group 任务组标识
 * @param phase 节点所属的处理阶段
 * @param order 阶段内排序值；数值越小越先执行
 * @param taskType 任务类型，决定投递到 CPU 池还是 IO 池
 * @param required 是否为失败后阻断链路的必需节点
 * @param handler 实际执行节点业务的处理器
 */
public record TaskNodeHolder<I, O>(String nodeId, String nodeName, String description,
                                   String group, ProcessPhase phase, int order,
                                   TaskType taskType, boolean required, TaskNodeHandler<I, O> handler) {
    /**
     * 手动注册时可省略展示元数据。
     *
     * @param nodeId 任务组内唯一的节点标识
     * @param group 任务组标识
     * @param phase 节点所属的处理阶段
     * @param order 阶段内排序值；数值越小越先执行
     * @param taskType 任务类型，决定投递到 CPU 池还是 IO 池
     * @param required 是否为失败后阻断链路的必需节点
     * @param handler 实际执行节点业务的处理器
     */
    public TaskNodeHolder(String nodeId, String group, ProcessPhase phase, int order,
                          TaskType taskType, boolean required, TaskNodeHandler<I, O> handler) {
        this(nodeId, "", "", group, phase, order, taskType, required, handler);
    }

    /**
     * 创建并校验TaskNodeHolder，拒绝不满足业务约束的数据。
     *
     * @param nodeId 任务组内唯一的节点标识
     * @param nodeName 节点展示名称；空白时回退为节点标识
     * @param description 说明文本
     * @param group 任务组标识
     * @param phase 节点所属的处理阶段
     * @param order 阶段内排序值；数值越小越先执行
     * @param taskType 任务类型，决定投递到 CPU 池还是 IO 池
     * @param required 是否为失败后阻断链路的必需节点
     * @param handler 实际执行节点业务的处理器
     */
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
