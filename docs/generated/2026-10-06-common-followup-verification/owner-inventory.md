# 当前79目录实体公共能力接入清单

来源：c41生产注册器与真实Spring装配，随后33文件增量未改任何实体注册器。45已有显式只读范围，34尚缺；所有79的scopeBinding均为空。注册器把79全部标为AGGREGATE_ROOT，这一标签不能把版本、引用、模板、授权和要求辅助表自动变成项目业务目标。

本清单区分目录读写、实体表单Provider、原生材料接入和项目操作目标。项目操作目前另有8个原生目标身份（JSON完整列出），不是79个全部开放。默认CRUD基础能力已存在，但这79个生产模型仅2个声明专业操作、77个操作列表为空；公共声明式交付桥接对79均关闭。

最终普通JAR真实分页：42通过、34安全拒绝SCOPE_POLICY_NOT_DECLARED、3隔离库缺后续列。c41时4父级读取另有Bean歧义；@Primary修复后4项均通过。V374阻断的库不能冒称当前完整schema通过。

| 实体 | 实际用途 | 公共读 | 公共写 | 表单 | 交付 | 待接原因/证据 |
|---|---|---|---|---|---|---|
| ACC/acceptance | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | LegacyAcceptanceAttachmentRegistration/Access；旧ACC根保存/提交归集，不移挂acceptanceActivity | legacyDirectProjectReads |
| ACC/acceptanceActivity | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | AcceptanceResultDeliveryAccess + 报告版本原生API；不等同旧ACC根 | legacyDirectProjectReads |
| ACC/acceptanceScopeBinding | 验收范围关联 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| ACC/archiveDocument | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | ArchiveDocumentDeliveryEvidenceProvider；成果事实 | legacyDirectProjectReads |
| ACC/completionCertificate | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/REJECTED_OR_FAILED | 未开放 | 未默认绑定 | CompletionCertificateDeliveryEvidenceProvider；成果事实 | legacyDirectProjectReads |
| ACC/deliverableChecklist | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | ChecklistAttachmentRegistration；原生保存/完成归集 | legacyDirectProjectReads |
| ACC/normalClosureApplication | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| ACC/satisfactionCollectionTask | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 问卷响应/结果原生文件策略和成果；不是目录默认桥接 | legacyDirectProjectReads |
| ACC/satisfactionQuestionnaire | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacySatisfactionQuestionnaires |
| ACC/satisfactionQuestionnaireTemplate | 问卷模板 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyTenantCatalogReads |
| AST/assetProductOfficial | 资产/产品目录或设备Owner，不能推定ProjectScope | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 资产/产品目录或设备Owner，不能推定ProjectScope |
| AST/assetProductType | 资产/产品目录或设备Owner，不能推定ProjectScope | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 资产/产品目录或设备Owner，不能推定ProjectScope |
| AST/device | 资产/产品目录或设备Owner，不能推定ProjectScope | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 资产/产品目录或设备Owner，不能推定ProjectScope |
| AST/site | 资产/产品目录或设备Owner，不能推定ProjectScope | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 资产/产品目录或设备Owner，不能推定ProjectScope |
| COM/authorityCandidate | 合同/订单/CRM授权候选Owner，需商业域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 合同/订单/CRM授权候选Owner，需商业域原生范围 |
| COM/contract | 合同/订单/CRM授权候选Owner，需商业域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 合同/订单/CRM授权候选Owner，需商业域原生范围 |
| COM/crmExecutionOrder | 合同/订单/CRM授权候选Owner，需商业域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 合同/订单/CRM授权候选Owner，需商业域原生范围 |
| COM/deliveryScope | 合同/订单/CRM授权候选Owner，需商业域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 合同/订单/CRM授权候选Owner，需商业域原生范围 |
| COM/salesOrder | 合同/订单/CRM授权候选Owner，需商业域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 合同/订单/CRM授权候选Owner，需商业域原生范围 |
| CUS/customerContact | 客户/联系人/服务级别Owner，需客户域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 客户/联系人/服务级别Owner，需客户域原生范围 |
| CUS/customerMaster | 客户/联系人/服务级别Owner，需客户域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 客户/联系人/服务级别Owner，需客户域原生范围 |
| CUS/customerServiceLevel | 客户/联系人/服务级别Owner，需客户域原生范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 客户/联系人/服务级别Owner，需客户域原生范围 |
| CUT/cutoverApprovalInstance | 割接审批记录 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 应通过cutoverTask或审批原生Owner授权；尚无显式父级范围适配 |
| CUT/cutoverAssessment | 割接任务子记录 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyCutoverTaskChildren |
| CUT/cutoverChecklist | 割接核对项 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyCutoverTaskChildren |
| CUT/cutoverClosure | 割接关闭记录 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 应通过cutoverTask关闭记录Owner；尚无显式父级范围适配 |
| CUT/cutoverConfigurationRevision | 割接配置修订 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 配置修订/有效版本Owner未适配 |
| CUT/cutoverPlanRevision | 割接计划修订 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyCutoverTaskChildren |
| CUT/cutoverSpareApplicationReference | 割接备件申请引用 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 备件申请引用需原始申请/任务Owner，不能按租户开放 |
| CUT/cutoverTask | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| IMP/arrival | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | ArrivalDeliveryRegistration；原生保存/签收归集 | legacyDirectProjectReads |
| IMP/arrivalAcceptance | 到货验收子记录 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 到货根的子记录范围未绑定 |
| IMP/configuration | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；保存/原生完成→同一材料；读取范围仍缺 | 原生上传与完成已接入公共材料，通用读范围仍未绑定 |
| IMP/deliverable | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| IMP/deliveryEvidence | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| IMP/docTemplate | 业务文档模板 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 文档模板目录范围未适配，不能凭租户推定 |
| IMP/externalProcurement | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/REJECTED_OR_FAILED | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；原生保存/完成归集 | legacyDirectProjectReads |
| IMP/installation | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 具有projectId，但通用读取没有显式Owner绑定；原生服务/文件策略不能代替它 |
| IMP/issue | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| IMP/jointTest | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；保存/原生完成→同一材料；读取范围仍缺 | 原生上传与完成已接入公共材料，通用读范围仍未绑定 |
| IMP/materialExchange | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；原生保存/完成归集 | legacyDirectProjectReads |
| IMP/materialRequisition | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/REJECTED_OR_FAILED | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；原生保存/完成归集 | legacyDirectProjectReads |
| IMP/risk | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| IMP/training | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | TrainingDeliveryEvidenceProvider；旧链接闭环仍待验 | legacyDirectProjectReads |
| KNO/announcement | 公告Owner目录；KNO增量仍hold | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyTenantCatalogReads |
| PLT/authorization | 业务授权根 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 敏感授权根需原生Owner范围 |
| PLT/authorizationGrant | 授权凭据/范围记录 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 敏感授权凭据需原生请求/响应/任务范围 |
| PLT/businessViewRevision | 业务视图修订 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 业务视图修订需发布Owner/不可变版本范围 |
| PLT/collectionTask | 归集任务 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 归集任务的文件策略已存在；通用任务读取范围仍未绑定 |
| PLT/collectionTemplate | 归集模板 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 归集模板原生Owner范围未适配 |
| PLT/deliveryRequirement | 交付要求，不能等同业务body | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 要求归属业务/项目/冻结来源的范围尚未绑定 |
| PLT/formInstance | 动态表单实例 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 原生动态表单API有独立Owner/执行范围；目录投影未绑定 |
| PLT/formTemplate | 表单模板 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyTenantCatalogReads |
| PRJ/exitRecord | 退出记录 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/governanceAction | 治理动作记录 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/planVersion | 执行计划版本 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/portfolio | 组合根，不能等同项目树 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 项目组合独立Owner，不能套项目树范围 |
| PRJ/project | 项目根，原生项目API单独接入 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 项目交付要求/履约/提交原生聚合；本轮无项目创建/历史改造 | 项目根须保留ProjectScope与生命周期；通用读取未绑定，创建/详情/迁移属独立活动 |
| PRJ/projectClosure | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/projectRisk | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/projectSite | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/projectTemplate | 项目模板 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 模板发布/版本目录Owner尚未显式适配 |
| PRJ/splitRequest | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyParentProjectReads |
| PRJ/stageSuggestionRule | 阶段建议规则 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyTenantCatalogReads |
| PRJ/taskAssignment | 任务分派记录 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 任务分派需原始项目/任务接收者Owner |
| PRJ/teamBatchChange | 团队批次变更 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 批量团队动作需原始项目/版本范围 |
| PRJ/treeChange | 树变更记录 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| PRJ/treeVersion | 项目树版本 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyTreeRootReads |
| RES/outsourceRequest | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | NativeAttachmentRegistration/Access；原生保存/完成归集 | legacyDirectProjectReads |
| SOL/briefing | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | SupplementalAttachmentRegistration + BriefingAttachmentDeliveryAccess；手工附件和生成文件用途分离 | legacyDirectProjectReads |
| SOL/constructionPlan | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| SOL/requirement | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| SOL/requirementAnalysis | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 专业 create,save,complete,copy | 原生Provider | RequirementAnalysisDeliveryEvidenceProvider/Access；冻结成果锚，不等同全操作文件接入 | requirementAnalysisBusinessScopePolicy |
| SOL/resourceReady | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| SOL/scheduleBackward | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| SOL/siteSurvey | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 专业 create,save,delete,confirm,reject,archive | 原生Provider | 公共声明桥接关闭 | siteSurveyBusinessScopePolicy |
| SOL/solution | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | SolutionDeliveryEvidenceProvider；成果事实，文件写链未在本轮全验 | legacyDirectProjectReads |
| SOL/stagePlanBatch | 业务根/工作对象；是否是项目操作目标见独立目标身份映射 | 绑定/PASS | 未开放 | 未默认绑定 | 公共声明桥接关闭 | legacyDirectProjectReads |
| SRV/inspectionRule | 服务规则目录，需原生规则Owner范围 | 缺失/安全拒绝 | 未开放 | 未默认绑定 | 公共声明桥接关闭 | 服务规则目录，需原生规则Owner范围 |

下一步最小公共闭环：先修复父级范围的读取Bean歧义；随后为已有真实projectId和原生权限证据的IMP/configuration、IMP/jointTest复用OwnerProjectReadScopePolicy，并用实际Owner数据验证越项目、越租户、仅query角色、表单及材料操作仍被拒绝。不能顺带开启77个旧模型的默认写入，不能把模板/凭据/修订/引用Owner套进租户范围。

完整细节、真实绑定类、权限引用、字段、原生服务引用、8个操作目标及逐实体原因见 [owner-inventory.json](owner-inventory.json)。
