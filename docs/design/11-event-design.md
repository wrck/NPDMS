# SDS Phase 2：事件设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：本分册覆盖全部 104 项 V1/V2 正式需求中的跨聚合、跨 Context、异步投影、通知和外部回调协作；具体事件组在第 5～10 节标注范围
> Owner：SDS Phase 2 事件与集成架构
> 前置设计：`02d-cross-context-contracts.md`、`08-data-model.md`、`09-database-design.md`、`10-api-design.md`

## 1. 事件分类与方向

| 类型 | 命名 | 含义 | 是否表示业务完成 |
|---|---|---|---|
| Command Message | 祈使语义，如 `CollectionTaskRequested` | Producer 请求 Consumer 执行动作 | 否 |
| Domain Event | 过去式，如 `ProjectClosureCompleted` | Owner Context 内业务事实已成立 | 仅表示该事件定义的事实完成 |
| Integration Event | 过去式/同步语义，如 `MasterDataSynchronized` | 跨边界发布稳定事实或同步结果 | 按契约定义，不等于下游处理完成 |
| Notification Event | `TodoRequested`、`NotificationRequested` | 请求生成待办或发送通知 | 否 |

`CollectionTaskRequested` 的 Producer 固定为 IMP/CUT/Inspection 等业务请求方，Consumer 固定为 Device Access & Collection；DAC 接受后另发 `CollectionTaskAccepted`，不得把 Requested 同时当成 DAC 回执。

## 2. 统一事件信封

```json
{
  "eventId": "uuid",
  "eventType": "CollectionTaskAccepted",
  "eventVersion": 1,
  "occurredAt": "2026-08-13T10:00:00.000+08:00",
  "tenantId": "1",
  "sourceContext": "DAC",
  "aggregateType": "CollectionTask",
  "aggregateId": "123",
  "aggregateVersion": 4,
  "traceId": "trace",
  "correlationId": "business-chain",
  "causationId": "preceding-command-or-event",
  "actor": {
    "type": "USER_OR_SERVICE",
    "id": "456"
  },
  "authorizationSnapshotRef": "auth-snapshot-id",
  "payload": {}
}
```

约束：

- `eventId` 全局唯一；Consumer 以 `consumerCode + eventId` 去重。
- 同一聚合的事件按 `aggregateVersion` 检查顺序；发现缺口时暂存并触发补拉/对账，不猜测中间状态。
- `correlationId` 串联完整业务链，`causationId` 指向直接原因。
- Payload 只包含稳定 ID、必要不可变快照和结果摘要；不放密码、私钥、Token、可逆密文或完整外部大报文。
- 未经明确版本兼容评审，不删除/改义既有字段；新增可选字段保持向后兼容，破坏性变更升级 `eventVersion`。

## 3. 投递、消费和事务边界

### 3.1 Producer

业务事务同时写聚合与 Outbox。发布器使用锁定领取或等价机制发送，成功后标记；超时/失败按策略重试。不能先发消息后提交数据库，也不能以消息 Broker ACK 代替业务提交。

### 3.2 Consumer

Consumer 在同一事务中插入 Inbox 去重记录并执行本地业务。处理成功记录结果；可重试失败保留错误分类和下次时间；不可重试失败进入隔离/人工对账。重复事件返回首次处理结果，不重复改变状态。

### 3.3 顺序与并发

【建议】同一聚合使用 `tenantId + aggregateType + aggregateId` 作为分区键。跨聚合不承诺全局顺序；依赖多个事实的门禁使用版本化快照或显式 Saga 状态，不根据“哪个消息先到”推断业务结论。

## 4. 事件最小字段规则

| 场景 | 必须字段 |
|---|---|
| 树变更 | changeBatchId、treeVersion、root/affected scope |
| 设备归属 | deviceId、old/new projectId、assignmentVersion、effectiveAt |
| 状态迁移 | fromStatus、toStatus、commandCode、aggregateVersion |
| 文件 | artifactId、fileVersion、contentHash、purposeCode |
| 外部同步 | sourceSystem、sourceKey、sourceVersion、syncBatchId、resultCode |
| 采集 | collectionTaskId、source business ref、deviceId、externalTaskId、raw/mapped status、resultRef |
| 审批/门禁 | snapshotId、ruleVersion、decisionCode、unmet item codes |

## 5. Project、Asset 与 Analytics 事件

适用 Requirement：PM-01～PM-11、PROJ-12、EQP-01～EQP-07、AST-01～AST-02、RPT-01/02/04、ANA-01、INT-01/02/06。

