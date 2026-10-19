package com.dianping.shared.taskchain.model;

/**
 * 业务节点的执行契约，业务结果经上下文写集提交。
 *
 * @param <I> 链路输入类型
 * @param <O> 链路输出类型
 */
@FunctionalInterface
public interface TaskNodeHandler<I, O> {
    /**
     * 执行节点业务，读取可见上下文并通过属性或输出写集提交结果。
     *
     * @param context 当前调用独立使用的任务上下文
     * @throws Exception 节点业务执行失败，由引擎记录并决定是否阻断链路
     */
    void execute(TaskContext<I, O> context) throws Exception;
}
