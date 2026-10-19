package com.dianping.shared.taskchain.config;

import com.dianping.shared.taskchain.annotation.TaskNode;
import com.dianping.shared.taskchain.model.TaskNodeHandler;
import com.dianping.shared.taskchain.model.TaskNodeHolder;
import com.dianping.shared.taskchain.model.TaskNodeRegistry;
import com.dianping.shared.taskchain.service.TaskChainEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import java.util.List;

/**
 * 共享任务链的 Spring 装配入口，创建线程池、冻结注册表并提供引擎。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskChainProperties.class)
public class TaskChainConfiguration {

    //创建线程池管理对象
    //复习一下，注册为bean就是把这个方法返回的对象交给Spring管理
    /**
     * 创建由 Spring 管理并在容器关闭时释放的双线程池。
     *
     * @param properties 任务链线程池与超时配置
     * @return 由 Spring 管理生命周期的双线程池对象
     */
    @Bean(destroyMethod = "close")//规定销毁bean时调用它自己定义的close方法
    public TaskChainExecutors taskChainExecutors(TaskChainProperties properties) {
        return new TaskChainExecutors(properties);
    }

    /**
     * 收集 Spring 任务处理器，读取目标类注解并注册排序后冻结。
     *
     * @param handlers Spring 发现的全部任务处理器
     * @return 完成节点校验、排序并冻结的注册表
     */
    @Bean//创建注册表                //这里生命的是一个集合，因此Spring会收集容器中匹配TaskNodeHandler类型的bean
    public TaskNodeRegistry taskNodeRegistry(List<TaskNodeHandler<?, ?>> handlers) {
        var registry = new TaskNodeRegistry();
        for (var handler : handlers) {
            //从每个节点上读取元数据
            var metadata = AnnotatedElementUtils.findMergedAnnotation(AopUtils.getTargetClass(handler), TaskNode.class);
            if (metadata == null) throw new IllegalArgumentException("任务处理器缺少 @TaskNode：" + handler.getClass().getName());
            register(registry, handler, metadata);
        }
        registry.freeze();
        return registry;
    }

    /**
     * 校验并注册节点；同组标识必须唯一，冻结后不再允许注册。
     *
     * @param <I> 链路输入类型
     * @param <O> 链路输出类型
     * @param registry 按任务组与阶段组织的节点注册表
     * @param handler 实际执行节点业务的处理器
     * @param metadata 节点注解元数据
     */
    private static <I, O> void register(TaskNodeRegistry registry, TaskNodeHandler<I, O> handler, TaskNode metadata) {
        registry.register(new TaskNodeHolder<>(metadata.nodeId(), metadata.nodeName(), metadata.description(),
                metadata.group(), metadata.phase(), metadata.order(),
                metadata.taskType(), metadata.required(), handler));
    }

    /**
     * 装配共享任务链引擎与默认阶段预算。
     *
     * @param registry 按任务组与阶段组织的节点注册表
     * @param executors CPU 与 IO 线程池管理对象
     * @param properties 任务链线程池与超时配置
     * @return 具备默认五阶段串行配置的任务链引擎
     */
    @Bean
    public TaskChainEngine taskChainEngine(TaskNodeRegistry registry, TaskChainExecutors executors,
                                           TaskChainProperties properties) {
        return new TaskChainEngine(registry, executors.cpuExecutor(), executors.ioExecutor(), properties.getDefaultPhaseTimeout());
    }
}
