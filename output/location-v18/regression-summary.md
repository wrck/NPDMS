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

## 2026-08-24 总门禁补充复验

原始回归时登记的全仓类型错误已在后续独立切片中完成收敛，保留上文作为当时的历史记录。本次从当前工作树重新执行：

- 规格快照：Codex 随附 Python 执行 `scripts/validate_specification_baseline.py`，结果 `SUMMARY PASS snapshot matches manifest and allowlist`。
- PMS 模块边界：显式传入当前仓库 `-RepositoryRoot`，结果 `PMS module boundary verification passed.`。
- 后端全量回归：`mvn -pl yudao-server -am test`，30 个 Reactor 模块全部 `SUCCESS`，总耗时 56.310 秒。
- 前端合同测试：项目创建、地点合同和地点选择器共 15/15 通过。
- 前端类型检查：`pnpm.cmd ts:check` 通过，0 个 TypeScript 错误。
- 前端构建：`pnpm.cmd build:local` 成功，8158 个模块完成转换，耗时 25.90 秒；仍保留上文已登记的 legacy CSS `*zoom` 非阻断警告。

因此计划总完成条件第 10 项的全局类型检查缺口已经关闭。本补充只证明当前 Feature 实施计划的工程验证完成，不构成 UAT、发布 Gate、治理 GO 或 Release GO。
