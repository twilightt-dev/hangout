package com.dianping.shared.taskchain.model;

import java.util.*;

/**
 * 每次链路调用独立创建；节点仅通过上下文提交结果。
 * 引擎拥有的属性映射与报告集合为只读快照；输入、输出及属性值保留实际类型并遵守业务只读协议。
 */
public class TaskContext<I, O> {
    private final I input;
    //true表示节点上下文，false表示主上下文
    private final boolean nodeView;
    //主上下文是否已经被领取执行，防止重复启动
    private boolean claimed;
    //上下文是否已经被封存，封存后禁止写入
    private boolean sealed;
    //节点工厂不得将同一个子上下文分配给多个任务
    private boolean issued;
    //身份标记，判断一个子上下文是否属于当前主上下文
    private final Object owner;
    private Snapshot<O> snapshot;
    //预置数据
    private final Map<String, Object> writes = new LinkedHashMap<>();
    private final Set<String> removals = new LinkedHashSet<>();
    private boolean outputWritten;
    private O localOutput;


    private boolean success;
    private Throwable error;
    private List<PhaseExecutionResult> phaseResults = List.of();

    public TaskContext(I input) {
        this(input, new Snapshot<>(Map.of(), null, false), false, new Object());
    }

    /** 子类创建节点上下文时调用；沿用本次请求身份与输入，使用引擎提供的可见快照。 */
    protected TaskContext(TaskContext<I, O> parent, Snapshot<O> snapshot) {
        this(Objects.requireNonNull(parent, "父上下文不得为空").input,
                Objects.requireNonNull(snapshot, "节点快照不得为空"), true, parent.owner);
    }

    /**
     * 子类覆盖此工厂，返回同类型的新节点上下文；只负责创建对象，不执行查询等业务操作。
     * 业务属性 getter/setter 应委托 getAttribute/setAttribute，不能另存一份可变执行结果。
     */
    protected TaskContext<I, O> createNodeContext(Snapshot<O> snapshot) {
        return new TaskContext<>(this, snapshot);
    }

    private TaskContext(I input, Snapshot<O> snapshot, boolean nodeView, Object owner) {
        this.input = input;
        this.snapshot = snapshot;
        this.nodeView = nodeView;
        this.owner = owner;
    }

    public final I getInput() {
        return input;
    }
    public final synchronized Object getAttribute(String key) {
        return getAttributes().get(key);
    }
    public final synchronized Map<String, Object> getAttributes() {
        var result = new LinkedHashMap<>(snapshot.attributes());
        removals.forEach(result::remove);
        result.putAll(writes);
        return Collections.unmodifiableMap(result);
    }

