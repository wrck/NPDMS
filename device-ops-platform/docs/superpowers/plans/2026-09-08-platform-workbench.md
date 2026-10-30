# Platform Workbench Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 修复已批准阶段一前端工作台的执行模式、查询恢复、历史证据、主数据竞态与解析选项重试。

**Architecture:** 保持现有 Vue SFC 结构与 Element Plus 风格。采用组件行为测试、可控 Promise 和 mock API 验证边界；只对现有函数和模板做局部修复，不改路由、API、包配置或管理页面。

**Tech Stack:** Vue 3、TypeScript、Element Plus、Vitest、Vue Test Utils、PowerShell、pnpm。

---

## 范围与协调

根目录：`M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform`。下文路径均相对于此根目录。

允许修改：`device-ops-web/src/views/ProjectCollectionView.vue`、`device-ops-web/src/components/CollectionTaskPanel.vue`、`TargetSelector.vue`、`ParserSidebar.vue`、`device-ops-web/src/utils/session-export.ts`，以及新增专用测试/辅助 utils。本计划为用户明确授权文档。

不修改 router、AppShell、api/device-ops.ts、package/config、其他管理页面，不提交、不推送、不读取或修改 data。主代理追加授权修改 `device-ops-web/src/types/collection.ts` 的 SSH executionMode 类型，并接入 UI 代理所有的 `device-ops-web/src/management/script-handoff.ts`；不通过不安全类型断言掩盖问题。不增加 URL 脚本内容载入。

## 测试命令

所有命令从 PowerShell 执行：

```powershell
Set-Location 'M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/device-ops-web'
pnpm exec vitest run src/components/workbench-phase-one.spec.ts
pnpm exec vitest run src/components/workbench-phase-one.spec.ts src/components/parser-sidebar.spec.ts src/app-shell.spec.ts
pnpm ts:check
```

## Task 1：项目 SSH 模式

Files: 修改 `device-ops-web/src/views/ProjectCollectionView.vue`；新增 `device-ops-web/src/components/workbench-phase-one.spec.ts`。

- [x] 用 TargetSelector stub 返回完整项目、设备及 `executionMode: 'SHELL'` 的 SSH 连接；编辑器 stub 提供有效脚本，触发任务面板 submit，断言 `start` 收到的项目 target 保持 SHELL。另测 EXEC 与 saved connection 引用保持原样。
- [x] 运行定向 Vitest，确认 SHELL 收到 EXEC 的断言失败。
- [x] 删除 SSH 返回对象的硬编码覆盖，采用 `return { ...context, ...structuredClone(connection) }`，Telnet 仍为 SHELL。
- [x] 再运行测试确认绿色；types 变更由主代理负责。

## Task 2：停止/恢复仅查询，不重复下发

Files: 修改 `device-ops-web/src/components/CollectionTaskPanel.vue`；扩展专用测试。

- [x] Mock 采集 SUCCEEDED、解析 RUNNING；调用 start，点击停止查看、继续查看，断言继续按钮可见、详情/解析查询增加且 submit API 仍只调用一次。另测解析查询失败时可恢复、全部完成后无继续按钮。
- [x] 运行 Vitest，确认终态采集不能恢复的断言失败。
- [x] 新增独立查询完成标记；启动/恢复历史时清零，只有终态采集且解析列表均终态时完成。继续按钮使用该标记而非采集终态；终态继续只启动轮询不重开输出流。start 在 busy 时拒绝重复下发。
- [x] 运行 Vitest，确认上述行为绿色。

## Task 3：历史证据诚实呈现

Files: 修改 `device-ops-web/src/components/CollectionTaskPanel.vue`、`device-ops-web/src/utils/session-export.ts`；扩展专用测试。

- [x] 从任务 URL 挂载组件，断言恢复时导出参数不包含恢复时间、输入下载不可用并明确提示脚本缺失。捕获 Blob 内容，断言 `submittedAt=unknown` 和 INPUT 的不可用说明；显式提交时间/脚本应原样导出。
- [x] 运行 Vitest，确认旧实现伪造时间和空白脚本证据导致失败。
- [x] 历史恢复设置 `submittedAt.value = ''`；记录导出用 `submittedAt || 'unknown'` 与脚本缺失说明；模板补充历史缺失提示，不尝试通过命令块重建脚本或从 URL 获取脚本。
- [x] 运行 Vitest，确认绿色。

## Task 4：项目/设备竞态

Files: 修改 `device-ops-web/src/components/TargetSelector.vue`；扩展专用测试。

