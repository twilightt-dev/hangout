package com.dianping.shared.taskchain.annotation;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import java.lang.annotation.*;

/** 节点还需由接入方声明为 Spring Bean；领域契约不依赖 Spring。 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskNode {
    String id();
    String group();
    ProcessPhase phase();
    int order() default 0;
    TaskType taskType();
    boolean required() default true;
}
