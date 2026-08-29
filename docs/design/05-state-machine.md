# SDS Phase 1：状态机设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.8（`docs/baseline/prd-v1.8.md`）
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；V1.8独立复审GO，当前分册已纳入正式基线
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 1. 规则

业务状态机与审批工作流分离：状态机表达业务事实，工作流表达审批节点。状态值采用基础平台可配置字典，但核心状态、终态和强制门禁不可被任意删除；扩展状态必须声明父状态映射、合法迁移、角色、进入/退出条件和版本。

## 2. 核心状态机

| 对象 | 核心状态 | 关键迁移与守卫 | 事件 |
|---|---|---|---|
| Project | `current_stage`：待开始(S0)、工前准备(S1)、施工计划(S2)、实施方案(S3)、实施部署(S4)、验收交维(S5)、闭环(S6)；另有`lifecycle_status`：ACTIVE、NORMAL_CLOSED、EXCEPTION_CLOSED；`assignment_status`和派生`display_status`独立维护 | 阶段推进只改变`current_stage`；CLO-02唯一产生NORMAL_CLOSED，PM-10唯一产生EXCEPTION_CLOSED；回退、重开和归档按V1.8守卫执行；闭环后不得进入维护阶段 | ProjectStageChanged、ProjectClosed（携带lifecycleStatus与关闭原因） |
| ProjectTask | 待分配、待开始、进行中、待验收、完成、关闭 | 父任务/阶段约束；不限制层级深度但禁止环；完成必须由冻结CompletionRule校验绑定业务事实/审批/表单/子任务/门禁快照，前端通用按钮不得直接推进 | TaskAssigned、TaskCompleted |
| DynamicFormTemplate | 修订状态`DRAFT/PUBLISHED`；模板可用性`ENABLED/DISABLED`独立 | 新模板以唯一DRAFT开始；DRAFT可编辑并发布为不可变PUBLISHED，后续修改从当前发布修订复制下一DRAFT；ENABLED只控制新实例选择，停用不改已发布修订或既有实例 | 首版不发布领域事件；发布、启停由PLT事务审计留痕 |
| Device | 无独立业务状态机（主数据事实）；设备状态、在网状态及停产停维状态使用来源事实和基础平台可配置字典 | 设备档案同步或受控平台扩展字段更新必须保留来源版本；项目归属变更不得隐式改写设备来源状态 | DeviceStatusSynchronized、DeviceOwnershipChanged |
| CollectionTask | 创建、授权校验、已下发、执行中、回调中、已消费、完成、失败 | 幂等键、短期授权、回调签名/来源校验；失败只允许创建新的受控重试任务 | CollectionTaskDispatched、CollectionResultAvailable、CollectionCompleted |
| CutoverTask | 待办理、等级确认中、调研中、方案编制中、审批中、驳回待修改、闭环中、已归档 | CUT-01贯穿P1～P6；P1是接入入口、工作台显示P2～P6；P3内部直接填写或关联CollectionTask不产生新状态；D级确认后跳过P3；P5任一否项驳回P4；P6提交形成归档闭环，最终成功才发布完成事件 | CutoverApproved、CutoverCompleted |
| InspectionTask | 待准备、待预检、巡检中、待报告、待标注、待办跟踪中、已闭环、已归档、已取消 | INS-02.S1与INS-03完成后，在线分支进入待预检且仅INS-04通过后进入巡检中，离线分支直接进入巡检中；执行后依次经过INS-05报告、INS-06标注和INS-07闭环归档，不能跳过报告、标注或待办跟踪门禁 | InspectionCompleted、InspectionClosed |
| DeliveryEvidence | 草稿、已上传、待审核、已通过、已驳回、已归档 | IMP 可在实施阶段上传并替换草稿；ACC 审核/归档；已归档版本不可被 IMP 覆盖 | DeliveryEvidenceUploaded、ArtifactAccepted |
| ArrivalAcceptance | 草稿、部分签收、已签收、差异待处理、已确认 | 到货数量/序列号和证据校验；差异未确认不得作为齐套依据 | ArrivalAccepted、ArrivalDifferenceRaised |
| InstallationRecord | 草稿、待确认、已安装、整改中、已确认 | 安装位置、照片和设备关联完整；整改完成后才能确认 | InstallationConfirmed、InstallationRemediationCreated |
| ConfigurationCollectionResult | 待采集、采集中、回调待处理、解析失败、已解析、已确认 | 只消费任务级结果引用；重复回调幂等；解析失败不得伪造成功 | ConfigurationCollected、ConfigurationParsed |
| JointDebuggingResult | 待联调、联调中、问题待处理、已完成、已确认 | 联调结果和关联设备/版本完整；未完成问题不得确认 | JointDebuggingCompleted、JointDebuggingIssueRaised |
| ImplementationRisk | 已标记、评估中、处置中、已关闭 | 风险等级、责任人和处置证据完整；高风险不得绕过门禁 | ImplementationRiskRaised、ImplementationRiskClosed |
| ImplementationQualityCheck | 草稿、待复核、整改中、复核通过、复核不通过、阻断 | 不合格必须整改后再复核；豁免需有权角色、依据、范围、有效期和审计 | ImplementationQualityChecked、ImplementationQualityBlocked |
| SatisfactionCollection | 待生成、待发送、收集中、待判定、未通过、已通过、归档待重试、已归档 | 冻结模板/题目/阈值；客户有效答案和签字不可覆盖；未通过须整改后创建新任务和问卷版本，不允许人工改分或异常放行 | SatisfactionTaskCreated、SatisfactionSubmitted、SatisfactionResultRecorded |
| AcceptanceActivity / AcceptanceReportVersion | 活动：`PENDING/COMPLETED`；报告：`DRAFT/EFFECTIVE/SUPERSEDED/REVOKED` | 草稿四项完备后才可发布；首次发布原子转EFFECTIVE，替换在同一事务关闭旧EFFECTIVE为SUPERSEDED并发布新版本，撤销关闭当前为REVOKED、清空活动当前指针且不恢复旧版；终验发布须锁定同项目当前有效初验；PROJ通过冻结ProjectTask/WorkBinding以MANDATORY调用ACC完成活动 | AcceptanceReportVersionChanged、ClosureGateRecheckRequested |
| ProjectClosure | 草稿、待审核、材料审核、已完成、驳回整改 | 项目冻结模板要求的交付件和有效满意度等门禁满足；CLO-02完成后形成不可变NORMAL_CLOSED闭环事实；不创建回访节点；驳回后重新校验并新建申请 | ClosureSubmitted、ProjectClosureCompleted |
| DeviceCredential | 创建、启用、授权、撤销、轮换、停用 | 仅授权范围内任务可引用；撤销影响后续任务，不改历史快照 | CredentialGranted、CredentialRevoked |

