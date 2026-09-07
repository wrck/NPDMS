# SDS Phase 1：状态机设计

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；既有独立复审GO仅属原批准范围，当前差量须按Gate重验证
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 1. 规则

业务状态机与审批工作流分离：状态机表达业务事实，工作流表达审批节点。状态值采用基础平台可配置字典，但核心状态、终态和强制门禁不可被任意删除；扩展状态必须声明父状态映射、合法迁移、角色、进入/退出条件和版本。

## 2. 核心状态机

| 对象 | 核心状态 | 关键迁移与守卫 | 事件 |
|---|---|---|---|
| Project | `current_stage`：待开始(S0)、工前准备(S1)、施工计划(S2)、实施方案(S3)、实施部署(S4)、验收交维(S5)、闭环(S6)；另有`lifecycle_status`：ACTIVE、NORMAL_CLOSED、NO_TRACKING_CLOSED、EXCEPTION_CLOSED；`assignment_status`和派生`display_status`独立维护 | 阶段推进只改变`current_stage`；CLO-02唯一产生NORMAL_CLOSED或NO_TRACKING_CLOSED，PM-10唯一产生EXCEPTION_CLOSED；回退、重开和归档按V1.8守卫执行；闭环后不得进入维护阶段 | ProjectStageChanged、ProjectClosed（携带lifecycleStatus与关闭原因） |
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
| ProjectClosure | 草稿、待审核、材料审核、已完成、驳回整改；闭环类型独立 | NORMAL/NO_TRACKING分别校验，CLO-02按类型原子形成终态；不造S5/S6，失败保持ACTIVE | ClosureSubmitted、ProjectClosureCompleted（携带closureType/closedFromStage） |
| DeviceCredential | 创建、启用、授权、撤销、轮换、停用 | 仅授权范围内任务可引用；撤销影响后续任务，不改历史快照 | CredentialGranted、CredentialRevoked |

### 2.1 Project状态分层守卫

1. PM-01创建项目时写入`lifecycle_status=ACTIVE`、`current_stage=S0`；未完成主责指派时`assignment_status=UNASSIGNED`。
2. 阶段推进只允许在当前阶段门禁满足且操作者有权时修改`current_stage`；`display_status`只读派生，不得反写任何生命周期字段。
3. PM-10“回退”保持`lifecycle_status=ACTIVE`，按允许重新指派的受控规则处理阶段并置为待指派，不隐式固定回到S0；PM-10“异常关闭”才写入`EXCEPTION_CLOSED`并保存关闭依据。
4. CLO-02审批全部通过后才按closure_type写入`NORMAL_CLOSED`或`NO_TRACKING_CLOSED`并形成不可变闭环事实；任何其他接口、同步回调或通知不得产生该终态。
5. 仅允许对`EXCEPTION_CLOSED`项目执行受控重开并恢复为`ACTIVE`；重开必须记录重开原因，恢复关闭前最后一个可恢复阶段并创建新的责任处理事项，不得自动恢复已终止的外部任务。`NORMAL_CLOSED`和`NO_TRACKING_CLOSED`不得通过PM-10直接重开。正常闭环后的巡检、割接保障和其他售后活动使用独立领域任务，不新增项目维护阶段。
6. EXE-01豁免到期内部命令不得沿用历史审批人或消费方冻结版本作为当前授权。PROJ以`ProjectSystemQualificationFactApi`锁定根项目、目标项目和当前根树版本，只在目标项目为`ACTIVE/S4`且当前项目经理、项目版本和树版本完整时返回系统资格事实；该契约不授予任何用户`ACTION_EDIT`。

## 3. 版本化

状态字典、迁移定义和门禁规则均带版本；任务实例保存绑定版本。已发布版本不可原地修改，只能新建版本并通过配置审批。

ProjectTask的WorkBinding和CompletionRule版本与任务状态机版本分别冻结，且每个任务必须且只能有一个当前绑定。`TASK_NATIVE`按ProjectTask自身状态机和任务事实执行受控迁移；其他绑定的业务对象状态变化只触发重新评估，不允许业务Context直接写ProjectTask状态。Project Delivery在校验任务版本、绑定版本、事实版本和规则版本后执行受控迁移并记录完成判定快照。

共享`DynamicFormInstance`在F-PLT-002首版只是冻结模板修订并以CAS保存值的载体，不新增提交、完成、审批、删除或换模状态。PRE、SCH、IMP、ACC、CUT等消费者各自拥有并冻结业务状态与门禁，不得把PLT实例保存解释为领域完成。

