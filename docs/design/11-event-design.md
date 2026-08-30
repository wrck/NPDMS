# SDS Phase 2：事件设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：本分册覆盖全部 100 项 V1/V2 正式需求中的跨聚合、跨 Context、异步投影、通知和外部回调协作；具体事件组在第 5～10 节标注范围
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

适用 Requirement：PM-01～PM-11、PROJ-12、EQP-01～EQP-05、EQP-07、AST-01～AST-02、RPT-02、ANA-01、INT-01/02/06。

| 事件 | Producer | Consumer | 幂等/顺序 | 语义 |
|---|---|---|---|---|
| `ProjectCreated` | Project Delivery | SOL/IMP/ACC/ANA | aggregateVersion | 项目身份和来源映射已建立 |
| `ProjectTreeChanged` | Project Delivery | Authorization/AST/ANA | changeBatchId + treeVersion | 一次无环树变更已提交；投影可据此重建 |
| `ProjectStageChanged` | Project Delivery | SOL/IMP/ACC/ANA | projectId + stageSnapshotId | 阶段门禁及进入项目设定验收阶段所需的验收范围绑定已在同一事务提交；事件只作提交后通知/投影，不触发、不补建也不反推`AcceptanceScopeBinding` |
| `ProjectClosed` | Project Delivery | Service Operations/ANA | aggregateVersion + lifecycleStatus + closeReason | 项目关闭事实成立；NORMAL_CLOSED仅来自CLO-02，EXCEPTION_CLOSED来自PM-10，消费方不得据此新增维护阶段 |
| `TaskAssigned` / `TaskCompleted` | Project Delivery | Todo/ANA | task aggregateVersion + executionContractId/contractVersion + completionEvaluationId + factVersion | 任务指派/完成事实；完成事件仅在CompletionRule回源校验绑定事实和版本、追加判定事实并完成状态迁移后发布 |
| `ProjectServiceManagerAssigned` | Project Delivery | PMS Notification Delivery | eventId + project aggregateVersion | PM-08 V1主责/协同服务经理关系及Project状态已提交；payload冻结assignmentId、projectId、recipientUserId、templateCode、templateParamsSnapshot、assignmentType、levelCode、effectiveFrom；处理器以eventId作为SYSTEM站内信deliveryKey，通知失败不回滚指派 |

F-PROJ-002的`ProjectTreeChanged`载荷至少包含`eventId/tenantId/changeBatchId/treeVersion/operationType/affectedRootProjectIds/affectedProjectIds/occurredAt`。同一`changeBatchId + treeVersion`只发布一次；消费者按根项目水位拒绝旧版本和乱序覆盖。事件表示父子真值及可识别的新完整版本已经提交，不表示Authorization、AST或ANA投影已经追平；投影未追平时消费方读取上一完整版本或明确返回结构更新中。
| `ProjectConversionCompleted` | Project Delivery | IMP/CUT/AST/ANA | conversionId + source/targetProjectId + aggregateVersion + item summary ref | PM-05 全部对象与设备处置成功且源项目已只读归档；部分失败不发布完成事件 |
| `ProjectConversionPartiallyFailed` | Project Delivery | Todo/运维 | conversionId + aggregateVersion + failedItemRefs | 仅表示原批次仍待处理；成功项不回滚、不重复生成 |
| `ProjectPhaseGroupChanged` | Project Delivery | Project Query/ANA | groupId + groupVersion + changedProjectIds | PM-06 多期关系有效版本变化；不改变成员项目自身状态 |
| `ProjectPortfolioPublished` | Project Delivery | ANA/Portfolio Query | portfolioId + revision + memberSnapshotRef | PROJ-12组合版本已发布；不改变成员项目树、状态或权限 |
| `DeviceOwnershipChanged` | Asset domain | Asset projection / Outbox adapter | deviceId + assignmentVersion | AST 内部归属事实已变化；不作为跨 Context 公共名称 |
| `DeviceAssigned` | Asset integration | Project/IMP/CUT/Inspection/ANA | deviceId + assignmentVersion | 对应 `02d` 的稳定跨 Context 契约，由同一归属事务的 Outbox 发布 |
| `DeviceAncestorProjectionUpdated` | Asset Projection | Query/ANA | treeVersion + assignmentVersion | 多级统计投影已达到指定水位 |
| `DeviceStatusSynchronized` | Asset | Service Operations/ANA | sourceKey + sourceVersion | 外部状态副本已更新 |
| `MetricSnapshotPublished` | Analytics | Portfolio UI | metricCode + metricVersion + watermark | 只读指标快照可用 |

`ProjectClosureCompleted` 到达后 Project Delivery 仍需校验事件版本和当前状态，再执行本地关闭命令并发布 `ProjectClosed`；Closure Consumer 不直接写 Project 表。

