# 任务链第一版设计清单

日期：2026-10-16。状态：第一版已实现，公开行为测试、默认后端完整回归及独立规范/规格审查已通过。本页保留实现基准；配置与验证结果见[接入指南](taskchain-usage.md)。

已确认语义以 [ADR 0012](../adr/0012-共享任务链采用固定阶段与节点结果隔离.md) 为准。用户在阅读本页方案后明确要求开始实现，因此下文原先标注“建议”的工程选择作为本轮开发默认值采用；后续业务容量与接入方案仍需单独验证。

## 1. 实现范围

| 区域 | 第一版工作 |
| --- | --- |
| backend/domain | 使用 JDK 实现共享任务链契约、模型、注册表和执行内核，保持无生产依赖 |
| backend/infrastructure | Spring 节点发现与装配、CPU/IO 线程池、可配置参数及生命周期管理 |
| backend/start | 必要时补充配置示例；现有模块依赖和 com.dianping 扫描根已满足接入条件 |
| 测试 | 独立测试节点验证行为，装配测试不连接真实 MySQL 或 Redis |

首轮仅建设通用能力，不迁移商详、评论或访问统计，不修改已有 HTTP 接口。保留九模块及 service 不依赖其他项目模块的约束。任意依赖图、节点级超时、自动重试、持久化恢复、嵌套任务链与分布式事务不列入首轮能力。

## 2. 分包及职责

domain 包根为用户指定的 com.dianping.shared.taskchain。

| 包 | 类型 | 职责 |
| --- | --- | --- |
| annotation | TaskNode | 节点元数据；包含 id、group、phase、order、taskType、required |
| enums | ExecutionMode | SERIAL、PARALLEL，表示模式而非状态 |
| enums | ProcessPhase | 已确认的五个阶段 |
| enums | TaskType | CPU_BOUND、IO_BOUND，选择对应执行器 |
| model | PhaseConfig | 阶段、模式、正数的阶段总超时 |
| model | PhaseExecutionResult | 是否允许继续、阶段原因、节点执行记录 |
| model | TaskContext | 输入、已接受结果、节点局部写入和最终链路状态 |
| model | TaskNodeHandler | 业务节点实现的处理接口 |
| model | TaskNodeHolder | 处理器实例与不可变元数据的绑定 |
| model | TaskNodeRegistry | 按 group 保存、按 phase 查询并确定顺序；不主动扫描 Spring |
| service | TaskChainEngine | 按配置顺序推进，消费阶段结果并设置最终链路状态 |
| service | PhaseExecutionEngine | 阶段预算、串并行调度、失败通知、结果检查和提交 |
| service | TaskDispatcher | 选择执行器、执行节点并归一化节点结果；不自行决定整条链路是否失败 |

在 model 追加 NodeExecutionResult，在 enums 追加 NodeExecutionStatus，保存可选失败、超时和未执行节点的结构化记录。状态为 SUCCESS、FAILED、TIMED_OUT、NOT_EXECUTED、ABANDONED；ABANDONED 仅表示阶段已结束、不再接受该节点结果，不宣称底层任务已停止。这两个类型只负责状态汇报，不是新的业务处理层。

infrastructure 使用 com.dianping.shared.taskchain.config，提供 TaskChainConfiguration、TaskChainExecutors 和 TaskChainProperties。通过 Spring 获取 Handler Bean 并解析实际目标类注解，兼顾代理对象；domain 不依赖 Spring、Lombok 或日志框架。CPU/IO 执行器通过构造器注入内核。

## 3. 对外接口

保留两类按 group 执行的入口，首轮不额外提供裸 List 节点入口。

    public interface TaskNodeHandler<I, O> {
        void execute(TaskContext<I, O> context) throws Exception;
    }

    // TaskChainEngine 的公开方法签名。
    public <I, O> TaskContext<I, O> executeChain(
            String group, TaskContext<I, O> context);

    public <I, O> TaskContext<I, O> executePhaseChain(
            String group, TaskContext<I, O> context,
            List<PhaseConfig> phaseConfigs);

executeChain 按五阶段默认顺序、全部串行执行。executePhaseChain 按传入配置列表顺序执行，不再按枚举排序改变顺序。阶段结果、节点报告和链路状态由内核汇总；节点可抛异常或标记自身失败，但不能修改其他节点或整条链路的诊断集合。

调用方必须检查 context.isSuccess()。必需失败、阻断性超时、运行期结果冲突返回失败 Context；可选失败保留节点报告而不污染主错误。注册和调用参数错误在运行节点前抛出异常。链路失败时，已有 output 不能作为有效业务响应。

## 4. 数据读写协议

