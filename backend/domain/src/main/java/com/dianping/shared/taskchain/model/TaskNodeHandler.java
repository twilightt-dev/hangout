package com.dianping.shared.taskchain.model;

@FunctionalInterface
public interface TaskNodeHandler<I, O> {
    void execute(TaskContext<I, O> context) throws Exception;
}
