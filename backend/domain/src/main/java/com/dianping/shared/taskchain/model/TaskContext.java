package com.dianping.shared.taskchain.model;

import java.util.*;

/**
 * 每次链路调用独立创建；节点仅通过上下文提交结果。
 *  引擎拥有的属性映射与报告集合为只读快照；输入、输出及属性值保留实际类型并遵守业务只读协议。
 *
 * @param <I> 链路输入类型
 * @param <O> 链路输出类型
 */
public class TaskContext<I, O> {
    /**
     * 链路输入；节点共享引用并按只读约定访问。
     */
    private final I input;
    //true表示节点上下文，false表示主上下文
    /**
     * 是否为节点视图。
     */
    private final boolean nodeView;
    //主上下文是否已经被领取执行，防止重复启动
    /**
     * 根上下文是否已领取执行权限，用于禁止复用。
     */
    private boolean claimed;
    //上下文是否已经被封存，封存后禁止写入
    /**
     * 上下文是否封存；封存后拒绝写入。
     */
    private boolean sealed;
    //节点工厂不得将同一个子上下文分配给多个任务
    /**
     * 节点视图是否已经交付给处理器，防止重复分配。
     */
    private boolean issued;
    //身份标记，判断一个子上下文是否属于当前主上下文
    /**
     * 同一次链路执行共享的身份标记。
     */
    private final Object owner;
    /**
     * 节点可见或准备提交的数据快照。
     */
    private Snapshot<O> snapshot;
    //预置数据
    /**
     * 尚未提交的属性写入或节点写集。
     */
    private final Map<String, Object> writes = new LinkedHashMap<>();
    /**
     * 本次写集要求删除的属性名。
     */
    private final Set<String> removals = new LinkedHashSet<>();
    /**
     * 当前节点或当前阶段是否显式写入输出。
     */
    private boolean outputWritten;
    /**
     * 尚未提交的节点输出。
     */
    private O localOutput;


    /**
     * 整条链路是否成功。
     */
    private boolean success;
    /**
     * 导致失败的异常；没有异常时为 {@code null}。
     */
    private Throwable error;
    /**
     * 链路结束后公布的阶段诊断报告。
     */
    private List<PhaseExecutionResult> phaseResults = List.of();

    /**
     * 创建本次请求专用的根上下文，初始快照为空。
     *
     * @param input 链路输入；节点共享引用并按只读约定访问
     */
    public TaskContext(I input) {
        this(input, new Snapshot<>(Map.of(), null, false), false, new Object());
    }

    /**
     * 子类创建节点上下文时调用；沿用本次请求身份与输入，使用引擎提供的可见快照。
     *
     * @param parent 创建当前节点视图的父上下文
     * @param snapshot 节点可见或准备提交的数据快照
     */
    protected TaskContext(TaskContext<I, O> parent, Snapshot<O> snapshot) {
        this(Objects.requireNonNull(parent, "父上下文不得为空").input,
                Objects.requireNonNull(snapshot, "节点快照不得为空"), true, parent.owner);
    }

    /**
     * 子类覆盖此工厂，返回同类型的新节点上下文；只负责创建对象，不执行查询等业务操作。
     *  业务属性 getter/setter 应委托 getAttribute/setAttribute，不能另存一份可变执行结果。
     *
     * @param snapshot 节点可见或准备提交的数据快照
     * @return 与根上下文具有相同业务类型的全新节点视图
     */
    protected TaskContext<I, O> createNodeContext(Snapshot<O> snapshot) {
        return new TaskContext<>(this, snapshot);
    }

    /**
     * 初始化输入、可见快照及节点身份，供根上下文和节点视图共用。
     *
     * @param input 链路输入；节点共享引用并按只读约定访问
     * @param snapshot 节点可见或准备提交的数据快照
     * @param nodeView 是否为节点视图
     * @param owner 同一次链路执行共享的身份标记
     */
    private TaskContext(I input, Snapshot<O> snapshot, boolean nodeView, Object owner) {
        this.input = input;
        this.snapshot = snapshot;
        this.nodeView = nodeView;
        this.owner = owner;
    }

