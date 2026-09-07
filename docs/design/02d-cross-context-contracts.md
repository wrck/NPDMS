# SDS Phase 1：跨 Context 契约

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；既有独立复审GO仅属原批准范围，当前差量须按Gate重验证
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 契约 | Requirement ID | Producer | Consumer | 语义 |
|---|---|---|---|---|
| ImplementationEvidencePublished | EXE-01～EXE-06、IMP-01、ACC-04 | Implementation Execution | Acceptance & Closure | 实施证据、来源版本、哈希和检查快照已发布 |
| ImplementationReadinessSnapshot | EXE-06、CUT-01 | Implementation Execution | Cutover | 割接前实施门禁快照，仅供 CUT 校验 |
| `ProjectSystemQualificationFactApi` | EXE-01 | Project / `T-FIMP002-PROJ-01`支撑Task | Implementation Execution / F-IMP-002 | `lockCurrentForSystem`仅供无用户主体的内部到期命令：在受信租户上下文按项目锁定当前主行、唯一`PROJECT_MANAGER`事实和当前根树版本，校验`ACTIVE/S4`并返回当前项目/参与者/树版本；不接收`subjectUserId/ACTION_EDIT/approvedBy/system actor`，不放宽现有用户授权API，也不以消费方冻结版本作相等前置。该Provider已选择性进入master，但不产生F-IMP-002 Feature Done。 |
| `ProjectDeliveryScopeQualificationFactApi` | COM-01 | Project / `T-FCOM001-PROJ-01`支撑Task | Commerce / F-COM-001 | `inspect/lockAndRevalidate`为COM交付范围写命令组合当前项目经理、项目生命周期/阶段与直管目标项目`ACTION_EDIT`事实。该用途只由锁定目标项目行的current `PROJECT_MANAGER`证明，不读取未锁定的授权Grant，也不扩展到后代项目；冻结并重验经理、项目/参与者/树版本、根身份、生命周期和阶段。三类闭环终态保留最后真实阶段，不限制为S6，树版本必须为正；S5/S6或关闭事实由COM用于把减配/释放转为`CONFLICT`。初次主体/范围失败与锁定期间任一冻结轴变化分别返回主体/范围错误和`FACT_STALE`，Owner损坏及Provider不可用不得混淆。当前master仅集成公共契约，生产Provider尚未实现。 |
| `DeliveryScopeApi.getAssignedScope` | COM-01、EXE-01 | Commerce / F-COM-001 | Implementation Execution / F-IMP-002 | 受信租户下按项目和可空期望`scopeVersion`读取；null为inspect，非null锁COM项目水位及当前范围后重验。返回以scope/detail为稳定分组的数量、单位、产品/型号及明确SN；待核对、取消、退货、释放量排除，任一未解决冲突则整体失败关闭。持久项目水位覆盖真实空范围，版本变化返回STALE，Owner损坏和Provider不可用独立分类。 |
| `CommerceAuthorityIngestApi.ingestBatch` | COM-01、INT-01 | Integration ACL / INT-01 | Commerce / F-COM-001 | 受信租户下以eventId接收一个原子批次的合同、销售订单、订单行和订单—合同关系精确事实；同event同载荷重放、异载荷永久冲突，旧来源版本不得覆盖。只形成COM本地副本，不包含ERP连接、认证、轮询或传输运行。 |
| `PlatformMigrationEvidenceApi` | COM-01、CUT-01～CUT-06 | Platform / PLT迁移证据Owner | Commerce Release导入器、Cutover旧数据前向核对 | PLT拥有迁移批次、不可变逐源行、外部键映射和迁移问题；批次按`IMPORTING -> STAGED_READY -> RECONCILING -> COMPLETED/FAILED`推进。消费方在同一外层事务领取、登记每个冻结来源的`MAPPED/ISSUE/RETAINED`唯一分类并完成计数核对；问题关闭只追加处理人、规则版本和目标结果。消费方不得访问PLT表，也不得以发送、导入或单行写入成功代替批次核对完成。 |
| `ProjectCutoverContextFactApi` / ProjectCutoverContextFact | CUT-01 | Project / PROJ | Cutover / F-CUT-002 | `inspect`按受信tenant和projectId读取同一`proj_project`行的项目编码/名称、项目发生时客户快照、发生时部门（办事处）快照及projectVersion；`lockAndRevalidate`携带从前次FOUND原样复制的完整Expected Fact，以`MANDATORY`加入CUT写事务并锁定同一项目行，锁后逐字段比较完整Fact。结果封闭为`FOUND/NOT_FOUND/INACTIVE/VERSION_CONFLICT`，只有ACTIVE、字段完整且全部字段精确匹配可供写入；Expected只作并发守卫，CUT只冻结Owner锁后返回的currentFact。编码字段最大64字符，三个名称字段最大255字符；公共DTO沿用`departmentId/departmentCode/departmentName`，CUT只在展示层称为办事处。该Fact不替代`ProjectScopeApi`的用户范围/treeVersion，也不替代CUS当前客户服务等级时间线；CUT不得读取PROJ/SYSTEM/CUS表或拼接无版本Summary。当前master仅集成ADR和机器合同，公共Java接口及生产Provider尚未实现。 |
| `ProjectCutoverServiceManagerFactApi` | CUT-05、PM-08 | Project / F-CUT-005物理Owner支撑Task | Cutover / F-CUT-005 | 按受信tenant/project解析当前唯一PRIMARY `SERVICE_MANAGER_L1/L2`及project/participant事实版本并锁定重验；零个或多个返回明确NOT_UNIQUE，不任选人员。服务经理节点改派目标只能等于当前唯一事实，不能用全局角色或项目范围单独替代。该支撑Task可复用ProjectParticipant聚合但不建立独立Feature或表；当前master仅集成候选Owner机器合同，公共Java接口及生产Provider尚未实现。 |
| CollectionTaskRequested | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Implementation Execution/Cutover/Inspection | Device Access & Collection | 业务 Context 请求受控下发任务；只传业务对象、设备、命令模板和授权引用，不传永久凭证权限或明文密码 |
| CollectionTaskAccepted | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Device Access & Collection | Implementation Execution/Cutover/Inspection | 采集任务已通过服务端授权校验并被接受，返回统一任务号和当前下发状态；不表示外部执行或业务处理成功 |
| CollectionResultCallback | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | 外部采集平台 | Device Access & Collection | 回调原值、签名、外部任务号和结果引用；重复回调幂等 |
| CollectionResultAvailable | INT-12、EXE-03、EXE-04、CUT-03、INS-02、INS-04 | Device Access & Collection | Implementation Execution/Cutover/Inspection | 返回CollectionTask、来源业务/项目/设备、原始状态和结果引用；CUT按清单版本与采集项引用回填P3工作台并独立判定业务结果 |
| ConfigurationLogPublished | EXE-03、EXE-04、EQP-02 | Implementation Execution | Asset Management | 实施域发布采集业务结果、原始文件引用、来源设备和实施解析状态；资产域幂等接收并形成ConfigurationLog及不可变解析版本，双方均不得覆盖来源证据 |
| ImplementationQualityGateChanged | IMP-01、CLO-01 | Implementation Execution | Project/ACC | 阶段质量检查通过、整改中或阻断的门禁事实；不包含已退出的IMP-02安全检查 |
| DeviceAssigned | EQP-01、EQP-03 | AST | Implementation Execution/Project | 设备当前最具体项目归属及生效版本 |
| EquipmentLocationEffective | EXE-02、EQP-01 | AST | Implementation Execution/Project | IMP通过`AssetLocationApi`公开命令提交已确认安装/迁移/拆除事实；AST在调用方事务内幂等更新设备当前地点和版本历史，AST不反向读取IMP表 |
| DeviceComponentRelationChanged | EXE-03、EQP-02、EQP-03 | AST | Implementation Execution/Cutover | 机框、槽位、板卡当前关系、生效区间、解析/人工绑定证据和关系版本 |
| SatisfactionResultVersionChanged | ACC-02、SUB-03 | ACC | ACC来源投影/未来CLO和SUB | 发布不可变Result业务版本及当前事实版本，区分RECORDED和INVALIDATED；按冻结任务身份精确重验，乱序不得恢复失效结果或覆盖新当前来源，消费者不得修改答卷 |
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
| `ProjectStageGateFactProviderApi` | PM-03@V1 | PROJ/ACC/BPM引用对象Owner | Project | 位于既有`pms-module-project-api`，按冻结Gate Reference身份调用类型化Provider；Query、Fact、Provider key和六类满足谓词见10分册。TASK/MILESTONE/STATE由PROJ，DELIVERABLE由ACC，APPROVAL/PROCESS由BPM Owner提供；Provider以`MANDATORY`加入阶段推进事务，不返回外域正文。 |
| `ProjectStageGateProcessOwnerApi` | PM-03@V1 | PMS Integration / Flowable | Project | 位于`pms-module-project-api`，由`pms-module-integration`实现；提供按`processDefinitionKey`检查当前生效定义、列出同租户可启动历史定义身份，以及按“冻结key + 可空显式processDefinitionId”启动Gate流程的反腐适配。PROJ对查询和启动均先重验`pms:project:update + PROJECT_MANAGE + 当前PROJECT_MANAGER`，不复用需要BPM全局定义查询权限的管理端接口；Provider只返回同key的`processDefinitionId/processDefinitionKey/name/selectable`。未显式选择时由BPM按key选取最新生效定义；显式选择时必须验证定义ID属于同一key且可启动。启动按固定businessKey/变量返回流程实例及实际定义ID；服务端设置Flowable authenticated initiator、start-user及RUNNING状态。既有Gate Reference `refVersion`仅保留历史且不得参与调用，不新增PMS流程版本接口、字段或解析规则，也不得修改Yudao接口或实现。 |
| `OrganizationScopeApi.getActiveScopes(userId)` | COM-01 | SYSTEM | COM | 在受信租户上下文返回当前有效UserCompanyDepartmentScope；COM只按`companyCode`与ERP合同所属公司编码精确匹配，scope ID/version仅用于审计。不得读取SYSTEM业务表或新建合同专用Provider。 |
| `SatisfactionQuestionnaireTemplateApi.resolvePublished` | ACC-02、PM-03 | ACC | PROJ | 项目创建时按项目类别、签约方式、实施方式、业务用途和适用时点唯一解析发布修订，返回模板/修订/规则/阈值Fact；零匹配或多匹配失败，PROJ只冻结引用 |
| `SatisfactionTaskInitializationApi.initialize` | ACC-02、PM-11 | ACC | PROJ/受信业务时点Owner | 以`MANDATORY`加入首次业务时点事务，ACC回查`ProjectWorkBindingFactApi`，冻结原始source/trigger Fact并返回collectionKey/revision=1；整改由ACC不可变RemediationFact触发同链下一revision，外部Owner不发明整改身份 |
| `SatisfactionResultFactApi.inspect/lockAndRevalidate` | ACC-02、CLO-01、SUB-03 | ACC | CLO/SUB | 返回稳定任务、问卷、答卷、结果、模板/规则/阈值、来源业务对象及版本和passed/valid/archive状态；消费者不得修改答卷或自行推断通过 |
| `FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload` | ACC-02、PLT-02 | PLT | ACC | 仅接受ACC已验证ACTIVE访问授权及其grant版本，按唯一满意度文件策略上传签字/附件；不伪造登录用户，PLT继续执行文件校验、版本和审计 |
| `FileArtifactApi.createGeneratedBusinessFile` | ACC-02、PLT-02 | PLT | ACC | 只为精确Result目标生成不可变判定文档；命令冻结责任人actor、scopeVersion、operationId和服务端内容，Provider以MANDATORY加入ACC判定事务并重验`pms:file:upload`/租户/FileBusinessScope；复用FileUploadSession补偿对象存储先行写入，失败零Result/Outbox |
| `ExportTaskApi.request/getFact/retry`、`ExportBusinessDataProvider` | ACC-02、PLT-02 | PLT | ACC及其他受控业务Owner | PLT拥有唯一异步Task/Audit与文件TTL；消费Context Provider拥有查询语义并在申请、生成、显式重试、下载时重验功能/数据/字段/文件/租户范围；只允许原actor把可重试FAILED按version CAS恢复为REQUESTED；F-ACC-002固定`ACC/SATISFACTION_RESULT`，不得建立第二导出真值 |
| `AcceptanceActivityInitializationApi.initialize` | ACC-03 | ACC | PROJ | 以`MANDATORY`加入项目创建事务；PROJ预分配执行契约ID并传精确初验/终验任务与应交码，ACC创建PENDING活动并返回`acceptanceId/activityVersion`，PROJ随后追加ACC执行契约；任一步失败整体回滚 |
| `FileArtifactApi.archiveReferenceSets` | ACC-03、ACC-04、PLT-02 | PLT | ACC | 受信命令显式携带报告发布时冻结的`actorUserId`；PLT按该用户重验既有`pms:file:archive`权限和租户/文件范围，持锁重验ACC报告附件ACTIVE集合，在独立`ACCEPTANCE_REPORT_ARCHIVE`集合按相同公共文件事实创建ARCHIVED引用并整组追加记录且写`archivedBy=actorUserId`；附件引用保持ACTIVE供历史下载，不暴露PLT内部主键，ACC只保存归档补偿投影 |
| ProjectStageAdvanceCommand | PM-03 | Project | Project | Project/tree/graph/scope版本；当前完成及准出→唯一冻结转移→目标准入→原子推进。目标由服务端解析，不按S编号加一。 |
| ProjectScopeAppendApplied | PM-06、COM-01、ACC-03 | Commerce | Project / Acceptance & Closure | COM拥有数量和范围水位；同一projectId的新精确范围、任务、绑定及门禁同事务生效。旧A验收不自动覆盖A+B。 |
| AcceptanceReportQualificationFact | ACC-03、CLO-01 | Acceptance & Closure | Project / Closure | reportVersion、reportEvidenceValid、acceptancePassed、scopeVersion、精确范围和文件版本分别保存；字段完整不等于验收通过。 |
| ProjectTypedClosureCommand | CLO-01、CLO-02、PM-10 | Acceptance & Closure / Project governance | Project | closureType、closedFromStage、最新Gate和BPM实际定义及项目版本；CLO-02/PM-10为唯一业务入口，事件不代替终态命令。 |
| ApprovedImplementationCommandSnapshot | EXE-03、INT-12 | Implementation Execution | Device Access & Collection | 仅EXE-03受信批准记录/版本/哈希/设备/主体范围；DAC重验。独立中心、CUT、INS仍需要已发布命令模板。 |
| CutoverChecklistCollectionBinding | CUT-03、INT-12 | Cutover | Device Access & Collection / Cutover | taskId、checklistVersion、itemId、deviceId、CollectionTask及resultVersion精确绑定；技术回调只提供证据，CUT判定业务通过。 |
| AuthenticatedFileCallback | PLT-02、INT-12 | Authenticated integration caller | Platform file service | 来源身份、契约验签、任务/对象权限、大小/类型/哈希和幂等同时校验；幂等键不能替代认证。 |

