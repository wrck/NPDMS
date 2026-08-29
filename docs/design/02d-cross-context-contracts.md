# SDS Phase 1：跨 Context 契约

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8及批准增量`CHG-PRD-2026-08-23-002`
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8独立复审GO，当前分册已纳入正式基线
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
| EquipmentLocationEffective | EXE-02、EQP-01 | AST | Implementation Execution/Project | IMP通过`AssetLocationApi`公开命令提交已确认安装/迁移/拆除事实；AST在调用方事务内幂等更新设备当前地点和版本历史，AST不反向读取IMP表 |
| DeviceComponentRelationChanged | EXE-03、EQP-02、EQP-03 | AST | Implementation Execution/Cutover | 机框、槽位、板卡当前关系、生效区间、解析/人工绑定证据和关系版本 |
| SatisfactionResultRecorded | ACC-02、SUB-03 | Acceptance & Closure | ProjectClosure/Supplier & Subcontract | 满意度任务、业务对象、冻结规则版本和不可变判定引用；消费者不得修改答卷 |
| ServiceHandoverCreated | ACC-06、SRV-01 | Acceptance & Closure | Service Operations | ACC-06完成并形成不可覆盖的服务交接快照；Service Operations只保存只读引用，不创建或改写交接事实 |
| CutoverCompleted | CUT-06 | Cutover | Project Delivery/Acceptance/Analytics | CUT任务、P6闭环版本、最终成功结果和归档引用；失败或仅完成采集不得发布完成事件 |
| MasterDataSynchronized | INT-01、INT-02、INT-03、INT-06、EQP-04 | CRM/ERP/MES/ITR/Integration ACL | Customer & Relationship/Asset Management/Contract & Fulfillment | 来源主键、来源版本、同步时间、同步状态和本地副本版本 |
| ProjectClosureCompleted | CLO-01、CLO-02、ACC-06、SRV-01 | Acceptance & Closure | Project Delivery/Service Operations | 闭环门禁快照、闭环版本和交接事实；不直接写 Project 状态 |
| `CustomerMasterDataApi.upsertFromCrm(command)` | CUS-03、INT-03 | CUS | INT-03 | 按租户、CRM客户ID、来源版本和eventId幂等写CRM权威字段；同版本不同载荷进入冲突，不覆盖平台扩展字段 |
| `CustomerReferenceApi.validateReferences(customerIds)` | CUS-03、EQP-01 | CUS | PROJ/AST | 校验客户存在、租户、状态和可引用性；停用客户禁止新关系 |
| `CustomerReferenceGuardApi.checkCustomerReferences(customerIds)` | CUS-03 | PROJ/AST/CUS | CUS | 批量返回有效引用类型、数量和最小摘要；未知、超时或不可用按存在风险处理 |
| `AssetLocationApi.validateCustomerLocations(references)` | CUS-03 | AST | CUS | 批量校验Address/Site的租户、对象类型、存在性和版本；CUS自行维护地点时态引用 |
| `DeviceCustomerAssigned` | EQP-01、CUS-03 | AST | CUS/PROJ | 设备当前客户直接归属及版本已生效；租用/共管不形成第二个当前直接归属 |
| `KnowledgePublicProductInfoQueryApi` | EQP-01 | KNO | AST | 按产品/设备映射查询已发布官网信息版本、来源URL、核验时间和摘要；无记录返回NOT_AVAILABLE |
| `OrganizationScopeApi.getActiveScopes(userId)` | COM-01 | SYSTEM | COM | 在受信租户上下文返回用户当前有效`UserCompanyDepartmentScope`的稳定ID、公司ID/编码、可选部门上下文、生效区间和版本；COM只按`companyCode`与ERP合同所属公司编码精确匹配，部门、项目关系和功能权限不得产生额外合同范围 |
| `AcceptanceActivityInitializationApi.initialize` | ACC-03 | ACC | PROJ | 以`MANDATORY`加入项目创建事务；PROJ预分配执行契约ID并传精确初验/终验任务与应交码，ACC创建PENDING活动并返回`acceptanceId/activityVersion`，PROJ随后追加ACC执行契约；任一步失败整体回滚 |
| `FileArtifactApi.archiveReferenceSets` | ACC-03、ACC-04、PLT-02 | PLT | ACC | 受信命令显式携带报告发布时冻结的`actorUserId`；PLT按该用户重验既有`pms:file:archive`权限和租户/文件范围，持锁重验ACC报告附件ACTIVE集合，在独立`ACCEPTANCE_REPORT_ARCHIVE`集合按相同公共文件事实创建ARCHIVED引用并整组追加记录且写`archivedBy=actorUserId`；附件引用保持ACTIVE供历史下载，不暴露PLT内部主键，ACC只保存归档补偿投影 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。

F-PROJ-001手动项目创建是经ADR-0032批准的限定例外：PROJ同步调用ACC公开内部应用接口，ACC加入调用方同一MySQL事务；F-ACC-001进一步由`AcceptanceActivityInitializationApi`在该事务内返回精确活动ID后再形成ACC执行契约。正式Project、ProjectTask执行契约、ACC交付件实例和验收活动必须全有或全无，不产生初始化中间状态。该例外不允许PROJ直接访问ACC Repository，也不改变其他跨Context契约的默认最终一致性。若部署边界不再共享同一事务资源，必须先批准创建完成语义变更，不得自行降级为Saga、异步补建或部分成功。

组织与地点遵循ADR-0033：SYSTEM通过`CompanyApi/DeptApi/OrganizationScopeApi`提供稳定主数据和同一行公司—部门范围；AST通过`AssetLocationApi`提供Address/Site/SiteLocation维护、版本校验、精确区划映射和设备位置生效命令。CUS、PROJ、IMP、COM不得直接访问SYSTEM或AST的DO、Mapper、Repository或业务表。COM-01只复用现有`getActiveScopes`查询，不要求SYSTEM新增合同专用授权表、接口或Provider。

与客户和设备主档相关的命令和查询只传稳定ID、来源版本、期望版本、权限快照与幂等键。`INT-02`、`INT-03`、`INT-04`及`EQP-04`保持独立同步Feature。