| 事件 | Producer | Consumer | 幂等/顺序 | 语义 |
|---|---|---|---|---|
| `ProjectCreated` | Project Delivery | SOL/IMP/ACC/ANA | aggregateVersion | 项目身份和来源映射已建立 |
| `ProjectTreeChanged` | Project Delivery | Authorization/AST/ANA | changeBatchId + treeVersion | 一次无环树变更已提交；投影可据此重建 |
| `ProjectStageChanged` | Project Delivery | SOL/IMP/ACC/ANA | projectId + stageSnapshotId | 阶段门禁已通过并迁移 |
| `ProjectClosed` | Project Delivery | Service Operations/ANA | aggregateVersion | 项目关闭事实成立 |
| `TaskAssigned` / `TaskCompleted` | Project Delivery | Todo/ANA | task aggregateVersion | 任务指派/完成事实 |
| `ProjectConversionCompleted` | Project Delivery | IMP/CUT/WO/AST/ANA | conversionId + source/targetProjectId + aggregateVersion + item summary ref | PM-05 全部对象与设备处置成功且源项目已只读归档；部分失败不发布完成事件 |
| `ProjectConversionPartiallyFailed` | Project Delivery | Todo/运维 | conversionId + aggregateVersion + failedItemRefs | 仅表示原批次仍待处理；成功项不回滚、不重复生成 |
| `ProjectPhaseGroupChanged` | Project Delivery | Project Query/ANA | groupId + groupVersion + changedProjectIds | PM-06 多期关系有效版本变化；不改变成员项目自身状态 |
| `ProjectPortfolioPublished` | Project Delivery | ANA/Portfolio Query | portfolioId + revision + memberSnapshotRef | PROJ-12组合版本已发布；不改变成员项目树、状态或权限 |
| `DeviceOwnershipChanged` | Asset domain | Asset projection / Outbox adapter | deviceId + assignmentVersion | AST 内部归属事实已变化；不作为跨 Context 公共名称 |
| `DeviceAssigned` | Asset integration | Project/IMP/CUT/Inspection/ANA | deviceId + assignmentVersion | 对应 `02d` 的稳定跨 Context 契约，由同一归属事务的 Outbox 发布 |
| `DeviceAncestorProjectionUpdated` | Asset Projection | Query/ANA | treeVersion + assignmentVersion | 多级统计投影已达到指定水位 |
| `DeviceStatusSynchronized` | Asset | Service Operations/ANA | sourceKey + sourceVersion | 外部状态副本已更新 |
| `MetricSnapshotPublished` | Analytics | Portfolio UI | metricCode + metricVersion + watermark | 只读指标快照可用 |

`ProjectClosureCompleted` 到达后 Project Delivery 仍需校验事件版本和当前状态，再执行本地关闭命令并发布 `ProjectClosed`；Closure Consumer 不直接写 Project 表。

## 6. IMP、ACC 与 CUT 事件

适用 Requirement：EXE-01～EXE-06、IMP-01～IMP-02、ACC-01～ACC-06、CLO-01～CLO-02、CUT-01～CUT-11。

| 事件 | Producer | Consumer | Payload 核心 | 注意 |
|---|---|---|---|---|
| `ArrivalAccepted` | IMP | COM/ACC/ANA | arrivalId、projectId、accepted quantities、version | 差异未处理时不得表达齐套完成 |
| `InstallationConfirmed` | IMP | ACC/AST/ANA | installationId、deviceId、location snapshot ref | 不修改设备外部身份 |
| `ConfigurationParsed` | IMP | CUT/ACC/ANA | resultId、collectionTaskId、parserVersion、resultRef | 解析成功不等于业务联调完成 |
| `DeviceComponentRelationChanged` | AST | IMP/CUT/Asset Query | chassisDeviceId、slotCode、cardDeviceId、effective interval、sourceRef、version | 仅在解析候选或人工绑定经校验后发布；原始Log和旧关系不覆盖 |
| `JointDebuggingCompleted` | IMP | CUT/ACC | resultId、issueRefs、decision | 未完成问题需显式列出 |
| `ImplementationRiskRaised/Closed` | IMP | Project/CUT Read Model | riskId、level、deviceId、evidenceRef | CUT 只消费引用，不共享风险状态表 |
| `QualitySafetyGateChanged` | IMP | Project/ACC/CUT | project/batch、gateCode、decision、snapshotId | 阻断/解除均带规则和复核版本 |
| `ImplementationEvidencePublished` | IMP | ACC | evidenceId、revision、hash、source snapshot | ACC 审核引用，不覆盖 IMP revision |
| `ImplementationReadinessSnapshotPublished` | IMP | CUT | snapshotId、version、decision、unmetCodes | CUT 执行冻结所校验快照 |
| `ArtifactAccepted/Archived` | ACC | IMP/Project/ANA | artifactId、fileVersion、review/archive record | 归档不改变 FileArtifact 内容历史 |
| `SatisfactionTaskCreated` | ACC | Todo/Project | taskId、projectId、businessRef、questionnaireRevision、assignee | 创建待办，不表示客户已提交或满意度通过 |
| `SatisfactionResultRecorded` | ACC | ProjectClosure/Resource/ANA | resultId、taskId、decision、score、thresholdRevision、signatureRef | 只发布不可变判定引用；未通过结果不得被下游当作门禁通过 |
| `ProjectClosureCompleted` | ACC | Project/Service Operations | closureId、gateSnapshotId、handoverRefs | 只表示 ACC 闭环完成 |
| `CutoverApproved` | CUT | Todo/DAC | taskId、planRevision、approval snapshot | 不自动下发采集任务 |
| `CutoverCompleted` | CUT | Project/ACC/ANA | taskId、executionRevision、resultRef | 失败任务不发布完成事件 |
| `CutoverSupportTaskChanged` | CUT | Project/Todo/ANA | supportTaskId、cutoverTaskId、statusMachineVersion、responsibilityIntervalId | 派发、处理、接管、转交、挂起和恢复发布当前版本；责任历史只追加 |
| `CutoverSupportClosed` | CUT | Project/Todo/ANA | supportTaskId、cutoverTaskId、statusMachineVersion、responsibilityIntervalId、resultRef | 证据门禁通过并关闭；保障任务关闭不等于CUT-06执行成功 |