### 2.1 Project状态分层守卫

1. PM-01创建项目时写入`lifecycle_status=ACTIVE`、`current_stage=S0`；未完成主责指派时`assignment_status=UNASSIGNED`。
2. 阶段推进只允许在当前阶段门禁满足且操作者有权时修改`current_stage`；进入项目设定的验收阶段时，PROJ必须先以同一事务调用ACC完成全部当前有效DeliveryScope分配版本绑定，任一失败时阶段快照、绑定和`current_stage`整体不成功；`display_status`只读派生，不得反写任何生命周期字段。
3. PM-10“回退”保持`lifecycle_status=ACTIVE`，将`current_stage`回到S0并按规则置为待指派；PM-10“异常关闭”才写入`EXCEPTION_CLOSED`并保存关闭依据。
4. CLO-02审批全部通过后才写入`NORMAL_CLOSED`并形成不可变闭环事实；任何其他接口、同步回调或通知不得产生该终态。
5. 仅允许对`EXCEPTION_CLOSED`项目执行受控重开并恢复为`ACTIVE`；重开必须记录重开原因，恢复关闭前最后一个可恢复阶段并创建新的责任处理事项，不得自动恢复已终止的外部任务。`NORMAL_CLOSED`不得通过PM-10直接重开。正常闭环后的巡检、割接保障和其他售后活动使用独立领域任务，不新增项目维护阶段。
6. 初验/终验报告不是进入项目设定验收阶段的门禁；PROJ申请完成冻结的初验或终验ProjectTask时，以同一事务调用ACC完成对应活动，ACC校验当前有效报告的验收时间、结论、验收人和附件，任一缺失时任务和活动均不完成；ACC不直接改PROJ任务。交付件索引或归档结果不是第五项完成字段，其失败保留有效报告并进入补偿，不回退已成功的阶段进入与范围绑定。
7. `Q-FCOM-002`关闭前，退出或PM-10回退不自动关闭、解锁或改写既有`AcceptanceScopeBinding`；该问题不阻断合法阶段回退本身，也不改变COM减量继续读取既有锁定事实。

## 3. 版本化

状态字典、迁移定义和门禁规则均带版本；任务实例保存绑定版本。已发布版本不可原地修改，只能新建版本并通过配置审批。

ProjectTask的WorkBinding和CompletionRule版本与任务状态机版本分别冻结，且每个任务必须且只能有一个当前绑定。`TASK_NATIVE`按ProjectTask自身状态机和任务事实执行受控迁移；其他绑定的业务对象状态变化只触发重新评估，不允许业务Context直接写ProjectTask状态。Project Delivery在校验任务版本、绑定版本、事实版本和规则版本后执行受控迁移并记录完成判定快照。

共享`DynamicFormInstance`在F-PLT-002首版只是冻结模板修订并以CAS保存值的载体，不新增提交、完成、审批、删除或换模状态。PRE、SCH、IMP、ACC、CUT等消费者各自拥有并冻结业务状态与门禁，不得把PLT实例保存解释为领域完成。
