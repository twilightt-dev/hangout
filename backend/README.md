# 后端运行与测试

后端采用九个 Maven 模块，Java 编译目标为 21。`start` 是唯一可执行入口，主类仍为 `com.dianping.CommentApplication`。

| 模块 | 当前职责 |
|---|---|
| domain | 纯 Java 评论模型、内容规则与仓储契约 |
| application | 评论创建/查询/点赞与计数同步应用服务、读模型及能力端口 |
| infrastructure | 评论 DO、Mapper、仓储实现，博客计数、作者与身份适配器 |
| facade | CommentFacade 调用新用例并转换原兼容响应 |
| controller | HTTP、过滤器、安全及全局异常处理 |
| job | 已有 Feed 与评论点赞计数同步定时工作器 |
| listener | 空的消息入口模块，本轮不新增消息功能 |
| service | 原实体、Mapper、服务、工具及技术配置 |
| start | 启动类、新旧 Mapper 扫描配置、运行配置及跨模块测试 |

执行默认离线测试与全 reactor 打包（不需要 clean）：

```powershell
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml test -B -ntp
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml package -DskipTests -B -ntp
java -jar D:/devprojects/java-projects/comment/backend/start/target/start.jar
```

运行配置位于 `start`，部署前按原配置方式准备数据库、Redis 及 JWT 等环境。Mapper XML、Lua 及已有 SQL 文件随 service 保留。

以上是本仓库 Windows 路径；其他环境将绝对仓库前缀换为实际路径。`start.jar` 是唯一 Boot 可执行产物，其他模块作为运行依赖；listener 当前没有生产类，Maven 可能不生成其普通 JAR。运行完整应用需要原 MySQL、Redis、上传目录与配置，并会执行已有初始化/调度/秒杀消费生命周期。打包检查及离线测试不等于已经在外部环境启动应用。

默认测试不要求启动外部服务。下列测试保留为显式开启的集成测试，必须使用隔离测试环境；不要指向生产数据库或 Redis。端口参数沿用已有 Redis 测试的开启方式。

| 开关 | 测试 | 环境要求 |
|---|---|---|
| `-Dlegacy.redis.port=16380` | RedisIdWorkerTest、RedissonTest | 隔离本机 Redis；随机测试键，ID 测试保留计数键 |
| `-Dlegacy.context.enabled=true` | ServerApplicationTests | 完整隔离数据库及 Redis，使用运行配置；包含已有启动与调度生命周期 |
| `-Dsignin.redis.port=16380` | SignServiceRedisTest | 已有隔离 Redis 签到测试 |
| `-Dvisits.redis.port=16380` | VisitStatsRedisTest | 已有隔离 Redis 访问统计测试 |
| `-Dstream.recovery.redis.port=16380` | VoucherOrderRecoveryRedisTest | 已有隔离 Redis 消费恢复测试 |

例如，在已准备好隔离 Redis 后运行 ID 和锁测试：

```powershell
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml -pl start -am -Dlegacy.redis.port=16380 '-Dtest=RedisIdWorkerTest,RedissonTest' -Dsurefire.failIfNoSpecifiedTests=false test -B -ntp
```

本轮已完成九模块迁移与评论创建/分页试点，评论入口经过新 facade/application/domain/infrastructure 链路；其余旧业务按需迁移。领域、应用和评论 H2/Mapper/事务/安全 HTTP 整链共新增 31 例，默认外部测试开关未开启时跳过 9 例。H2 使用 MySQL 兼容模式验证实际 SQL 和事务，不能替代真实 MySQL 锁/隔离行为或 Redis 集成验证。DDD 迁移阶段未开启外部环境测试；后续点赞实施已单独运行真实隔离 Redis 测试。

其他服务内已有 `Scheduled`、`PostConstruct` 及秒杀消费逻辑暂留 service，不重复装配；FeedTaskWorker 在 job 单份装配。九模块与评论试点验收后允许开发新功能，仍按需迁移旧业务，不包含任务引擎、保障申诉或 MQ。依赖图及下一个业务迁移步骤见 [DDD 架构](DDD.md)。

## 评论点赞运行要求

已有一级评论支持登录后点击切换、自赞，列表新增 liked/isLike/likeAvailable。Redis 为关系事实来源，MySQL 为最终一致计数。上线前必须执行增量 DDL 并逐条核对历史启用；未核对代次为 0 的评论保留数量且暂停点赞。完整步骤见 [评论点赞部署与恢复](../docs/design/评论点赞部署与恢复.md)。

运行专用隔离 Redis 测试（先独立启动无持久化测试实例，不能指向现有 Redis）：

```powershell
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml -pl start -am '-Dtest=CommentLike*IntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DcommentLikeRedisTest=true' '-DcommentLikeRedisPort=18643' test -B -ntp
```

测试使用随机命名空间，默认关闭。实现阶段使用 WSL 内自行构建的 Redis 7.4.2 专用实例、随机 H2 数据库验证 Lua 并发、失败后重试、关系丢失暂停及恢复代次；没有连接现有 MySQL/Redis。生产 MySQL 锁隔离、Redis Cluster 部署和完整应用启动仍需实际隔离环境验收。

2026-10-14 完整验证：九模块 package 成功，后端 163 例中 154 例通过、9 例既有外部测试跳过（评论点赞的 11 例真实隔离 Redis/组合测试已开启并通过）；前端 27 个测试文件、160 例全部通过，typecheck 与 build 通过。旧 target 在工作区归档后重新构建，未使用 clean。统计来自本轮完整测试报告；真实数据库迁移未执行。
