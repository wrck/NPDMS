# SDS Phase 1：跨 Context 契约

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8及批准增量`CHG-PRD-2026-08-23-002`
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8独立复审GO，当前分册已纳入正式基线
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ImplementationEvidencePublished | EXE-01～EXE-06、IMP-01、ACC-04 | Implementation Execution | Acceptance & Closure | IMP出向事件；实施`evidenceId/revision`、来源需求/记录/版本、FileReference、哈希和检查快照已发布，ACC按该不可变revision建立审核/归档引用，不覆盖IMP事实 |
| ArtifactAccepted / ArtifactArchived | EXE-01～EXE-06、IMP-01、ACC-04 | Acceptance & Closure | Implementation Execution | ACC入向回执；回显`evidenceId/evidenceRevision/artifactId/fileVersion/reviewOrArchiveRecordId`，IMP按eventId Inbox和`evidenceId+revision`幂等推进同步投影；Accepted后Archived超时保留已接受事实并以独立归档回执重试态重发同revision，匹配Archived可恢复至已归档；旧序、错配或重复回执不得覆盖当前revision，回执失败不回滚来源业务事实 |
| `ImplementationReadinessApi` / ImplementationReadinessSnapshot | EXE-06、CUT-01 | Implementation Execution / F-IMP-001 | Cutover / F-CUT-002 | `inspect`按受信租户、项目和完整设备归属水位读取最新CUTOVER快照并对照当前Owner事实；`lockAndRevalidate`再携带明确快照ID/版本加入CUT写事务重验。结果封闭为`READY/NOT_READY/STALE`，返回不可变快照及结构化项目、设备、批准方案和EXE-01～04来源水位；缺快照、Owner损坏和Provider不可用使用不同公共失败。CUT不得直读IMP/EXE表，测试替身不得进入生产装配或充当真实Owner证据。 |
| `CustomerServiceLevelFactApi` / CustomerServiceLevelRevision | CUS-02、CUT-01 | Customer & Relationship / CUS | Cutover / F-CUT-002 | `inspectCurrent`按受信租户和客户返回`AVAILABLE`或`NOT_CONFIGURED`完整当前事实；`lockAndRevalidate`携带此前完整事实加入CUT写事务并锁定CUS客户等级时间线，精确匹配返回原状态，变化返回携带完整当前事实的`STALE`。`NOT_CONFIGURED`不得伪造revision/code/生效区间，客户不存在、Owner损坏和Provider不可用分别失败。CUT不得以`CustomerSummaryDTO`、旧PROJ服务等级表或用户输入替代。 |
| `ProjectCutoverContextFactApi` / ProjectCutoverContextFact | CUT-01 | Project / PROJ | Cutover / F-CUT-002 | `inspect`按受信tenant和projectId读取同一`proj_project`行的项目编码/名称、项目发生时客户快照、发生时部门（办事处）快照及projectVersion；`lockAndRevalidate`携带从前次FOUND原样复制的完整Expected Fact，以`MANDATORY`加入CUT写事务并锁定同一项目行，锁后逐字段比较完整Fact。结果封闭为`FOUND/NOT_FOUND/INACTIVE/VERSION_CONFLICT`，只有ACTIVE、字段完整且全部字段精确匹配可供写入；Expected只作并发守卫，CUT只冻结Owner锁后返回的currentFact。编码字段最大64字符，三个名称字段最大255字符；公共DTO沿用`departmentId/departmentCode/departmentName`，CUT只在展示层称为办事处。该Fact不替代`ProjectScopeApi`的用户范围/treeVersion，也不替代CUS当前客户服务等级时间线；CUT不得读取PROJ/SYSTEM/CUS表或拼接无版本Summary。 |
| `ArrivalAcceptanceFactApi` | EXE-01、EXE-02、EXE-06 | Implementation Execution / F-IMP-002 | F-IMP-003、F-IMP-001 | 按项目、设备/订单数量范围返回`ACCEPTED/NOT_ACCEPTED/STALE`、稳定有序`sourceAcceptanceIds`、项目级单调`factVersion`、由DeliveryScope版本和设备归属版本组成的`scopeWatermark`、已签/豁免/未满足范围与`reopened`；提供无副作用`inspect/lockAndRevalidate`，不返回DO、文件正文或签收人隐私 |
| `ProjectSystemQualificationFactApi` | EXE-01 | Project / `T-FIMP002-PROJ-01`支撑Task | Implementation Execution / F-IMP-002 | `lockCurrentForSystem`仅供无用户主体的内部到期命令：在受信租户上下文按项目锁定当前主行、唯一`PROJECT_MANAGER`事实和当前根树版本，校验`ACTIVE/S4`并返回当前项目/参与者/树版本；不接收`subjectUserId/ACTION_EDIT/approvedBy/system actor`，不放宽现有用户授权API，也不以消费方冻结版本作相等前置 |
| `ProjectDeliveryScopeQualificationFactApi` | COM-01 | Project / `T-FCOM001-PROJ-01`支撑Task | Commerce / F-COM-001 | `inspect/lockAndRevalidate`为COM交付范围写命令组合当前项目经理、项目生命周期/阶段与直管目标项目`ACTION_EDIT`事实。该用途只由锁定目标项目行的current `PROJECT_MANAGER`证明，不读取未锁定的授权Grant，也不扩展到后代项目；冻结并重验经理、项目/参与者/树版本、根身份、生命周期和阶段。`NORMAL_CLOSED`只允许S6，树版本必须为正；S5/S6或关闭事实由COM用于把减配/释放转为`CONFLICT`。初次主体/范围失败与锁定期间任一冻结轴变化分别返回主体/范围错误和`FACT_STALE`，Owner损坏及Provider不可用不得混淆。 |
| `InstallationCompletionFactApi` | EXE-02、EXE-06 | Implementation Execution / F-IMP-003 | F-IMP-001 | 按稳定设备范围返回`COMPLETED/NOT_COMPLETED/STALE`、安装来源对象、业务版本、范围水位及`reopened`，并按期望版本锁定重验；不以位置投影或附件替代安装完成事实 |
| `ConfigurationCompletionFactApi` | EXE-03、EXE-06 | Implementation Execution / F-IMP-004 | F-IMP-001 | 按稳定设备和批准模板/采集版本返回`COMPLETED/NOT_COMPLETED/STALE`、结果版本、范围水位及`reopened`，并锁定重验；采集任务受理、原始Log存在或解析尝试不等于配置完成 |
| `JointDebuggingCompletionFactApi` | EXE-04、EXE-06 | Implementation Execution / F-IMP-005 | F-IMP-001 | 按项目/设备/联调项范围返回`COMPLETED/NOT_COMPLETED/STALE`、结果版本、范围水位及`reopened`，并锁定重验；问题未闭环或证据失效时失败关闭 |
| `DeviceScopeFactApi` | EQP-01、EXE-01～EXE-06、CUT-01、CUT-03 | AST / F-AST-001 Device聚合（`T-FIMP001-AST-01`支撑Task） | IMP、CUT | `resolveBySerials/lockAndRevalidate`接收受信`tenantId/projectId`并要求运行时租户一致；SN以`trim + Locale.ROOT uppercase`形成比较键，规范化重复拒绝。成功事实按`deviceId`升序返回`deviceId/sn/currentProjectId/projectAssignmentVersion`，并加性返回产品主数据投影的`deviceTypeCode/deviceTypeSourceKey/deviceTypeSourceVersion/deviceTypeAssignmentVersion`；水位为同序的`deviceId/projectAssignmentVersion/deviceTypeAssignmentVersion`结构化向量。产品主数据拥有产品到设备类型的赋值，SYSTEM仅拥有`pms_device_type`值域，AST保存当前投影；不得从产品编码、型号或CONP推断。锁定重验按`deviceId`升序锁当前投影：归属或设备类型赋值版本变化返回`STALE`；同`deviceId`的当前SN比较键或产品主数据来源身份损坏抛`OWNER_DATA_CORRUPTED`；缺失、状态不可用或错项目返回`INVALID`。F-CUT-002要求全部设备具有有效类型事实并冻结快照，其他消费者不得因不需要该分类而被迫解释CUT门禁。契约不替代ProjectScope授权、DeliveryScope应到数量或业务完成事实；复用`ast_device`且不建立第二Owner表 |
| `DeliveryScopeApi.getAssignedScope` | COM-01、EXE-01 | Commerce / F-COM-001 | Implementation Execution / F-IMP-002 | 受信租户下按项目和可空期望`scopeVersion`读取；null为inspect，非null锁COM项目水位及当前范围后重验。返回以scope/detail为稳定分组的数量、单位、产品/型号及明确SN；待核对、取消、退货、释放量排除，任一未解决冲突则整体失败关闭。持久项目水位覆盖真实空范围，版本变化返回STALE，Owner损坏和Provider不可用独立分类 |
| `CommerceAuthorityIngestApi.ingestBatch` | COM-01、INT-01 | Integration ACL / INT-01 | Commerce / F-COM-001 | 受信租户下以eventId接收一个原子批次的合同、销售订单、订单行和订单—合同关系精确事实；普通Owner按单sourceKey定位，订单—合同关系按salesOrderSourceKey+contractSourceKey二元组定位并原样持久化；同event同载荷重放、异载荷永久冲突，旧来源版本不得覆盖。只形成COM本地副本，不包含ERP连接、认证、轮询或传输运行 |
| `PlatformMigrationEvidenceApi` | COM-01 | Platform / `T-FCOM001-PLT-01`支撑Task | Commerce / F-COM-001 Release导入器与核对Job | PLT拥有迁移批次、不可变逐源行、外部键映射和迁移问题。批次按`IMPORTING -> STAGED_READY -> RECONCILING -> COMPLETED/FAILED`推进；导入器通过`markStagedReady`的`READY/FAIL_IMPORT`判别联合形成暂存就绪或封闭导入失败。逐源行的tenant/sourceSystem/sourceTable必须与锁定批次完全一致；核对结果只允许引用同tenant、同`RECONCILING`批次的冻结来源。COM在同一外层事务领取、逐行登记`MAPPED/ISSUE/RETAINED`结果并完成计数核对；逐源结果追加不改变claim返回的批次版本或最终计数，complete使用该版本重算并一次CAS完成。固定八个写动作之外只提供冻结来源游标查询；COM不得访问PLT表。问题关闭只追加处理人、规则版本和目标结果，不重开或重算已完成批次。 |
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

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。

F-PROJ-001手动项目创建是经ADR-0032批准的限定例外：PROJ同步调用ACC公开内部应用接口，ACC加入调用方同一MySQL事务；正式Project、ProjectTask执行契约和ACC交付件实例必须全有或全无，不产生初始化中间状态。该例外不允许PROJ直接访问ACC Repository，也不改变其他跨Context契约的默认最终一致性。若部署边界不再共享同一事务资源，必须先批准创建完成语义变更，不得自行降级为Saga、异步补建或部分成功。

组织与地点遵循ADR-0033：SYSTEM通过`CompanyApi/DeptApi/OrganizationScopeApi`提供稳定主数据和同一行公司—部门范围；AST通过`AssetLocationApi`提供Address/Site/SiteLocation维护、版本校验、精确区划映射和设备位置生效命令。CUS、PROJ、IMP不得直接访问SYSTEM或AST的DO、Mapper、Repository或业务表。

与客户和设备主档相关的命令和查询只传稳定ID、来源版本、期望版本、权限快照与幂等键。`INT-02`、`INT-03`、`INT-04`及`EQP-04`保持独立同步Feature。
