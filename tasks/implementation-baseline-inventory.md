# NPDMS存量实现基线清单

| 项目 | 内容 |
|---|---|
| 历史清单枚举 | `BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED`；仅为机器清单兼容分类，不是当前全局准入状态 |
| 当前规格来源 | `docs/baseline/prd-v1.8.md`、`docs/engineering/00-engineering-chain.md`及相关Feature Spec |
| 机器清单 | `tasks/implementation-baseline-inventory.json` |
| 使用方式 | 最长`codePaths`匹配优先；具体对象分类覆盖模块级未核实分类 |
| 当前实现现状审查 | [2026-09-08逐需求证据](delivery-units/DU-20260908-REQUIREMENT-IMPLEMENTATION-REVIEW.md#逐需求证据)；本清单只说明复用/退役，不维护Feature或Requirement完成状态 |

## 已确认差异

| 对象 | 分类 | 当前结论 | Requirement |
|---|---|---|---|
| `CustomerMasterCurrentRuntime` | `ADAPTED` | F-CUS-001本地主档已完成；2026-08-28真实浏览器证据覆盖创建、删除/恢复与权限，8月26日局部验证的分类/数据阻断不再是当前结论。CRM同步及CUS-01/02/04仍不在该Feature完成范围 | `CUS-03` |
| `LegacyCustomerHistoryReadOnly` | `REUSED` | 旧 project 客户入口只保留历史列表和详情，禁止创建、更新、删除、停用、恢复及地点写入，也不代理新 Owner 写操作 | `CUS-03` |
| `PlatformAuthorizationGrant` | `ADAPTED` | F-PROJ-003 已建立平台授权事实运行时，保留 `AuthorizationGrant` 当前实现及其正式授权边界 | `AUT-01`、`AUT-02`、`INT-06`、`INT-09` |
| `PlatformCommandFacts` | `ADAPTED` | 平台共享命令设施保留幂等、操作审计和 Outbox 事实，作为已实现命令链的基础能力 | `NFR-01`、`PM-01` |
| `CommerceCurrentRuntime` | `ADAPTED` | COM可信摄入、合同/订单副本与范围分配已有生产增量；F-COM-001及修订018消费者差量仍在途，ADAPTED不代表Requirement Done | `COM-01` |
| `PlatformDynamicFormRuntime` | `ADAPTED` | F-PLT-002已完成共享表单子闭环；SOL-01全部准备数据业务消费者不随底座一并完成 | `SOL-01` |
| `PlatformFileRuntime` | `ADAPTED` | F-PLT-001文件底座有历史Done与生产实现；当前Spec重验证要求保留，不外推各业务来源归档或当前PLT-02全部义务完成 | `PLT-02` |
| `CutExecution` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 已退出当前构建、菜单、API和前端入口；旧表不删除，只有后续逐字段证明的P6闭环事实才可迁入闭环记录 | `CUT-01`、`CUT-06` |
| `CutObservation` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 稳定观察状态机和运行入口已退役；旧表仅保留待判定数据，不进入当前写模型或迁移 | `CUT-01`、`CUT-06` |
| `SrvReport` | `VALID_V2_POSTPONED` | 是有效巡检报告能力，保留代码但不进入九月Feature和UAT | `INS-05` |
| `SrvMaintenance` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 独立维保经营生命周期运行入口已退役；旧表冻结为兼容来源，仅逐字段证明的客观维保事实后续迁入`ast_maintenance_fact` | `EQP-02` |
| `MaintenanceTransition` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 转维保运行入口已退役；旧表不改，仅逐字段证明的交接事实后续迁入`acc_service_handover`，续保字段不进入新模型 | `ACC-06` |
| MES生产工单 | `PLATFORM_UPSTREAM_UNCHANGED` | 基础平台生产能力，不属于PMS工单排除范围 | 基础平台 |
| `ProjectTemplateFoundation` | `ADAPTED` | 与机器清单一致：V1.8模板基座已有改造及历史子闭环；修订018的配置化校验、独立验收及阶段推进差量仍待，不再误标为仅有V1.7代码 | `PM-03` |
| `ProjectManualCreation` | `ADAPTED` | 与机器清单一致：F-PROJ-001手工建项主体及历史Done已存在；PM-01仍按当前Spec保持PARTIAL和差量重验证 | `PM-01` |
| `ProjectTreeAdaptedRuntime` | `ADAPTED` | F-PROJ-002项目树、拆分、进度与响应式详情已有历史改造/验收；修订017后当前覆盖重验证保留，不从历史完成外推当前全部义务 | `PM-02` |
| `ProjectTreeLegacySchema` | `REPLACED` | V60只保留不可变历史；V1.8物理载体由V70/V71前向替代 | `PM-02` |
| `ProjectTreeLegacyDemo` | `RETIRED` | V61只保留不可变历史；V1.8组合场景、权限和流水种子由V72～V76承接 | `PM-02` |
| `ProjectTreeReusedTemplate` | `REUSED` | V62发布的S0～S6完整模板经V75绑定给V1.8验收根项目继续复用 | `PM-02` |

## 未核实存量面

机器清单对尚未被对应Feature认证可复用的聚合面保留历史枚举`BLOCKED_BY_SPEC`，具体已对齐对象由更长路径的`ADAPTED/REUSED`项覆盖。这不是“所有模块没有实现”或“所有业务规格缺失”的结论，也不是全项目阻断。本轮逐需求审查已区分生产实现、受控服务、旧CRUD和未见实现；没有据此批量放行旧模块复用。真实业务缺失/冲突仍按工程链另行登记，不从该旧枚举推导新Question。

旧SQL迁移包含多个业务对象，不能按文件整体继续沿用。后续前向迁移必须按当前领域表结构拆分，且不得通过重命名旧`pms_`表冒充模型对齐。

`pms_cut_execution`和`pms_cut_observation`的历史表及既有数据不在本次运行时退役中删除。P6事实提取、字段证据判定和最终物理收缩必须作为独立迁移任务评审；在此之前不得恢复菜单、API、前端写入口或CutTask旁路动作。

`pms_srv_maintenance`和`pms_acc_maintenance_transition`的历史表及既有数据不在本次运行时退役中删除。客观维保事实迁入`ast_maintenance_fact`、交接事实迁入`acc_service_handover`的字段级证据判定属于独立迁移任务；在此之前不得恢复菜单、API或前端写入口，续保字段不得进入新写模型。
