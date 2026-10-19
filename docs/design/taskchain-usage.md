# 任务链接入与阅读指南

本指南对应第一版共享任务链。实现规格见[设计清单](taskchain-v1.md)，核心取舍见[ADR 0012](../adr/0012-共享任务链采用固定阶段与节点结果隔离.md)。本轮只实现共享能力，示例不等于已经接入真实商详接口。

## 1. 按职责阅读源码

包根统一为 `com.dianping.shared.taskchain`。

| 所在模块 | 类型 | 负责什么 | 阅读时关注什么 |
| --- | --- | --- | --- |
| domain | annotation.TaskNode | 声明 nodeId、nodeName、description、组、阶段、执行类型与重要性 | 元数据本身不依赖 Spring，也不会自动成为 Bean |
| domain | model.TaskNodeHandler / TaskNodeHolder | 节点处理契约、处理器与元数据的绑定 | Handler 做一个业务步骤；Holder 供引擎执行 |
| domain | model.TaskNodeRegistry | 注册、唯一性、分组、排序与冻结 | 不在 domain 内扫描 Spring |
| domain | model.TaskContext | 输入、节点局部写集、已接受结果与最终状态 | 并行节点拥有独立容器，结果对象仍遵守只读约定 |
| domain | model.PhaseConfig / PhaseExecutionResult | 阶段策略与阶段诊断 | 执行模式和执行结果是不同概念 |
| domain | model.NodeExecutionResult | 节点诊断 | 可选失败仍可追溯，不能只看整条链是否成功 |
| domain | service.TaskChainEngine | 按配置推进阶段，返回整条链的结果 | 默认五阶段全部串行；显式配置按列表顺序执行 |
| domain | service.PhaseExecutionEngine | 阶段预算、串并行、快速失败、冲突检查与提交 | 节点成功不代表失败阶段的数据也会被接受 |
| domain | service.TaskDispatcher | 选择 CPU/IO 执行器，执行节点并产生结果 | 不替业务决定可选失败是否应阻断链路 |
| infrastructure | config 包 | Spring 发现、装配、参数与线程池生命周期 | 启动时校验注册；使用有界队列和明确拒绝策略 |

后续商详接入时，application 层定义用例的任务组、任务节点和阶段配置，并调用领域能力获取业务结果。领域服务仍负责业务规则；任务链内核负责通用执行机制。不要把商详查询、价格计算等业务规则写进共享引擎。

## 2. 不依赖 Spring 的完整示例

下面是 Java 21 的独立示例，使用本模块真实公开 API。示例的固定线程池只用于演示；实际应用使用 infrastructure 提供的有界线程池。

手动构造引擎时，注入的执行器需要异步执行节点，提交不能阻塞协调线程，拒绝时抛出 RejectedExecutionException。不能使用直接执行的执行器或 CallerRunsPolicy，否则节点会阻塞阶段协调线程，超时与快速失败的前提不成立。第一版不支持在工作节点里嵌套调用任务链。

