# NPDMS 项目任务 WBS

> 用途：任务跟踪与项目进度汇报的统一视图。
> 事实来源：本文件是工程链（`docs/engineering/00-engineering-chain.md` V1.8）的跟踪视图，不产生业务规则，不替代 PRD、SDS、Feature Spec 与 gate-status；状态冲突时以后者为准。
> 基线依据：规格快照 `docs/specification-baseline/manifest.json`（源提交 `8c047604bc54d0111c12da67b971e9149bf0c0ee`）；PRD V1.7 正式需求 103 项（V1 55 / V2 48；P0 43 / P1 58 / P2 2）。
> 维护规则：每个可独立验收的任务完成后更新状态列；里程碑结论同步引用对应 `gate-status.md` 或任务记录，不在此文件单独放行。

## 0. 当前状态快照（2026-08-17）

| 项 | 状态 |
|---|---|
| PRD Baseline | BASELINE（V1.7，103 项正式需求） |
| SDS Phase 1 | APPROVED / READY_FOR_PHASE_2 |
| SDS Phase 2 | APPROVED / READY_FOR_PHASE_3 |
| SDS Phase 3 | APPROVED / READY_FOR_SDS_BASELINE（P3-E09 MODEL_BASELINE_READY） |
| 规格基线同步 | PASS（109 文件锁定，校验通过） |
| 存量实现状态 | `BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED` |
| Feature Ready | NO（纠偏未完成前不得开始首发 Feature） |
| 关键阻断 | 存量纠偏 3 项（WBS 2）；P3-E08 前端类型债务（WBS 3） |

## 1. WBS 总览

| 编号 | 任务包 | 内容 | 前置 | 状态 | 进度 |
|---|---|---|---|---|---|
| WBS-1 | 规格基线与工程门禁 | 快照同步、校验工具、三阶段门禁 | — | DONE | 100% |
| WBS-2 | 存量实现纠偏 | 3 个独立纠偏计划 + 复核 | WBS-1 | IN_PROGRESS | 40% |
| WBS-3 | 前端类型债务治理 | P3-E08，182 项类型错误清零 | — | NOT_STARTED | 0% |
| WBS-4 | SDS Baseline 固化 | 总册基线提交与门禁登记 | WBS-1 | READY | 0% |
| WBS-5 | Vertical Slice 首发闭环 | 认证→客户→项目→模板→权限→审计端到端 | WBS-2、WBS-3、WBS-4 | NOT_STARTED | 0% |
| WBS-6 | V1 领域交付（55 项） | S0–S6 主链 + 支撑域 V1 Feature | WBS-5 | NOT_STARTED | 0% |
| WBS-7 | V2 领域交付（48 项） | 巡检、资源外包、资产扩展等 V2 Feature | WBS-6（P0 部分） | NOT_STARTED | 0% |
| WBS-8 | 系统验证与发布 | Deployment、SIT、UAT、Release | WBS-6 | NOT_STARTED | 0% |
| WBS-9 | 历史数据迁移 | AI-MIG-000，按真实批次条件触发 | 独立授权 | OPEN（等待批次） | 0% |

进度口径：任务包进度 = 已关闭子任务数 / 子任务总数；DONE 需引用提交或门禁证据。

## 2. 任务包分解

### WBS-1 规格基线与工程门禁（DONE）

| 编号 | 任务 | 产出/证据 | 状态 |
|---|---|---|---|
| 1.1 | 规格基线同步、校验与冲突保护工具 | 提交 `e6f97e5`；`scripts/*specification_baseline*` | DONE |
| 1.2 | 锁定 109 文件本地规格快照 | 提交 `c3ed12f`；manifest 校验 PASS | DONE |
| 1.3 | 工程入口切换到锁定基线 | 提交 `0fc43eb`；`implementation-baseline-status.md` | DONE |
| 1.4 | SDS Phase 1–3 门禁关闭与快照锁定 | 各 `gate-status.md`；提交 `3b162b8` 等 | DONE |
| 1.5 | 全量验证（后端 30 模块构建、前端生产构建、33/33 自动化测试） | `tasks/implementation-baseline-status.md` 2026-08-15 记录 | DONE |

### WBS-2 存量实现纠偏（IN_PROGRESS，当前最高优先级）

> 完成前不得开始首发 Feature 实现（Feature Ready = NO）。