- [x] 用 deferred Promise 构建项目 A 请求未完成时路由改 B 的情况，先完成 B 再完成/拒绝 A，断言 B 数据和 loading/error 不被覆盖。
- [x] 设备 A 请求未完成时选 B/清空，旧响应成功和失败均不得污染新设备列表/错误。隐藏上下文及卸载时使旧请求失效；卸载用 onBeforeUnmount 递增两类 generation，不修改 API 做真实网络取消。
- [x] 运行 Vitest，确认旧请求覆盖或新请求被 loading 锁阻止的断言失败。
- [x] 采用独立 project/device generation 计数；await 后 success/catch/finally 都校验 generation；route/context 失效时递增，清理关联列表和选择。只有当前查询成功才写 loadedProjectQuery，允许新查询替换进行中的旧查询。
- [x] 运行 Vitest，确认绿色。

## Task 5：解析选项重试与空状态

Files: 修改 `device-ops-web/src/components/ParserSidebar.vue`；扩展专用测试。

- [x] 初次请求 reject 后断言错误提示与重试按钮存在且不显示空版本；点击重试返回 options=[]，断言错误消失、显示暂无已发布版本。另测重复点击 loading 期间仅一个请求。
- [x] 运行 Vitest，确认旧实现没有重试入口/空版本提示。
- [x] `loadOptions` 开头增加 loading 防重复；失败提示旁增加 loading 禁用重试按钮；成功且列表为空时独立空版本提示，区别默认版本未配置和网络失败。
- [x] 运行 Vitest，确认绿色。

## 追加授权：安全脚本交接

- [x] 主代理授权使用 UI 代理的 `consumeScript(): {content, scriptKey, scriptVersion} | undefined`，模块由 UI 代理实现且只用内存。
- [x] 编写真实编辑器组件测试：offer 后挂载应填入脚本、key、version 与 EXECUTION_ONLY；摘要异步重算，submit API 未调用，URL 无内容，consume 再读为空，第二次挂载编辑器空白。先运行看到模型仍为空、policy 仍为 REGISTER_VERSION 的失败，再实现 onMounted 消费。
- [x] 最小实现只替换模型中的 key/version/content/sha256/policy；保留初始 LOCAL_MANAGED source，避免触发编辑器 source watcher 重置 policy。不修改 ScriptArtifactEditor 或交接模块。

## 实际验证记录

- 首轮定向运行（15:13）：11 测试，10 失败 / 1 通过；失败分别覆盖 SSH SHELL 被覆盖、解析恢复入口缺失、重叠 POST、历史证据、重试入口和项目/设备陈旧响应。实现后（15:15）11 全通过。
- 第二轮追加（15:16）：14 测试，2 失败 / 12 通过；失败为安全交接未消费与隐藏上下文后设备响应污染。最小修复后绿色；真实 WebCrypto 摘要用 waitFor 等待，非修改业务代码绕过异步。
- 最终（15:21）：PowerShell 定向 Vitest 4 文件 24 测试全通过，其中专用回归18项，既有 parser-sidebar 2项、app-shell 3项、UI代理 script-handoff 1项。
- PowerShell `pnpm ts:check` 已执行；当前仅其他代理在建的 `src/management/lists.spec.ts` 找不到 `CollectionList.vue` / `ParseTasksPanel.vue`，未越权修复。更早一轮管理页缺失已由该代理推进解决。本任务文件没有类型错误。
- 指定源文件 `git diff --check` 通过。未运行全仓格式修复、浏览器、后端测试或生产连接；未提交、推送或触碰 data。

## 主代理复核追加（15:24）

- 路由项目 props watcher 已存在；A→B 可见上下文由新项目请求递增 generation，隐藏上下文由关闭时失效，成功/失败乱序和旧 finally 不覆盖新 loading 均有回归，无需额外改写 watcher。
- 卸载缺少失效：新增 project/device 两项测试，先运行2项都失败（卸载后仍写 projects/devices/loading）；补 onBeforeUnmount 递增两类 generation 后绿色。
- 最终 PowerShell 定向4文件26测试全部通过（专用20项）。类型检查当前仅其他代理 `src/components/management/CollectionList.vue:30` 两处参数 value 隐式 any；未越权修复。
- handoff 注释精简为 EXECUTION_ONLY 与 source watcher 的约束说明。

## 最终检查

- [x] PowerShell 运行定向回归与 pnpm ts:check，保留实际红绿计数和类型检查结果；有其他代理引入的问题仅报告不越权修复。
- [x] git diff --check；只检查本任务文件 diff，不运行全仓格式化。
- [x] 向主代理报告绝对路径、行为修改、红绿证据、协调依赖及未验证项，不提交或推送。