```java
package example;

import com.dianping.shared.taskchain.enums.ExecutionMode;
import com.dianping.shared.taskchain.enums.ProcessPhase;
import com.dianping.shared.taskchain.enums.TaskType;
import com.dianping.shared.taskchain.model.PhaseConfig;
import com.dianping.shared.taskchain.model.TaskContext;
import com.dianping.shared.taskchain.model.TaskNodeHolder;
import com.dianping.shared.taskchain.model.TaskNodeRegistry;
import com.dianping.shared.taskchain.service.TaskChainEngine;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

public final class TaskChainExample {
    public record DetailInput(long shopId) {}
    public record DetailView(String name, String promotion) {}

    public static void main(String[] args) {
        var registry = new TaskNodeRegistry();
        registry.register(new TaskNodeHolder<DetailInput, DetailView>(
                "base", "detail-example", ProcessPhase.DATA_PROCESSING, 10,
                TaskType.IO_BOUND, true, context ->
                    context.setAttribute("name", "店铺-" + context.getInput().shopId())));
        registry.register(new TaskNodeHolder<DetailInput, DetailView>(
                "promotion", "detail-example", ProcessPhase.DATA_PROCESSING, 20,
                TaskType.IO_BOUND, false, context -> {
                    // 模拟可选数据源失败：后续组装仍可执行。
                    throw new IllegalStateException("优惠信息暂不可用");
                }));
        registry.register(new TaskNodeHolder<DetailInput, DetailView>(
                "assemble", "detail-example", ProcessPhase.POST_PROCESSING, 10,
                TaskType.CPU_BOUND, true, context -> context.setOutput(new DetailView(
                    (String) context.getAttribute("name"),
                    (String) context.getAttribute("promotion")))));
        registry.freeze();

        try (var cpu = Executors.newFixedThreadPool(1);
             var io = Executors.newFixedThreadPool(2)) {
            var engine = new TaskChainEngine(registry, cpu, io, Duration.ofSeconds(3));
            var context = new TaskContext<DetailInput, DetailView>(new DetailInput(7));
            var result = engine.executePhaseChain("detail-example", context, List.of(
                    new PhaseConfig(ProcessPhase.DATA_PROCESSING,
                            ExecutionMode.PARALLEL, Duration.ofSeconds(1)),
                    new PhaseConfig(ProcessPhase.POST_PROCESSING,
                            ExecutionMode.SERIAL, Duration.ofSeconds(1))));

            // 不能用 output 非空判断成功，前序阶段结果可能仍被保留。
            if (!result.isSuccess()) {
                throw new IllegalStateException("任务链执行失败", result.getError());
            }
            System.out.println(result.getOutput());
            System.out.println(result.getNodeResults());
        }
    }
}
```

预期输出业务结果为 `DetailView[name=店铺-7, promotion=null]`；节点报告仍保留 promotion 节点的失败。这里的名称是演示数据，不会查询或修改现有商详数据。

## 3. Spring 接入

启动模块现有的 `com.dianping` 扫描根和 infrastructure 依赖已覆盖配置类。`TaskChainConfiguration` 负责创建注册表与引擎，`TaskChainExecutors` 管理两个工作线程池，`TaskChainProperties` 读取下列参数。

| 参数 | 默认值 | 含义 |
| --- | --- | --- |
| `taskchain.cpu-threads` | `max(1, availableProcessors)` | CPU 工作池线程数 |
| `taskchain.io-threads` | `max(4, 2 × availableProcessors)` | IO 工作池线程数 |
| `taskchain.queue-capacity` | `128` | 每个池各自的队列容量 |
| `taskchain.default-phase-timeout` | `3s` | 默认链每个阶段的总预算；显式 PhaseConfig 可覆盖 |
| `taskchain.shutdown-timeout` | `5s` | 两个工作池共用的关闭等待预算 |

可在应用配置中覆盖参数，例如：

```yaml
taskchain:
  queue-capacity: 128
  default-phase-timeout: 3s
  shutdown-timeout: 5s
```

| 接入步骤 | 具体要求 |
| --- | --- |
| 定义用例输入输出 | 建议使用不可变 record，把身份等必要信息放入 input |
| 实现业务节点 | 实现 `TaskNodeHandler<I, O>`，通过构造器注入所需领域能力 |
| 声明节点元数据 | 加 `@TaskNode`，显式填写 nodeId、group、phase、taskType；nodeName、description 可选；order 默认 0，required 默认 true |
| 注册为 Spring Bean | 同时使用 `@Component` 或配置类的 `@Bean`；TaskNode 自身不是 Spring 组件注解 |
| 调用引擎 | 在用例入口注入 `TaskChainEngine`，每次请求创建 Context，并选择默认链或显式阶段配置 |
| 处理执行结果 | 先检查 `isSuccess()`；成功后读 output，可选失败另从节点报告观察 |

