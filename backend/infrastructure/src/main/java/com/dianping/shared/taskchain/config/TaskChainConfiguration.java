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

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaskChainProperties.class)
public class TaskChainConfiguration {

    //创建线程池管理对象
    //复习一下，注册为bean就是把这个方法返回的对象交给Spring管理
    @Bean(destroyMethod = "close")//规定销毁bean时调用它自己定义的close方法
    public TaskChainExecutors taskChainExecutors(TaskChainProperties properties) {
        return new TaskChainExecutors(properties);
    }

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

    private static <I, O> void register(TaskNodeRegistry registry, TaskNodeHandler<I, O> handler, TaskNode metadata) {
        registry.register(new TaskNodeHolder<>(metadata.nodeId(), metadata.nodeName(), metadata.description(),
                metadata.group(), metadata.phase(), metadata.order(),
                metadata.taskType(), metadata.required(), handler));
    }

    @Bean
    public TaskChainEngine taskChainEngine(TaskNodeRegistry registry, TaskChainExecutors executors,
                                           TaskChainProperties properties) {
        return new TaskChainEngine(registry, executors.cpuExecutor(), executors.ioExecutor(), properties.getDefaultPhaseTimeout());
    }
}