    /**
     * 读取链路输入；返回的业务对象按只读约定使用。
     *
     * @return 本次链路的原始输入引用
     */
    public final I getInput() {
        return input;
    }
    /**
     * 读取当前快照与本地写集合成后的属性。
     *
     * @param key 上下文属性名或区域名称
     * @return 当前视图可见的业务值；尚未提供时为 {@code null}
     */
    public final synchronized Object getAttribute(String key) {
        return getAttributes().get(key);
    }
    /**
     * 生成当前视图的只读属性容器；不深复制其中的业务对象。
     *
     * @return 叠加当前写集后的只读属性映射
     */
    public final synchronized Map<String, Object> getAttributes() {
        var result = new LinkedHashMap<>(snapshot.attributes());
        removals.forEach(result::remove);
        result.putAll(writes);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 将属性写入当前视图的本地写集；不会直接修改其他节点的可见快照。
     *
     * @param key 上下文属性名或区域名称
     * @param value 待读取或转换的值
     * @throws IllegalStateException 当前视图已封存，或根上下文已领取执行权限
     */
    public final synchronized void setAttribute(String key, Object value) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.put(key, value);
        removals.remove(key);
    }
    /**
     * 记录属性删除，并清除该属性尚未提交的本地写入。
     *
     * @param key 上下文属性名或区域名称
     * @throws IllegalStateException 当前视图已封存，或根上下文已领取执行权限
     */
    public final synchronized void removeAttribute(String key) {
        ensureWritable();
        Objects.requireNonNull(key, "属性 key 不得为空");
        writes.remove(key);
        removals.add(key);
    }
    /**
     * 显式写入当前视图的输出槽位，包括写入空值。
     *
     * @param output 业务输出；允许显式写入 {@code null}
     * @throws IllegalStateException 当前视图已封存，或根上下文已领取执行权限
     */
    public final synchronized void setOutput(O output) {
        ensureWritable();
        localOutput = output;
        outputWritten = true;
    }
    /**
     * 读取当前视图可见的输出，优先使用本地显式写入值。
     *
     * @return 当前视图的业务输出；未设置或显式设空时均可能为 null，二者通过 hasOutput 区分
     */
    public final synchronized O getOutput() { return outputWritten ? localOutput : snapshot.output(); }
    /**
     * 判断输出是否曾被显式设置，区分未设置与显式设置为空。
     *
     * @return 输出是否曾被显式设置
     */
    public final synchronized boolean hasOutput() { return outputWritten || snapshot.outputSet(); }
    /**
     * 读取引擎封存的整条链路执行状态。
     *
     * @return 链路是否成功
     */
    public final synchronized boolean isSuccess() { return success; }
    /**
     * 读取使链路终止的首个阻断性异常。
     *
     * @return 首个阻断异常；没有异常时为 {@code null}
     */
    public final synchronized Throwable getError() { return error; }
    /**
     * 读取引擎封存的阶段执行报告。
     *
     * @return 按阶段执行顺序保存的只读报告列表
     */
    public final synchronized List<PhaseExecutionResult> getPhaseResults() { return phaseResults; }
    /**
     * 按阶段顺序展开全部节点诊断报告。
     *
     * @return 按阶段顺序展开的只读节点报告列表
     */
    public final synchronized List<NodeExecutionResult> getNodeResults() {
        return phaseResults.stream().flatMap(phase -> phase.nodeResults().stream()).toList();
    }

    //只有节点上下文可以标记自己失败，必需节点失败导致链路失败，可选节点失败保留诊断信息
    /**
     * 标记当前节点自身失败；必需或可选节点的影响由引擎统一判断。
     *
     * @param cause 失败原因
     * @throws IllegalStateException 根上下文试图标记节点失败，或当前节点已经封存
     */
    public final synchronized void fail(Throwable cause) {
        ensureWritable();
        if (!nodeView) throw new IllegalStateException("仅节点可以标记自身失败");
        if (error == null) error = Objects.requireNonNull(cause, "失败原因不得为空");
    }

    //确认上下文是否允许修改
    /**
     * 检查上下文是否允许写入，封存后的视图与已启动的根上下文均拒绝外部修改。
     *
     * @throws IllegalStateException 当前视图已封存，或根上下文已领取执行权限
     */
    private void ensureWritable() {
        //1.任何上下文被封存后都不得修改
        //2.主上下文开始执行后不允许修改，只能由引擎处理，没开始执行时调用方可以预置数据
        if (sealed || (!nodeView && claimed)) throw new IllegalStateException("上下文已进入执行或完成状态");
    }

