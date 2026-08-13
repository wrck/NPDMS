# SDS Phase 1：状态机设计

> 文档状态：`BASELINE`
> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）
> Requirement ID：PRD V1.7 附录 A.1 的全部 104 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；业务 Owner 已签署，见 `docs/design/phase-1-domain-ownership.md`
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


## 1. 规则

业务状态机与审批工作流分离：状态机表达业务事实，工作流表达审批节点。状态值采用基础平台可配置字典，但核心状态、终态和强制门禁不可被任意删除；扩展状态必须声明父状态映射、合法迁移、角色、进入/退出条件和版本。

## 2. 核心状态机

| 对象 | 核心状态 | 关键迁移与守卫 | 事件 |
|---|---|---|---|
| Project | 待开始(S0)、工前准备(S1)、施工计划(S2)、实施方案(S3)、实施部署(S4)、验收交维(S5)、闭环(S6)、维护 | 只有当前阶段门禁满足且操作者有权时前进；回退需理由和权限；关闭后 V1/V2 只读 | ProjectStageChanged、ProjectClosed |
| ProjectTask | 待分配、待开始、进行中、待验收、完成、关闭 | 父任务/阶段约束、交付件和验收条件；不限制层级深度但禁止环 | TaskAssigned、TaskCompleted |
| Device | 无独立业务状态机（主数据事实）；设备状态、在网状态及停产停维状态使用来源事实和基础平台可配置字典 | 设备档案同步或受控平台扩展字段更新必须保留来源版本；项目归属变更不得隐式改写设备来源状态 | DeviceStatusSynchronized、DeviceOwnershipChanged |
| CollectionTask | 创建、授权校验、已下发、执行中、回调中、已消费、完成、失败 | 幂等键、短期授权、回调签名/来源校验；失败只允许创建新的受控重试任务 | CollectionTaskDispatched、CollectionResultAvailable、CollectionCompleted |
| CutoverTask | 新建、评估、采集、方案、审批、执行、闭环、归档 | A/B/C/D等级决定审批链；方案、采集和审批门禁齐全后执行 | CutoverApproved、CutoverCompleted |
| CutoverSupportTask | 待派单、已派单、处理中、已接管、已转单、已挂起、已关闭 | 状态机版本在创建时冻结；接管/转交必须结束原责任区间并创建新区间；挂起保留责任区间；关闭需结果、证据和待办齐全且V1/V2关闭后只读 | CutoverSupportAssigned、CutoverSupportTransferred、CutoverSupportSuspended、CutoverSupportClosed |
| InspectionTask | 新建、准备、执行、报告、待办、闭环 | 在线/离线互斥；规则版本冻结；问题待办必须关闭或按规则转服务 | InspectionCompleted、InspectionClosed |
| DeliveryEvidence | 草稿、已上传、待审核、已通过、已驳回、已归档 | IMP 可在实施阶段上传并替换草稿；ACC 审核/归档；已归档版本不可被 IMP 覆盖 | DeliveryEvidenceUploaded、ArtifactAccepted |
| ArrivalAcceptance | 草稿、部分签收、已签收、差异待处理、已确认 | 到货数量/序列号和证据校验；差异未确认不得作为齐套依据 | ArrivalAccepted、ArrivalDifferenceRaised |
| InstallationRecord | 草稿、待确认、已安装、整改中、已确认 | 安装位置、照片和设备关联完整；整改完成后才能确认 | InstallationConfirmed、InstallationRemediationCreated |
| ConfigurationCollectionResult | 待采集、采集中、回调待处理、解析失败、已解析、已确认 | 只消费任务级结果引用；重复回调幂等；解析失败不得伪造成功 | ConfigurationCollected、ConfigurationParsed |
| JointDebuggingResult | 待联调、联调中、问题待处理、已完成、已确认 | 联调结果和关联设备/版本完整；未完成问题不得确认 | JointDebuggingCompleted、JointDebuggingIssueRaised |
| ImplementationRisk | 已标记、评估中、处置中、已关闭 | 风险等级、责任人和处置证据完整；高风险不得绕过门禁 | ImplementationRiskRaised、ImplementationRiskClosed |
| ImplementationQualityCheck | 草稿、待复核、整改中、复核通过、复核不通过、阻断 | 不合格必须整改后再复核；豁免需有权角色、依据、范围、有效期和审计 | ImplementationQualityChecked、ImplementationQualityBlocked |
| ImplementationSafetyCheck | 草稿、待复核、整改中、复核通过、复核不通过、安全阻断 | 高风险安全项阻断关联作业；解除阻断必须复核并留痕 | ImplementationSafetyChecked、ImplementationSafetyBlocked |
| SatisfactionCollection | 待生成、待发送、收集中、待判定、未通过、已通过、归档待重试、已归档 | 冻结模板/题目/阈值；客户有效答案和签字不可覆盖；未通过须整改后创建新任务和问卷版本，不允许人工改分或异常放行 | SatisfactionTaskCreated、SatisfactionSubmitted、SatisfactionResultRecorded |
| ProjectClosure | 草稿、待审核、材料审核、已闭环、驳回整改 | 项目冻结模板要求的交付件和有效满意度等门禁满足；不创建回访节点；驳回后重新校验并新建申请；完成后发布闭环事件请求 Project 关闭 | ClosureSubmitted、ProjectClosureCompleted |
| DeviceCredential | 创建、启用、授权、撤销、轮换、停用 | 仅授权范围内任务可引用；撤销影响后续任务，不改历史快照 | CredentialGranted、CredentialRevoked |

## 3. 版本化

状态字典、迁移定义和门禁规则均带版本；任务实例保存绑定版本。已发布版本不可原地修改，只能新建版本并通过配置审批。
