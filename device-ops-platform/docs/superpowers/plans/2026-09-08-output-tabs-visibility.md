# Output Tabs Visibility Implementation Plan

**Goal:** 修复已批准的输出越界和解析内部 tabs 被命令摘要推远问题，不改变数据与执行行为。

**Architecture:** CollectionTaskPanel 明确区分 workbench 与默认 standalone（记录页）布局。足高桌面使用剩余空间 flex，固定卡头、状态与外层 tabs，仅内容滚动；高度 <=850px 使用可滚动文档流，左右面板不裁切。SemanticResultPanel 内部 tabs 前置，原命令和内嵌摘要移入默认收起的 Element Plus collapse，保留全部证据。

**Tech Stack:** Vue 3 / Element Plus / CSS / Playwright / Vitest。

## 任务
- [x] 新增 `device-ops-web/e2e/output-tabs-visibility.spec.ts`，通过 URL 恢复 mock 历史，拦截全部 API，拒绝非 GET 和外部连接；不执行设备、不触碰 48181、data 或 commit。
- [x] 在 1280x720、1440x900、1024x768 上先运行失败 e2e；断言所有 outer/inner tabs 的真实可达矩形、点击切换、内容滚动和左侧底部可达。禁止仅字符串 CSS 断言。
- [x] 修改 `ProjectCollectionView.vue`、`main.css`、`CollectionTaskPanel.vue` 的布局所有权；不再让工作台继承 standalone clamp 高度，不用 !important 掩盖冲突。
- [x] 修改 `SemanticResultPanel.vue` 的摘要折叠和 tabs 顺序，不复制输出组件。
- [x] 重跑 e2e、定向 Vitest、`pnpm ts:check`、`pnpm build`，保留原有未提交修改，不做 root clean 或 git commit。

## 验收边界
足高桌面 task-panel bottom 不超出中心列，滚动后外层卡头/状态/tabs 矩形不移动；短屏可滚动到所有 tabs 与左侧末项。内部 tabs 无需越过命令列表；展开摘要后原命令与内嵌行号证据仍可访问。最终真实服务验收交主代理。

## 验证结果
- 红测：三个视口失败，内部 tabs 初始距离约1436px。
- 绿测：三视口含记录页 standalone 回归重复6/6通过，追加默认收起断言后3/3通过；使用真实矩形与可点击性，并等待折叠动画几何稳定。
- 定向Vitest 40/40；ts:check、build通过。定向ESLint无错误，有189条格式warning；构建有依赖PURE注释与chunk体积warning。
- 最终真实48181验收由主代理执行。
