# 探店星球前端

Vue 3、Vite、TypeScript、Vue Router 与 Pinia 构建的“探店星球”单页应用。

## 环境

- Node.js 20 或更高版本
- npm
- 后端服务与其 MySQL、Redis 依赖（本地联调时需要）

## 安装与运行

```bash
cd frontend
npm install
npm run dev
```

Vite 开发服务会请求相对路径 `/api`。请通过开发代理或 Nginx 将其转发到 Spring Boot 服务。

## 验证命令

```bash
npm test -- --pool=forks --maxWorkers=1
npm run typecheck
npm run build
npm run e2e
```

E2E 默认复用本机已安装的 Google Chrome，无需单独下载浏览器。如果本机没有 Chrome，可移除 `playwright.config.ts` 中的 `channel: 'chrome'`，再安装 Playwright Chromium：

```bash
npx playwright install chromium
```

Playwright 配置会在独立的 `4175` 端口启动 Vite，并对 `/api/**` 请求使用仅存在于 `e2e/app.spec.ts` 中的 `Result<T>` 网络拦截数据验证路由、品牌与响应式布局；生产源码不含这些测试数据。截图写入 `test-results/`。

## 生产部署

普通构建产物位于 `frontend/dist/`。本项目当前生产静态目录为 `nginx-1.18.0/html/hmdp/`，可从 `frontend/` 显式部署：

```bash
npm run build -- --outDir ../nginx-1.18.0/html/hmdp
```

Nginx 应：

- 将 SPA 根目录指向构建产物，并用 `try_files $uri $uri/ /index.html` 支持 history 深层路由；
- 将 `/api/` 转发给 Spring Boot；
- 将 `/imgs/` 映射到后端持久上传目录，而非 Vite 构建目录。

后端运行时需要配置 `UPLOAD_DIR`（图片持久目录）和 `JWT_SECRET`（JWT 签名密钥）。不要将上传图片放入可替换的前端构建目录。

## 接口契约与文档

前后端统一使用以下响应结构：

```json
{
  "code": 1,
  "msg": null,
  "data": {}
}
```

- `code = 1` 表示成功，`code = 0` 表示失败；失败原因读取 `msg`；
- OpenAPI JSON：`http://localhost:8081/v3/api-docs`；
- Swagger UI：`http://localhost:8081/swagger-ui/index.html`。

响应契约已经由旧版 `success/errorMsg/data/total` 切换为 `code/msg/data`，因此新版前端与新版后端必须同步部署，不能与旧版本交叉组合。文档只描述当前已经存在的接口；博客详情、关注等尚未实现的业务端点仍保持未实现状态。

## 回滚旧站

旧站已备份到 `nginx-1.18.0/html/hmdp-legacy/`。如新版出现部署问题，不要删除新版：先将当前 `html/hmdp` 移到独立暂存目录，再将 `hmdp-legacy` 移回 `hmdp` 并重载 Nginx；继续保留根目录 `uploads/`，不需要回滚业务数据库。

## 当前后端限制

- 没有资料保存端点，因此资料页明确只读，不会显示伪造的保存成功；
- 当前后端仍缺少点赞列表 `/blog/likes/{id}` 及部分关注接口；用户主页使用的用户详情 `/user/{id}` 和博主博客分页 `/blog/of/user` 已提供，相应页面会显示真实错误和重试，不会伪造博客、用户或关系数据；
- 秒杀成功与库存变化完全以接口实际返回为准，前端不乐观扣减库存。
- 图片上传当前安全支持 JPEG、PNG、GIF；WebP 尚未加入后端真实解码校验。
- 上传和删除均要求登录；后端用 Redis 记录 24 小时临时文件所有权，仅原上传者可删除，无 owner 的种子图片不会被上传删除 API 移除。