`ProjectServiceManagerAssigned`只服务PM-08通知闭环，不作为跨Context权限、成员或项目状态投影来源。Producer与成员关系、Project版本/状态、幂等成功和操作审计同事务写Outbox，并冻结`assignmentId/projectId/recipientUserId/templateCode/templateParamsSnapshot/assignmentType/levelCode/effectiveFrom`；模板参数快照只含生成本次站内信所需不可变值，不含秘密。消费者只能用事件payload构造SYSTEM请求，重试不得查询当前Project、成员关系或用户资料重新推导收件人、模板和内容。`system_notify_message.delivery_key`防止“消息已创建但Outbox未标成功”的重复通知，Outbox记录失败次数和下次重试时间。

## 6. IMP、ACC 与 CUT 事件

适用 Requirement：EXE-01～EXE-06、IMP-01、ACC-01～ACC-04、ACC-06、CLO-01～CLO-02、CUT-01～CUT-10。

| 事件 | Producer | Consumer | Payload 核心 | 注意 |
|---|---|---|---|---|
| `ArrivalAccepted` | IMP | COM/ACC/ANA | arrivalId、projectId、accepted quantities、version | 差异未处理时不得表达齐套完成 |
| `InstallationConfirmed` | IMP | ACC/AST/ANA | installationId、deviceId、location snapshot ref | 不修改设备外部身份 |
| `ConfigurationParsed` | IMP | CUT/ACC/ANA | resultId、collectionTaskId、parserVersion、resultRef | 解析成功不等于业务联调完成 |
| `DeviceComponentRelationChanged` | AST | IMP/CUT/Asset Query | chassisDeviceId、slotCode、cardDeviceId、effective interval、sourceRef、version | 仅在解析候选或人工绑定经校验后发布；原始Log和旧关系不覆盖 |
| `JointDebuggingCompleted` | IMP | CUT/ACC | resultId、issueRefs、decision | 未完成问题需显式列出 |
| `ImplementationRiskRaised/Closed` | IMP | Project/CUT Read Model | riskId、level、deviceId、evidenceRef | CUT 只消费引用，不共享风险状态表 |
| `ImplementationQualityGateChanged` | IMP | Project/ACC/CUT | project/batch、gateCode、decision、snapshotId | 仅表示IMP-01质量检查结论；不承载IMP-02安全检查或额外安全豁免语义 |
| `ImplementationEvidencePublished` | IMP | ACC | evidenceId、revision、hash、source snapshot | ACC 审核引用，不覆盖 IMP revision |
| `ImplementationReadinessSnapshotPublished` | IMP | CUT | snapshotId、version、decision、unmetCodes | CUT 执行冻结所校验快照 |
| `ArtifactAccepted/Archived` | ACC | IMP/Project/ANA | artifactId、fileVersion、review/archive record | 归档不改变 FileArtifact 内容历史 |
| `AcceptanceReportVersionChanged` | ACC | ACC交付件索引 | acceptanceId、projectId、reportType、changeType(`EFFECTIVE/REPLACED/REVOKED`)、publisherActorUserId、currentReportVersionId（撤销为空）、previousReportVersionId、attachments[{sequence,artifactId,versionNo,referenceKey,fileFactVersion,scopeVersion,sha256}] | 与发布/替换/撤销通过`PlatformCommandExecutionApi`同事务写Outbox；`publisherActorUserId`取首次生效/替换时服务端认证用户，撤销沿用被撤销版本发布人；附件逐项来自PLT公共事实，不携带内部FileVersion/FileReference ID；完整附件集合不得缩成单文件；按来源版本幂等维护应交根及来源历史，失败保留报告并进入补偿，不触发范围绑定 |
| `ClosureGateRecheckRequested` | ACC | CLO | projectId、sourceRequirementId、sourceObjectId、sourceVersion、reasonCode | 只请求后续CLO重新读取Owner事实；不表示闭环门禁已通过，CLO Feature未交付时允许Outbox保留待消费 |
| `SatisfactionTaskCreated` | ACC | Todo/Project | taskId/projectTaskId/projectTaskVersion/taskCode、projectId、collectionKey/taskRevisionNo/priorTaskId、sourceOwnerContext/sourceObjectType/sourceObjectId/sourceObjectVersion、triggerOwnerContext/triggerObjectType/triggerFactId/triggerFactVersion、questionnaireId/revision、template/rule/threshold版本、assignee | projectTaskVersion只能取初始化时PROJ已锁定Fact并供消费者精确重验；首次source=trigger；整改保持source不变且trigger必须为`ACC/SatisfactionRemediationFact`；创建待办投影不表示客户已提交或满意度通过，Todo完成不反向推进ACC |
| `SatisfactionResultVersionChanged` | ACC | ACC满意度来源投影；未来CLO/SUB | changeType(`RECORDED/INVALIDATED`)、projectId/projectTaskId/projectTaskVersion/taskCode、collectionKey/taskRevisionNo、task/questionnaire/response/resultId/resultVersion/resultFactVersion、template/rule/threshold、sourceOwnerContext/sourceObjectType/sourceObjectId/sourceObjectVersion、passed/resultStatus/archiveActorUserId、`invalidationReasonCode/invalidatedByUserId/invalidatedAt`（仅INVALIDATED）、files[{role,sequence,sourceSequence,artifactId,versionNo,referenceKey,fileFactVersion,scopeVersion,sha256}] | `sequence`保留Result/Response角色内文件身份；`sourceSequence`按结果文档→签字→附件、角色内序号冻结为跨角色连续唯一1..N，ACC-04来源附件只使用sourceSequence。Result事件生产事务先用PROJ窄方法锁定当前`T-SAT-SURVEY`事实并冻结`projectTaskVersion`；`resultVersion`仅为业务来源版本，`resultFactVersion`仅为Owner状态重验版本，RECORDED冻结提交后实际初始版本，INVALIDATED冻结CAS后新版本。任何身份失配时Result和Outbox零写入；消费者只能以事件版本调用Owner接口精确重验，不得取当前版本、传零或跨Context查表。与Result事务同提交Outbox；满意度来源只允许taskCode=`T-SAT-SURVEY`并精确投影到同租户同项目`D-SAT-REPORT/T-SAT-SURVEY`唯一根；根缺失/重复/错配待补偿。RECORDED置CURRENT前按Result ID/factVersion重验Owner仍EFFECTIVE且passed且无更新结果；否则只保留非当前历史。INVALIDATED仅在根仍指向该业务版本时清空；两向乱序均不得恢复失效版本或覆盖新来源，历史ACTIVE文件与归档事实保留 |

