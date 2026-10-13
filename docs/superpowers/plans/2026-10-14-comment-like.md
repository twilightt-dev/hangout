# 评论点赞实现计划

> 执行方式：按已确认的测试边界进行 TDD；领域与应用、基础设施、前端分别开发，最后集成并执行 Standards/Spec 两轴审查。保留当前 DDDUpgrade 工作目录与现有暂存内容。

目标：实现一级评论点赞切换、故障降级和可重试计数同步。

架构：Comment 实体检查资格，单用户 CommentLike 计算切换。Redis 原子保存关系、版本和待同步记录；MySQL 计数按代次与版本条件更新。job 调用应用同步服务。

技术：Java 21、Spring Boot 3.5.5、MyBatis-Plus 3.5.12、Redis Lua、Vue 3、Vitest。

规格：[已确认设计](../../design/2026-10-14-评论点赞领域模型设计.md)。

## 全局约束

- 中文新增注释；不删除文件、不执行真实数据库或 Redis 数据变更。
- 当前已有大量未提交迁移，先保存源码基线；点赞审查以本轮基线比较。用户已确认将九模块 DDD 迁移与评论点赞合并提交，其他资源、课程文件及无关暂存删除保留。
- 测试边界已在规格的验证重点确认；各任务先跑失败行为，再最小实现并跑针对性测试。
- 不重试结果未知的切换写入；历史未核对数据禁用；Redis 故障不伪装成未点赞。
- 迁移 SQL 默认历史 like_epoch=0；新评论 like_epoch=1、like_version=0、liked=0。

## 共享接口

领域包 com.dianping.domain.comment：Comment.restore(Long id,Long blogId,Long authorId,CommentContent content,Long parentId,Long answerId,Integer status)，ensureLikeable()；CommentLikeId(Long commentId,Long userId)；CommentLike.restore(CommentLikeId,boolean).toggle() 返回 CommentLikeChange(id,expectedLiked,targetLiked)。

CommentRepository 保留 insert，新增 Comment findById(Long id)。CommentLikeRepository：CommentLike findState(CommentLikeId id,long epoch,boolean allowInitialize)；CommentLikeApplyResult tryApply(CommentLikeChange change,long epoch)。结果 record(boolean applied,boolean isLike,long liked)，applied=false 表示确定未执行的竞争。

应用 port：CommentLikeMetadata(long epoch,long version,long liked,boolean initialized)，enabled() 为 epoch>0；canInitialize() 仅在已启用、版本与数量均为零且未初始化时成立。初始化在 MySQL 行锁内核验，持久化 initialized 后才允许切换，防止首次同步前 Redis 数据丢失被当成新关系。CommentLikeEligibilityPort.metadata(Long id)。CommentLikeReadPort.findStates(List<Long> ids,Long userId) 返回 Map<Long,CommentLikeStatus>，状态 record(Boolean isLike,boolean available)。匿名 userId=null。

CommentLikeCountSyncPort：List<CommentLikeCountSnapshot> pending(int limit)，boolean writeCount(snapshot)，void acknowledge(snapshot)，void defer(snapshot)。快照 record(Long commentId,long epoch,long version,long liked)。writeCount=true 仅表示同代次已应用该版本或更新至该版本，可确认；停用、不同代次返回 false。

ToggleCommentLikeService.toggle(Long blogId,Long commentId) 返回 CommentLikeResult(long liked,boolean isLike)。CommentLikeUnavailableException 位于 application.comment.like，controller 映射503。

同步服务 SyncCommentLikeCountsService.synchronize(int limit)，每条失败调用 defer 且继续；job 每秒100条，失败退避通过 Redis 待同步有序集合实现。

## 任务1：领域与应用/HTTP纵向切片

文件：backend/domain/src/.../comment；backend/application/src/.../comment；backend/facade/.../CommentFacade；backend/controller/.../BlogCommentsController、GlobalExceptionHandler；service/.../VO/CommentVO。

- [x] 测试 Comment.restore 保留 status=2 并拒绝点赞、身份相等和未保存对象不相等；运行 domain 定向测试得到失败。
- [x] 实现实体与关系值对象；运行测试通过。
- [x] 测试 toggle 自赞、历史停用、路径不符、竞争重试、未知结果不重试；实现接口与应用流程。
- [x] 测试分页匿名/登录/故障降级；扩展读模型 liked/isLike/likeAvailable，保留旧构造器供原测试过渡。
- [x] 测试同步每条失败保留、旧版本不确认；实现服务。
- [x] 门面和HTTP接入，规则拒绝沿用Result，未知写入503。

## 任务2：Redis/MySQL基础设施和调度

文件：infrastructure.persistence.*.comment；infrastructure.redis.comment；infrastructure/src/main/resources/lua/comment-like-*；job/.../CommentLikeCountSyncJob；service/resources/db/migrations/20261014_comment_like.sql。

- [x] 先以隔离Redis公开仓储接口测切换及待同步快照，确认失败后实现Lua与适配器；明确不清理真实键。
- [x] 元数据Lua检查代次，缺少元数据仅允许epoch启用、version=0、count=0时初始化；缺失已用数据返回不可用。
- [x] 关系比较切换、SCARD、版本和分区ZSET待同步在同槽脚本内原子完成；未知结果不重试。
- [x] pending批量读取原子快照；ack只有epoch/version相符才清理；defer延迟1秒且新写入可提早调度。
- [x] 真实H2条件SQL测旧版本拒绝、新版本应用、代次不符和重复执行；实现投影适配器与findById/读模型。
- [x] 新评论设置epoch1/version0/liked0；历史epoch0默认拒绝；增加增量DDL，仅写脚本不执行。
- [x] job依赖application，单份调度；记录核对、暂停、代次恢复操作流程，不开放无鉴权管理接口。

## 任务3：前端评论点赞

文件：frontend/src/types/api.ts、api/blog.ts、views/blog/BlogDetailView.vue及测试。

- [x] Vitest测试点击切换、颜色、数量、匿名登录；先红后实现。
- [x] 测防连点、POST失败不重发而GET刷新、未知状态禁用及重试、路由/身份变化后旧请求忽略。
- [x] 保持原评论分页；状态刷新按ID合并，成功操作结果不立即被旧投影覆盖。
- [x] 运行定向Vitest与npm run typecheck。

## 任务4：集成、审查与交付

- [x] start H2/安全HTTP测试加入新应用装配与隔离点赞替身，验证401/503、跨博客拒绝、原事务回归。
- [x] 保留目标模块target后重新打包，运行完整后端和前端测试、typecheck、build，日志保存本轮scratch。
- [x] 比较本轮源码基线进行 code-review Standards/Spec 两轴审查，修复后重跑相关验证。
- [x] 更新CONTEXT、DDD、README及设计实现状态，真实环境限制如实报告。
- [x] implement要求提交当前分支：按用户确认合并九模块 DDD 迁移与评论点赞，使用专项 index 提交；核对范围外原暂存内容精确保留，不包含资源、课程文件或无关删除。

## 验证记录

后端完整 package：41 套、163 例，154 通过，9 例既有外部测试跳过；其中隔离 Redis 7.4.2 测试已开启。前端 27 文件、160 例通过，typecheck 与 build 通过。运行日志保存在本轮 `.superpowers/comment-like-20261014-011415/`。

Standards/Spec 审查由主线程完成自检：子代理任务正文未成功送达，不声称独立代理审查完成。未执行现有数据库迁移或历史数据修复，真实 MySQL 隔离与锁、Redis Cluster、完整应用启动仍待部署环境验证。
