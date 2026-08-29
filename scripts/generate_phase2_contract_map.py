#!/usr/bin/env python3
"""Generate the 100-requirement/111-slice Phase 2 implementation-contract map."""

from __future__ import annotations

import argparse
import importlib.util
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Contract:
    data: str
    tables: str
    apis: str
    events: str
    integration: str
    files: str
    workflow: str
    authorization: str


NA_EVENT = "N/A（同步命令或查询，无跨 Context 业务事件）"
NA_INTEGRATION = "N/A（平台内部契约）"
NA_FILE = "N/A（不产生或不持有文件正文）"


def contract(data: str, tables: str, apis: str, *, events: str = NA_EVENT,
             integration: str = NA_INTEGRATION, files: str = NA_FILE,
             workflow: str, authorization: str) -> Contract:
    return Contract(data, tables, apis, events, integration, files, workflow, authorization)


GROUPS: list[tuple[tuple[str, ...], Contract]] = [
    (("PM-01",), contract("Project、ProjectMemberAssignment", "proj_project、proj_project_member_assignment", "/projects、/projects/{id}/actions/assign-manager", events="ProjectCreated", integration="CRM、ERP", workflow="项目创建、来源匹配与指派守卫", authorization="ProjectTreeScope；来源字段只读")),
    (("PM-02", "PM-04"), contract("ProjectHierarchy、ProjectAncestorProjection", "proj_project、proj_project_tree_path、proj_project_tree_change", "/projects/{id}/tree、/projects/{id}/actions/move", events="ProjectTreeChanged", workflow="无环移动、完整投影版本切换", authorization="ProjectTreeScope；后代范围服务端计算")),
    (("PM-03",), contract("ProjectTemplate、ProjectStageSnapshot", "proj_project_template_revision、proj_project_template_task_definition、proj_project_stage_snapshot", "/project-templates", workflow="模板内StageDefinition/TaskDefinition发布、实例冻结、必填WorkBinding（默认TASK_NATIVE）/PermissionPolicy/CompletionRule/GateRef校验和阶段门禁", authorization="项目模板维护权限；项目阶段范围；非TASK_NATIVE绑定目标权限不得越权")),
    (("PM-05",), contract("BorrowedProjectConversion、ConversionItem、ConversionDeviceDisposition", "proj_project_conversion、proj_project_conversion_item、proj_project_conversion_device", "/project-conversions、/project-conversions/{id}/actions/retry-failed", events="ProjectConversionCompleted、ProjectConversionPartiallyFailed", integration="CRM、ERP", files="FileArtifact", workflow="处理中→部分失败/待处理→已完成；全部成功后源项目只读归档", authorization="同时具备源/目标项目管理权限；原敏感对象权限继续生效")),
    (("PM-06",), contract("MultiPhaseProjectGroup、MultiPhaseProjectMember、CrossPhaseContentReference", "proj_multi_phase_project_group、proj_multi_phase_project_member、proj_project_cross_phase_reference", "/project-phase-groups、/project-phase-groups/{id}/actions/add-phase、/project-phase-groups/{id}/actions/derive-content", events="ProjectPhaseGroupChanged", workflow="群组成员增删、唯一期次、无环和派生版本", authorization="同时有权的期次可维护；查询按各期权限裁剪")),
    (("PM-07",), contract("Project、ProjectTemplateMatchHistory", "proj_project、proj_project_template_match_history", "复用POST /projects与/projects/{id}当前四属性；/projects/{id}/template-match-history；/projects/{id}/actions/classify；内部ProjectAttributeResolutionService与ProjectAttributeSourceCorrectionCommand", events="N/A（当前只保存影响识别历史，不发布CHG事件）", integration="N/A（INT自动建项、来源定位和重试不在本Feature完成范围）", workflow="模板匹配前形成确定输入；正式Project不新增待分类/待选模状态；创建后只追加影响识别历史", authorization="项目管理范围；手工创建不得写CRM重大级别；工程管理部受控修正允许维度；来源证据只读")),
    (("PM-08",), contract("ProjectMemberAssignment", "proj_project_member_assignment、ast_area_department_mapping", "/projects/{id}/service-manager-candidates、/projects/{id}/actions/assign-manager、/projects/{rootId}/service-manager-responsibilities；SYSTEM OrganizationScopeApi.pageActiveUsers、NotifyMessageSendApi(deliveryKey)", events="ProjectServiceManagerAssigned（仅通知投递；不派生权限、成员或状态事实）", workflow="V1按区域—部门映射过滤候选并人工指派；V2仅在冻结规则唯一匹配时自动形成并生效主责指派，无匹配或多匹配保持待指派并回到V1人工流程；ASSIGNED仅在有效主责服务经理和有效项目经理同时存在时成立", authorization="项目管理范围；仅PRD角色")),
    (("PM-09",), contract("ProjectMemberAssignment", "proj_project_member_assignment", "/projects/{id}/members:batch-change", workflow="批量逐项变更并保留有效区间", authorization="项目管理范围；逐项目校验")),
    (("PM-10",), contract("Project、ProjectStageSnapshot", "proj_project、proj_project_stage_snapshot", "/projects/{id}/actions/rollback、/projects/{id}/actions/close", events="ProjectStageChanged、ProjectClosed(lifecycleStatus=EXCEPTION_CLOSED)", workflow="受控回退保持ACTIVE；异常关闭置EXCEPTION_CLOSED；正常闭环仅由CLO-02产生NORMAL_CLOSED", authorization="ProjectTreeScope；状态命令权限与门禁")),
    (("PM-11",), contract("ProjectTask、TaskWorkBinding、TaskCompletionRule、TaskCompletionEvaluation、TaskAncestorProjection、TaskDependency", "proj_project_task、proj_project_task_execution_contract、proj_project_task_completion_evaluation、proj_task_tree_path、proj_task_dependency", "/projects/{id}/workspace、/projects/{id}/gantt、/projects/{id}/tasks、/project-tasks/{id}/workbench、/project-tasks/{id}/dependencies、/project-tasks/{id}/actions/move、/project-tasks/{id}/actions/{submit|start|complete|cancel}", events="TaskAssigned、TaskCompleted", workflow="V1完成ProjectTask层级、WorkBinding/CompletionRule、基础查询与Stage→ProjectTask工作台投影；V2只增加甘特展示和受控依赖新增、更新、删除，依赖与层级正交且不建立第二套任务事实；TASK_NATIVE按任务自身事实执行，其他类型回源绑定事实并追加完成判定后完成", authorization="ProjectTreeScope；TASK_NATIVE任务范围；其他类型由服务端合并目标业务对象权限")),

    (("PRE-01",), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision、sol_construction_plan_change", "/construction-plans、/{id}/actions/{submit|approve|reject}", files="FileArtifact", workflow="工期基线、变更申请与审批", authorization="ProjectStageScope；计划审批节点")),
    (("PRE-02",), contract("Preparation、PreparationDynamicFormInstance", "sol_preparation、sol_dynamic_form_instance", "/preparations、/{id}/actions/{submit|confirm|return}", files="FileArtifact", workflow="工勘填写、提交、确认和退回；既有F-SOL-002表单事实保持不变", authorization="ProjectStageScope；字段与文件权限")),
    (("PRE-04",), contract("Preparation、DynamicFormInstance", "sol_preparation、plt_dynamic_form_instance", "/preparations、/{id}/form、/{id}/actions/{submit|create-draft}；内部DynamicFormBusinessInstanceApi", files="FileArtifact", workflow="WorkBinding自动冻结PLT修订；SOL草稿/完成及有效版切换；PLT实例值/文件组合与版本克隆", authorization="ProjectStageScope、当前项目经理、SOL Owner策略及PLT文件权限")),
    (("PRE-03",), contract("Preparation", "sol_preparation、ast_asset_sync_item", "/preparations", integration="ERP", files="FileArtifact", workflow="换货申请、外部处理映射与恢复对账", authorization="ProjectStageScope；物料范围")),
    (("PRE-05",), contract("Preparation、FileArtifact", "sol_preparation、plt_file_artifact、plt_file_version", "/preparations、/files:init-upload", files="FileArtifact", workflow="交底书生成、版本提交和确认", authorization="ProjectStageScope、FileBusinessScope")),
    (("PLN-01", "PLN-02", "PLN-03"), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision", "/schedules、/{id}/actions/{calculate|apply}", workflow="计划计算候选、预警和显式应用", authorization="ProjectStageScope；计划只读/维护分离")),
    (("PLN-04",), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision", "/construction-plans、/{id}/actions/{submit|approve|reject}", files="FileArtifact", workflow="施工计划提交、审批、驳回与换版", authorization="ProjectStageScope；审批人按PRD")),
    (("SCH-01", "SCH-02", "SCH-04", "SCH-05"), contract("Solution", "sol_solution、sol_solution_revision、sol_solution_review", "/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}", files="FileArtifact", workflow="方案导入/编审、重大复审和发布版本", authorization="ProjectStageScope；方案审批与文件权限")),
    (("SCH-03",), contract("Solution、FileArtifact", "sol_solution_revision、plt_file_artifact、plt_file_version", "/solutions、/files:init-upload", files="FileArtifact", workflow="配置脚本上传、解析、发布和版本冻结", authorization="ProjectStageScope、FileBusinessScope；已发布模板使用权限")),
    (("SOL-01",), contract("DynamicFormTemplate、DynamicFormTemplateRevision、DynamicFormInstance", "plt_dynamic_form_template、plt_dynamic_form_template_revision、plt_dynamic_form_instance", "/dynamic-form-templates、/dynamic-form-template-revisions、/dynamic-form-instances；内部DynamicFormBusinessInstanceApi", files="FileArtifact", workflow="模板唯一草稿、发布修订不可变、启停独立；手工与受信业务实例冻结修订并按CAS保存，消费方业务状态另行拥有", authorization="PLT模板/实例功能权限；业务实例叠加Owner Provider；发布者为高信任配置主体；目标API继续独立鉴权")),

    (("EXE-01",), contract("ArrivalAcceptance", "imp_arrival_acceptance、imp_arrival_line、imp_arrival_difference", "/arrival-acceptances", events="ArrivalAccepted", files="FileArtifact", workflow="草稿、差异处理、项目经理最终确认", authorization="ImplementationProjectBatchScope；项目经理确认")),
    (("EXE-02",), contract("InstallationRecord", "imp_installation_record、imp_installation_item、imp_installation_evidence", "/installation-records", events="InstallationConfirmed", files="FileArtifact", workflow="提交、项目经理确认/退回和整改", authorization="ImplementationProjectDeviceScope；设备归属与项目权限")),
    (("EXE-03",), contract("ConfigurationCollectionResult、DeviceComponentRelation、CollectionTask", "imp_configuration_collection_result、imp_configuration_collection_parse_attempt、imp_configuration_component_candidate、ast_device_component_relation、plt_collection_task", "/configuration-results、/devices/{id}/component-relations、/collection-tasks", events="ConfigurationParsed、DeviceComponentRelationChanged、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="采集回调、框板解析、待匹配/人工绑定和业务消费确认", authorization="ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope；设备关系维护权限")),
    (("EXE-04",), contract("JointDebuggingResult、CollectionTask", "imp_joint_debugging_result、imp_joint_debugging_item、plt_collection_task", "/debugging-results、/collection-tasks", events="JointDebuggingCompleted、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="采集回调、联调结论和问题引用", authorization="ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope")),
    (("EXE-05",), contract("ImplementationRisk", "imp_risk、imp_risk_treatment", "/implementation-risks", events="ImplementationRiskRaised/Closed", files="FileArtifact", workflow="风险提出、处置和关闭", authorization="ImplementationProjectDeviceScope；风险范围")),
    (("EXE-06",), contract("ImplementationReadinessSnapshot", "proj_project_stage_snapshot", "/implementation-readiness/{projectId}", events="ImplementationReadinessSnapshotPublished", workflow="就绪门禁汇总、快照发布与CUT消费", authorization="ImplementationProjectCutoverScope")),
    (("IMP-01",), contract("ImplementationQualityCheck", "imp_quality_check、imp_quality_item、imp_quality_remediation、imp_quality_review", "/quality-checks", events="ImplementationQualityGateChanged", files="FileArtifact", workflow="提交→复核→整改→再复核", authorization="ImplementationProjectBatchScope；复核权限")),

    (("ACC-01",), contract("Acceptance", "acc_acceptance、acc_acceptance_item、acc_confirmation", "/acceptances、/acceptances/{id}/actions/send-confirmation", events="NotificationRequested", integration="短信/邮件、钉钉", files="FileArtifact", workflow="V1培训记录与链接/扫码客户确认；V2自动推送并分别记录受理/送达，送达不等于确认，渠道失败回退V1链接/扫码", authorization="ProjectStageScope、FileBusinessScope；接收人和联系方式按业务范围裁剪")),
    (("ACC-03",), contract("Acceptance、AcceptanceScopeBinding", "acc_acceptance、acc_acceptance_item、acc_confirmation、acc_acceptance_scope_binding", "/acceptances；内部AcceptanceScopeGuardApi", files="FileArtifact", workflow="报告提交、确认和问题留痕；进入验收范围时冻结DeliveryScope分配版本", authorization="ProjectStageScope、FileBusinessScope")),
    (("ACC-02",), contract("SatisfactionCollection", "acc_satisfaction_collection_task、acc_satisfaction_questionnaire、acc_satisfaction_response、acc_satisfaction_result", "/satisfaction-tasks、/satisfaction-questionnaires/{token}/responses、/satisfaction-results", events="SatisfactionTaskCreated、SatisfactionResultRecorded、NotificationRequested", integration="短信/邮件、钉钉", files="FileArtifact", workflow="V1冻结模板→指派→客户提交→判定→整改后新版本重收→归档；V2仅增加自动触达并记录受理/送达，不重复问卷、评分、整改重收、签字或导出事实", authorization="ProjectStageScope；客户一次性实例范围；答案/签字不可改写；接收人按业务范围裁剪")),
    (("ACC-04",), contract("DeliveryArtifact", "acc_delivery_artifact、acc_artifact_review、acc_archive_record", "/delivery-artifacts", events="ArtifactAccepted/Archived", files="FileArtifact", workflow="齐套检查、审核和归档分离", authorization="ProjectStageScope、FileBusinessScope；ACC归档")),
    (("CLO-01", "CLO-02"), contract("ProjectClosure、ClosureGateSnapshot、SatisfactionCollection", "acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result", "/closure-gates/{projectId}、/project-closures", events="ProjectClosureCompleted", files="FileArtifact", workflow="门禁校验、冻结流程审批、整改和闭环；不创建回访节点", authorization="ProjectStageScope；全部后代项目门禁与审批范围")),
    (("ACC-06",), contract("ServiceHandover、ProjectClosure", "acc_service_handover、acc_handover_item、acc_handover_result", "/service-handovers", events="ProjectClosureCompleted", files="FileArtifact", workflow="交接门禁、静态交接快照和接收确认；不创建持续服务跟踪项", authorization="ProjectStageScope；交接双方项目/服务范围")),

    (("SUB-01", "SUB-02", "SUB-05"), contract("SubcontractRequest", "res_subcontract_request", "/subcontract-requests", events="SubcontractApproved", integration="OA", files="FileArtifact", workflow="平台内转包审批、价格审批与版本冻结", authorization="OrganizationSupplierScope；项目/供应商范围")),
    (("SUB-03", "SUB-04"), contract("PaymentGate、SatisfactionCollection", "res_payment_gate、acc_satisfaction_result", "/payment-gates", events="PaymentGateChanged", integration="财务系统", files="FileArtifact", workflow="付款前置满意度事实、批准版本和财务确认", authorization="OrganizationSupplierScope；付款门禁权限；满意度只读引用")),

    (("CUS-01",), contract("Customer、CustomerContact、CustomerRelationshipSnapshot", "cus_customer_master、cus_customer_external_mapping、cus_customer_field_history、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot", "/customers/{id}/panorama", integration="CRM及平台内项目/设备/服务事实", workflow="按授权聚合客户全景；单卡片失败保留其他卡片并显示最近成功截止时间，不以0替代未知", authorization="OrganizationCustomerScope；敏感联系人、故障、配置和维保字段专项权限")),
    (("CUS-02",), contract("CustomerServiceLevelRevision", "cus_customer_service_level_revision", "/customers/{id}/service-level-revisions", events="CustomerServiceLevelChanged", workflow="结束原有效区间并生成新等级版本；新业务动作冻结等级与策略版本，历史业务快照不回写", authorization="OrganizationCustomerScope；服务经理或管理层客户等级维护权限")),
    (("CUS-03",), contract("Customer、MarketRelation、CustomerLocationReference、CustomerScopeSlice", "cus_customer_master、cus_customer_external_mapping、cus_customer_field_history、cus_customer_location_reference、cus_market_relation、cus_customer_scope_slice", "/api/v1/pms/customers、/api/v1/pms/customers/{id}/locations、/api/v1/pms/customers/{id}/projects、/api/v1/pms/customers/{id}/devices", events="CustomerUpdated", integration="CRM（由INT-03负责连接、认证、同步、重试和对账）", workflow="CUS作为唯一当前写Owner；CRM客户同步和平台临时客户受控创建；ENABLED/DISABLED/DELETED本地生命周期；旧project客户入口只保留历史列表和详情读取，不双写、不代理新Owner写操作", authorization="五维复选权限切片；同维度OR、不同维度AND、多切片OR；管理员无显式切片时全量、显式切片时按切片降权；普通角色无有效切片时为空；联系方式RAW/MASKED/HIDDEN服务端裁剪")),
    (("CUS-04",), contract("Customer、CustomerContact、CustomerRelationshipSnapshot", "cus_customer_master、cus_customer_external_mapping、cus_customer_field_history、cus_customer_contact、cus_project_customer_contact_relation、cus_customer_relationship_snapshot", "/customer-contacts、/projects/{id}/customer-contacts", events="CustomerContactChanged", integration="CRM", workflow="联系人维护、项目角色时态关系和业务发生时联系信息快照", authorization="OrganizationCustomerScope、ProjectTreeScope；联系人字段专项权限")),
    (("EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"), contract("Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment", "ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history", "/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history", events="DeviceAssigned、DeviceComponentRelationChanged", files="FileArtifact", workflow="设备档案、配置Log引用、框板关系、扫码和唯一归属", authorization="ProjectDeviceScope；当前归属、框板关系维护与祖先范围")),
    (("EQP-04",), contract("AssetSyncSnapshot、Device", "ast_asset_sync_batch、ast_asset_sync_item、ast_device", "/devices", events="MasterDataSynchronized", integration="MES", workflow="MES来源版本幂等同步与冲突隔离", authorization="ProjectDeviceScope；MES字段只读")),
    (("AST-01",), contract("RMAReplacement、MaintenanceFact", "ast_rma_replacement、ast_maintenance_fact", "/rma-replacements、/devices/{deviceId}/service-status", events="DeviceStatusSynchronized", integration="备件系统", files="FileArtifact", workflow="RMA替换、设备归属校验和维保事实衔接", authorization="ProjectDeviceScope；设备与来源范围")),
    (("AST-02", "SRV-01"), contract("MaintenanceFact、ServiceStatus", "ast_maintenance_fact、srv_service_status", "/devices/{deviceId}/service-status", events="ServiceStatusChanged", integration="CRM", workflow="客观维保/停产停维状态计算与提示", authorization="ProjectDeviceScope；客观事实只读")),

    (("RPT-02",), contract("MetricSnapshot", "ana_metric_snapshot", "/analytics/metrics", events="MetricSnapshotPublished", workflow="项目状态指标口径计算、快照发布和水位展示", authorization="OrganizationReportScope；字段级脱敏")),
    (("PROJ-12",), contract("ProjectPortfolio", "proj_project_portfolio、proj_project_portfolio_member、proj_project_portfolio_revision", "/project-portfolios", events="ProjectPortfolioPublished", workflow="组合成员、发布快照和下钻", authorization="ProjectTreeScope；组合汇总不扩权")),
    (("ANA-01",), contract("PortfolioView、MetricSnapshot", "ana_portfolio_projection、ana_metric_snapshot", "/analytics/portfolios/{id}、/analytics/metrics", events="MetricSnapshotPublished", workflow="组合指标快照生成与只读展示", authorization="OrganizationReportScope；下钻回项目权限")),

    (("CUT-01",), contract("CutoverTask、CutoverAssessment", "cut_task、cut_assessment", "/cutover-tasks、/cutover-dashboard/kpis、/cutover-tasks/{id}/assessment", events="CutoverApproved", files="FileArtifact", workflow="V1任务创建、分级评估及P1～P6闭环；V2按授权范围聚合首页KPI，不改变CUT任务状态和流程", authorization="CutoverTaskScope；项目/设备范围；KPI按可见任务服务端聚合")),
    (("CUT-02",), contract("CutoverTask、CutoverAssessment", "cut_task、cut_assessment", "/cutover-tasks/{id}/assessment", events="CutoverApproved", files="FileArtifact", workflow="分级评估与风险/调研矩阵", authorization="CutoverTaskScope；项目/设备范围")),
    (("CUT-03",), contract("CutoverTask、CutoverChecklist、CollectionTask", "cut_task、cut_cutover_checklist、cut_cutover_checklist_item、cut_cutover_checklist_item_result、cut_cutover_configuration_revision、plt_collection_task", "/cutover-tasks/{id}/checklist、/cutover-tasks/{id}/checklist/actions/{rematch|export}、/cutover-tasks/{id}/checklist/items/{itemId}/actions/request-collection、/cutover-config/navigation-rules", events="CollectionTaskRequested、CollectionResultAvailable、CutoverChecklistItemResultLinked", integration="现有采集平台子应用", files="FileArtifact", workflow="V1在P3同一工作台完成动态匹配、填写、暂存、提交、采集回填和基础跳转；V2增加授权清单导出及受控流程跳转配置优化；不复制DAC技术状态", authorization="CutoverTaskScope、BusinessObjectDeviceCredentialScope；导出和跳转均按清单项、流程状态和设备范围服务端裁剪")),
    (("CUT-04",), contract("CutoverPlan", "cut_plan_revision、cut_step", "/cutover-tasks/{id}/plan-revisions", events="CutoverApproved", files="FileArtifact", workflow="方案编审和版本冻结", authorization="CutoverTaskScope；方案编审权限")),
    (("CUT-05",), contract("CutoverPlan", "cut_plan_revision、cut_step、plt_todo", "/cutover-tasks/{id}/plan-revisions、/cutover-tasks/{id}/approval-actions/{approve|reject}", events="CutoverApproved、NotificationRequested", integration="短信/邮件（INT-10）、钉钉（INT-05）", files="FileArtifact", workflow="V1按冻结等级完成分级审批；V2校验A/B级专项提前时间并发送经定义的外部提醒，提醒失败不改变审批状态且不新增平台通用SLA", authorization="CutoverTaskScope；分级审批权限；提醒接收人按冻结审批路由")),
    (("CUT-06",), contract("CutoverClosure、CollectionTask", "cut_cutover_closure、plt_collection_task", "/cutover-tasks/{id}/closure", events="CollectionResultConsumed、CutoverCompleted", integration="现有采集平台子应用、ITR", files="FileArtifact", workflow="P6闭环填写、INT-12证据引用、提交归档和结果回流；不建立逐步骤执行或稳定观察", authorization="CutoverTaskScope、BusinessObjectDeviceCredentialScope")),
    (("CUT-08",), contract("CutoverTask", "cut_task、ast_asset_sync_item", "/cutover-tasks", integration="备件系统", workflow="备件申请映射、回调、门禁和对账", authorization="CutoverTaskScope；外部备件范围")),
    (("CUT-07", "CUT-09", "CUT-10"), contract("CutoverConfigurationRevision", "cut_cutover_configuration_revision、cut_cutover_checklist_item_definition_revision、cut_cutover_checklist_binding_rule_revision", "/cutover-config/types、/cutover-config/network-modes、/cutover-config/checklist-items、/cutover-config/binding-rules", events="CutoverConfigurationPublished", integration="基础平台字典、可选外部动态数据源", workflow="V1首批配置基础：动态模板、表单、风险/调研矩阵和匹配规则按草稿→已发布→已停用管理；发布前校验稳定编码、版本、动态维度、引用启用状态和条件可判定性，且先于或不晚于首个消费能力交付；已生成实例继续按消费版本解释", authorization="系统管理员配置权限；已发布版本和历史业务实例不可覆盖")),

    (("INS-01", "INS-02", "INS-04", "INS-07"), contract("InspectionTask、CollectionTask", "srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task", "/inspection-tasks、/collection-tasks", events="InspectionDispatched、InspectionCompleted、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="方式选择、预检/执行、业务消费和归档门禁", authorization="AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope")),
    (("INS-03", "INS-09"), contract("InspectionRule", "srv_inspection_rule、srv_inspection_rule_revision", "/inspection-rules、/{id}/revisions", workflow="规则配置、发布版本和任务冻结", authorization="AssignedProjectDeviceScope；规则维护/使用分离")),
    (("INS-05",), contract("InspectionReport", "srv_inspection_report_revision", "/inspection-reports/{id}/versions", events="InspectionCompleted", integration="UMC", files="FileArtifact", workflow="报告生成、回调校验、发布版本", authorization="AssignedProjectDeviceScope、FileBusinessScope")),
    (("INS-06", "INS-08"), contract("ServiceIssue", "srv_service_issue、srv_service_issue_remediation", "/service-issues", events="InspectionIssueRaised/Closed", files="FileArtifact", workflow="问题标注、误报、整改复核和关闭", authorization="AssignedProjectDeviceScope；问题责任范围")),

    (("INT-01",), contract("Project、Contract、SalesOrder", "ast_asset_sync_batch、ast_asset_sync_item、com_contract、com_sales_order", "/projects、/contracts、/sales-orders", events="MasterDataSynchronized", integration="CRM、ERP", workflow="双源同步、待映射、字段Owner裁决和对账", authorization="集成服务账号限Owner字段；工程管理部映射权限")),
    (("INT-02",), contract("AssetSyncSnapshot", "ast_asset_sync_batch、ast_asset_sync_item、ast_device、cut_task、cut_cutover_closure", "/devices、/internal/integrations/itr/versions:sync、/internal/integrations/itr/faults:sync、/internal/integrations/itr/cutover-results:push", events="MasterDataSynchronized、CutoverCompleted", integration="ITR", workflow="V1同步设备版本并接收割接任务；V2接收故障来源并回传ITR来源CUT归档结果，出向失败不回滚本地归档；技术公告唯一归INT-04", authorization="集成账号限ITR字段；ProjectDeviceScope/CutoverTaskScope查询")),
    (("INT-03",), contract("Customer、MarketRelation", "cus_customer_master、cus_customer_external_mapping、cus_customer_field_history、cus_market_relation、ast_asset_sync_batch、ast_asset_sync_item", "/api/v1/pms/customers", events="CustomerUpdated、MasterDataSynchronized", integration="CRM", workflow="CRM客户、四维组合目录、来源版本和字段历史同步；平台本地生命周期不被外部同步直接改写", authorization="五维CustomerScope；CRM权威字段只读")),
    (("INT-04",), contract("TechnicalNoticeReference", "FEATURE_FORWARD_MIGRATION(INT-04)：逻辑对象`TechnicalNoticeReference`；物理表由INT-04 Feature前向迁移确定", "/technical-notices、/technical-notices/{id}/references", events="TechnicalNoticeSynchronized", integration="ITR", workflow="公告镜像、版本同步和业务引用", authorization="ProductDeviceProjectScope；V2只读")),
    (("INT-05",), contract("Todo", "plt_todo、plt_sync_batch、plt_external_key_mapping", "/todos、/integration/hr/directory、/internal/integrations/oa/material-requests、/internal/integrations/oa/purchase-requests", events="MasterDataSynchronized、TodoRequested、TodoCompleted", integration="钉钉、HR、OA", workflow="V1同步必要人员组织并承接钉钉待办/通知；V2创建OA领料/外采流程引用并同步结果，转包仅发送平台待办链接；OA/钉钉不拥有平台审批与业务状态，且不接入打卡/工时", authorization="TenantOrganizationProjectScope；目录身份不直接等于项目角色；外部流程按来源业务对象权限")),
    (("INT-06",), contract("RMAReplacement、AuthorizationGrant、InspectionReport", "ast_rma_replacement、plt_authorization_grant、srv_inspection_report_revision", "/rma-replacements、/authorization-grants、/inspection-reports/{id}/versions", events="MasterDataSynchronized", integration="备件系统、授权系统、UMC", files="FileArtifact", workflow="外部申请/结果映射、回调和对账", authorization="业务对象范围；完整授权码不可见")),
    (("INT-07",), contract("PaymentGate", "res_payment_gate、plt_integration_reconciliation", "/payment-gates", events="PaymentGateChanged", integration="财务系统", files="FileArtifact", workflow="批准费用出向、结果查询和人工对账", authorization="OrganizationSupplierScope；财务结果复核")),
    (("INT-09",), contract("AuthorizationGrant", "plt_authorization_grant、ast_asset_sync_item", "/authorization-grants", integration="LDAP/AD", workflow="认证断言校验、目录映射和平台会话", authorization="平台RBAC/DataScope；目录组不直授项目角色")),
    (("INT-10", "NFR-03"), contract("Todo", "plt_todo、ast_asset_sync_item", "/todos", events="NotificationRequested、NotificationDelivered/Failed", integration="短信/邮件、钉钉", workflow="节点通知、受理/送达分离和兜底", authorization="业务对象接收人范围；模板变量白名单")),
    (("INT-12",), contract("DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference", "plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption", "/device-credentials、/collection-tasks、/internal/collection-tasks/{id}/actions/confirm-consumption", events="CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted", integration="现有采集平台子应用", files="FileArtifact", workflow="V1供实施/割接复用授权→下发→回调→业务消费契约；V2在线巡检复用同一凭证、任务和采集执行引擎；独立中心按成功回调终态", authorization="BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权")),
    (("NFR-02",), contract("DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference", "plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption", "/device-credentials、/collection-tasks、/internal/collection-tasks/{id}/actions/confirm-consumption", events="CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted", integration="现有采集平台子应用", files="FileArtifact", workflow="V1设备凭证与采集安全基线；V2在线巡检当前命令超时即终止并失败，后续命令是否继续由任务冻结的已发布规则决定并留痕", authorization="BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权")),
    (("NFR-01",), contract("AuditRecord、MetricSnapshot", "plt_operation_audit、ana_metric_snapshot", "/analytics/metrics", workflow="性能水位、安全审计和兼容性验证", authorization="TenantOrganizationProjectScope；服务端强制范围")),
    (("AUT-01", "AUT-02"), contract("AuthorizationGrant", "plt_authorization_grant", "/authorization-grants", integration="OA、授权系统", files="FileArtifact", workflow="申请、OA审批引用、外部授权确认和查询", authorization="申请对象/设备/产品范围；授权码脱敏")),
    (("CHG-01",), contract("ChangeRequest", "plt_change_request", "/change-requests", files="FileArtifact", workflow="变更申请、审批、目标版本校验和执行", authorization="TenantOrganizationProjectScope；变更对象权限")),
    (("PLT-01",), contract("Todo", "plt_todo", "/todos、/{id}/actions/complete", events="TodoRequested、TodoCompleted", workflow="统一待办接入、完成回调Owner再校验", authorization="TenantOrganizationProjectScope；待办责任人范围")),
    (("PLT-02",), contract("FileArtifact", "plt_file_artifact、plt_file_version、plt_file_reference", "/files:init-upload、/files/{id}:complete-upload、/file-references", events="FileVersionCommitted、FileReferenceAttached/Detached、FileArchived", files="FileArtifact", workflow="初始化上传、内容校验、版本提交、引用和归档", authorization="FileBusinessScope；下载实时回源业务权限")),
    (("COM-01",), contract("Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail、AcceptanceScopeBinding", "com_contract、com_sales_order、com_sales_order_line、com_delivery_scope、com_delivery_scope_detail、acc_acceptance_scope_binding", "/contracts、/sales-orders、/order-lines、/delivery-scopes；内部ProjectOfficeFactApi、AcceptanceScopeGuardApi、DeliveryScopeAcceptanceLockApi", events="DeliveryScopeAssigned/Released", integration="ERP（合同订单权威）；CRM仅提供项目/客户上下文", workflow="ERP来源版本与单位精度守卫、项目办事处发生时快照、范围追加版本、明细合计、超分配及验收减量守卫", authorization="ContractProjectScope（Q-FCOM-001关闭前BLOCKED_BY_SPEC）；ERP核心字段只读")),
    (("RES-01",), contract("Supplier", "res_supplier、res_qualification", "/suppliers", integration="OA", files="FileArtifact", workflow="服务商档案、资质版本和可用状态", authorization="OrganizationSupplierScope；资质文件字段权限")),
]


def load_requirement_model(prd: Path) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    generator = Path(__file__).with_name("generate_requirement_traceability.py")
    spec = importlib.util.spec_from_file_location("requirement_generator", generator)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    text = module.read(prd)
    requirements = module.extract_requirements(text)
    return requirements, module.extract_version_slices(text, requirements)


def build_catalog(requirements: list[dict[str, str]]) -> dict[str, Contract]:
    catalog: dict[str, Contract] = {}
    for identifiers, item in GROUPS:
        for identifier in identifiers:
            if identifier in catalog:
                raise SystemExit(f"duplicate Phase 2 contract: {identifier}")
            catalog[identifier] = item
    expected = {item["id"] for item in requirements}
    if set(catalog) != expected:
        missing = sorted(expected - set(catalog))
        extra = sorted(set(catalog) - expected)
        raise SystemExit(f"Phase 2 contract coverage mismatch; missing={missing}, extra={extra}")
    return catalog


def phase3_verification(identifier: str, spec: Contract) -> tuple[str, str]:
    """Derive a concrete Phase 3 verification profile from the explicit contract.

    The common checks are the runtime invariants shared by every write/query
    contract. Optional checks are included only when the Phase 2 contract
    explicitly declares the corresponding event, integration or file surface.
    Requirement-specific overrides carry the NFR and high-risk acceptance
    evidence that cannot be inferred from a bounded-context name alone.
    """
    tests = [
        "业务规则/聚合单元测试",
        "API契约与输入边界测试",
        "服务端授权拒绝测试",
        "状态/异常恢复测试",
        "幂等与并发冲突测试",
        "数据库约束与迁移测试",
    ]
    evidence = ["自动化测试报告（用例ID、业务对象ID、断言与结果）", "数据库迁移/约束验证记录"]

    if not spec.events.startswith("N/A"):
        tests.append("事件Outbox/Inbox、重复/乱序/重放测试")
        evidence.append("事件消息ID、Outbox/Inbox及消费水位证据")
    if not spec.integration.startswith("N/A"):
        tests.append("外部集成映射、超时/重试/对账/降级测试")
        evidence.append("脱敏请求响应、幂等键、重试/对账与降级记录")
    if spec.files != NA_FILE:
        tests.append("文件上传/下载/版本/恶意内容与权限回源测试")
        evidence.append("文件哈希、版本、扫描、引用与权限拒绝记录")

    exact: dict[str, tuple[list[str], list[str]]] = {
        "PM-07": (["append-only匹配历史与真实浏览器响应式闭环"], ["真实MySQL原子性与append-only历史、真实浏览器响应式闭环、值域清查和独立评审"]),
        "PM-05": (["转换部分失败、逐项重试、源项目只读归档测试"], ["转换批次、逐项结果及源/目标一致性清单"]),
        "PM-06": (["多期群组无环、唯一期次、跨期派生版本测试"], ["群组树快照、派生来源与无环校验记录"]),
        "PM-11": (["5万节点、2000直接子节点、深度30任务树查询/移动测试"], ["任务树数据集版本与性能报告"]),
        "INT-12": (["凭证五元组、创建人默认授权、临时明文不落库、保存为凭证原子切换测试"], ["密文/密钥版本抽查、秘密扫描零命中和DAC任务回调链证据"]),
        "NFR-02": (["AES-256或同等强度、任务级短期取密、撤销与泄露处置测试"], ["密码学配置、密钥轮换演练、秘密扫描及授权拒绝报告"]),
        "PLT-02": (["50MB文件、分片/直传、哈希校验、恶意内容和引用权限测试"], ["50MB样本、哈希、扫描、版本与下载权限证据"]),
        "NFR-01": (["50并发用户30分钟且不少于10000请求的性能测试", "Chrome/Edge/Firefox四视口真实浏览器安全与兼容验收"], ["P95/错误率/资源曲线性能报告", "Playwright trace、截图/录像及浏览器控制台记录"]),
        "NFR-03": (["通知到达率不低于99%及项目进度60秒内可读测试"], ["有效发送/送达明细、事件时间与项目状态版本延迟报告"]),
    }
    extra_tests, extra_evidence = exact.get(identifier, ([], []))
    tests.extend(extra_tests)
    evidence.extend(extra_evidence)
    return "；".join(tests), "；".join(evidence)


def phase3_side_effect(spec: Contract) -> str:
    return (
        f"成功仅按契约写入/引用数据对象“{spec.data}”及数据表“{spec.tables}”；"
        f"事件边界为“{spec.events}”，文件边界为“{spec.files}”，外部集成为“{spec.integration}”。"
        "授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；"
        "仅允许保存拒绝/失败审计和已有事实不变的结果。"
    )


def markdown_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\r", " ").replace("\n", " ").strip()

DECISION_NOTES = {
    "CUS-03": "F-CUS-001实现证据：NPDMS `a9f8b7c568546839d3d641531f8036bb75889a82`；前向Flyway按合并后序号登记为`V106__fcus001_customer_master.sql`、`V107__fcus001_customer_classification_scope.sql`、`V108__fcus001_seed_data.sql`，不得回写为暂存补丁中的V87～V89。",
    "PM-07": "F-PROJ-004聚焦裁决（`CHG-PRD-2026-08-25-003`）：本Feature仅实现PM-07的PROJ子切片，包括四属性复用、INITIAL_CREATE决策历史、SOURCE_CORRECTION/MANUAL_ADJUSTMENT影响识别；无匹配拒绝，多匹配仅在显式选择本次合法候选时创建。不新增待分类/待选模状态、独立属性历史、分类案例、影响处理表、重新实例化或CHG事件。INT来源定位/自动建项/重试/对账及CHG分派/处理/关闭保持未完成，不计入本Feature完成度。",
    "PM-08": "F-PROJ-005聚焦裁决：PM-08局部验收中的服务经理指派后ASSIGNED，解释为该操作使有效主责服务经理和有效项目经理两项条件全部满足时ASSIGNED；仅服务经理有效时仍为UNASSIGNED。V1只支持服务端即时生效，不实现PM-11项目经理指派或预约生效。",
}

FEATURE_LINES = {
    "PM-07": "Feature：F-PROJ-004（项目业务属性判定、模板匹配历史与影响识别）",
}

BUSINESS_GUARD_OVERRIDES = {
    "PM-07": "按“模板匹配前确定输入、首次创建原子记录、创建后只读影响评估”执行；非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变",
}

AUTHORIZATION_ASSERTION_OVERRIDES = {
    "PM-07": "项目管理范围、属性Owner和手工重大级别禁写边界",
}

SIDE_EFFECT_OVERRIDES = {
    "PM-07": "成功仅按契约写入/引用数据对象“Project、ProjectTemplateMatchHistory”及数据表“proj_project、proj_project_template_match_history”；事件边界为“N/A（不发布CHG事件）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（INT自动建项与重试不在本Feature）”。授权拒绝、业务守卫失败或幂等重放不得新增有效业务版本、事件、文件引用或外部完成事实；仅允许保存拒绝审计和已有事实不变的结果。",
    "PM-08": "成功仅按契约写入/引用数据对象“ProjectMemberAssignment”及数据表“proj_project_member_assignment、ast_area_department_mapping”；事件边界为“ProjectServiceManagerAssigned（仅通知投递；不派生权限、成员或状态事实）”，文件边界为“N/A（不产生或不持有文件正文）”，外部集成为“N/A（平台内部契约）”。区域—部门映射表只读；同时写入Project版本/状态、幂等/审计及一个Outbox。处理器以eventId调用SYSTEM幂等站内信接口；通知失败不回滚指派，只更新Outbox重试事实；授权拒绝或业务守卫失败不得新增有效业务版本、事件、站内信或外部完成事实，一致重放不得新增成员区间、事件或站内信。",
}


def render(prd: Path) -> str:
    requirements, slices = load_requirement_model(prd)
    catalog = build_catalog(requirements)
    slices_by_requirement: dict[str, list[dict[str, str]]] = {}
    for item in slices:
        slices_by_requirement.setdefault(item["requirement_id"], []).append(item)
    lines = [
        "# SDS Phase 2 显式需求契约映射",
        "",
        "> 文档状态：`BASELINE`",
        "> 适用基线：PRD V1.8 修订008（`docs/baseline/prd-v1.8.md`）",
        "> Requirement ID：附录 A.1 的100项正式Requirement及附录A.1.1派生的111个目标版本切片（V1 53个、V2 58个）",
        "> Owner：SDS Phase 2 追溯治理；具体业务 Owner 以 `requirement-matrix.md` 为准",
        "> Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`（仅表示SDS设计可进入Phase 3，不批准DDL、Feature或Release）",
        "",
        "本文件按100项Requirement声明共享的数据对象、表、API、事件/集成/文件、工作流和授权落点，并在每项下逐一登记正式版本切片的独立业务结果与边界。相同基础契约可被多个相关Requirement复用，但111个切片键必须精确同源；`N/A` 必须说明为何该类契约不适用。",
        "",
    ]
    for item in requirements:
        identifier = item["id"]
        spec = catalog[identifier]
        phase3_tests, phase3_evidence = phase3_verification(identifier, spec)
        acceptance = item["acceptance"]
        lines.extend([
            f"### {identifier}",
            "",
            f"- 需求名称：{item['name']}",
            *([f"- {FEATURE_LINES[identifier]}"] if identifier in FEATURE_LINES else []),
            "",
            "#### Requirement版本切片",
            "",
            "| 切片键 | 目标版本 | 独立业务结果 | 版本边界 |",
            "|---|---|---|---|",
            *[
                f"| {markdown_cell(slice_item['key'])} | {markdown_cell(slice_item['version'])} | "
                f"{markdown_cell(slice_item['business_result'])} | {markdown_cell(slice_item['boundary'])} |"
                for slice_item in slices_by_requirement[identifier]
            ],
            "",
            f"- 数据对象：{spec.data}",
            f"- 数据表：{spec.tables}",
            f"- API：{spec.apis}",
            f"- 事件：{spec.events}",
            f"- 外部集成：{spec.integration}",
            f"- 文件契约：{spec.files}",
            f"- 工作流/状态：{spec.workflow}",
            f"- 授权与数据范围：{spec.authorization}",
            f"- Phase 3测试类别：{phase3_tests}",
            f"- Phase 3 PRD验收基线：{acceptance}",
            *([f"- {DECISION_NOTES[identifier]}"] if identifier in DECISION_NOTES else []),
            f"- Phase 3授权拒绝断言：越权按“{AUTHORIZATION_ASSERTION_OVERRIDES.get(identifier, spec.authorization)}”拒绝，不返回未授权业务事实且不产生业务副作用",
            f"- Phase 3业务守卫断言：{BUSINESS_GUARD_OVERRIDES.get(identifier, f'按“{spec.workflow}”执行；PRD验收基线中的非法状态、版本冲突、重复请求或无效输入由对应业务守卫拒绝，原有效业务事实保持不变')}",
            f"- Phase 3副作用断言：{SIDE_EFFECT_OVERRIDES.get(identifier, phase3_side_effect(spec))}",
            f"- Phase 3证据类型：{phase3_evidence}",
            "",
        ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prd", type=Path, default=Path("docs/baseline/prd-v1.8.md"))
    parser.add_argument("--output", type=Path, default=Path("docs/traceability/phase2-contract-map.md"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    content = render(args.prd)
    if args.check:
        if not args.output.is_file() or args.output.read_text(encoding="utf-8") != content:
            print(f"[FAIL] Phase 2 contract map drift: {args.output}")
            return 1
        print(f"[PASS] Phase 2 contract map: {args.output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(content, encoding="utf-8", newline="\n")
    print(f"WROTE {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