    /**
     * 只有根上下文可领取一次协调权限；节点拿不到此权限。
     *
     * @return 只交给引擎使用的一次性执行协调权限
     * @throws IllegalStateException 上下文为节点视图，或根上下文已经开始过执行
     */
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
     *  @param outputSet 标记是否显式设置过输出
     *
     * @param <O> 链路输出类型
     * @param attributes 只读属性映射；属性值遵守业务只读约定
     * @param output 业务输出；允许显式写入 {@code null}
     */
    public record Snapshot<O>(Map<String, Object> attributes, O output, boolean outputSet) {
        /**
         * 复制容器建立只读视图，业务对象本身仍按只读约定访问。
         *
         * @param attributes 只读属性映射；属性值遵守业务只读约定
         * @param output 业务输出；允许显式写入 {@code null}
         * @param outputSet 快照是否曾显式设置输出
         */
        public Snapshot {
            //创建一个只读的map，禁止调用put,remove等方法修改，但是如果val是可变数据类型就还能修改，所以统一约定遵守业务对象的只读规则
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        }
        //apply方法接收一个增量修改然后创建新的快照
        /**
         * 应用节点写集生成新快照，先删除属性再合并写入。
         *
         * @param delta 需要应用到旧快照上的节点写集
         * @return 合并删除、属性写入与输出更新后的新快照，原快照保持不变
         */
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
     *  @param attributes 属性
     *  @param removals 要删除的key
     *  @param outputWritten 标记，该节点是否显式设置过output
     *
     * @param <O> 链路输出类型
     * @param output 业务输出；允许显式写入 {@code null}
     * @param failure 节点失败原因；成功时为 {@code null}
     */
    public record WriteSet<O>(Map<String, Object> attributes, Set<String> removals,
                              boolean outputWritten, O output, Throwable failure) {
        /**
         * 复制容器建立只读视图，业务对象本身仍按只读约定访问。
         *
         * @param attributes 本节点待提交的属性写入映射；构造时复制容器
         * @param removals 本次写集要求删除的属性名
         * @param outputWritten 当前节点或当前阶段是否显式写入输出
         * @param output 业务输出；允许显式写入 {@code null}
         * @param failure 节点失败原因；成功时为 {@code null}
         */
        public WriteSet {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            //记录要删除的键
            removals = Set.copyOf(removals);
        }
    }

    //为引擎提供操作入口，负责创建节点上下文、收集修改、提交数据、确定链路结果
    /**
     * 仅由引擎持有的协调权限，负责创建节点视图、接受写集及封存报告。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     */
    public static final class Execution<I, O> {
        //绑定主上下文
        /**
         * 本次执行绑定的根上下文。
         */
        private final TaskContext<I, O> root;
        /**
         * 将协调权限绑定到唯一根上下文，供引擎管理本次执行。
         *
         * @param root 本次执行绑定的根上下文
         */
        private Execution(TaskContext<I, O> root) { this.root = root; }

        //获取主上下文当前已提交的数据
        /**
         * 读取主上下文当前已经提交的数据快照。
         *
         * @return 主上下文当前已提交的只读容器快照
         */
        public Snapshot<O> snapshot() { synchronized (root) { return root.snapshot; } }

        //为节点创建独立子上下文
        /**
         * 创建并检查属于本次执行的全新节点上下文，拒绝跨请求引用或重复分配。
         *
         * @param snapshot 节点可见或准备提交的数据快照
         * @return 与根上下文具有相同业务类型的全新节点视图
         * @throws IllegalStateException 根上下文已结束，或节点工厂未遵守同类型全新视图约定
         */
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
        /**
         * 封存节点视图并提取写集；提取结果是否接受由阶段引擎决定。
         *
         * @param node 任务节点或本次执行创建的节点上下文
         * @return 封存后的节点增量及显式失败原因
         * @throws IllegalArgumentException 传入的视图不是本次执行所属的节点上下文
         */
        public WriteSet<O> finishNode(TaskContext<I, O> node) {
            synchronized (node) {
                if (!node.nodeView || node.owner != root.owner) throw new IllegalArgumentException("不是本次执行的节点上下文");
                node.sealed = true;
                return new WriteSet<>(node.writes, node.removals, node.outputWritten, node.localOutput, node.error);
            }
        }

        //提交阶段通过后的快照
        /**
         * 将本阶段已经接受的快照提交到根上下文。
         *
         * @param snapshot 节点可见或准备提交的数据快照
         * @throws IllegalStateException 根上下文已经结束
         */
        public void commit(Snapshot<O> snapshot) {
            synchronized (root) {
                if (root.sealed) throw new IllegalStateException("链路已经结束");
                root.snapshot = snapshot;
            }
        }

        //确认整条链路的最终状态
        /**
         * 写入最终链路状态及只读报告集合，并封存根上下文。
         *
         * @param success 整条链路是否成功
         * @param error 导致失败的异常；没有异常时为 {@code null}
         * @param reports 按阶段或节点顺序保存的执行报告
         * @throws IllegalStateException 根上下文已经结束
         */
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