实际业务节点放在对应业务的 application 用例层；共享 domain 内核不引用 Spring。当前既有 service 模块不反向依赖这些新模块，本轮未修改已有业务调用链。

同一 group 的节点 ID 不能重复；阶段内按 order 再按 ID 排序。并行阶段中的排序只控制提交顺序，不建立节点依赖。节点注册表启动后冻结，无节点时允许启动；执行未知 group 会报参数错误。

`nodeId` 用于定位，`nodeName` 用于展示；名称为空、纯空白或手动注册时为 null，都回退到 nodeId。名称由注解传入 TaskNodeHolder，最终进入 NodeExecutionResult.nodeName()；description 仅保存在 Holder，供节点说明使用。名称无需唯一，不影响排序或重复注册检查。

```java
@TaskNode(
    nodeId = "load-shop",
    nodeName = "加载店铺基础信息",
    description = "按输入店铺编号查询基础信息，写入 shop 属性",
    group = "shop-detail",
    phase = ProcessPhase.DATA_PROCESSING,
    taskType = TaskType.IO_BOUND
)
```

手动注册可使用完整构造器 `(nodeId, nodeName, description, group, phase, order, taskType, required, handler)`，也可继续使用本文示例中的七参数构造器省略名称和描述。旧注解的 `id = ...` 改为 `nodeId = ...`，原 Holder 的 `id()` 改为 `nodeId()`；直接构造 NodeExecutionResult 时，需要在 nodeId 后传入 nodeName。

## 4. 节点之间如何传递结果

### 业务上下文继承

2026-10-19 起支持业务上下文继承 TaskContext，业务节点拿到的子上下文也保留该类型。以简化的名称查询为例：

```java
public class ShopContext extends TaskContext<Long, String> {
    public ShopContext(Long shopId) {
        super(shopId);
    }

    private ShopContext(ShopContext parent, Snapshot<String> snapshot) {
        super(parent, snapshot);
    }

    @Override
    protected ShopContext createNodeContext(Snapshot<String> snapshot) {
        return new ShopContext(this, snapshot);
    }

    public String getShopName() {
        return (String) getAttribute("shopName");
    }

    public void setShopName(String name) {
        setAttribute("shopName", name);
    }
}
```

节点仍实现 TaskNodeHandler<Long, String>，在 execute 中通过 `ShopContext business = (ShopContext) context` 访问业务方法。调用方可以直接使用 `ShopContext result = engine.executeChain(group, new ShopContext(shopId))`。同一组的业务节点必须与调用入口使用的上下文类型匹配。

子类必须覆盖工厂并通过 `super(parent, snapshot)` 创建全新节点实例；漏写工厂、返回其他类型、错误执行身份、复用主上下文或节点实例都会报错并进入节点失败报告。工厂仅负责轻量对象创建，不做业务查询。基础读写与生命周期方法保持 final，子类新增业务访问方法即可。

不能只声明 `private String shopName` 并用 Lombok 直接生成 setter：该字段不会参与 Snapshot/WriteSet，无法自动合并回根上下文。业务结果通过 getAttribute/setAttribute 访问；列表等属性值仍遵守只读协议。所有节点继承以下提交规则。

本次继承扩展验证：TaskContextInheritanceTest 的 7 项测试与 application 的 ShopDetailTaskContextTest 的 1 项测试通过，覆盖真实引擎中的子类类型、串行传递、并行快照隔离、同名冲突、失败写入丢弃、节点工厂校验及封存。完整后端回归为 208 项通过、16 项跳过、1 项架构测试失败；失败原因是工作区已有的 domain Lombok 依赖与零生产依赖约束冲突，未在此次改造中调整。前端构建通过并按仓库约定复制产物。

