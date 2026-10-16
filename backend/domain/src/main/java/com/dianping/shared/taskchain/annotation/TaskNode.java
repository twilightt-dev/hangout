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
    /** 节点标识，同一任务组内唯一。 */
    String nodeId();

    /** 面向阅读的节点名称，留空时使用 nodeId。 */
    String nodeName() default "";

    /** 节点职责、输入要求与产出说明，不参与执行决策。 */
    String description() default "";

    /** 所属任务组。 */
    String group();
    /** 所属执行阶段，需要显式指定。 */
    ProcessPhase phase();
    /** 阶段内的排序值；并行模式下不表示节点之间存在依赖。 */
    int order() default 0;
    /** 决定使用 CPU 还是 IO 工作线程池，需要显式指定。 */
    TaskType taskType();
    /** 必需节点执行失败会阻断链路，可选节点失败仅记录诊断。 */
    boolean required() default true;
}
