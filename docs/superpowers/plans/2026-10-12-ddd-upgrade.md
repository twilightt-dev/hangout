# 九模块 DDD 渐进迁移实施计划

> 历史实施记录：评论 DDD 分层已于 2026-10-14 按用户决定撤回，点赞保留并迁入传统 Service。当前源码以 ADR 0010 和 backend/DDD.md 为准。


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前后端迁入九模块，保持已有行为，并完整实现评论创建与分页查询试点。

**Architecture:** 参照教学项目的九模块组织，遗留业务集中于 service，HTTP 和启动装配分离。评论通过门面、应用用例、纯 Java 领域模型及基础设施适配器贯通，数据库事务保持强一致。

**Tech Stack:** Java 21、Spring Boot 3.5.5、MyBatis-Plus 3.5.12、MySQL、Redis、JUnit 5、H2。

**Spec:** `../../../backend/DDD.md`

## Global Constraints

- 全程中文，新增代码注释使用中文。
- 必须采用九个 hmdp- Maven 模块；保留 Java 21、Spring Boot 3.5.5、MyBatis-Plus 3.5.12。
- 保持接口路径、响应格式、数据库表和 Redis 数据含义兼容。
- 评论保存与博客计数同一数据库事务，计数采用 SQL 原子递增。
- domain 不依赖框架或其他生产模块；application 不依赖 infrastructure、service、facade。
- 不移植任务引擎、保障申诉、RabbitMQ 等新功能。
- 不删除文件或目录，不运行 clean，不提交、不推送；使用保留内容的移动和普通编辑。
- 所有命令使用绝对路径，Git 使用 git -C，避免依赖执行器的 workdir。

## Task 1: 九模块安置和装配

**Files:** 原 `backend/pom.xml`、`backend/pojo`、`backend/common`、`backend/server`；目标九模块 POM、源码、资源及测试；已有运行脚本和文档中旧构建路径。

**Interfaces:** 产出原 `com.dianping.*` 包名与服务 API，供评论基础设施适配；所有可运行原测试保持可运行；仅 start 有 Boot 入口。

- [ ] 记录当前全量测试基线。新增架构测试到原 server，检查目标九模块列表和内层禁用依赖，先运行观察旧结构下的断言失败。
- [ ] 将 server 迁为 hmdp-service，合并 common、pojo 源码；重用原 POM 为新的模块 POM，保留源文件内容，不删除历史文件。
- [ ] 将 Controller、安全 HTTP 适配移到 controller，启动类与 YAML 移到 start；配置按实际依赖归属，不形成 service→controller/start 的反向依赖。
- [ ] 按规格建立九模块 Maven 依赖，测试按依赖归属，SpringBootTest/WebMvcTest 等跨层装配测试可置于 start，纯旧服务测试可留 service。
- [ ] 运行九模块全量测试并修复装配回归，报告实际结果。命令：`mvn -f D:/devprojects/java-projects/comment/backend/pom.xml test -B -ntp`。

架构测试关键断言：
```java
assertThat(modules).containsExactlyInAnyOrder(
    "hmdp-domain", "hmdp-application", "hmdp-infrastructure", "hmdp-facade",
    "hmdp-controller", "hmdp-job", "hmdp-listener", "hmdp-service", "hmdp-start");
assertThat(applicationDependencies).doesNotContain(
    "hmdp-infrastructure", "hmdp-service", "hmdp-facade");
```

## Task 2: 评论纵向试点

**Files:** 新建 domain/application/infrastructure/facade 下 `com/dianping/content/comment` 业务包；修改 controller 中 `BlogCommentsController`；测试置于对应模块或 start。

**Interfaces:** Controller 调 facade，不再调用旧评论 Service。领域提供 `CommentContent` 值对象与 `Comment` 创建行为，仓储保存返回持久化 ID。应用提供创建与分页用例，以及博客存在性/计数、当前身份和作者批量查询等必要端口；基础设施实现端口，门面转换原兼容响应。具体 Java 类型签名由此任务统一定义，不跨任务预设矛盾签名。

- [ ] 先写评论内容规则测试（空、trim、255/256 UTF-16 长度）并运行红灯，再实现值对象和创建行为。
- [ ] 先写创建与分页用例测试，包括错误信息、验证顺序、作者缺失、排序与页大小，再实现应用编排和端口。
- [ ] 基础设施适配原 Mapper 与作者/身份能力，应用层不引用遗留类型；原表无需修改。
- [ ] 先写 HTTP 兼容与真实事务测试再接入门面/Controller。事务测试使用 H2 和 Spring 事务代理，不用 Mockito 冒充回滚证据。
- [ ] 运行试点和全量测试，确认旧服务只作为未使用的遗留实现存在，新链路完整分层。

关键测试行为：
```java
assertThat(CommentContent.of("  很值得去  ").value()).isEqualTo("很值得去");
assertThatThrownBy(() -> CommentContent.of("a".repeat(256)))
    .hasMessage("评论内容不能超过255个字符");
// 使用真实数据库事务：先成功插入评论，再让博客计数更新失败。
assertThatThrownBy(() -> application.create(command)).isInstanceOf(RuntimeException.class);
assertThat(jdbc.queryForObject("select count(*) from tb_blog_comments", Integer.class))
    .isZero();
```

## Task 3: 运行验证和设计记录

**Files:** `../../CONTEXT.md`、`docs/adr/0008-DDD升级采用九模块渐进迁移并保持兼容.md`、`../../../backend/DDD.md`、`.gitignore` 中精确文档例外、运行说明及受影响脚本。

- [ ] 更新已确认的迁移结构及试点后开发新功能的时点；未答复的业务上下文仍标记为未决。
- [ ] 修正文档忽略规则，只让本次规格/计划/ADR进入可提交范围，保留其他忽略规则。
- [ ] 执行全量 `mvn test`、`mvn package -DskipTests`、`git diff --check`；核实 Boot jar 包含九模块运行所需类和资源。
- [ ] 对实际 diff 进行任务审查和最终整体审查，修复重要问题，交付代码和测试证据。
