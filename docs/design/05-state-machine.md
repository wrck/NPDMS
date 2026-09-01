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
| CutoverTask | 等级确认中、调研中、方案编制中、审批中、闭环中、已归档 | CUT-01贯穿P1～P6；新平台持久阶段/状态对依次为P2/GRADE_CONFIRMING、P3/SURVEYING、P4/PLAN_DRAFTING、P5/APPROVING、P6/CLOSURE_IN_PROGRESS；D级确认后跳过P3。P4方案revision仅有DRAFT/SUBMITTED/INVALIDATED，提交与CUT-05审批实例同成同败后由F-CUT-004写P4→P5；F-CUT-005最终驳回写P5→P4，全部通过写P5→P6；来源失效由F-CUT-004在暂停审批的同事务写P5→P4并派生替代revision。P6提交形成归档闭环 | CutoverApproved（仅CUT-05）、CutoverCompleted（仅CUT-06） |
| CutoverApproval | PENDING、PAUSED_SOURCE_INVALIDATED、APPROVED、REJECTED | 节点按等级冻结为A四级、B三级、C/D二级；节点仅按WAITING→PENDING→APPROVED/REJECTED推进，驳回取消未来节点。候选未唯一或授权失效时根保持PENDING并以holdReason暂停待办，不新增伪业务终态；改派只改变当前处理人并追加历史 | CutoverApproved（仅全部节点通过） |

`Q-FCUT004-001`关闭前不得从上述状态机推导`P6/CLOSURE_IN_PROGRESS -> P4/PLAN_DRAFTING`。批准后职责变化虽需创建新方案并重走P5，但其回退Owner、历史触发器、旧批准revision/APPROVED事实及在途CUT-06闭环处置尚未锁定；当前只允许执行不需要该迁移的批准联系人PATCH。
| InspectionTask | 待准备、待预检、巡检中、待报告、待标注、待办跟踪中、已闭环、已归档、已取消 | INS-02.S1与INS-03完成后，在线分支进入待预检且仅INS-04通过后进入巡检中，离线分支直接进入巡检中；执行后依次经过INS-05报告、INS-06标注和INS-07闭环归档，不能跳过报告、标注或待办跟踪门禁 | InspectionCompleted、InspectionClosed |
| DeliveryEvidence | 草稿、已上传、待审核、已通过、已驳回、已归档 | IMP 可在实施阶段上传并替换草稿；ACC 审核/归档；已归档版本不可被 IMP 覆盖 | DeliveryEvidenceUploaded、ArtifactAccepted |
| ArrivalAcceptance | 草稿、部分签收、已签收、差异待处理、已确认 | 到货数量/序列号和证据校验；差异未确认不得作为齐套依据 | ArrivalAccepted、ArrivalDifferenceRaised |
| InstallationRecord | 草稿、待确认、已安装、整改中、已确认 | 安装位置、照片和设备关联完整；整改完成后才能确认 | InstallationConfirmed、InstallationRemediationCreated |
| ConfigurationCollectionResult | 待采集、采集中、回调待处理、解析失败、已解析、已确认 | 只消费任务级结果引用；重复回调幂等；解析失败不得伪造成功 | ConfigurationCollected、ConfigurationParsed |
| JointDebuggingResult | 待联调、联调中、问题待处理、已完成、已确认 | 联调结果和关联设备/版本完整；未完成问题不得确认 | JointDebuggingCompleted、JointDebuggingIssueRaised |
| ImplementationRisk | 已标记、评估中、处置中、已关闭 | 风险等级、责任人和处置证据完整；高风险不得绕过门禁 | ImplementationRiskRaised、ImplementationRiskClosed |
| ImplementationQualityCheck | 草稿、待复核、整改中、复核通过、复核不通过、阻断 | 不合格必须整改后再复核；豁免需有权角色、依据、范围、有效期和审计 | ImplementationQualityChecked、ImplementationQualityBlocked |
| SatisfactionCollection | 待生成、待发送、收集中、待判定、未通过、已通过、归档待重试、已归档 | 冻结模板/题目/阈值；客户有效答案和签字不可覆盖；未通过须整改后创建新任务和问卷版本，不允许人工改分或异常放行 | SatisfactionTaskCreated、SatisfactionSubmitted、SatisfactionResultRecorded |
| ProjectClosure | 草稿、待审核、材料审核、已完成、驳回整改 | 项目冻结模板要求的交付件和有效满意度等门禁满足；CLO-02完成后形成不可变NORMAL_CLOSED闭环事实；不创建回访节点；驳回后重新校验并新建申请 | ClosureSubmitted、ProjectClosureCompleted |
| DeviceCredential | 创建、启用、授权、撤销、轮换、停用 | 仅授权范围内任务可引用；撤销影响后续任务，不改历史快照 | CredentialGranted、CredentialRevoked |

### ArrivalAcceptance转换细化

