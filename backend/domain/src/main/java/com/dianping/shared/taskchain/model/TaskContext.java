package com.dianping.shared.taskchain.model;

import java.util.*;

/**
 * 每次链路调用独立创建；节点仅通过上下文提交结果。
 * 引擎拥有的属性映射与报告集合为只读快照；输入、输出及属性值保留实际类型并遵守业务只读协议。
 */
public final class TaskContext<I, O> {
    private final I input;
    private final boolean nodeView;
    private final Object owner;
    private Snapshot<O> snapshot;
    private final Map<String, Object> writes = new LinkedHashMap<>();
    private final Set<String> removals = new LinkedHashSet<>();
    private boolean outputWritten;
    private O localOutput;
    private boolean claimed;
    private boolean sealed;
    private boolean success;
    private Throwable error;
    private List<PhaseExecutionResult> phaseResults = List.of();

    public TaskContext(I input) {
        this(input, new Snapshot<>(Map.of(), null, false), false, new Object());
    }

    private TaskContext(I input, Snapshot<O> snapshot, boolean nodeView, Object owner) {
        this.input = input;
        this.snapshot = snapshot;
        this.nodeView = nodeView;
        this.owner = owner;
    }

    public I getInput() { return input; }
    public synchronized Object getAttribute(String key) { return getAttributes().get(key); }
    public synchronized Map<String, Object> getAttributes() {
        var result = new LinkedHashMap<>(snapshot.attributes());
        removals.forEach(result::remove);
        result.putAll(writes);
        return Collections.unmodifiableMap(result);
    }
    public synchronized void setAttribute(String key, Object value) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.put(key, value);
        removals.remove(key);
    }
    public synchronized void removeAttribute(String key) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.remove(key);
        removals.add(key);
    }
    public synchronized void setOutput(O output) {
        ensureWritable();
        localOutput = output;
        outputWritten = true;
    }
    public synchronized O getOutput() { return outputWritten ? localOutput : snapshot.output(); }
    public synchronized boolean hasOutput() { return outputWritten || snapshot.outputSet(); }
    public synchronized boolean isSuccess() { return success; }
    public synchronized Throwable getError() { return error; }
    public synchronized List<PhaseExecutionResult> getPhaseResults() { return phaseResults; }
    public synchronized List<NodeExecutionResult> getNodeResults() {
        return phaseResults.stream().flatMap(phase -> phase.nodeResults().stream()).toList();
    }

    /** 节点只能标记自身失败，无法修改链路报告。 */
    public synchronized void fail(Throwable cause) {
        ensureWritable();
        if (!nodeView) throw new IllegalStateException("仅节点可以标记自身失败");
        if (error == null) error = Objects.requireNonNull(cause, "失败原因不得为空");
    }

    private void ensureWritable() {
        if (sealed || (!nodeView && claimed)) throw new IllegalStateException("上下文已进入执行或完成状态");
    }

    /** 只有根上下文可领取一次协调权限；节点拿不到此权限。 */
    public synchronized Execution<I, O> beginExecution() {
        if (nodeView || claimed) throw new IllegalStateException("上下文不能复用或由节点启动链路");
        claimed = true;
        snapshot = snapshot.apply(new WriteSet<>(writes, removals, outputWritten, localOutput, null));
        writes.clear();
        removals.clear();
        outputWritten = false;
        return new Execution<>(this);
    }

    public record Snapshot<O>(Map<String, Object> attributes, O output, boolean outputSet) {
        public Snapshot { attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes)); }
        public Snapshot<O> apply(WriteSet<O> delta) {
            var merged = new LinkedHashMap<>(attributes);
            delta.removals().forEach(merged::remove);
            merged.putAll(delta.attributes());
            return new Snapshot<>(merged, delta.outputWritten() ? delta.output() : output,
                    outputSet || delta.outputWritten());
        }
    }

    public record WriteSet<O>(Map<String, Object> attributes, Set<String> removals,
                              boolean outputWritten, O output, Throwable failure) {
        public WriteSet {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            removals = Set.copyOf(removals);
        }
    }

    /** 协调权限对象不传给业务节点，阶段提交与报告只由引擎维护。 */
    public static final class Execution<I, O> {
        private final TaskContext<I, O> root;
        private Execution(TaskContext<I, O> root) { this.root = root; }
        public Snapshot<O> snapshot() { synchronized (root) { return root.snapshot; } }
        public TaskContext<I, O> nodeContext(Snapshot<O> snapshot) {
            return new TaskContext<>(root.input, snapshot, true, root.owner);
        }
        public WriteSet<O> finishNode(TaskContext<I, O> node) {
            synchronized (node) {
                if (!node.nodeView || node.owner != root.owner) throw new IllegalArgumentException("不是本次执行的节点上下文");
                node.sealed = true;
                return new WriteSet<>(node.writes, node.removals, node.outputWritten, node.localOutput, node.error);
            }
        }
        public void commit(Snapshot<O> snapshot) {
            synchronized (root) {
                if (root.sealed) throw new IllegalStateException("链路已经结束");
                root.snapshot = snapshot;
            }
        }
        public void finish(boolean success, Throwable error, List<PhaseExecutionResult> reports) {
            synchronized (root) {
                if (root.sealed) throw new IllegalStateException("链路已经结束");
                root.success = success;
                root.error = error;
                root.phaseResults = List.copyOf(reports);
                root.sealed = true;
            }
        }
    }
}