| 情况 | 可见性与提交规则 |
| --- | --- |
| 串行节点 A 成功后执行 B | B 能读取 A 的成功结果；阶段整体成功后才提交主上下文 |
| 并行节点 A 与 B | 都读取阶段开始的快照，不能依赖对方本阶段新写入的属性 |
| 节点失败或超时 | 丢弃该节点全部局部写入，包括失败前的写入 |
| 多个成功并行节点写同一 key | 整个阶段失败，即使值相同也不能覆盖 |
| 多个成功并行节点设置 output | 整个阶段失败；通常只让后处理组装节点设置最终 output |
| 阶段失败 | 本阶段不提交，前序成功阶段保留；外部数据库或 HTTP 副作用不会回滚 |
| Context 已执行过 | 禁止再次执行或并发复用，每次请求创建一个新实例 |

共享对象是浅层只读协议：容器隔离不能阻止业务自行修改 input 或属性值中的可变对象。需要更新时创建新对象并通过节点局部写入提交。

## 5. 失败和超时的调用约定

| 场景 | 调用方观察到什么 |
| --- | --- |
| 必需节点失败 | 返回失败 Context，保留首先观察到的致命错误，后续阶段不执行 |
| 可选节点失败 | 链路允许继续，错误保留在节点报告中 |
| 阶段到期且必需节点未完成 | 链路失败，本阶段数据不提交 |
| 阶段到期且仅可选节点未完成 | 记录可选超时，合并其余成功结果并继续 |
| 节点记录 ABANDONED | 引擎已不接受其结果，不代表底层线程一定停止 |
| 错误注册或非法调用参数 | 抛出异常，属于使用错误；配置在任何节点执行前校验 |

阶段超时包含排队和执行时间，串行节点共用阶段预算。线程池拒绝提交时按该节点的必需/可选规则处理。提前返回会尽力取消未完成任务；迟到写入不能改变已返回的上下文或报告。

调用方应把身份等必要信息显式放入 input。线程池执行不会自动继承调用线程的事务、登录身份或日志上下文。真实商详接入前，仍需确定各节点的业务降级值、下游超时及线程池容量。

## 6. 本轮验证记录

验证日期：2026-10-16，已包含节点名称与描述的增量实现。Java 21，使用项目 Maven 多模块测试。

| 验证范围 | 结果 |
| --- | --- |
| TaskChainEngineTest | 23 项通过：串并行、快速失败、超时、冲突、阶段提交、排队、拒绝、中断与请求隔离 |
| TaskContextTest | 4 项通过：只读容器、集合类型保持、生命周期与节点主动失败 |
| TaskNodeRegistryTest | 7 项通过：唯一性、排序、冻结、参数校验，以及空名称回退并进入失败报告 |
| TaskChainConfigurationTest | 15 项通过：Spring 代理与装配、配置、有界池、共享关闭预算、禁止继承线程本地身份、元数据传递和默认值 |
| 后端默认完整套件 | 共 184 项，168 项通过、16 项按原有环境开关跳过，0 失败、0 错误；BUILD SUCCESS |
| 架构测试 | ArchitectureLayoutTest 的 3 项通过，包含在后端完整套件中 |
| 文档示例 | 从本文直接提取 Java 代码，独立编译运行；成功输出示例详情并保留可选失败诊断 |
| Standards 独立审查 | 硬规范违例 0 项，值得处理的可能 smell 0 项 |
| Spec 独立审查 | 缺失/部分实现、范围扩张、错误实现均为 0 项 |

完整回归命令（从项目根目录运行，JAVA_HOME 指向 JDK 21）：

```text
mvn -B -ntp -f backend/pom.xml test
```

跳过的 16 项为已有外部环境测试：15 项真实 Redis 相关测试及 1 项 legacy.context.enabled 控制的完整应用上下文测试。本轮没有开启这些环境开关，没有验证真实 MySQL/Redis 部署或商详业务接入。已有隔离持久化测试使用 H2 内存数据库。
