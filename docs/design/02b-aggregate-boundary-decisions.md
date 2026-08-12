# SDS Phase 1：聚合边界决策

| 聚合 | Owner Context | 一致性边界 | 关键命令 | 禁止跨聚合事务 |
|---|---|---|---|---|
| ArrivalAcceptance | Implementation Execution | 单次到货批次、序列号和签收证据 | ConfirmArrival、RaiseArrivalDifference | 不同步修改设备主档和验收结论 |
| InstallationRecord | Implementation Execution | 一次安装记录及设备位置/照片 | RecordInstallation、ConfirmInstallation | 不修改到货签收状态 |
| ConfigurationCollectionResult | Implementation Execution | 一次配置Log采集结果及解析版本 | ConsumeCollectionCallback、ConfirmParsedResult | 不下发设备命令、不持有凭证明文 |
| JointDebuggingResult | Implementation Execution | 一次业务联调结果及问题引用 | RecordDebuggingResult、ConfirmDebugging | 不改变割接执行状态 |
| ImplementationRisk | Implementation Execution | 单机风险标记及处置记录 | RaiseRisk、CloseRisk | 不代替 CUT 风险矩阵 |
| ImplementationQualityCheck | Implementation Execution | 阶段质量检查及整改复核 | SubmitQualityCheck、ReviewQuality、CompleteRemediation | 不直接关闭项目 |
| ImplementationSafetyCheck | Implementation Execution | 现场安全检查、阻断和豁免审批 | SubmitSafetyCheck、BlockWork、ApproveExemption | 不绕过安全阻断 |
| DeliveryEvidence | Implementation Execution | 实施阶段文件版本和来源证据 | UploadEvidence、ReplaceDraft | 不执行 ACC 归档审批 |
| DeviceCredential | Device Access & Collection | 凭证、轮换、授权引用和撤销 | CreateCredential、GrantCredential、RevokeCredential | 不向业务 Context 暴露明文 |
| CollectionTask | Device Access & Collection | 采集任务、任务级授权、外部状态和回调幂等 | CreateCollectionTask、DispatchCollectionTask、ConsumeCallback | 不拥有 IMP/CUT/INS 业务结论，不直接修改外部引擎 |
| ProjectClosure | Acceptance & Closure | 闭环申请、门禁快照、材料审核和闭环事件 | SubmitClosure、ReviewClosure、CompleteClosure | 不直接修改 Project 主状态 |

跨聚合通过 ID、不可变版本、查询快照和领域事件关联；Phase 2 再确定数据库约束和并发策略。一个聚合只归属一个 Context；跨 Context 只通过应用服务、查询契约或事件协作。
