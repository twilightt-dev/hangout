package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 任务节点注册表，启动时按任务组校验唯一性并排序冻结，运行时按阶段只读查询。
 */
public final class TaskNodeRegistry {
    /**
     * 按任务组组织的节点集合。
     */
    private final Map<String, List<TaskNodeHolder<?, ?>>> groups = new HashMap<>();
    //是否已经结束注册，进入只读使用状态
    /**
     * 注册表是否已排序并进入只读状态。
     */
    private boolean frozen;

    /**
     * 校验并注册节点；同组标识必须唯一，冻结后不再允许注册。
     *
     * @param holder 待注册的处理器及节点元数据
     * @throws IllegalStateException 注册表已经冻结
     * @throws IllegalArgumentException 同组节点标识重复
     */
    public synchronized void register(TaskNodeHolder<?, ?> holder) {
        if (frozen) throw new IllegalStateException("节点注册表已经冻结");

        Objects.requireNonNull(holder, "节点不得为空");

        var nodes = groups.computeIfAbsent(holder.group(), ignored -> new ArrayList<>());

        if (nodes.stream().anyMatch(node -> node.nodeId().equals(holder.nodeId()))) {
            throw new IllegalArgumentException("任务组内节点 nodeId 重复：" + holder.group() + "/" + holder.nodeId());
        }
        nodes.add(holder);
    }

    //排序并结束注册,先按order排，再按nodeId排
    /**
     * 按排序值和节点标识排序，并将注册表切换为只读状态。
     */
    public synchronized void freeze() {
        groups.replaceAll((group, nodes) -> nodes.stream()
                .sorted(Comparator.<TaskNodeHolder<?, ?>>comparingInt(TaskNodeHolder::order)
                        .thenComparing(TaskNodeHolder::nodeId)).toList());
        frozen = true;
    }

    /**
     * 检查节点注册表是否已经冻结。
     *
     * @return 注册表是否已冻结
     */
    public synchronized boolean isFrozen() {
        return frozen;
    }

    //按组和阶段取出节点
    /**
     * 按任务组和阶段取得排序后的节点列表。
     *
     * @param group 任务组标识
     * @param phase 节点所属的处理阶段
     * @return 指定任务组和阶段的只读有序节点列表
     */
    public synchronized List<TaskNodeHolder<?, ?>> nodes(String group, ProcessPhase phase) {
        if (!frozen) throw new IllegalStateException("执行前必须冻结节点注册表");

        Objects.requireNonNull(phase, "阶段不得为空");

        var nodes = groups.get(group);
        if (nodes == null) throw new IllegalArgumentException("未知任务组：" + group);
        return nodes.stream().filter(node -> node.phase() == phase).toList();
    }
}
