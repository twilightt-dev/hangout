package com.dianping.shared.taskchain.model;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskNodeRegistry {
    private final Map<String, List<TaskNodeHolder<?, ?>>> groups = new HashMap<>();
    //是否已经结束注册，进入只读使用状态
    private boolean frozen;

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
    public synchronized void freeze() {
        groups.replaceAll((group, nodes) -> nodes.stream()
                .sorted(Comparator.<TaskNodeHolder<?, ?>>comparingInt(TaskNodeHolder::order)
                        .thenComparing(TaskNodeHolder::nodeId)).toList());
        frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    //按组和阶段取出节点
    public synchronized List<TaskNodeHolder<?, ?>> nodes(String group, ProcessPhase phase) {
        if (!frozen) throw new IllegalStateException("执行前必须冻结节点注册表");

        Objects.requireNonNull(phase, "阶段不得为空");

        var nodes = groups.get(group);
        if (nodes == null) throw new IllegalArgumentException("未知任务组：" + group);
        return nodes.stream().filter(node -> node.phase() == phase).toList();
    }
}
