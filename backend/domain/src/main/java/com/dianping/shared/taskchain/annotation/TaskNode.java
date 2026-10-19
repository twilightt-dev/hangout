package com.dianping.shared.taskchain.annotation;

import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import java.lang.annotation.*;

/**
 * 节点还需由接入方声明为 Spring Bean；领域契约不依赖 Spring。
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskNode {
    /**
     * 节点标识，同一任务组内唯一。
     *
     * @return 任务组内唯一的节点标识
     */
    String nodeId();

    /**
     * 面向阅读的节点名称，留空时使用 nodeId。
     *
     * @return 节点展示名称；空白值由注册过程回退为标识
     */
    String nodeName() default "";

    /**
     * 节点职责、输入要求与产出说明，不参与执行决策。
     *
     * @return 节点用途说明
     */
    String description() default "";

    /**
     * 所属任务组。
     *
     * @return 节点所属任务组标识
     */
    String group();
    /**
     * 所属执行阶段，需要显式指定。
     *
     * @return 节点所属处理阶段
     */
    ProcessPhase phase();
    /**
     * 阶段内的排序值；并行模式下不表示节点之间存在依赖。
     *
     * @return 排序值；数值越小越先执行
     */
    int order() default 0;
    /**
     * 决定使用 CPU 还是 IO 工作线程池，需要显式指定。
     *
     * @return 决定工作线程池的 CPU 或 IO 任务类型
     */
    TaskType taskType();
    /**
     * 必需节点执行失败会阻断链路，可选节点失败仅记录诊断。
     *
     * @return 该业务判断是否成立
     */
    boolean required() default true;
}
