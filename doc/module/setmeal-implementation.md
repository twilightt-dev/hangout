# 店铺套餐第一版实施计划

> 执行方式：在用户指定的 `feat/setmeal` 分支内联执行；按 TDD 逐个行为推进，完成后使用 code-review 技能并行进行规范与规格审查。

**目标：** 实现已确认的独立套餐查询与商详内展开展示。

**架构：** Controller → Facade → ApplicationService → Repository。领域实体与仓储契约属于 domain，Mapper、DO 与仓储实现属于 infrastructure；套餐主表与明细表独立建表。

**技术栈：** Java 21、Spring Boot 3.5.5、MyBatis-Plus、MySQL、Vue 3、TypeScript、Vitest；后端测试使用隔离 H2。

**规格：** [套餐上下文](CONTEXT.md)。

## 全局约束

- 保留用户已暂存的 ShopVisitBlog.java 和已有 .scratch 内容。
- 不接入商详聚合与任务链；不增加交易、库存、销量或套餐管理写接口。
- 用户已确认测试边界：后端公开查询接口、仓储接口、前端套餐组件。
- 不向本地业务数据库写入数据；建表与演示 SQL 作为待审阅部署产物。
- 金额单位为整数分，菜品数量支持最多两位小数；查询仅返回上架套餐。
- 修改代码的注释与产品文案使用中文。
- 完成后执行前端构建，叠加复制至 Nginx hmdp 目录，不删除旧文件。
- Git 提交按根 AGENTS.md 在完成审查后说明具体目标、命令和影响，再等待确认。

## 任务一：独立套餐查询的最小闭环

**文件与职责：**

| 文件位置 | 内容 |
|---|---|
| backend/domain/.../shopdetail/model/ShopSetmeal.java | 套餐实体及完整性约束 |
| backend/domain/.../shopdetail/model/ShopSetmealItem.java | 固定菜品条目 |
| backend/domain/.../shopdetail/repository/ShopSetmealRepository.java | shopExists、findPublishedByShopId 查询契约 |
| backend/infrastructure/.../shopdetail/persistence/dataobject | 套餐主信息和明细 DO |
| backend/infrastructure/.../shopdetail/persistence/mapper/ShopSetmealMapper.java | 店铺存在性、上架套餐及批量菜品查询 |
| backend/infrastructure/.../shopdetail/repository/ShopSetmealRepositoryImpl.java | DO 映射与套餐明细组装 |
| backend/application/.../shopdetail/service/ShopSetmealApplicationService.java | 参数、店铺存在性与只读查询编排 |
| backend/application/.../shopdetail/model/result/ShopSetmealResult.java | API 返回模型 |
| backend/facade/.../shopdetail/ShopSetmealFacade.java、impl/ShopSetmealFacadeImpl.java | 门面及转调 |
| backend/controller/.../controller/ShopSetmealController.java | GET /shop/{shopId}/setmeals |
| backend/start/src/test/java/com/dianping/setmeal/ShopSetmealIntegrationTest.java | 真实 HTTP 和仓储边界测试 |
| backend/infrastructure/src/main/resources/db/migrations/20261018_shop_setmeal.sql | 独立主表与明细表 |

**接口：**

```java
boolean shopExists(Long shopId);
List<ShopSetmeal> findPublishedByShopId(Long shopId);
List<ShopSetmealResult> list(Long shopId);
```

- [x] 先增加一个 HTTP 查询测试：隔离数据库中准备一个上架套餐，断言游客读到完整主信息及菜品；后续通过仓储接口覆盖多套餐排序、下架与跨店过滤。

```java
mvc.perform(get("/shop/101/setmeals"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data[0].name").value("双人餐"))
    .andExpect(jsonPath("$.data[0].price").value(9900));
```

- [x] 运行该测试，确认缺少套餐接口导致失败，再实现最小查询闭环。
- [x] 通过后逐项补充并运行仓储排序、同排序 ID 顺序、下架与跨店过滤、空列表、400、404、503、残缺明细错误场景。
- [x] 运行：`mvn -pl start -am -Dtest=ShopSetmealIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`。

## 任务二：套餐区块与商详接入

**文件：** 新增 frontend/src/api/shopSetmeal.ts、components/shop/ShopSetmealSection.vue 及其 .spec.ts；修改 types/api.ts、views/shop/ShopDetailView.vue 和既有视图测试的接口边界桩。

**接口：**

```typescript
getShopSetmeals(shopId: number, signal?: AbortSignal): Promise<ShopSetmeal[]>
// ShopSetmealSection 接收 shopId，独立加载；列表数据包含 items 和 rules。
```

