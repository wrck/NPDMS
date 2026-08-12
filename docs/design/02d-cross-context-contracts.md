# SDS Phase 1：跨 Context 契约

| 契约 | Producer | Consumer | 语义 |
|---|---|---|---|
| ImplementationEvidencePublished | Implementation Execution | Acceptance & Closure | 实施证据、来源版本、哈希和检查快照已发布 |
| ImplementationReadinessSnapshot | Implementation Execution | Cutover | 割接前实施门禁快照，仅供 CUT 校验 |
| CollectionTaskRequested | PLT采集能力 | Implementation Execution/CUT/SRV | 受控下发任务；不传永久凭证权限或明文密码 |
| CollectionResultCallback | 外部采集平台 | PLT采集能力 | 回调原值、签名、外部任务号和结果引用；重复回调幂等 |
| QualitySafetyGateChanged | Implementation Execution | Project/ACC | 检查通过、整改中或阻断的门禁事实 |
| DeviceAssigned | AST | Implementation Execution/Project | 设备当前最具体项目归属及生效版本 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。