## 7. Collection 事件链

适用 Requirement：INT-12、EXE-03～EXE-04、CUT-06、INS-02、INS-04、NFR-02。

```text
IMP / CUT / Inspection
  -> CollectionTaskRequested
Device Access & Collection
  -> CollectionTaskAccepted
  -> CollectionTaskDispatched
External Collection Platform
  -> callback
Device Access & Collection
  -> CollectionResultAvailable
Business Owner Context
  -> CollectionResultConsumed
Device Access & Collection
  -> CollectionCompleted
```

| 事件/命令 | Producer | Consumer | 幂等键 | 完成含义 |
|---|---|---|---|---|
| `CollectionTaskRequested` | IMP/CUT/Inspection | DAC | sourceContext + business idempotencyKey | 业务请求已提交，不表示 DAC 接受 |
| `CollectionTaskAccepted` | DAC | 请求方 | collectionTaskId | 授权和输入校验通过，任务已创建 |
| `CollectionTaskDispatched` | DAC | 请求方/运维 | collectionTaskId + dispatchAttemptNo | 外部平台已接受下发，不表示设备执行成功 |
| `CollectionResultCallbackReceived` | DAC | DAC 内部处理 | provider + callbackId/hash | 回调已验签接收，不表示业务消费成功 |
| `CollectionResultAvailable` | DAC | 原请求方 | collectionTaskId + resultVersion | 受控结果引用可读取 |
| `CollectionResultConsumed` | IMP/CUT/Inspection | DAC | collectionTaskId + consumerContext + consumerObjectType + consumerObjectId + resultVersion | 必须与任务冻结的必要消费者和结果版本完全匹配；重复确认不重复推进 |
| `CollectionCompleted` | DAC | 请求方/ANA | collectionTaskId + aggregateVersion + completionMode | 业务入口仅在匹配的 `CollectionResultConsumed` 后发布；独立中心仅在有效成功终态回调后发布 |
| `CollectionFailed` | DAC | 请求方/运维 | collectionTaskId + aggregateVersion | 本任务失败；重试必须创建引用原任务的新任务 |
| `CollectionCancelled` | DAC | 请求方/运维 | collectionTaskId + aggregateVersion + actualStopPoint | 本任务已取消或撤销收敛；不得再发布完成事件 |

临时登录用户名可按最小必要原则保存在采集任务事实中；临时密码永不进入事件。凭证模式只传 credentialId/version 与授权快照引用，不传可逆密文。`BUSINESS_CONSUMPTION` 与 `CALLBACK_TERMINAL` 在任务创建时冻结：前者只用于 IMP/CUT/Inspection，后者只用于 PRD 定义的独立中心，不允许用模糊“契约终态”跳过业务消费确认。

## 8. Inspection 与 Service 事件

适用 Requirement：INS-01～INS-09、SRV-01、INT-05。

| 事件 | Producer | Consumer | 语义 |
|---|---|---|---|
| `InspectionDispatched` | Inspection | Todo/ANA | 巡检业务任务已进入执行安排；在线采集另行发送 `CollectionTaskRequested`，DAC 不消费本事件 |
| `InspectionCompleted` | Inspection | ACC/ANA | 规则快照范围内巡检结果已完成 |
| `InspectionIssueRaised/Closed` | Inspection | Todo/Service Operations | 问题产生/闭环事实，不等于工单完成 |
| `ServiceStatusChanged` | Service Operations | Asset/Project/ANA | 客观服务状态或提示改变，不含续保动作 |