契约只传稳定标识、版本和快照，不允许消费者直接写 Producer 的 Repository。跨域契约统一保留 eventId、eventType、eventVersion、aggregateId、aggregateVersion、actor、tenant、authorizationSnapshot、traceId、sourceContext、occurredAt；默认最终一致，使用 Outbox、Inbox、幂等、补偿和对账。

F-PROJ-001手动项目创建是经ADR-0032批准的限定例外：PROJ同步调用ACC公开内部应用接口，ACC加入调用方同一MySQL事务；正式Project、ProjectTask执行契约和ACC交付件实例必须全有或全无，不产生初始化中间状态。该例外不允许PROJ直接访问ACC Repository，也不改变其他跨Context契约的默认最终一致性。若部署边界不再共享同一事务资源，必须先批准创建完成语义变更，不得自行降级为Saga、异步补建或部分成功。

组织与地点遵循ADR-0033：SYSTEM通过`CompanyApi/DeptApi/OrganizationScopeApi`提供稳定主数据和同一行公司—部门范围；AST通过`AssetLocationApi`提供Address/Site/SiteLocation维护、版本校验、精确区划映射和设备位置生效命令。CUS、PROJ、IMP不得直接访问SYSTEM或AST的DO、Mapper、Repository或业务表。

与客户和设备主档相关的命令和查询只传稳定ID、来源版本、期望版本、权限快照与幂等键。`INT-02`、`INT-03`、`INT-04`及`EQP-04`保持独立同步Feature。

