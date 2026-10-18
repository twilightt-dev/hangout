# 推荐菜独立业务实施记录

**目标：** 完成用户新增菜品、推荐已有菜品和分页浏览，不接入商详任务链。

**架构：** 沿用 Controller → Facade → ApplicationService → Repository。领域模型放在 domain 的 com.dianping.shopdetail 下，MyBatis 与事务持久化由 infrastructure 实现；application 不依赖旧 service。前端在现有详情页挂载独立组件，复用现有图片上传能力。

**技术栈：** Java 21、Spring Boot、MyBatis、MySQL、Vue 3、TypeScript、JUnit/H2、Vitest。

**规格：** [模块上下文](CONTEXT.md#推荐菜独立业务已实现sql-待部署)。此前已确认的同店同名去重、按人数排序、首屏 6 道等规则保持不变。

## 本轮实施默认值

以下为本轮向用户说明的最小实现选择，区别于此前逐项确认的业务结论：登录后新增菜名和一张封面图，创建者自动推荐一次；暂不提供取消、编辑或删除；查看更多默认每页 12 条，允许 1～50 条；新增使用 UUID 提交标识支持安全重试。菜名去除首尾空白后最长 100 个 UTF-16 单元，不做同义词合并。

## 任务与验证

| 顺序 | 交付 | 关键文件与验收 |
|---|---|---|
| 1 | 后端行为测试 | start/src/test/java/com/dianping/recommenddish/RecommendDishIntegrationTest.java；游客读取、写入登录、重复推荐、同名冲突、重试与排序 |
| 2 | DDD 业务与 SQL | domain 的 RecommendDish、RecommendDishRepository；application 的 RecommendDishApplicationService 和 model.request/result；infrastructure 的 DO、Mapper、RepositoryImpl 与 db/migrations/20261018_recommend_dish.sql |
| 3 | HTTP 与前端 | RecommendDishFacade、RecommendDishController；frontend/src/api/recommendDish.ts 与 components/shop/RecommendDishSection.vue；真实表单交互与错误重试测试 |
| 4 | 验收与文档 | 更新 CONTEXT.md 与 ADR；运行后端隔离测试、前端类型检查及测试、npm run build，将产物复制到 nginx-1.18.0/html/hmdp |

- [x] 先运行新增接口测试并确认因功能缺失失败。
- [x] 完成后端实现，在隔离 H2 中执行本次建表 SQL，验证同店同名、并发推荐和失败回滚。
- [x] 先写前端行为测试，再完成上传、推荐、查看更多与故障状态。
- [x] 运行后端和前端测试及浏览器验收，修复本轮问题并记录外部环境限制。
- [x] 构建和复制前端产物，核对实际文件；未执行 Git 提交和真实数据库写入。

## 接口约定

- GET /shop/{shopId}/recommend-dishes?current=1&size=6：公开读取；列表携带推荐人数和当前用户是否已推荐。
- POST /shop/{shopId}/recommend-dishes：登录后新增；请求包含 requestId、name、coverImage；相同提交重试返回原记录，同店同名新提交返回 409。
- PUT /shop/{shopId}/recommend-dishes/{dishId}/recommendation：登录后推荐，重复调用保持已推荐，不切换状态。

写入与计数同事务，推荐明细以菜品和用户联合主键去重。新增锁定店铺行，推荐锁定菜品行；失败回滚。读侧 COUNT 与分页使用同一只读可重复读事务。

## 执行记录

2026-10-18 已完成独立业务代码与行为验证；未接入商详任务链。真实数据库迁移未执行，用户已有暂存删除与其他工作区修改保持原样。

| 检查 | 结果与范围 |
|---|---|
| 后端完整测试 | `mvn test -q` 退出码 0；44 份报告共 217 项，201 项通过、16 项按环境条件跳过、0 失败、0 错误。推荐菜新增 12 项集成测试全部通过，覆盖真实 HTTP 调用链、隔离 H2、并发推荐、同名并发创建、提交重试、排序和回滚 |
| 前端完整测试 | `npm test`：29 个文件、175 项测试通过；包含推荐菜 8 项组件测试和商详 20 项测试 |
| 浏览器行为验证 | 推荐菜 Playwright 4 项通过，覆盖桌面与手机首屏、查看更多、翻页失败重试、推荐和上传发布；使用接口模拟，不写入真实业务库 |
| 视觉检查 | 已查看手机列表与桌面新增表单截图；卡片和表单排版正常，图片请求故意中止以验证列表图片失败占位 |
| 代码审查 | 已完成规范与规格双轴审查；修复翻页失败后仍显示上一页内容的问题，并添加先失败后通过的回归用例 |
| 类型检查与构建 | `npm run typecheck`、`npm run build` 均退出码 0；构建生成 index.html、CSS 和 JavaScript 共 3 个文件 |
| 前端产物复制 | 已复制至 `D:/devprojects/java-projects/comment/nginx-1.18.0/html/hmdp`；与 frontend/dist 的 3 个文件逐一校验 SHA-256，全部一致 |
| 工作区检查 | `git diff --check` 通过；未执行 Git 提交、推送或真实数据库迁移，保留用户已有暂存删除及其他修改 |

写事务采用 `READ_COMMITTED` 配合行锁；读事务采用 `REPEATABLE_READ`。并发测试用于验证应用侧事务、幂等与计数行为，不能替代目标 MySQL 的部署验收。

### 部署前待办

- 确认目标数据库后部署 `20261018_recommend_dish.sql`，创建两张新表；本轮仅提供脚本，没有在真实数据库执行。
- 在目标环境验收 MySQL 并发行为、认证与实际图片上传链路。
- 本轮实现默认值仍待用户验收；不执行 Git 提交或推送。
