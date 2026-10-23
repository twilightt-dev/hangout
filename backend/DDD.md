# 九模块架构准备

当前保留九个 Maven 模块，评论已回归传统 Service。用户于 2026-10-14 撤回评论 DDD 试点，明确保留点赞并一并迁入 service。历史架构取舍见 [ADR 0010](../docs/adr/0010-评论回归传统Service并保留九模块与点赞.md)。

## 当前职责

| 模块 | 职责 |
|---|---|
| service | 已有实体、Mapper、业务服务、工具、配置与资源；评论创建、分页及点赞均在这里 |
| controller | HTTP、JWT/安全、异常映射；评论直接调用 BlogCommentsService |
| job | 已有 Feed 任务调度入口，调用 service；评论不再使用同步任务 |
| listener | 预留消息入口，原秒杀消费者仍留 service |
| start | Boot 启动、Mapper 扫描与运行配置；唯一可执行 JAR |
| domain | 保留 POM，当前无评论生产代码；供未来复杂领域模型使用 |
| application | 保留 POM 和内层依赖约束，当前无评论应用服务 |
| infrastructure | 保留 POM，当前无评论适配器 |
| facade | 保留 POM，当前无评论门面 |

模块不等于领域，预留模块并不要求简单业务立即迁入 DDD。service 不依赖 application 或 infrastructure；本次店铺缓存实现允许 service 依赖 domain 中无框架的共享缓存协调端口，使旧店铺入口和商详应用用例使用同一技术实现，禁止从此依赖引入商详用例或领域编排。端口定义、失败语义与取舍见 [ADR 0019](../docs/adr/0019-店铺防穿透采用共享布隆过滤器与空值标记.md)。domain 不声明运行时生产依赖，application 仅依赖 domain 和 Spring 的上下文、事务库。空模块可能不生成普通 JAR；start 仍可构建。

## 评论结构

```text
controller/BlogCommentsController
    → service/BlogCommentsServiceImpl
        → mapper/BlogCommentsMapper
        → mapper/BlogMapper
        → UserService
        → StringRedisTemplate
        → RedissonClient
```

一套实体与 Mapper 完成评论创建、分页和点赞。LikeResult 是服务接口内的响应记录；不设独立点赞模型、应用端口、仓储或 Redis 辅助层。点赞直接调用 Set 操作与数据库原子加减，MapperConfiguration 仍扫描 com.dianping.mapper。

## 兼容行为

| 流程 | 行为 |
|---|---|
| 创建 | 服务端身份、trim/非空/255 UTF-16 单元，一级状态，插入与博客计数同事务 |
| 查询 | 可见一级、时间和 ID 降序、每页 10 条、批量作者、分页五字段与 ISO 时间 |
| 点赞 | 登录、自赞、归属与可见一级检查；同一用户串行切换，数据库数量原子加减 |
| 状态降级 | Redis 故障保留评论与数量；登录状态未知、按钮禁用，游客未选中 |
| 历史异常 | 数量非零而无关系时暂停，不修改旧数字；其他不一致单独核对处理 |

Redis 保留原用户 Set 键。新流程不使用旧 Hash、待同步 ZSet 或同步列；不需要执行旧增量 DDL，也不删除真实环境额外列。数据库与 Redis 可能部分成功，已确认接受偶发不一致，不承诺自动修复。上线切换前应停用旧任务、核对待同步数量。存储取舍见 [ADR 0011](../docs/adr/0011-评论点赞收拢到Service并取消自动同步.md)。

## 后续 DDD 开发

下一项复杂业务先明确规则、边界、事务和接口，再决定模型和分层；避免只为填满预留模块而拆类。现有简单业务可以继续留 service。

## 验证

复用真实 H2、事务、生产 Mapper 与 JWT/HTTP 测试，另显式开启专用 Redis 和真实 Redisson 测试验证点击切换、并发计数、原关系键兼容及故障降级。H2 不替代真实 MySQL 的锁与隔离行为；Redis Cluster 和完整应用启动需在隔离环境验收。
