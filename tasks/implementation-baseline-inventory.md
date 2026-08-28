# NPDMS存量实现基线清单

| 项目 | 内容 |
|---|---|
| 状态 | `BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED` |
| 规格来源 | `docs/specification-baseline/manifest.json` |
| 机器清单 | `tasks/implementation-baseline-inventory.json` |
| 使用方式 | 最长`codePaths`匹配优先；具体对象分类覆盖模块级未核实分类 |

## 已确认差异

| 对象 | 分类 | 当前结论 | Requirement |
|---|---|---|---|
| `CustomerMasterCurrentRuntime` | `ADAPTED` | F-CUS-001 已建立独立 CUS 当前写 Owner、V87～V89 前向迁移、新客户工作台、跨域摘要与删除守卫；创建、删除成功和恢复浏览器闭环仍受权威分类与真实数据阻断 | `CUS-03` |
| `LegacyCustomerHistoryReadOnly` | `REUSED` | 旧 project 客户入口只保留历史列表和详情，禁止创建、更新、删除、停用、恢复及地点写入，也不代理新 Owner 写操作 | `CUS-03` |
| `PlatformAuthorizationGrant` | `ADAPTED` | F-PROJ-003 已建立平台授权事实运行时，保留 `AuthorizationGrant` 当前实现及其正式授权边界 | `AUT-01`、`AUT-02`、`INT-06`、`INT-09` |
| `PlatformCommandFacts` | `ADAPTED` | 平台共享命令设施保留幂等、操作审计和 Outbox 事实，作为已实现命令链的基础能力 | `NFR-01`、`PM-01` |
| `CutExecution` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 已退出当前构建、菜单、API和前端入口；旧表不删除，只有后续逐字段证明的P6闭环事实才可迁入闭环记录 | `CUT-01`、`CUT-06` |
| `CutObservation` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 稳定观察状态机和运行入口已退役；旧表仅保留待判定数据，不进入当前写模型或迁移 | `CUT-01`、`CUT-06` |
| `SrvReport` | `VALID_V2_POSTPONED` | 是有效巡检报告能力，保留代码但不进入九月Feature和UAT | `INS-05` |
| `SrvMaintenance` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 独立维保经营生命周期运行入口已退役；旧表冻结为兼容来源，仅逐字段证明的客观维保事实后续迁入`ast_maintenance_fact` | `EQP-02` |
| `MaintenanceTransition` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 转维保运行入口已退役；旧表不改，仅逐字段证明的交接事实后续迁入`acc_service_handover`，续保字段不进入新模型 | `ACC-06` |
| MES生产工单 | `PLATFORM_UPSTREAM_UNCHANGED` | 基础平台生产能力，不属于PMS工单排除范围 | 基础平台 |
| `ProjectTemplateFoundation` | `V1_7_REVALIDATION_REQUIRED` | V1.7 模板基座代码与验收证据仅作存量输入；必须按 V1.8 Feature Spec 逐项审计与改造 | `PM-03` |
| `ProjectManualCreation` | `V1_7_REVALIDATION_REQUIRED` | V1.7 手工创建代码与验收证据仅作存量输入；不自动继承已实现结论 | `PM-01` |
| `ProjectTreeAdaptedRuntime` | `ADAPTED` | 项目树、拆分、进度、闭环、Commerce范围与响应式详情页已按F-PROJ-002 V1.8改造并验收 | `PM-02` |
| `ProjectTreeLegacySchema` | `REPLACED` | V60只保留不可变历史；V1.8物理载体由V70/V71前向替代 | `PM-02` |
| `ProjectTreeLegacyDemo` | `RETIRED` | V61只保留不可变历史；V1.8组合场景、权限和流水种子由V72～V76承接 | `PM-02` |
| `ProjectTreeReusedTemplate` | `REUSED` | V62发布的S0～S6完整模板经V75绑定给V1.8验收根项目继续复用 | `PM-02` |

## 未核实存量面

项目、工程、割接、服务、资产、外包、分析、集成模块及旧SQL迁移已全部登记为`BLOCKED_BY_SPEC`范围。该分类不表示需求缺失，而表示旧实现尚未逐Feature证明与当前Requirement一致；后续只能依据当前Feature Spec逐项转为可复用、重构、后置或排除。

旧SQL迁移包含多个业务对象，不能按文件整体继续沿用。后续前向迁移必须按当前领域表结构拆分，且不得通过重命名旧`pms_`表冒充模型对齐。

`pms_cut_execution`和`pms_cut_observation`的历史表及既有数据不在本次运行时退役中删除。P6事实提取、字段证据判定和最终物理收缩必须作为独立迁移任务评审；在此之前不得恢复菜单、API、前端写入口或CutTask旁路动作。

`pms_srv_maintenance`和`pms_acc_maintenance_transition`的历史表及既有数据不在本次运行时退役中删除。客观维保事实迁入`ast_maintenance_fact`、交接事实迁入`acc_service_handover`的字段级证据判定属于独立迁移任务；在此之前不得恢复菜单、API或前端写入口，续保字段不得进入新写模型。
