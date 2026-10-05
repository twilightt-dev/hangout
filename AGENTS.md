# Agent 工作指南

## 项目的文档分层
按照下面这个例子的结构：

项目根目录/
├── AGENTS.md(本文件作统一约束)
├── CONTEXT.md（模块设计简要总览）
├── docs/
│   ├── adr/
│   │   ├── 0001-选择评论存储方案.md
│   │   └── 0002-评论接口权限设计.md
│   └── modules/
│       ├── blog/
│       │   └── CONTEXT.md
│       └── user/
│           └── CONTEXT.md

## 敏感操作确认

执行以下操作前，必须先向用户说明具体目标、命令和影响，并等待用户明确确认：

- 删除数据库、表或数据
- 删除文件或目录
- 清空数据或批量修改数据
- `git reset --hard`
- `git clean`
- 强制覆盖分支或远程提交
- 其他不可逆操作

未经明确确认，不得通过其他命令或变通方式执行上述操作。

执行只读检查、编译、测试和普通代码编辑时，不需要额外确认。

## CONTEXT.md 维护规则

- 开始设计新模块前，先读取根目录和对应模块的 `CONTEXT.md`。
- 使用 `grill-with-docs` 后，只把已经确认的设计结论写入文档。
- 不要把完整聊天记录写入 `CONTEXT.md`。
- 每个模块必须说明：
  - 模块目标
  - 负责范围
  - 不负责范围
  - 核心概念
  - 主要业务流程
  - 数据模型
  - 对外接口
  - 业务约束
  - 已确认的设计
  - 未决问题
- 重要的架构取舍必须单独创建 ADR，并在 `CONTEXT.md` 中建立链接。
- 未确认的内容必须标记为“未决问题”，不能伪装成最终设计。
- 修改模块设计后，同时更新对应的 `CONTEXT.md`。

## Agent 技能

### Issue Tracker

本仓库的 issue 和规格说明记录在 GitHub Issues 中，使用 `gh` CLI 操作。详见 `docs/agents/issue-tracker.md`。

### Triage 标签

使用五个默认标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain 文档

本仓库采用单上下文布局，使用根目录的 `CONTEXT.md` 和 `docs/adr/`。详见 `docs/agents/domain.md`。