## F-PROJ-008 阶段门禁 Owner Fact 基线（GO）

Registry按固定`refType -> providerKey`映射唯一分派：`TASK/PROJ_TASK`、`MILESTONE/PROJ_MILESTONE`、`STATE/PROJ_STATE`、`DELIVERABLE/ACC_DELIVERABLE`、`APPROVAL/BPM_APPROVAL`、`PROCESS/BPM_PROCESS`，不接受客户端Owner选择。未登记、重复Provider、Owner不可用或身份/版本不一致均失败关闭；PROJ不得跨Context读表或按名称推断事实。目标为实际S5时，统一PROJ图推进服务通过COM/ACC公开接口在同事务完成精确范围校验和绑定；旧专用入口只委托统一推进，不保留第二个current_stage Writer。

## 修订017差量契约

修订017契约已合并至本文件上方唯一契约表，分别列明Requirement、Producer、Consumer和语义；详细字段与事务见05、08、12、13分册。

对应PRD审查项、派生覆盖和验证结果见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。本文不能替代Feature物理合同重验证、独立复审或运行测试。

## 实施就绪与证据回执补充

来源：`codex/f-cut-001-matrices@faed8387`。本节补齐既有非COM事实契约，不改变修订017、当前Feature状态或生产装配边界。

| 接口/事件 | Requirement | 提供方 | 消费方 | 契约 |
|---|---|---|---|---|
| ImplementationEvidencePublished | EXE-01～EXE-06、IMP-01、ACC-04 | Implementation Execution | Acceptance & Closure | IMP出向事件；实施`evidenceId/revision`、来源需求/记录/版本、FileReference、哈希和检查快照已发布，ACC按该不可变revision建立审核/归档引用，不覆盖IMP事实 |
| ArtifactAccepted / ArtifactArchived | EXE-01～EXE-06、IMP-01、ACC-04 | Acceptance & Closure | Implementation Execution | ACC入向回执；回显`evidenceId/evidenceRevision/artifactId/fileVersion/reviewOrArchiveRecordId`，IMP按eventId Inbox和`evidenceId+revision`幂等推进同步投影；Accepted后Archived超时保留已接受事实并以独立归档回执重试态重发同revision，匹配Archived可恢复至已归档；旧序、错配或重复回执不得覆盖当前revision，回执失败不回滚来源业务事实 |
| `ImplementationReadinessApi` / ImplementationReadinessSnapshot | EXE-06、CUT-01 | Implementation Execution / F-IMP-001 | Cutover / F-CUT-002 | `inspect`按受信租户、项目和完整设备归属水位读取最新CUTOVER快照并对照当前Owner事实；`lockAndRevalidate`再携带明确快照ID/版本加入CUT写事务重验。结果封闭为`READY/NOT_READY/STALE`，返回不可变快照及结构化项目、设备、批准方案和EXE-01～04来源水位；缺快照、Owner损坏和Provider不可用使用不同公共失败。CUT不得直读IMP/EXE表，测试替身不得进入生产装配或充当真实Owner证据。 |
| `ArrivalAcceptanceFactApi` | EXE-01、EXE-02、EXE-06 | Implementation Execution / F-IMP-002 | F-IMP-003、F-IMP-001 | 按项目、设备/订单数量范围返回`ACCEPTED/NOT_ACCEPTED/STALE`、稳定有序`sourceAcceptanceIds`、项目级单调`factVersion`、由DeliveryScope版本和设备归属版本组成的`scopeWatermark`、已签/豁免/未满足范围与`reopened`；提供无副作用`inspect/lockAndRevalidate`，不返回DO、文件正文或签收人隐私 |
| `InstallationCompletionFactApi` | EXE-02、EXE-06 | Implementation Execution / F-IMP-003 | F-IMP-001 | 按稳定设备范围返回`COMPLETED/NOT_COMPLETED/STALE`、安装来源对象、业务版本、范围水位及`reopened`，并按期望版本锁定重验；不以位置投影或附件替代安装完成事实 |
| `ConfigurationCompletionFactApi` | EXE-03、EXE-06 | Implementation Execution / F-IMP-004 | F-IMP-001 | 按稳定设备和批准模板/采集版本返回`COMPLETED/NOT_COMPLETED/STALE`、结果版本、范围水位及`reopened`，并锁定重验；采集任务受理、原始Log存在或解析尝试不等于配置完成 |
| `JointDebuggingCompletionFactApi` | EXE-04、EXE-06 | Implementation Execution / F-IMP-005 | F-IMP-001 | 按项目/设备/联调项范围返回`COMPLETED/NOT_COMPLETED/STALE`、结果版本、范围水位及`reopened`，并锁定重验；问题未闭环或证据失效时失败关闭 |
