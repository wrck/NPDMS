# SDS Phase 1：跨 Context 契约

> 文档状态：`IN_REVIEW`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8机器差量校验已完成，待fresh-context独立复审
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ImplementationEvidencePublished | EXE-01～EXE-06、IMP-01、ACC-04 | Implementation Execution | Acceptance & Closure | 实施证据、来源版本、哈希和检查快照已发布 |
| ImplementationReadinessSnapshot | EXE-06、CUT-01 | Implementation Execution | Cutover | 割接前实施门禁快照，仅供 CUT 校验 |
| CollectionTaskRequested | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Implementation Execution/Cutover/Inspection | Device Access & Collection | 业务 Context 请求受控下发任务；只传业务对象、设备、命令模板和授权引用，不传永久凭证权限或明文密码 |
| CollectionTaskAccepted | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Device Access & Collection | Implementation Execution/Cutover/Inspection | 采集任务已通过服务端授权校验并被接受，返回统一任务号和当前下发状态；不表示外部执行或业务处理成功 |
| CollectionResultCallback | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | 外部采集平台 | Device Access & Collection | 回调原值、签名、外部任务号和结果引用；重复回调幂等 |
| CollectionResultAvailable | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Device Access & Collection | Implementation Execution/Cutover/Inspection | 返回CollectionTask、来源业务/项目/设备、原始状态和结果引用；CUT按清单版本与采集项引用回填P3工作台并独立判定业务结果 |
| ConfigurationLogPublished | EXE-03、EXE-04、EQP-02 | Implementation Execution | Asset Management | 实施域发布采集业务结果、原始文件引用、来源设备和实施解析状态；资产域幂等接收并形成ConfigurationLog及不可变解析版本，双方均不得覆盖来源证据 |
| ImplementationQualityGateChanged | IMP-01、CLO-01 | Implementation Execution | Project/ACC | 阶段质量检查通过、整改中或阻断的门禁事实；不包含已退出的IMP-02安全检查 |
| DeviceAssigned | EQP-01、EQP-03 | AST | Implementation Execution/Project | 设备当前最具体项目归属及生效版本 |
| DeviceComponentRelationChanged | EXE-03、EQP-02、EQP-03 | AST | Implementation Execution/Cutover | 机框、槽位、板卡当前关系、生效区间、解析/人工绑定证据和关系版本 |
| SatisfactionResultRecorded | ACC-02、SUB-03 | Acceptance & Closure | ProjectClosure/Supplier & Subcontract | 满意度任务、业务对象、冻结规则版本和不可变判定引用；消费者不得修改答卷 |
| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |
| CutoverCompleted | CUT-06 | Cutover | Project Delivery/Acceptance/Analytics | CUT任务、P6闭环版本、最终成功结果和归档引用；失败或仅完成采集不得发布完成事件 |
| MasterDataSynchronized | INT-01、INT-02、INT-03、INT-06、EQP-04 | CRM/ERP/MES/ITR/Integration ACL | Customer & Relationship/Asset Management/Contract & Fulfillment | 来源主键、来源版本、同步时间、同步状态和本地副本版本 |
| ProjectClosureCompleted | CLO-01、CLO-02、ACC-06、SRV-01 | Acceptance & Closure | Project Delivery/Service Operations | 闭环门禁快照、闭环版本和交接事实；不直接写 Project 状态 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。