- [x] 先写组件测试，断言卡片价格 9900 分显示 99.00 元，点击可展开菜品和多行规则，展开不增加查询请求。
- [x] 运行并确认失败，再实现类型、API 和组件，沿用现有样式变量及语义化 details/summary。
- [x] 逐项增加空列表隐藏、失败后重试、选填项隐藏、图片占位、切店取消旧请求及忽略迟到结果测试。
- [x] 在现有商详内接入独立套餐区块，补充套餐失败不阻断店铺基本信息的视图验证。
- [x] 运行：`npm test -- src/components/shop/ShopSetmealSection.spec.ts src/views/shop/ShopDetailView.spec.ts`；阶段性运行 `npm run typecheck`。

## 任务三：部署产物、审查和完整验证

**文件：** backend/infrastructure/src/main/resources/db/demo/20261018_shop_setmeal_demo.sql、doc/module/CONTEXT.md、根 CONTEXT.md。

- [x] 提供独立演示 SQL，覆盖上架、下架、多菜品与排序场景；脚本不覆盖已有业务表数据。
- [x] 读取实际迁移脚本初始化 H2，避免测试表结构与部署结构各自维护。
- [x] 使用 code-review 技能分别进行规范、规格审查；基点为开始实施时 HEAD `6ab91a3116c765cfa462f3c1b8c3d061047ddde9`，检查该基点之后的工作区差异，包括新增文件，排除用户原有改动。
- [x] 修复审查发现的问题并运行针对性验证。
- [x] 最后各执行一次后端默认全套测试与前端全套 Vitest；另执行 typecheck、build。
- [x] 复制 frontend/dist 的构建产物至 D:/devprojects/java-projects/comment/nginx-1.18.0/html/hmdp，不删除旧文件；核对入口和新资源文件。
- [x] 更新上下文中的实现与部署状态，报告验证结果和真实数据库脚本尚待执行的情况。
- [ ] 执行本次 Git 提交：文件清单和提交命令已准备，按仓库要求等待用户明确确认。

## 验证与交付记录（2026-10-18）

| 检查 | 命令或方式 | 结果 |
|---|---|---|
| 后端完整默认测试 | backend 下 `mvn test -q` | 43 个测试集，共 205 项；189 项通过、16 项按环境条件跳过，0 失败、0 错误 |
| 套餐接口与仓储测试 | ShopSetmealIntegrationTest，真实 MyBatis + 隔离 H2，直接读取建表 SQL | 17 项通过，覆盖游客、排序、过滤、空数据、参数与数据完整性、数据库故障 |
| 前端完整单元与组件测试 | frontend 下 `npm test` | 28 个测试文件、166 项全部通过 |
| 前端类型检查 | `npm run typecheck` | 通过 |
| 浏览器检查 | Chrome，1440×1000 和 390×844；通过模拟 API 展示套餐 | 键盘展开、封面占位、规则换行、无横向溢出、失败隔离和重试均通过 |
| Standards 审查 | code-review 规范子代理，只读审查本次差异和新增文件 | 0 项发现，符合 DDD、中文注释和已确认门面分层 |
| Spec 审查 | code-review 规格子代理，对照套餐 CONTEXT.md | 0 项发现，无遗漏或范围扩张 |
| 生产构建 | `npm run build` | 通过，3 个构建文件 |
| 前端部署文件 | `Copy-Item -Path frontend/dist/* -Destination nginx-1.18.0/html/hmdp -Recurse -Force` | 已叠加复制；3 个文件 SHA256 与 dist 一致，未删除旧资源 |

默认跳过项来自已有完整应用/Redis 等外部环境测试，未强行启用；套餐新增测试全部执行。H2 和模拟 API 浏览器检查不能替代实际 MySQL 与完整应用联调。建表、演示 SQL 尚未在实际业务数据库执行。

构建入口引用 `assets/index-DJdMLano.js` 和 `assets/index-R1UWP_Fq.css`。本地验证日志、浏览器截图和辅助脚本保存在 `.scratch/setmeal-*`，不纳入提交范围。

## Git 提交待确认

目标分支：`feat/setmeal`。建议提交说明：`feat(setmeal): 补齐店铺套餐查询与展示`。只提交本次文件，不推送远程；保留原已暂存的 `ShopVisitBlog.java` 以及其他本地草稿。

待用户明确确认后，从仓库根目录执行 `& ./.scratch/setmeal-commit.ps1`。该脚本显式列出文件，逐一暂存后用 `git commit --only` 提交同一清单，避免纳入其他已暂存内容。脚本尚未执行。
