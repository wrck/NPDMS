# SDS Phase 1：Context Map

## 正式 Context 名称

`CRM/ERP/MES/ITR → Integration / ACL → Project Delivery → Preparation & Solution → Implementation Execution → Acceptance & Closure`

实施执行域向下依赖 `Device Access & Collection` 提供的 `CollectionTask` 和凭证授权，不拥有设备连接或原始采集执行；割接与巡检分别通过受控业务契约复用该 Context。现有采集平台可作为该 Context 的模块或子应用集成，不重复建设采集引擎。

## Context 关系

| 上游 | 下游 | 关系 | 允许内容 |
|---|---|---|---|
| Preparation & Solution | Implementation Execution | Customer/Supplier | 下发已批准方案、计划和设备范围引用 |
| Implementation Execution | Acceptance & Closure | Published Language | 发布实施证据、质量/安全检查快照和阶段完成事实 |
| Implementation Execution | Platform Governance | Customer/Supplier | 请求文件、待办、审计、字典和权限校验 |
| Implementation Execution | Device Access & Collection | Customer/Supplier | 以任务级授权下发采集请求，接收结果引用；不接管原始执行 |
| Implementation Execution | Cutover | Open Host Service | 提供割接上线门禁快照，不修改割接内部状态 |
| Cutover | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Inspection | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Work Order & Time | Project Delivery | Published Language | 发布工单、责任区间和工时快照 |
| Customer & Relationship | Project Delivery / Asset Management | Published Language | 提供客户关系和资产关系查询 |
| Asset Management | Service Operations | Published Language | 提供设备服务状态和设备档案查询 |

Context Map 只展示 bounded context 或外部系统，不把 `CollectionTask`、`DeliveryEvidence`、`CutoverTask` 或设备凭证当作 Context 节点。`Platform Governance` 仅作为基础平台能力集合标注，不拥有业务交易事实。
