# SDS Phase 1：Context Map

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：PRD V1.7 附录 A.1 的全部 104 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；业务 Owner 已签署，见 `docs/design/phase-1-domain-ownership.md`
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 正式 Context 名称

`CRM/ERP/MES/ITR → 集成适配层 / ACL → Project Delivery → Preparation & Solution → Implementation Execution → Acceptance & Closure`

实施执行域向下依赖 `Device Access & Collection` 提供的 `CollectionTask` 和凭证授权，不拥有设备连接或原始采集执行；割接与巡检分别通过受控业务契约复用该 Context。V1 由现有采集平台子应用承载该 Context 的执行能力，后续可演进为内部模块，不重复建设采集引擎。

## Context 关系

| 上游 | 下游 | 关系 | 允许内容 |
|---|---|---|---|
| Preparation & Solution | Implementation Execution | Customer/Supplier | 下发已批准方案、计划和设备范围引用 |
| Implementation Execution | Acceptance & Closure | Published Language | 发布实施证据、质量/安全检查快照和阶段完成事实 |
| Implementation Execution | 基础平台能力 | Customer/Supplier | 请求文件、待办、审计、字典和权限校验 |
| Implementation Execution | Device Access & Collection | Customer/Supplier | 以任务级授权下发采集请求，接收结果引用；不接管原始执行 |
| Implementation Execution | Cutover | Open Host Service | 提供割接上线门禁快照，不修改割接内部状态 |
| Cutover | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Inspection | Device Access & Collection | Customer/Supplier | 复用统一采集任务与回调契约 |
| Cutover | Project Delivery | Published Language | 发布割接保障任务进度、责任区间和关闭结果引用；不替代割接执行结论 |
| Customer & Relationship | Project Delivery / Asset Management | Published Language | 提供客户关系和资产关系查询 |
| Asset Management | Service Operations | Published Language | 提供设备服务状态和设备档案查询 |

Context Map 只展示 bounded context 或外部系统，不把 `CollectionTask`、`DeliveryEvidence`、`CutoverTask` 或设备凭证当作 Context 节点。基础平台能力仅作为横向能力集合标注，不拥有业务交易事实；集成适配层不拥有外部系统或业务域事实。

V1.7不再包含`Work Order & Time` Context。历史工单、工时、附件、审批和审计证据通过迁移契约只读保留，不暴露当前流转能力。