| 场景 | 规则 |
| --- | --- |
| 输入和既有结果 | 业务对象按只读约定访问并保留实际类型；引擎自己的属性映射、写集和报告容器只读；不声称深度冻结任意业务对象 |
| 节点本地写入 | 独立增量写集，读取可见快照及自身写入；不把整个快照当成待合并结果 |
| 并行节点 | 读取同一阶段开始时的快照，互不可见本阶段其他节点的新结果 |
| 串行节点 | 成功结果先提交到阶段工作上下文，下一个节点可读；失败节点写入丢弃 |
| 阶段提交 | 可继续的阶段统一检查并提交；失败阶段不提交本阶段局部结果；前序已完成阶段不回滚 |
| 失败或超时节点 | 丢弃全部局部写入，包括失败前已调用 setAttribute 或 setOutput 的内容 |
| 并行同名 key | 检查将被接受的成功节点写集；两个节点写或删除同一 key 都是冲突，即使值相同也不覆盖 |
| 跨阶段或串行更新 | 允许明确覆盖已有 key；与同一并行阶段争写区别处理 |
| output | 单独的结果槽位；多个成功并行节点同时设置则报冲突，串行节点可替换；区分未设置与明确设置 null |
| Context 生命周期 | 每次执行独立创建，禁止并发复用和再次执行；返回后报告固定，不接受迟到回写 |

阶段提交仅约束 Context 数据，不提供外部副作用回滚。节点也必须遵守输入只读约定，否则隔离容器无法阻止对象级别修改。

## 5. 失败与超时

下表核心语义已经确认；执行器拒绝和阶段提交方式采用本轮批准的工程约定。

| 事件 | 链路处理 | 数据处理 |
| --- | --- | --- |
| 必需节点失败 | 尽快结束等待，保留首先观察到的致命错误，不进入后续阶段 | 不提交失败阶段，拒绝迟到结果 |
| 可选节点失败 | 记录失败，继续其他工作 | 丢弃该节点写集 |
| 到期仍有必需节点未完成 | 链路失败 | 不提交本阶段 |
| 到期仅有可选节点未完成 | 记为超时，阶段仍可继续 | 检查并合并成功结果 |
| 成功结果之间 key/output 冲突 | 链路失败，与冲突节点是否可选无关 | 拒绝整个阶段合并 |
| 执行器拒绝提交 | 记为节点失败，按必需/可选规则处理 | 没有可提交结果 |

阶段开始建立统一截止时间，使用单调时钟计算剩余预算。预算覆盖提交、排队和等待；串行节点不重置预算。通过完成通知及时观察必需失败或截止时间，不先无条件等待所有节点完成再合并错误。

截止时对未及时完成的节点定格超时记录，迟到成功不能覆盖该结论。早退后尽力取消剩余任务，不把取消请求当作任务已经停止。拒绝策略不在调用线程直接执行任务，避免提交操作变成不可控的同步执行。阶段协调工作与 CPU/IO 工作线程分离。

## 6. 注册及校验默认值

| 项目 | 约定 |
| --- | --- |
| 节点 ID | 同一 group 内唯一，不同 group 可复用；空白 id/group 非法 |
| 排序 | 阶段内按 order 升序，相同 order 按 nodeId 稳定排序；并行排序不表示依赖 |
| 注解 | id/group/phase/taskType 显式填写；order 默认 0，required 默认 true |
| 注册生命周期 | 启动时注册校验，装配后冻结；无节点允许启动，执行未知 group 报错 |
| 阶段列表 | 非空、无 null、无重复阶段；超时为正；运行前整体校验 |
| 阶段选择 | 未选择阶段不执行；选中但无节点的阶段作为空阶段完成 |
| 参数 | 缺失或复用 Context 等错误在运行前报错；业务 input 的有效性由业务校验节点决定 |

## 7. 开发起步配置

以下是本轮采用的可覆盖开发默认值，不代表后续商详的性能目标。

| 参数 | 开发默认值 |
| --- | --- |
| CPU 工作线程 | max(1, availableProcessors) |
| IO 工作线程 | max(4, 2 × availableProcessors) |
| 每池队列容量 | 128，显式有界 |
| 默认阶段预算 | 3000 毫秒，可由显式 PhaseConfig 覆盖 |
| 关闭等待 | 两个池共用最多 5 秒的关闭预算，之后尽力停止 |

由 infrastructure 创建和关闭线程池，提供线程名前缀及明确拒绝策略，参数启动时校验。首轮不隐式传播调用线程的事务、登录身份或日志上下文，业务身份显式放入输入。后续业务接入时按实际调用量和下游能力调整预算及容量。

## 8. 实现顺序与验收

| 顺序 | 实现及测试重点 |
| --- | --- |
| 1 | 元数据、Handler、Context、报告及注册表：唯一性、排序和校验 |
| 2 | 调度与串行阶段：结果传递、可选失败、必需失败、共享预算 |
| 3 | 并行阶段：独立写集、快速失败、超时、冲突及阶段提交 |
| 4 | 顶层引擎：默认链与配置链、结果汇总、提前结束、上下文生命周期 |
| 5 | Spring 装配：节点发现、代理注解、有界队列、拒绝及关闭 |

重点验收：可选失败后后续节点仍执行；必需失败不等待被门闩阻塞的其他节点；仅可选节点超时可以继续；必需超时失败；串行阶段不重置预算；失败节点写入不泄漏；同名 key/output 冲突无部分提交；迟到任务不改变已接受状态；请求互不串数据；非法配置在执行前失败；拒绝按节点重要性处理；空注册可启动且不访问数据库。

并发测试用门闩、受控完成和适度超时边界证明执行关系，不只断言一次运行耗时小于过紧阈值。真实业务接入另行验证接口、下游超时、事务及容量。

源码职责与接入示例见[任务链接入与阅读指南](taskchain-usage.md)。