F-ACC-001的`AcceptanceReportOutboxDeliveryJob`通过`PlatformOutboxDeliveryApi`只领取`AcceptanceReportVersionChanged`。它先把事件交给来源投影事务；事务成功后调用`markDelivered`，反序列化、身份校验或投影失败则调用`scheduleRetry`且不得标成功。该Job不得领取或标记`ClosureGateRecheckRequested`已投递；CLO消费者不在本Feature内实现。

F-ACC-002的`SatisfactionResultOutboxDeliveryJob`只领取`SatisfactionResultVersionChanged`。它先维护ACC-04满意度来源版本及完整文件集合，投影事务成功后才按消息retryCount调用`markDelivered`，失败使用同一expectedRetryCount调用`scheduleRetry`。`ClosureGateRecheckRequested`及未来SUB重校验事件不由该Job领取或误标成功。
| `ProjectClosureCompleted` | ACC | Project/Service Operations | closureId、gateSnapshotId、handoverRefs | 只表示 ACC 闭环完成 |
| `CutoverApproved` | CUT | Todo/DAC | taskId、planRevision、approval snapshot | 不自动下发采集任务 |
| `CutoverCompleted` | CUT | Project/ACC/ANA | taskId、closureRevision、resultRef、archivedAt | 仅P6提交归档且最终成功时发布；失败、回退未成功或仅采集完成不得发布 |
| `CutoverChecklistItemResultLinked` | CUT | ProjectTask Query/CUT Read Model | taskId、checklistId/checklistVersion、stableItemKey/itemVersion、collectionTaskId、resultRef/resultVersion、resultSourceCode | P3同工作台已选择一个结果版本；只引用DAC技术结果，不复制其状态，不表示采集项通过或CUT阶段完成 |

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

适用 Requirement：CUS-01～CUS-04、COM-01、RES-01、SUB-01～SUB-05、INT-03/04/07/09/10。

| 事件 | Producer | Consumer | 幂等与结果 |
|---|---|---|---|
| `MasterDataSynchronized` | 对应 Owner Context 的 Integration ACL | 各本地查询/投影 | sourceSystem + sourceKey + sourceVersion |
| `CustomerMerged` | Customer | Project/ACC/Inspection | mergeBatchId；旧 ID 保留别名/引用映射 |
| `DeliveryScopeAssigned/Released` | Commerce | Project/IMP/ACC | orderLine + scopeVersion |
| `SubcontractApproved` | Resource | Project/Todo | requestId + revision |
| `PaymentGateChanged` | Resource | Project/ACC | gate snapshot；外部付款回执并不自动表示所有门禁完成 |
| `TechnicalNoticeSynchronized` | Knowledge | Asset/CUT/Inspection | ITR sourceKey + sourceVersion |
| `NotificationRequested` | 任意 Owner | 基础平台通知适配器 | businessObject + notificationType + recipient + revision |

F-PROJ-002使用的`DeliveryScopeAssigned/Released`载荷至少包含`eventId/tenantId/orderLineId/projectId/scopeId/scopeVersion/allocatedQty/dimensionDigest/occurredAt`。`dimensionDigest`只用于一致性和审计，不包含SN明文列表或商务正文；事件与COM范围事实同事务进入COM Outbox。Project拆分确认失败时不得出现任何已分配事件；重复、乱序或旧`scopeVersion`不得覆盖新范围投影。
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
