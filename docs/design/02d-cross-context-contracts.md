# SDS Phase 1：跨 Context 契约

| 契约 | Producer | Consumer | 语义 |
|---|---|---|---|
| ImplementationEvidencePublished | Implementation Execution | Acceptance & Closure | 实施证据、来源版本、哈希和检查快照已发布 |
| ImplementationReadinessSnapshot | Implementation Execution | Cutover | 割接前实施门禁快照，仅供 CUT 校验 |
| CollectionTaskRequested | Device Access & Collection | Implementation Execution/Cutover/Inspection | 受控下发任务；不传永久凭证权限或明文密码 |
| CollectionResultCallback | 外部采集平台 | Device Access & Collection | 回调原值、签名、外部任务号和结果引用；重复回调幂等 |
| QualitySafetyGateChanged | Implementation Execution | Project/ACC | 检查通过、整改中或阻断的门禁事实 |
| DeviceAssigned | AST | Implementation Execution/Project | 设备当前最具体项目归属及生效版本 |
| MasterDataSynchronized | CRM/ERP/MES/ITR/Integration ACL | Customer & Relationship/Asset Management/Contract & Fulfillment | 来源主键、来源版本、同步时间、同步状态和本地副本版本 |
| ProjectClosureCompleted | Acceptance & Closure | Project Delivery/Service Operations | 闭环门禁快照、闭环版本和交接事实；不直接写 Project 状态 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。