    public final synchronized void setAttribute(String key, Object value) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.put(key, value);
        removals.remove(key);
    }
    public final synchronized void removeAttribute(String key) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.remove(key);
        removals.add(key);
    }
    public final synchronized void setOutput(O output) {
        ensureWritable();
        localOutput = output;
        outputWritten = true;
    }
    public final synchronized O getOutput() { return outputWritten ? localOutput : snapshot.output(); }
    public final synchronized boolean hasOutput() { return outputWritten || snapshot.outputSet(); }
    public final synchronized boolean isSuccess() { return success; }
    public final synchronized Throwable getError() { return error; }
    public final synchronized List<PhaseExecutionResult> getPhaseResults() { return phaseResults; }
    public final synchronized List<NodeExecutionResult> getNodeResults() {
        return phaseResults.stream().flatMap(phase -> phase.nodeResults().stream()).toList();
    }

    //只有节点上下文可以标记自己失败，必需节点失败导致链路失败，可选节点失败保留诊断信息
    public final synchronized void fail(Throwable cause) {
        ensureWritable();
        if (!nodeView) throw new IllegalStateException("仅节点可以标记自身失败");
        if (error == null) error = Objects.requireNonNull(cause, "失败原因不得为空");
    }

    //确认上下文是否允许修改
    private void ensureWritable() {
        //1.任何上下文被封存后都不得修改
        //2.主上下文开始执行后不允许修改，只能由引擎处理，没开始执行时调用方可以预置数据
        if (sealed || (!nodeView && claimed)) throw new IllegalStateException("上下文已进入执行或完成状态");
    }

    /** 只有根上下文可领取一次协调权限；节点拿不到此权限。 */
    public final synchronized Execution<I, O> beginExecution() {
        if (nodeView || claimed) {
            throw new IllegalStateException("上下文不能复用或由节点启动链路");
        }
        claimed = true;
        //用预置数据建立快照
        snapshot = snapshot.apply(new WriteSet<>(writes, removals, outputWritten, localOutput, null));
        writes.clear();
        removals.clear();
        outputWritten = false;
        return new Execution<>(this);
    }

    /**
     * 任务链在某个时刻已经积累的数据快照
     * @param attributes
     * @param output
     * @param outputSet 标记是否显式设置过输出
     * @param <O>
     */
    public record Snapshot<O>(Map<String, Object> attributes, O output, boolean outputSet) {
        public Snapshot {
            //创建一个只读的map，禁止调用put,remove等方法修改，但是如果val是可变数据类型就还能修改，所以统一约定遵守业务对象的只读规则
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
        //apply方法接收一个增量修改然后创建新的快照
        public Snapshot<O> apply(WriteSet<O> delta) {
            var merged = new LinkedHashMap<>(attributes);
            //应用删除
            delta.removals().forEach(merged::remove);
            //应用写入
            merged.putAll(delta.attributes());
            //生成新快照
            return new Snapshot<>(merged, delta.outputWritten() ? delta.output() : output,
                    outputSet || delta.outputWritten());
        }
    }
    /**
     * 保存节点做了哪些修改
     * @param attributes 属性
     * @param removals 要删除的key
     * @param outputWritten 标记，该节点是否显式设置过output
     * @param output
     * @param failure
     * @param <O>
     */
    public record WriteSet<O>(Map<String, Object> attributes, Set<String> removals,
                              boolean outputWritten, O output, Throwable failure) {
        public WriteSet {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            //记录要删除的键
            removals = Set.copyOf(removals);
        }
    }

    //为引擎提供操作入口，负责创建节点上下文、收集修改、提交数据、确定链路结果
    public static final class Execution<I, O> {
        //绑定主上下文
        private final TaskContext<I, O> root;
        private Execution(TaskContext<I, O> root) { this.root = root; }

        //获取主上下文当前已提交的数据
        public Snapshot<O> snapshot() { synchronized (root) { return root.snapshot; } }

        //为节点创建独立子上下文
        public TaskContext<I, O> nodeContext(Snapshot<O> snapshot) {
            synchronized (root) {
                if (root.sealed) throw new IllegalStateException("链路已经结束");
                var node = root.createNodeContext(Objects.requireNonNull(snapshot, "节点快照不得为空"));
                if (node == null || node.getClass() != root.getClass()) {
                    throw new IllegalStateException("节点上下文必须与主上下文类型一致，请在子类覆盖 createNodeContext");
                }
                synchronized (node) {
                    if (!node.nodeView || node.owner != root.owner || node.input != root.input
                            || node.snapshot != snapshot || node.issued || node.claimed || node.sealed
                            || !node.writes.isEmpty() || !node.removals.isEmpty() || node.outputWritten || node.error != null) {
                        throw new IllegalStateException("节点工厂必须通过父上下文与当前快照创建本次执行的全新节点上下文");
                    }
                    node.issued = true;
                }
                return node;
            }
        }

        //收集节点结果
        public WriteSet<O> finishNode(TaskContext<I, O> node) {
            synchronized (node) {
                if (!node.nodeView || node.owner != root.owner) throw new IllegalArgumentException("不是本次执行的节点上下文");
                node.sealed = true;
                return new WriteSet<>(node.writes, node.removals, node.outputWritten, node.localOutput, node.error);
            }
        }

        //提交阶段通过后的快照
        public void commit(Snapshot<O> snapshot) {
            synchronized (root) {
                if (root.sealed) throw new IllegalStateException("链路已经结束");
                root.snapshot = snapshot;
            }
        }

        //确认整条链路的最终状态
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