| 编号 | 任务 | 内容 | 需求 | 状态 |
|---|---|---|---|---|
| 2.1 | 割接当前模型纠偏（`npdms-cutover-current-model-correction`） | `CutExecution`/`CutObservation` 运行面退役收尾：退役校验、旧表数据保留待判、禁止旁路恢复；运行证据防绕过修复已提交（`7e4e6ca`、`51ba009`） | CUT-01、CUT-06 | IN_PROGRESS |
| 2.2 | 资产维保事实重构（`npdms-asset-maintenance-fact-rework`） | `SrvMaintenance` 停止独立维保生命周期，客观维保事实归入资产领域；代码、菜单、API、前向迁移纠偏 | EQP-02 | NOT_STARTED |
| 2.3 | 服务交接重构（`npdms-service-handover-rework`） | `MaintenanceTransition` 重构为 `ServiceHandover`，隔离续保字段 | ACC-06 | NOT_STARTED |
| 2.4 | 纠偏复核与状态解除 | `EXCLUDED_CURRENT`/`SEMANTIC_REWORK` 全部关闭；存量清单复核；`Feature Ready` 转 YES | — | NOT_STARTED |

### WBS-3 前端类型债务治理（NOT_STARTED，前端 Feature 强制门禁）

| 编号 | 任务 | 内容 | 状态 |
|---|---|---|---|
| 3.1 | 既有 PMS 页面类型契约修复 | P3-E08 实测 182 项类型错误，分域分批修复（页面类型契约、组件导入、字典常量、未使用变量） | NOT_STARTED |
| 3.2 | `corepack pnpm ts:check` 清零并纳入验证 | 禁止关闭类型检查或放宽 TS 规则规避；纳入后续 Feature DoD | NOT_STARTED |

### WBS-4 SDS Baseline 固化（READY）

| 编号 | 任务 | 内容 | 状态 |
|---|---|---|---|
| 4.1 | SDS 总册基线提交 | Phase 1–3 全部 BASELINE 后形成 SDS Baseline（60 表 / 1,240 列 / 447 项约束索引，哈希 `5EB9742F…4249`） | READY |
| 4.2 | 追溯矩阵基线化 | 103 项 Requirement → SDS 链路固化为 Feature 输入 | READY |

### WBS-5 Vertical Slice 首发闭环（NOT_STARTED）

> 工程链第 10 节指定范围，须先形成独立 Feature Spec 并列出准确 Requirement ID 与验收标准。

| 编号 | 任务 | 内容 | 状态 |
|---|---|---|---|
| 5.1 | Feature Spec 与 Technical Plan | 从追溯矩阵选取需求、定义验收标准 | NOT_STARTED |
| 5.2 | 认证/登录 + 客户基础数据 | 平台骨架接入、客户主数据闭环 | NOT_STARTED |
| 5.3 | 手动创建项目 + 项目模板选择 | 模板实例化阶段/里程碑/任务/交付件 | NOT_STARTED |
| 5.4 | 人工指派服务经理 + 项目详情/项目树 | 项目树非固定层级实现 | NOT_STARTED |
| 5.5 | 权限 + 审计闭环验证 | UI→API→Domain→DB→Permission→Audit→Test 全链贯通 | NOT_STARTED |

### WBS-6 V1 领域交付（NOT_STARTED，55 项：P0 38 / P1 17）

> 按 Owner 组织任务包；每 Feature 走 DoR→实现→DoD 循环；P0 优先。

| 编号 | 任务包 | Owner | V1 需求（P0） | 状态 |
|---|---|---|---|---|
| 6.1 | 项目治理（S0） | PROJ | PM-01~PM-04、PM-07、PM-08、PM-10、PM-11（P0：PM-01~03） | NOT_STARTED |
| 6.2 | 交付准备与方案（S1–S3） | SOL | PRE-01、02、04；PLN-01、04；SCH-01、05（P0：PRE-02/04、PLN-01/04、SCH-01/05） | NOT_STARTED |
| 6.3 | 现场实施（S4） | IMP | EXE-01~06（P0：EXE-01~04、06） | NOT_STARTED |
| 6.4 | 验收与项目闭环（S5–S6） | ACC | ACC-01~04、CLO-01~04（P0：7 项） | NOT_STARTED |
| 6.5 | 割接管理 | CUT | CUT 领域 V1 9 项（P0 8 项） | NOT_STARTED |
| 6.6 | 客户与服务关系 | CUS | CUS V1 3 项 | NOT_STARTED |
| 6.7 | 资产管理 | AST | AST V1 4 项 | NOT_STARTED |
| 6.8 | 合同订单履约 | COM | COM V1 1 项 | NOT_STARTED |
| 6.9 | 平台公共能力 | PLT | PLT V1 7 项（P0 7 项） | NOT_STARTED |