## 修订017差量契约

### 阶段图、范围与终态

ProjectTemplateVersion以已发布StageDefinition/TaskDefinition、StageTransitionDefinition、阶段/任务交付件要求及Stage/Task WorkBinding形成不可变快照。每个执行节点只有一个主绑定，原生分别为STAGE_NATIVE/TASK_NATIVE，组合视图仍由各Owner鉴权。发布校验唯一开始、可达收口、无环、无悬空和分支可唯一判定；只实例化图中真实阶段，不使用虚假的NOT_APPLICABLE阶段。

PROJ拥有唯一阶段推进命令：锁定并重验Project/当前阶段/模板图版本/范围水位，计算当前CompletionRule及准出，唯一解析出向转移，再校验目标准入；全部通过后原子关闭当前节点、激活实际目标并写current_stage、版本、不可变快照、审计及Outbox。目标来自冻结图，不来自S编号加一、SOL/CUT回调或客户端。无目标但当前为允许收口节点时使用CLO入口，不生成新阶段；零/多目标或目标准入失败不推进。修订018中，终验结果只在配置显式引用时参与，S5不自动要求终验；受控创建动作与只读判定分离，范围绑定按明确Owner事务契约执行，COM/ACC不另写阶段。具体独立验收身份与完成点见Q-TPLACC-001。

售前模板只有S0/S4，S4只要求EXE-03/04；没有EXE-02、S5和S6实例。其他模板仅在启用EXE-02时要求安装完成。所有场景继续校验设备、项目、命令和文件范围。后续计划/方案换版只改变Owner基线和当前门禁，不隐式移动current_stage；上游条件失效保留过去快照，并阻止依赖该条件的新动作。

终态固定ACTIVE/NORMAL_CLOSED/NO_TRACKING_CLOSED/EXCEPTION_CLOSED。CLO-02是前两类闭环的唯一业务入口，由其同一业务事务调用PROJ受控终态Writer；PM-10仅产生EXCEPTION_CLOSED。终态同时冻结closure_type、closed_from_stage及闭环依据，current_stage保留最后真实阶段。普通事件消费者、BPM回调和ACC-06不能另写终态。NO_TRACKING先检查代理商自服资格、显式意图、依据和在途/后代处置，不先要求正常交付准出；NORMAL才检查模板实际交付条件。PM-10重开只适用于EXCEPTION_CLOSED。

ACC报告证据与验收通过分离、范围变化及CLO快照失效见08。CUT-03暂存保持P3，只有有效提交进入P4；巡检实际归档状态由服务经理受控命令推进，详见06/07/12。

对应PRD审查项、派生覆盖和验证结果见`docs/engineering/gates/phase-1/prd-revision-016-alignment.md`。本文不能替代Feature物理合同重验证、独立复审或运行测试。

## CUT P4/P5与到货批次事实补充

来源：`codex/f-cut-001-matrices@faed8387`。本节补齐既有非COM事实契约，不改变修订017、当前Feature状态或生产装配边界。

- CUT-01仍以P1为接入入口、P2～P6为五步工作台。新平台持久阶段/状态对为P2/GRADE_CONFIRMING、P3/SURVEYING、P4/PLAN_DRAFTING、P5/APPROVING、P6/CLOSURE_IN_PROGRESS；D级确认后跳过P3。
- P4 revision只使用DRAFT/SUBMITTED/INVALIDATED；提交与CUT-05审批实例同成同败后由F-CUT-004写P4→P5。F-CUT-005最终驳回写P5→P4，全部通过写P5→P6。来源失效由F-CUT-004在暂停审批的同事务写P5→P4，并在恢复办理时派生替代revision。
- CUT-05根为PENDING/PAUSED_SOURCE_INVALIDATED/APPROVED/REJECTED；A四级、B三级、C/D二级路由冻结。候选不唯一或授权失效以holdReason暂停待办，不新增伪终态；改派只改变当前处理人并追加历史。

`Q-FCUT004-001`关闭前不得从上述状态机推导`P6/CLOSURE_IN_PROGRESS -> P4/PLAN_DRAFTING`。批准后职责变化虽需创建新方案并重走P5，但其回退Owner、历史触发器、旧批准revision/APPROVED事实及在途CUT-06闭环处置尚未锁定；当前只允许执行不需要该迁移的批准联系人PATCH。

- `DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED`均为到货批次状态；项目级里程碑由`ArrivalAcceptanceFactApi`独立返回`ACCEPTED/NOT_ACCEPTED/STALE`，不把任一批次状态直接当作项目完成。