- `DRAFT/PARTIALLY_ACCEPTED/DIFFERENCE_PENDING/ACCEPTED/CONFIRMED`均为到货批次状态；项目级里程碑由`ArrivalAcceptanceFactApi`独立返回`ACCEPTED/NOT_ACCEPTED/STALE`，不把任一批次状态直接当作项目完成。
- 授权现场成员提交DRAFT时：有未解决差异进入DIFFERENCE_PENDING；无未解决差异但累计候选范围未覆盖全部当前应到范围进入PARTIALLY_ACCEPTED；累计候选范围全部满足进入ACCEPTED。数量、SN、DeliveryScope/设备水位或证据无效时拒绝提交并保持DRAFT。
- DIFFERENCE_PENDING只有在全部差异追加明确处置后，按重算结果进入PARTIALLY_ACCEPTED或ACCEPTED；拒收保持对应范围未满足，补签形成ACCEPTED明细，具体豁免仅在有效期内满足其明确范围。
- 项目经理最终确认只允许`PARTIALLY_ACCEPTED -> CONFIRMED`或`ACCEPTED -> CONFIRMED`。CONFIRMED仅表示本批最终确认；只有确认批次中的ACCEPTED明细及有效具体豁免参与项目事实计算，项目仍有未到/拒收/过期豁免时返回NOT_ACCEPTED。
- CONFIRMED批次不回退、不覆盖。补签、更正、差异关闭或豁免失效创建关联原批次的后续DRAFT；普通补签后继确认分配新的项目事实版本但`reopened=false`，更正、已发布事实重开或豁免失效才使新事实`reopened=true`。
- 后续DRAFT不得仅以`predecessorAcceptanceId`推导事实含义；服务端同时固化`successorReason=SUPPLEMENT/CORRECTION/DIFFERENCE_CLOSURE/EXEMPTION_INVALIDATION`。普通新到范围补签不标记重开；更正、已确认历史上的差异关闭或豁免失效均为可证明的重开来源。后继DRAFT本身不发布事实，确认时才由根分配项目事实版本；豁免到期的独立失效revision按下一条规则即时分配。
- successor原样继承直接前驱`batchCode`，用新的acceptance id和`predecessorAcceptanceId`区分记录；初始根占用`batch_root_marker=1`，后继marker为NULL。创建后继必须锁当前前驱且同一前驱至多一个直接后继；已存在后继时除平台幂等重放外返回状态冲突，后续更正只能从链上最新已确认记录继续。
- 数量差异部分补签时，当前OPEN revision只能收窄为同一订单/型号/单位身份下的精确剩余正数量；全量补齐才进入SUPPLEMENTED，设备差异只能整项补签。豁免到期不由读取事实触发，而由Task 5B内部到期命令在PROJ项目锁内追加`EXEMPTION_INVALIDATION`事实影响revision并创建后继DRAFT；任一Owner锁定重验失败则失败关闭并等待重试。
- `CONFIRMED`来源不得保留current `OPEN`差异。确认后的人工处置只允许从current `REJECTED`进入：`SUPPLEMENT`整项补设备或按严格剩余量部分/全量补数量，`EXEMPT`形成带新证据和期限的明确豁免，`CLOSE`保持未满足范围并关闭该差异；`KEEP_REJECTED`只允许未确认批次的`OPEN -> REJECTED`。current `SUPPLEMENTED/EXEMPTED/CLOSED`不再由人工差异分支变更，豁免失效只走内部命令。
- 豁免到期只从没有任意直接successor的最新`CONFIRMED`链节点领取。内部命令以PROJ系统资格锁取得当前`ACTIVE/S4`、唯一项目经理及项目/参与者/树版本，新后继保存当前版本；历史审批人只作证据，不作当前授权主体，前驱冻结版本不回填也不作为当前系统锁相等条件。

DeliveryEvidence的ACC同步投影区分两类重试：Accepted前发布/回执失败使用`ARCHIVE_PENDING_RETRY`；已收到匹配Accepted后等待Archived超时使用`ARCHIVE_ACK_PENDING_RETRY`，不得丢失已接受事实。两类重试均重发同一`evidenceId+revision`；匹配Archived只允许从`ACCEPTED_PENDING_ARCHIVE`或`ARCHIVE_ACK_PENDING_RETRY`进入ARCHIVED，重复Accepted幂等且不创建新revision。

### 2.1 Project状态分层守卫

1. PM-01创建项目时写入`lifecycle_status=ACTIVE`、`current_stage=S0`；未完成主责指派时`assignment_status=UNASSIGNED`。
2. 阶段推进只允许在当前阶段门禁满足且操作者有权时修改`current_stage`；`display_status`只读派生，不得反写任何生命周期字段。
3. PM-10“回退”保持`lifecycle_status=ACTIVE`，将`current_stage`回到S0并按规则置为待指派；PM-10“异常关闭”才写入`EXCEPTION_CLOSED`并保存关闭依据。
4. CLO-02审批全部通过后才写入`NORMAL_CLOSED`并形成不可变闭环事实；任何其他接口、同步回调或通知不得产生该终态。
5. 仅允许对`EXCEPTION_CLOSED`项目执行受控重开并恢复为`ACTIVE`；重开必须记录重开原因，恢复关闭前最后一个可恢复阶段并创建新的责任处理事项，不得自动恢复已终止的外部任务。`NORMAL_CLOSED`不得通过PM-10直接重开。正常闭环后的巡检、割接保障和其他售后活动使用独立领域任务，不新增项目维护阶段。

## 3. 版本化

状态字典、迁移定义和门禁规则均带版本；任务实例保存绑定版本。已发布版本不可原地修改，只能新建版本并通过配置审批。

ProjectTask的WorkBinding和CompletionRule版本与任务状态机版本分别冻结，且每个任务必须且只能有一个当前绑定。`TASK_NATIVE`按ProjectTask自身状态机和任务事实执行受控迁移；其他绑定的业务对象状态变化只触发重新评估，不允许业务Context直接写ProjectTask状态。Project Delivery在校验任务版本、绑定版本、事实版本和规则版本后执行受控迁移并记录完成判定快照。

共享`DynamicFormInstance`在F-PLT-002首版只是冻结模板修订并以CAS保存值的载体，不新增提交、完成、审批、删除或换模状态。PRE、SCH、IMP、ACC、CUT等消费者各自拥有并冻结业务状态与门禁，不得把PLT实例保存解释为领域完成。
