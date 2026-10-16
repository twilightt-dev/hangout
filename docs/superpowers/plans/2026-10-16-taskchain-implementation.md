# 可编排任务链实现计划

> 执行方式：使用 superpowers:subagent-driven-development 实现完整任务，执行者不得继续派生代理；主代理负责文档、独立审查、总体验证和最终提交。

**目标：** 在当前 upgrade/taskchain 分支落地已讨论的共享任务链，保持既有业务行为。

**架构：** domain 为纯 JDK 内核；infrastructure 负责 Spring 装配和两个有界线程池。阶段协调方按完成通知处理快速失败和统一截止时间，节点只写独立结果，阶段接受后才提交。

**技术：** Java 21、Spring Boot 3.5.5、JUnit 5、Maven 多模块。

**规格：** docs/design/taskchain-v1.md、docs/adr/0012-共享任务链采用固定阶段与节点结果隔离.md。

## 全局约束

- 用户已通过 implement 指令授权按当前设计开始实现；不再次确认已批准的测试边界和工程默认值。
- domain 包根 com.dianping.shared.taskchain，保持无生产依赖，所有新增注释使用中文。
- 首轮仅共享内核与装配；不迁移商详、评论或访问统计；不增加 HTTP 接口。
- 在当前 upgrade/taskchain 分支工作，不切分支、不创建其他 worktree、不推送。
- 测试边界为公开引擎入口、注册表及 Spring 装配；不测试私有方法，不 mock 内部引擎协作者。
- 不删除用户文件，不清理或覆盖现有未提交材料；只暂存并提交任务链相关改动。
- 审查基线为本轮起点 a613ebd5d1db0ce658b216496bd8bfff621f7f34，评审使用本轮暂存差异；最终由主代理提交。

## 任务 1：共享内核与 Spring 装配

**文件范围：**

- 新建 backend/domain/src/main/java/com/dianping/shared/taskchain 下 annotation、enums、model、service。
- 新建 backend/domain/src/test/java/com/dianping/shared/taskchain 下公开行为测试。
- 新建 backend/infrastructure/src/main/java/com/dianping/shared/taskchain/config 下配置与资源管理。
- 新建 backend/infrastructure/src/test/java/com/dianping/shared/taskchain 下装配测试。
- 如必须，修改 infrastructure/pom.xml 明确声明直接依赖；不得给 domain 添加生产依赖。

**入口：**

    TaskContext<I, O> executeChain(String group, TaskContext<I, O> context);
    TaskContext<I, O> executePhaseChain(String group, TaskContext<I, O> context,
                                      List<PhaseConfig> phases);
    void TaskNodeHandler.execute(TaskContext<I, O> context) throws Exception;

**实施步骤：**

- [x] 先写一个串行两节点的数据传递测试，运行确认失败，再实现足以令它通过的元数据、上下文、注册和执行路径。

      assertTrue(result.isSuccess());
      assertEquals("店铺-7", result.getOutput());

- [x] 每次增加一个行为测试并观察失败，再补实现：可选失败继续、必需失败停止、阶段数据提交、并行独立写入与冲突。
- [x] 使用门闩测试必需失败不等待其他仍阻塞节点；同样测试迟到写入不改变已返回数据。
- [x] 增加可选/必需超时、串行共享预算、线程池拒绝、引擎只读容器及 Context 不复用的行为测试。
- [x] 通过 Spring 公开配置加载测试发现节点和代理注解、重复注册、配置校验、队列限制与线程池关闭，不连接数据库。
- [x] 定期运行单文件测试和编译，完成后运行全部任务链测试并输出确切数量与命令。

**测试命令：** JAVA_HOME 指向 C:/Users/Lenovo/.jdks/ms-21.0.12.1。Maven 为 D:/develop/apache-maven-3.9.15/bin/mvn.cmd。

    mvn -B -ntp -f backend/pom.xml -pl domain -am -Dtest=TaskChainEngineTest -Dsurefire.failIfNoSpecifiedTests=false test
    mvn -B -ntp -f backend/pom.xml -pl infrastructure -am -Dtest=TaskChain*Test,TaskNodeRegistryTest,TaskContextTest -Dsurefire.failIfNoSpecifiedTests=false test

## 任务 2：文档、独立审查与交付

- [x] 主代理同步 CONTEXT、ADR、设计文档的已授权/已实现状态，并补一段真实可编译的接入示例。
- [x] 检查已有集成测试对外部环境的依赖，使用测试原有开关与隔离数据规则运行后端全套测试一次；不连接或清理生产数据。
- [x] 对本轮差异同时进行 Standards 与 Spec 独立审查，修复重要问题并运行覆盖修复的测试。
- [x] 检查九模块和 domain 无生产依赖约束，检查工作区只暂存本次相关文件。
- [x] 按已核对的本轮范围提交到 upgrade/taskchain；提交标识以包含本计划的 Git 提交记录为准，交付时报告测试与剩余接入范围。

## 验收结果

2026-10-16：任务链专项45项全部通过；后端完整套件180项，164通过、16按原有环境开关跳过、0失败、0错误。ArchitectureLayoutTest的3项包含在全套结果内。Standards和Spec两项独立审查均无发现。文档Java示例已独立编译运行。详见[接入与验证指南](../../design/taskchain-usage.md)。

并行实现期间发现并修正两处边界：保留业务泛型集合的实际类型，避免自动只读包装导致类型转换异常；线程工厂显式禁用 InheritableThreadLocal 继承。两项均有先失败后通过的测试证据。
