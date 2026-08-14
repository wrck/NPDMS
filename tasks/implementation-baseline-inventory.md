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
| `CutExecution` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 已退出当前构建、菜单、API和前端入口；旧表不删除，只有后续逐字段证明的P6闭环事实才可迁入闭环记录 | `CUT-01`、`CUT-06` |
| `CutObservation` | `RUNTIME_RETIRED_DATA_PENDING_EVIDENCE` | 稳定观察状态机和运行入口已退役；旧表仅保留待判定数据，不进入当前写模型或迁移 | `CUT-01`、`CUT-06` |
| `SrvReport` | `VALID_V2_POSTPONED` | 是有效巡检报告能力，保留代码但不进入九月Feature和UAT | `INS-05` |
| `SrvMaintenance` | `SEMANTIC_REWORK` | 停止独立维保经营生命周期；客观维保事实后续归入资产领域 | `EQP-02` |
| `MaintenanceTransition` | `SEMANTIC_REWORK` | 按持续服务交接重构为`ServiceHandover`，隔离续保字段 | `ACC-06` |
| MES生产工单 | `PLATFORM_UPSTREAM_UNCHANGED` | 基础平台生产能力，不属于PMS工单排除范围 | 基础平台 |

## 未核实存量面

项目、工程、割接、服务、资产、外包、分析、集成模块及旧SQL迁移已全部登记为`BLOCKED_BY_SPEC`范围。该分类不表示需求缺失，而表示旧实现尚未逐Feature证明与当前Requirement一致；后续只能依据当前Feature Spec逐项转为可复用、重构、后置或排除。

旧SQL迁移包含多个业务对象，不能按文件整体继续沿用。后续前向迁移必须按当前领域表结构拆分，且不得通过重命名旧`pms_`表冒充模型对齐。

`pms_cut_execution`和`pms_cut_observation`的历史表及既有数据不在本次运行时退役中删除。P6事实提取、字段证据判定和最终物理收缩必须作为独立迁移任务评审；在此之前不得恢复菜单、API、前端写入口或CutTask旁路动作。