### WBS-7 V2 领域交付（NOT_STARTED，48 项）

| 编号 | 任务包 | Owner | V2 需求 | 状态 |
|---|---|---|---|---|
| 7.1 | 巡检服务运营 | SRV | SRV 10 项（`INS-05/SrvReport` 为有效后置能力，不进九月首发） | NOT_STARTED |
| 7.2 | 资源与外包 | RES | RES 7 项 | NOT_STARTED |
| 7.3 | 项目治理 V2 | PROJ | PM-05、06、09 等 4 项 | NOT_STARTED |
| 7.4 | 交付准备与方案 V2 | SOL | PRE-03、05；PLN-02/03；SCH-02~04 等 6 项 | NOT_STARTED |
| 7.5 | 资产管理 V2 | AST | AST V2 6 项 | NOT_STARTED |
| 7.6 | 现场实施 V2 | IMP | EXE V2 2 项 | NOT_STARTED |
| 7.7 | 割接 V2 | CUT | CUT V2 1 项 | NOT_STARTED |
| 7.8 | 客户/合同/平台/分析/知识 V2 | CUS/COM/PLT/ANA/KNO | CUS 2、COM 1、PLT 5、ANA 2、KNO 1 | NOT_STARTED |

### WBS-8 系统验证与发布（NOT_STARTED）

| 编号 | 任务 | 内容 | 门禁 | 状态 |
|---|---|---|---|---|
| 8.1 | 部署准备 | 制品、配置契约、Schema 前向迁移、环境准备 | Deployment | NOT_STARTED |
| 8.2 | SIT | 真实系统联调 | SIT | NOT_STARTED |
| 8.3 | UAT | 业务验收（真实浏览器 UI 闭环） | UAT | NOT_STARTED |
| 8.4 | 生产发布 | DoD、发布与回退条件关闭 | Release | NOT_STARTED |
| 8.5 | 专项验收（按需） | 性能（P3-E06）、可观测（P3-E05）、恢复（P3-E03）、设备凭证（P3-E04） | 对应下游门禁 | NOT_STARTED |

### WBS-9 历史数据迁移（OPEN，条件触发）

| 编号 | 任务 | 内容 | 状态 |
|---|---|---|---|
| 9.1 | 迁移批次门禁定义 | 真实批次形成后按源范围、水位、程序、校验、演练、对账、回退、授权设计最小门禁（AI-MIG-000） | OPEN |
| 9.2 | 批次执行与验证 | 批准窗口内执行，`VERIFIED` 后方可切换 | BLOCKED（等待批次） |

## 3. 里程碑与汇报口径

| 里程碑 | 判定 | 当前 |
|---|---|---|
| M1 规格基线就绪 | WBS-1 DONE | 已达成 |
| M2 实现基线就绪（Feature Ready） | WBS-2、WBS-3 DONE | 未达成（当前阻断） |
| M3 SDS Baseline | WBS-4 DONE | 可启动 |
| M4 首发闭环贯通 | WBS-5 DONE | 待 M2 |
| M5 V1 功能完成 | WBS-6 全部 Feature DoD | 待 M4 |
| M6 V2 功能完成 | WBS-7 全部 Feature DoD | 待 M5 |
| M7 验收通过 | SIT/UAT PASS | 待 M5/M6 |
| M8 生产发布 | Release APPROVED | 待 M7 |

指标（引自工程链第 11 节，从现有工具采集）：需求追溯覆盖率、设计返工率、缺陷逃逸数、Feature Lead Time、无效门禁阻塞数。

## 4. 状态字典

| 状态 | 含义 |
|---|---|
| DONE | 已完成并有提交/门禁证据 |
| IN_PROGRESS | 执行中 |
| READY | 前置满足，可启动 |
| NOT_STARTED | 未开始（前置未满足或待排期） |
| OPEN | 条件触发型任务，等待真实输入 |
| BLOCKED / BLOCKED_BY_SPEC | 被阻断 / 需回到规格处理 |
