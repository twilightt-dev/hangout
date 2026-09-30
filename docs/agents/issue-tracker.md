# Issue Tracker：GitHub

本仓库的 issue 和规格说明记录在 GitHub Issues 中，所有操作使用 `gh` CLI。

## 操作约定

- **创建 issue**：使用 `gh issue create --title "..." --body "..."`。多行内容使用 heredoc。
- **读取 issue**：使用 `gh issue view <number> --comments`，同时读取评论和标签。
- **列出 issue**：使用 `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，并按需要添加 `--label` 和 `--state` 过滤条件。
- **评论 issue**：使用 `gh issue comment <number> --body "..."`。
- **添加或移除标签**：使用 `gh issue edit <number> --add-label "..."` 或 `gh issue edit <number> --remove-label "..."`。
- **关闭 issue**：使用 `gh issue close <number> --comment "..."`。

从 `git remote -v` 推断仓库；在本仓库目录中运行时，`gh` 会自动识别仓库。

## Pull Request 是否作为 triage 入口

**PR 不作为 request surface：否。** 如仓库流程改变，可将此项改为 `yes`，triage 技能会读取该设置。

设置为 `yes` 时，PR 使用与 issue 相同的标签和状态，并对应使用 `gh pr` 命令：

- **读取 PR**：`gh pr view <number> --comments` 和 `gh pr diff <number>`。
- **列出外部 PR**：`gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`，只保留 `CONTRIBUTOR`、`FIRST_TIME_CONTRIBUTOR` 或 `NONE`，排除 `OWNER`、`MEMBER`、`COLLABORATOR`。
- **评论、管理标签或关闭**：使用 `gh pr comment`、`gh pr edit --add-label`/`--remove-label`、`gh pr close`。

GitHub 的 issue 和 PR 共用编号空间。遇到 `#42` 时，先用 `gh pr view 42` 判断，失败后再用 `gh issue view 42`。

## 技能要求发布到 issue tracker 时

创建 GitHub issue。

## 技能要求读取相关 ticket 时

运行 `gh issue view <number> --comments`。

## Wayfinder 操作

Wayfinder 使用一条作为地图的 issue 和若干子 issue 作为 ticket。

- **地图**：创建带有 `wayfinder:map` 标签的 issue，内容包含 Notes、Decisions-so-far 和 Fog。命令为 `gh issue create --label wayfinder:map`。
- **子 ticket**：使用 GitHub sub-issue API 将 issue 关联到地图。若仓库未启用 sub-issue，则在地图正文中维护任务清单，并在子 issue 正文开头写 `Part of #<map>`。标签使用 `wayfinder:<type>`，类型为 `research`、`prototype`、`grilling` 或 `task`。认领后分配给负责开发者。
- **阻塞关系**：优先使用 GitHub 原生 issue dependencies。使用 `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>` 创建关系，其中 `<blocker-db-id>` 是阻塞 issue 的数字 database id，可用 `gh api repos/<owner>/<repo>/issues/<n> --jq .id` 获取，不是 `#number` 或 `node_id`。如果不支持 dependencies，则在子 issue 正文开头使用 `Blocked by: #<n>, #<n>`。所有阻塞项关闭后，子 issue 才算解除阻塞。
- **Frontier 查询**：列出地图下仍开放的子 issue，排除存在开放阻塞项或已分配人员的 issue，按地图任务顺序选择第一个。
- **认领**：使用 `gh issue edit <n> --add-assignee @me`，作为本次会话第一次写操作。
- **解决**：使用 `gh issue comment <n> --body "<answer>"` 回复，再使用 `gh issue close <n>` 关闭，最后将上下文指针追加到地图的 Decisions-so-far。
