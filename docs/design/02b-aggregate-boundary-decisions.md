# SDS Phase 1：聚合边界决策

> 文档状态：`REVALIDATION_REQUIRED`（修订017差量已回写；正式复审以当前Gate为准）
> 适用基线：PRD V1.8修订017（`docs/baseline/prd-v1.8.md`）；未受影响旧设计及历史证据保留
> Requirement ID：PRD V1.8 附录 A.1 的全部 100 项 V1/V2 正式需求；逐项范围与本分册落位见 `docs/traceability/requirement-matrix.md`
> Owner：SDS Phase 1 架构设计；既有独立复审GO仅属原批准范围，当前差量须按Gate重验证
> 适用规则：上述 Requirement 范围适用于本分册全部章节；章节或表格明确缩小范围时，以其明示范围为准


| 聚合 | Owner Context | 一致性边界 | 关键命令 | 禁止跨聚合事务 |
|---|---|---|---|---|
| ProjectTask | Project Delivery | 单个任务身份、树关系、恰好一个当前WorkBinding/PermissionPolicy/CompletionRule/GateRef和完成判定快照；TASK_NATIVE由任务自身承载，其他类型引用Owner业务事实 | CreateTask、MoveTask、AssignTask、EvaluateTaskCompletion | 不复制或直接修改非TASK_NATIVE绑定业务对象，不以通用完成命令绕过目标业务事实 |
| DynamicFormTemplate | 基础平台能力 | 稳定模板身份、唯一草稿、不可变已发布修订、当前发布指针和新实例可用性 | CreateTemplate、CreateRevision、PublishRevision、EnableTemplate、DisableTemplate | 不修改消费方业务模板选择、状态、审批或完成事实 |
| DynamicFormInstance | 基础平台能力 | 一个手工实例冻结明确模板修订及其普通字段值；文件字段引用F-PLT-001事实 | CreateManualInstance、PatchInstanceValues | 不切换既有实例模板，不把通用保存解释为业务提交/完成，不直接修改消费方聚合 |
| ArrivalAcceptance | Implementation Execution | 单次到货批次、序列号和签收证据 | ConfirmArrival、RaiseArrivalDifference | 不同步修改设备主档和验收结论 |
| InstallationRecord | Implementation Execution | 一次安装记录及设备位置/照片 | RecordInstallation、ConfirmInstallation | 不修改到货签收状态 |
| ConfigurationCollectionResult | Implementation Execution | 一次配置Log采集业务结果、实施解析状态和结果引用 | ConsumeCollectionCallback、PublishConfigurationLogResult | 不下发设备命令、不持有凭证明文、不拥有ConfigurationLog原始文件和不可变解析版本 |
| ConfigurationLog | Asset Management | EQP-02统一管理一个原始整机Log及其不可变解析版本、设备/板卡关联和来源证据 | AcceptConfigurationLog、PublishConfigurationLogVersion | 不改写IMP实施结论，不覆盖原始文件或既有解析版本 |
| JointDebuggingResult | Implementation Execution | 一次业务联调结果及问题引用 | RecordDebuggingResult、ConfirmDebugging | 不改变割接执行状态 |
| ImplementationRisk | Implementation Execution | 单机风险标记及处置记录 | RaiseRisk、CloseRisk | 不代替 CUT 风险矩阵 |
| ImplementationQualityCheck | Implementation Execution | 阶段质量检查及整改复核 | SubmitQualityCheck、ReviewQuality、CompleteRemediation | 不直接关闭项目 |
| DeliveryEvidence | Implementation Execution | 实施阶段文件版本和来源证据 | UploadEvidence、ReplaceDraft | 不执行 ACC 归档审批 |
| DeviceCredential | Device Access & Collection | 凭证、轮换、授权引用和撤销 | CreateCredential、GrantCredential、RevokeCredential | 不向业务 Context 暴露明文 |
| CollectionTask | Device Access & Collection | 采集任务、任务级授权、外部状态和回调幂等 | CreateCollectionTask、DispatchCollectionTask、ConsumeCallback | 不拥有 IMP/CUT/INS 业务结论，不直接修改外部引擎 |
| ProjectClosure | Acceptance & Closure | 闭环申请、门禁快照、材料审核和闭环事件 | SubmitClosure、ReviewClosure、CompleteClosure | 不直接修改 Project 主状态 |

跨聚合通过 ID、不可变版本、查询快照和领域事件关联；Phase 2 再确定数据库约束和并发策略。一个聚合只归属一个 Context；跨 Context 只通过应用服务、查询契约或事件协作。

ProjectTask导航投影不是新聚合：一级Stage、二级ProjectTask来自项目实例；深层任务仍是同一ProjectTask树。CUT-03在P3内引用CollectionTask并消费结果，DAC不进入CutoverTask事务，CUT也不直接写DAC状态。

## 修订017当前聚合边界

| 对象 | Owner | 聚合边界及失败原子性 |
|---|---|---|
| DeliveryConfigurationRevision | Project Delivery | 受控类型的不可变发布定义；发布前图和引用校验，发布失败全回滚；不是任意脚本执行器 |
| StageTransitionDefinition / ProjectStageTransition | Project Delivery | 模板边与项目冻结边分离；唯一开始、可达、无环、唯一目标；未实例化阶段无状态行 |
| StageWorkBinding | Project Delivery | ProjectStage当前唯一绑定；与TaskWorkBinding分别管理，必须合并目标Owner权限，不以通用完成命令绕过目标业务事实 |
| BusinessViewRegistration | 基础平台 | 只发布受信实体/组件/命令及权限Provider版本，不拥有领域对象状态 |
| ContractScopeAppendRequest | Project Delivery | 同一项目的批准追加申请；COM数量/水位、PROJ任务、ACC绑定共同成功或失败；不产生群组、另一期项目或组合 |
| ProjectScopeVersion | Contract & Fulfillment | 唯一COM水位＋不可变范围版本；PROJ只持引用，不能双写当前量 |
| AcceptanceActivity / AcceptanceReportRevision | Acceptance & Closure | 独立验收活动身份与报告当前指针/不可变版本；项目、范围、来源、文件和配置前置精确冻结，不依赖S5或固定任务编码存在 |
| ProjectExitRecord | Project Delivery | 来源闭环命令的退出历史；受控写当前生命周期并同事务追加历史，回调不能直接写终态 |

旧MultiPhaseProjectGroup、MultiPhaseProjectMember、CrossPhaseContentReference只留在Git历史，不作为PM-06当前对象、API或前向建表依据。PROJ仍是唯一current_stage Writer；COM/ACC仅在配置指定的受控业务动作中参与范围校验和绑定，不因目标阶段为S5自动增加终验或绑定义务。修订018独立验收的具体触发/范围契约见Q-TPLACC-001，旧阶段进入接口不静默改义。