通知失败通过 `NotificationDeliveryFailed` 反映，但不回滚业务事件，不改变满意度任务、巡检、割接或闭环的业务状态。

## 9. 主数据、商务、资源与知识事件

适用 Requirement：CUS-01～CUS-04、COM-01～COM-02、RES-01、SUB-01～SUB-05、INT-03/04/07/09/10。

| 事件 | Producer | Consumer | 幂等与结果 |
|---|---|---|---|
| `MasterDataSynchronized` | 对应 Owner Context 的 Integration ACL | 各本地查询/投影 | sourceSystem + sourceKey + sourceVersion |
| `CustomerMerged` | Customer | Project/ACC/Inspection | mergeBatchId；旧 ID 保留别名/引用映射 |
| `DeliveryScopeAssigned/Released` | Commerce | Project/IMP/ACC | orderLine + scopeVersion |
| `FulfillmentSnapshotPublished` | Commerce | Project/ANA | snapshotId + source watermarks |
| `SubcontractApproved` | Resource | Project/Todo | requestId + revision |
| `PaymentGateChanged` | Resource | Project/ACC | gate snapshot；外部付款回执并不自动表示所有门禁完成 |
| `TechnicalNoticeSynchronized` | Knowledge | Asset/CUT/Inspection | ITR sourceKey + sourceVersion |
| `NotificationRequested` | 任意 Owner | 基础平台通知适配器 | businessObject + notificationType + recipient + revision |
| `NotificationDelivered/Failed` | 通知适配器 | Owner/运维 | providerMessageId + attemptNo；只表示交付结果 |

V1/V2 不定义 `TechnicalNoticePublishedByPlatform`，避免把 V3 本地治理提前纳入。

## 10. 文件与待办事件

适用 Requirement：PLT-01～PLT-02 及所有需要文件/审批的领域需求。

| 事件 | Producer | Consumer | 规则 |
|---|---|---|---|
| `FileVersionCommitted` | File Service | 业务 Owner Context | artifactId、version、hash、scan status；正文不进事件 |
| `FileReferenceAttached/Detached` | 业务 Owner/File Service | Audit/Archive | 必须带业务对象和 purposeCode |
| `FileArchived` | ACC/File Service | 业务 Owner | 归档版本固定，不等于业务审批自动通过 |
| `TodoRequested` | 业务 Owner | Todo | 业务对象+节点+责任人+幂等键 |
| `TodoCompleted` | Todo | 业务 Owner | 只触发 Owner 校验并尝试业务命令，不直接改状态 |

## 11. 失败、补偿和对账

| 失败类型 | 处理 |
|---|---|
| Producer 业务事务失败 | 无 Outbox 事件；整体回滚 |
| Broker 不可用 | Outbox 保留并退避重试，业务响应说明异步处理中 |
| Consumer 暂时失败 | Inbox 标记可重试；同一 eventId 不重复产生副作用 |
| Consumer 永久失败 | 隔离队列/人工处理；发布受控失败事件或对账项 |
| 事件乱序 | 按 aggregateVersion 暂存/补拉；超过窗口进入对账 |
| Payload 与本地事实冲突 | 不覆盖；记录版本冲突并请求 Owner 查询/重放 |
| 通知失败 | 重发通知，不回滚业务完成事实 |
| 外部回调重复/冲突 | callbackId/hash 去重；同序号不同内容隔离并告警 |

补偿以业务反向命令或新事实表达，不删除原事件，不直接回滚已被其他 Context 消费的历史。

## 12. Schema 治理与测试

- 事件 Schema 版本化存储，Owner 负责发布兼容说明、示例和字段分类。
- Consumer-driven contract 测试验证必填字段、枚举兼容、未知可选字段和敏感字段黑名单。
- Outbox/Inbox 测试覆盖提交原子性、重复投递、进程崩溃恢复、乱序、部分失败和隔离重放。
- 发布前以“Producer、Consumer、Requirement、Schema Version、分区键、幂等键、顺序、补偿、对账、敏感级别”登记事件目录。

## 13. 事件门禁结论

| 门禁项 | 结论 | 落位 |
|---|---|---|
| Producer/Consumer 方向明确 | PASS | 第 5～10 节；Requested 与 Accepted 分离 |
| 版本、幂等和顺序明确 | PASS | 统一信封、Inbox/Outbox、aggregateVersion |
| 外部成功不等于业务完成 | PASS | Collection、Todo、Notification、ProjectClosure 分段事件 |
| 敏感数据不进入事件 | PASS | 统一信封与 Collection 事件链 |
| 补偿与对账可实现 | PASS | 第 11 节 |

本分册可进入外部集成详细契约评审；Phase 2 放行前仍需与 12、15、16 的超时、重试和异常分类逐项一致。
