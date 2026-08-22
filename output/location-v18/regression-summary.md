# V1.8 组织与地点回归摘要

验证日期：2026-08-23

## 已通过

- PMS 模块边界：`verify-pms-module-boundaries.ps1` 通过。脚本默认根参数在当前 PowerShell 参数绑定阶段不可用，改用其公开的 `-RepositoryRoot` 参数执行。
- 边界整改：新增非空 `pms-module-asset-api`，PROJ 与 IMP 只依赖 AST 公开位置契约；AST 实现仍由 `pms-module-asset` 持有。
- 后端聚合：`mvn.cmd -pl yudao-server -am test`，30 个 Reactor 模块全部 `SUCCESS`，总耗时 1 分 49 秒。
- 真实 MySQL 定向集成：12/12 通过，详见 `mysql-acceptance.md`。
- 前端定向 Node 契约：14/14 通过，覆盖项目创建状态、组织与多站点范围、地点维护、任意深度位置树、行政区划映射及设备位置只读契约。
- 前端构建：`pnpm.cmd build:local` 成功，8158 个模块完成转换，耗时 33.33 秒。

## 已登记的非当前阻断

`pnpm.cmd ts:check` 未通过。输出为 251 行既有全仓类型错误，首个错误位于 `src/components/PmsEntitySelect/index.vue`；错误清单还包含 AI、BPM、CRM、IoT、MES 及多个既有 PMS 页面。本次组织、地点、项目创建及地点选择器改造路径未出现在错误清单中。

构建同时保留一条既有 Lightning CSS 警告：旧式 `.clearfix` 中的 `*zoom: 1` 无法被 minifier 解析；该警告未阻断产物生成。

上述两项已作为基线技术债记录，本任务不扩大范围修复。该摘要不构成 UAT 或正式发布批准。
