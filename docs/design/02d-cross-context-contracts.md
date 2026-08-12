# SDS Phase 1：跨 Context 契约

> 文档状态：`BASELINE`
> 适用基线：PRD V1.6（`docs/baseline/prd-v1.6.md`）
> Requirement ID：PRD V1.6 附录 A.1 的全部 115 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；业务 Owner 已签署，见 `docs/design/phase-1-domain-ownership.md`
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 契约 | Producer | Consumer | 语义 |
|---|---|---|---|
| ImplementationEvidencePublished | Implementation Execution | Acceptance & Closure | 实施证据、来源版本、哈希和检查快照已发布 |
| ImplementationReadinessSnapshot | Implementation Execution | Cutover | 割接前实施门禁快照，仅供 CUT 校验 |
| CollectionTaskRequested | Implementation Execution/Cutover/Inspection | Device Access & Collection | 业务 Context 请求受控下发任务；只传业务对象、设备、命令模板和授权引用，不传永久凭证权限或明文密码 |
| CollectionTaskAccepted | Device Access & Collection | Implementation Execution/Cutover/Inspection | 采集任务已通过服务端授权校验并被接受，返回统一任务号和当前下发状态；不表示外部执行或业务处理成功 |
| CollectionResultCallback | 外部采集平台 | Device Access & Collection | 回调原值、签名、外部任务号和结果引用；重复回调幂等 |
| QualitySafetyGateChanged | Implementation Execution | Project/ACC | 检查通过、整改中或阻断的门禁事实 |
| DeviceAssigned | AST | Implementation Execution/Project | 设备当前最具体项目归属及生效版本 |
| MasterDataSynchronized | CRM/ERP/MES/ITR/Integration ACL | Customer & Relationship/Asset Management/Contract & Fulfillment | 来源主键、来源版本、同步时间、同步状态和本地副本版本 |
| ProjectClosureCompleted | Acceptance & Closure | Project Delivery/Service Operations | 闭环门禁快照、闭环版本和交接事实；不直接写 Project 状态 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。
