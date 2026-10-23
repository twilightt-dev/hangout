# 后端运行与测试

后端采用九个 Maven 模块，Java 编译目标为 21。`start` 是唯一可执行入口，主类仍为 `com.dianping.CommentApplication`。

| 模块 | 当前职责 |
|---|---|
| domain | 预留领域模型，当前无评论实现 |
| application | 预留应用流程，当前无评论实现 |
| infrastructure | 预留基础设施，当前无评论实现 |
| facade | 预留门面，当前评论直接调用 Service |
| controller | HTTP、过滤器、安全及全局异常处理 |
| job | 已有 Feed 定时工作器，评论不再后台同步 |
| listener | 空的消息入口模块，本轮不新增消息功能 |
| service | 原实体、Mapper、服务、工具及技术配置，包含全部评论业务 |
| start | 启动类、已有 Mapper 扫描配置、运行配置及跨模块测试 |

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
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml -pl start -am -Dlegacy.redis.port=16380 '-Dtest=RedisIdWorkerTest,RedissonTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -B -ntp
```

九模块整理已保留；评论试点已撤回，当前评论创建、查询和点赞统一走传统 Service，只有入口与定时调度位于 controller/job。默认外部测试开关未开启时会跳过外部环境用例。H2 使用 MySQL 兼容模式验证实际 SQL 和事务，不能替代真实 MySQL 锁/隔离行为或 Redis 集成验证。DDD 迁移阶段未开启外部环境测试；后续点赞实施已单独运行真实隔离 Redis 测试。

其他服务内已有 `Scheduled`、`PostConstruct` 及秒杀消费逻辑暂留 service，不重复装配；FeedTaskWorker 在 job 单份装配。九模块与评论试点验收后允许开发新功能，仍按需迁移旧业务，不包含任务引擎、保障申诉或 MQ。依赖图及下一个业务迁移步骤见 [DDD 架构](../docs/DDD.md)。

## 评论点赞运行要求

已有一级评论支持登录后点击切换、自赞，列表返回 liked/isLike/likeAvailable。Redis Set 保存用户关系，MySQL liked 直接加减；数据库不保存用户关系，两个存储可能部分成功，失败后人工核对，不再自动同步或恢复。实现直接位于 BlogCommentsServiceImpl，见 [ADR 0011](../docs/adr/0011-评论点赞收拢到Service并取消自动同步.md)。

不需要执行 20261014_comment_like.sql 的旧增量 DDL。若旧迁移已在其他环境执行，额外列暂留；新代码兼容原表，不清空数据、不删除列。沿用原 `{comment:likes:分区}:评论ID:users` Set。上线前停止旧任务并核对待同步数量及历史异常，Redis 非零关系与数据库数量的完整校验由部署人员单独处理。

运行专用隔离 Redis/H2/Redisson 测试（须先独立启动测试实例，不能指向已有 Redis）：

```powershell
mvn -f D:/devprojects/java-projects/comment/backend/pom.xml -pl start -am '-Dtest=CommentLikePersistenceRedisIntegrationTest,CommentPilotIntegrationTest,BlogCommentsServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DcommentLikeRedisTest=true' '-DcommentLikeRedisPort=18646' test -B -ntp
```

Redis 集成测试默认关闭；随机 H2 数据库和随机评论 ID 配合专用 Redis，验证原 Set 兼容、两次点击、同用户与不同用户并发、SQL 失败、关系丢失和错误键类型。HTTP/H2 测试还验证部分 Redis 写失败后只增加一次数据库数量、故障时列表仍可查看。真实 MySQL 隔离行为与完整应用启动需另行验收。

2026-10-14 简化后验证：九模块 package 成功；36 个后端测试套件共 135 例，126 通过、9 例既有外部测试跳过，其中 7 例真实隔离 Redis/H2/Redisson 测试全部通过。前端 27 文件、160 例通过。Boot JAR 中已无旧点赞辅助类、点赞 Lua 或同步 Job；Git HEAD/index 保持不变，未执行真实数据库或 Redis 数据迁移。
