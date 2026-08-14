#!/usr/bin/env python3
"""Generate the explicit 103-requirement Phase 2 implementation-contract map."""

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
    (("PM-03",), contract("ProjectTemplate、ProjectStageSnapshot", "proj_project_template_revision、proj_project_stage_snapshot", "/project-templates", workflow="模板发布、实例冻结和阶段门禁", authorization="项目模板维护权限；项目阶段范围")),
    (("PM-05",), contract("BorrowedProjectConversion、ConversionItem、ConversionDeviceDisposition", "proj_project_conversion、proj_project_conversion_item、proj_project_conversion_device", "/project-conversions、/project-conversions/{id}/actions/retry-failed", events="ProjectConversionCompleted、ProjectConversionPartiallyFailed", integration="CRM、ERP", files="FileArtifact", workflow="处理中→部分失败/待处理→已完成；全部成功后源项目只读归档", authorization="同时具备源/目标项目管理权限；原敏感对象权限继续生效")),
    (("PM-06",), contract("MultiPhaseProjectGroup、MultiPhaseProjectMember、CrossPhaseContentReference", "proj_multi_phase_project_group、proj_multi_phase_project_member、proj_project_cross_phase_reference", "/project-phase-groups、/project-phase-groups/{id}/actions/add-phase、/project-phase-groups/{id}/actions/derive-content", events="ProjectPhaseGroupChanged", workflow="群组成员增删、唯一期次、无环和派生版本", authorization="同时有权的期次可维护；查询按各期权限裁剪")),
    (("PM-07",), contract("Project", "proj_project", "/projects/{id}/actions/classify", workflow="自动识别结果确认与留痕", authorization="项目管理范围；来源证据只读")),
    (("PM-08",), contract("ProjectMemberAssignment", "proj_project_member_assignment", "/projects/{id}/actions/assign-manager", workflow="V1手动指派、V2规则候选确认", authorization="项目管理范围；仅PRD角色")),
    (("PM-09",), contract("ProjectMemberAssignment", "proj_project_member_assignment", "/projects/{id}/members:batch-change", workflow="批量逐项变更并保留有效区间", authorization="项目管理范围；逐项目校验")),
    (("PM-10",), contract("Project、ProjectStageSnapshot", "proj_project、proj_project_stage_snapshot", "/projects/{id}/actions/rollback、/projects/{id}/actions/close", events="ProjectStageChanged、ProjectClosed", workflow="受控回退与闭环完成后关闭", authorization="ProjectTreeScope；状态命令权限与门禁")),
    (("PM-11",), contract("ProjectTask、TaskAncestorProjection、TaskDependency", "proj_project_task、proj_task_tree_path、proj_task_dependency", "/projects/{id}/tasks、/project-tasks/{id}/actions/move", events="TaskAssigned、TaskCompleted", workflow="任务任意层级移动、状态迁移和依赖守卫", authorization="ProjectTreeScope；任务数据范围")),

    (("PRE-01",), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision、sol_construction_plan_change", "/construction-plans、/{id}/actions/{submit|approve|reject}", files="FileArtifact", workflow="工期基线、变更申请与审批", authorization="ProjectStageScope；计划审批节点")),
    (("PRE-02", "PRE-04"), contract("Preparation、DynamicFormInstance", "sol_preparation、sol_dynamic_form_instance", "/preparations、/{id}/actions/{submit|confirm|return}", files="FileArtifact", workflow="工勘/需求填写、提交、确认和退回", authorization="ProjectStageScope；字段与文件权限")),
    (("PRE-03",), contract("Preparation", "sol_preparation、ast_asset_sync_item", "/preparations", integration="ERP", files="FileArtifact", workflow="换货申请、外部处理映射与恢复对账", authorization="ProjectStageScope；物料范围")),
    (("PRE-05",), contract("Preparation、FileArtifact", "sol_preparation、plt_file_artifact、plt_file_version", "/preparations、/files:init-upload", files="FileArtifact", workflow="交底书生成、版本提交和确认", authorization="ProjectStageScope、FileBusinessScope")),
    (("PLN-01", "PLN-02", "PLN-03"), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision", "/schedules、/{id}/actions/{calculate|apply}", workflow="计划计算候选、预警和显式应用", authorization="ProjectStageScope；计划只读/维护分离")),
    (("PLN-04",), contract("ConstructionPlan", "sol_construction_plan、sol_construction_plan_revision", "/construction-plans、/{id}/actions/{submit|approve|reject}", files="FileArtifact", workflow="施工计划提交、审批、驳回与换版", authorization="ProjectStageScope；审批人按PRD")),
    (("SCH-01", "SCH-02", "SCH-04", "SCH-05"), contract("Solution", "sol_solution、sol_solution_revision、sol_solution_review", "/solutions、/{id}/revisions、/{id}/actions/{submit|approve|reject|publish}", files="FileArtifact", workflow="方案导入/编审、重大复审和发布版本", authorization="ProjectStageScope；方案审批与文件权限")),
    (("SCH-03",), contract("Solution、FileArtifact", "sol_solution_revision、plt_file_artifact、plt_file_version", "/solutions、/files:init-upload", files="FileArtifact", workflow="配置脚本上传、解析、发布和版本冻结", authorization="ProjectStageScope、FileBusinessScope；已发布模板使用权限")),
    (("SOL-01",), contract("DynamicFormSchema、DynamicFormInstance", "sol_dynamic_form_schema、sol_dynamic_form_schema_revision、sol_dynamic_form_instance", "/form-schemas、/form-instances", workflow="Schema发布后只读、实例按版本校验", authorization="ProjectStageScope；Schema维护与实例填写分离")),

    (("EXE-01",), contract("ArrivalAcceptance", "imp_arrival_acceptance、imp_arrival_line、imp_arrival_difference", "/arrival-acceptances", events="ArrivalAccepted", files="FileArtifact", workflow="草稿、差异处理、项目经理最终确认", authorization="ImplementationProjectBatchScope；项目经理确认")),
    (("EXE-02",), contract("InstallationRecord", "imp_installation_record、imp_installation_item、imp_installation_evidence", "/installation-records", events="InstallationConfirmed", files="FileArtifact", workflow="提交、项目经理确认/退回和整改", authorization="ImplementationProjectDeviceScope；设备归属与项目权限")),
    (("EXE-03",), contract("ConfigurationCollectionResult、DeviceComponentRelation、CollectionTask", "imp_configuration_collection_result、imp_configuration_collection_parse_attempt、imp_configuration_component_candidate、ast_device_component_relation、plt_collection_task", "/configuration-results、/devices/{id}/component-relations、/collection-tasks", events="ConfigurationParsed、DeviceComponentRelationChanged、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="采集回调、框板解析、待匹配/人工绑定和业务消费确认", authorization="ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope；设备关系维护权限")),
    (("EXE-04",), contract("JointDebuggingResult、CollectionTask", "imp_joint_debugging_result、imp_joint_debugging_item、plt_collection_task", "/debugging-results、/collection-tasks", events="JointDebuggingCompleted、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="采集回调、联调结论和问题引用", authorization="ImplementationProjectDeviceCollectionScope、BusinessObjectDeviceCredentialScope")),
    (("EXE-05",), contract("ImplementationRisk", "imp_risk、imp_risk_treatment", "/implementation-risks", events="ImplementationRiskRaised/Closed", files="FileArtifact", workflow="风险提出、处置和关闭", authorization="ImplementationProjectDeviceScope；风险范围")),
    (("EXE-06",), contract("ImplementationReadinessSnapshot", "proj_project_stage_snapshot", "/implementation-readiness/{projectId}", events="ImplementationReadinessSnapshotPublished", workflow="就绪门禁汇总、快照发布与CUT消费", authorization="ImplementationProjectCutoverScope")),
    (("IMP-01",), contract("ImplementationQualityCheck", "imp_quality_check、imp_quality_item、imp_quality_remediation、imp_quality_review", "/quality-checks", events="QualitySafetyGateChanged", files="FileArtifact", workflow="提交→复核→整改→再复核", authorization="ImplementationProjectBatchScope；复核权限")),
    (("IMP-02",), contract("ImplementationSafetyCheck", "imp_safety_check、imp_safety_item、imp_safety_remediation、imp_safety_exemption", "/safety-checks", events="QualitySafetyGateChanged", files="FileArtifact", workflow="提交→复核→阻断→整改/豁免→再复核", authorization="ImplementationProjectBatchScope；安全复核/豁免权限")),

    (("ACC-01", "ACC-03"), contract("Acceptance", "acc_acceptance、acc_acceptance_item、acc_confirmation", "/acceptances", files="FileArtifact", workflow="培训/报告提交、确认和问题留痕", authorization="ProjectStageScope、FileBusinessScope")),
    (("ACC-02",), contract("SatisfactionCollection", "acc_satisfaction_collection_task、acc_satisfaction_questionnaire、acc_satisfaction_response、acc_satisfaction_result", "/satisfaction-tasks、/satisfaction-questionnaires/{token}/responses、/satisfaction-results", events="SatisfactionTaskCreated、SatisfactionResultRecorded", files="FileArtifact", workflow="冻结模板→指派→客户提交→判定→整改后新版本重收→归档", authorization="ProjectStageScope；客户一次性实例范围；答案/签字不可改写")),
    (("ACC-04",), contract("DeliveryArtifact", "acc_delivery_artifact、acc_artifact_review、acc_archive_record", "/delivery-artifacts", events="ArtifactAccepted/Archived", files="FileArtifact", workflow="齐套检查、审核和归档分离", authorization="ProjectStageScope、FileBusinessScope；ACC归档")),
    (("CLO-01", "CLO-02"), contract("ProjectClosure、ClosureGateSnapshot、SatisfactionCollection", "acc_project_closure、acc_closure_gate_snapshot、acc_closure_review、acc_satisfaction_result", "/closure-gates/{projectId}、/project-closures", events="ProjectClosureCompleted", files="FileArtifact", workflow="门禁校验、冻结流程审批、整改和闭环；不创建回访节点", authorization="ProjectStageScope；全部后代项目门禁与审批范围")),
    (("ACC-05", "ACC-06"), contract("ServiceHandover、ProjectClosure", "acc_service_handover、acc_handover_item、acc_handover_result", "/service-handovers", events="ProjectClosureCompleted", files="FileArtifact", workflow="遗留问题及持续服务交接、接收确认", authorization="ProjectStageScope；交接双方项目/服务范围")),

    (("SUB-01", "SUB-02", "SUB-05"), contract("SubcontractRequest", "res_subcontract_request", "/subcontract-requests", events="SubcontractApproved", integration="OA", files="FileArtifact", workflow="平台内转包审批、价格审批与版本冻结", authorization="OrganizationSupplierScope；项目/供应商范围")),
    (("SUB-03", "SUB-04"), contract("PaymentGate、SatisfactionCollection", "res_payment_gate、acc_satisfaction_result", "/payment-gates", events="PaymentGateChanged", integration="财务系统", files="FileArtifact", workflow="付款前置满意度事实、批准版本和财务确认", authorization="OrganizationSupplierScope；付款门禁权限；满意度只读引用")),

    (("CUS-01", "CUS-02", "CUS-03", "CUS-04"), contract("Customer、CustomerContact、CustomerRelationshipSnapshot", "cus_customer、cus_customer_contact、cus_project_customer_contact_relation", "/customers、/customer-contacts、/customer-relationships", events="CustomerMerged、MasterDataSynchronized", integration="CRM", workflow="客户同步、临时客户受控合并和联系人关系", authorization="OrganizationCustomerScope；CRM字段只读")),
    (("EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"), contract("Device、DeviceArchive、DeviceComponentRelation、DeviceCurrentAssignment", "ast_device、ast_device_component_relation、ast_device_current_assignment、ast_device_assignment_history", "/devices、/devices/{id}/archive、/devices/{id}/component-relations、/devices/{id}/assignment-history", events="DeviceAssigned、DeviceComponentRelationChanged", files="FileArtifact", workflow="设备档案、配置Log引用、框板关系、扫码和唯一归属", authorization="ProjectDeviceScope；当前归属、框板关系维护与祖先范围")),
    (("EQP-04",), contract("AssetSyncSnapshot、Device", "ast_asset_sync_batch、ast_asset_sync_item、ast_device", "/devices", events="MasterDataSynchronized", integration="MES", workflow="MES来源版本幂等同步与冲突隔离", authorization="ProjectDeviceScope；MES字段只读")),
    (("AST-01",), contract("RMAReplacement、MaintenanceFact", "ast_rma_replacement、ast_maintenance_fact", "/rma-replacements、/devices/{deviceId}/service-status", events="DeviceStatusSynchronized", integration="备件系统", files="FileArtifact", workflow="RMA替换、设备归属校验和维保事实衔接", authorization="ProjectDeviceScope；设备与来源范围")),
    (("AST-02", "SRV-01"), contract("MaintenanceFact、ServiceStatus", "ast_maintenance_fact、srv_service_status", "/devices/{deviceId}/service-status", events="ServiceStatusChanged", integration="CRM", workflow="客观维保/停产停维状态计算与提示", authorization="ProjectDeviceScope；客观事实只读")),

    (("RPT-02",), contract("MetricSnapshot", "ana_metric_snapshot", "/analytics/metrics", events="MetricSnapshotPublished", workflow="项目状态指标口径计算、快照发布和水位展示", authorization="OrganizationReportScope；字段级脱敏")),
    (("PROJ-12",), contract("ProjectPortfolio", "proj_project_portfolio、proj_project_portfolio_member、proj_project_portfolio_revision", "/project-portfolios", events="ProjectPortfolioPublished", workflow="组合成员、发布快照和下钻", authorization="ProjectTreeScope；组合汇总不扩权")),
    (("ANA-01",), contract("PortfolioView、MetricSnapshot", "ana_portfolio_projection、ana_metric_snapshot", "/analytics/portfolios/{id}、/analytics/metrics", events="MetricSnapshotPublished", workflow="组合指标快照生成与只读展示", authorization="OrganizationReportScope；下钻回项目权限")),

    (("CUT-01", "CUT-02", "CUT-09", "CUT-10"), contract("CutoverTask、CutoverAssessment", "cut_task、cut_assessment", "/cutover-tasks、/cutover-tasks/{id}/assessment", events="CutoverApproved", files="FileArtifact", workflow="任务创建、分级评估、风险/调研矩阵", authorization="CutoverTaskScope；项目/设备范围")),
    (("CUT-03", "CUT-04", "CUT-05", "CUT-07"), contract("CutoverPlan", "cut_plan_revision、cut_step", "/cutover-tasks/{id}/plan-revisions", events="CutoverApproved", files="FileArtifact", workflow="动态清单、方案编审、分级审批和版本冻结", authorization="CutoverTaskScope；分级审批权限")),
    (("CUT-06",), contract("CutoverClosure、CollectionTask", "cut_cutover_closure、plt_collection_task", "/cutover-tasks/{id}/closure", events="CollectionResultConsumed、CutoverCompleted", integration="现有采集平台子应用、ITR", files="FileArtifact", workflow="P6闭环填写、INT-12证据引用、提交归档和结果回流；不建立逐步骤执行或稳定观察", authorization="CutoverTaskScope、BusinessObjectDeviceCredentialScope")),
    (("CUT-08",), contract("CutoverTask", "cut_task、ast_asset_sync_item", "/cutover-tasks", integration="备件系统", workflow="备件申请映射、回调、门禁和对账", authorization="CutoverTaskScope；外部备件范围")),

    (("INS-01", "INS-02", "INS-04", "INS-07"), contract("InspectionTask、CollectionTask", "srv_inspection_task、srv_inspection_task_rule_snapshot、plt_collection_task", "/inspection-tasks、/collection-tasks", events="InspectionDispatched、InspectionCompleted、CollectionResultConsumed", integration="现有采集平台子应用", files="FileArtifact", workflow="方式选择、预检/执行、业务消费和归档门禁", authorization="AssignedProjectDeviceScope、BusinessObjectDeviceCredentialScope")),
    (("INS-03", "INS-09"), contract("InspectionRule", "srv_inspection_rule、srv_inspection_rule_revision", "/inspection-rules、/{id}/revisions", workflow="规则配置、发布版本和任务冻结", authorization="AssignedProjectDeviceScope；规则维护/使用分离")),
    (("INS-05",), contract("InspectionReport", "srv_inspection_report_revision", "/inspection-reports/{id}/versions", events="InspectionCompleted", integration="UMC", files="FileArtifact", workflow="报告生成、回调校验、发布版本", authorization="AssignedProjectDeviceScope、FileBusinessScope")),
    (("INS-06", "INS-08"), contract("ServiceIssue", "srv_service_issue、srv_service_issue_remediation", "/service-issues", events="InspectionIssueRaised/Closed", files="FileArtifact", workflow="问题标注、误报、整改复核和关闭", authorization="AssignedProjectDeviceScope；问题责任范围")),

    (("INT-01",), contract("Project、Contract、SalesOrder", "ast_asset_sync_batch、ast_asset_sync_item、com_contract、com_sales_order", "/projects、/contracts、/sales-orders", events="MasterDataSynchronized", integration="CRM、ERP", workflow="双源同步、待映射、字段Owner裁决和对账", authorization="集成服务账号限Owner字段；工程管理部映射权限")),
    (("INT-02",), contract("AssetSyncSnapshot", "ast_asset_sync_batch、ast_asset_sync_item、ast_device", "/devices", events="MasterDataSynchronized", integration="ITR", workflow="版本同步、来源冲突隔离和对账", authorization="集成账号限ITR字段；ProjectDeviceScope查询")),
    (("INT-03",), contract("Customer、CustomerRelationshipSnapshot", "cus_customer、ast_asset_sync_batch、ast_asset_sync_item", "/customers", events="MasterDataSynchronized、CustomerMerged", integration="CRM", workflow="客户同步、临时客户合并与字典映射", authorization="OrganizationCustomerScope；CRM字段只读")),
    (("INT-04",), contract("TechnicalNoticeReference", "FEATURE_FORWARD_MIGRATION(INT-04)：逻辑对象`TechnicalNoticeReference`；物理表由INT-04 Feature前向迁移确定", "/technical-notices、/technical-notices/{id}/references", events="TechnicalNoticeSynchronized", integration="ITR", workflow="公告镜像、版本同步和业务引用", authorization="ProductDeviceProjectScope；V2只读")),
    (("INT-05",), contract("Todo", "plt_todo、plt_sync_batch、plt_external_key_mapping", "/todos、/integration/hr/directory", events="MasterDataSynchronized、TodoRequested、TodoCompleted", integration="钉钉、HR、OA", workflow="必要人员组织同步复用基础平台主数据、已有同步批次和来源键映射；待办链接和通知回执不接入打卡/工时", authorization="TenantOrganizationProjectScope；目录身份不直接等于项目角色")),
    (("INT-06",), contract("RMAReplacement、AuthorizationGrant、InspectionReport", "ast_rma_replacement、plt_authorization_grant、srv_inspection_report_revision", "/rma-replacements、/authorization-grants、/inspection-reports/{id}/versions", events="MasterDataSynchronized", integration="备件系统、授权系统、UMC", files="FileArtifact", workflow="外部申请/结果映射、回调和对账", authorization="业务对象范围；完整授权码不可见")),
    (("INT-07",), contract("PaymentGate", "res_payment_gate、plt_integration_reconciliation", "/payment-gates", events="PaymentGateChanged", integration="财务系统", files="FileArtifact", workflow="批准费用出向、结果查询和人工对账", authorization="OrganizationSupplierScope；财务结果复核")),
    (("INT-09",), contract("AuthorizationGrant", "plt_authorization_grant、ast_asset_sync_item", "/authorization-grants", integration="LDAP/AD", workflow="认证断言校验、目录映射和平台会话", authorization="平台RBAC/DataScope；目录组不直授项目角色")),
    (("INT-10", "NFR-03"), contract("Todo", "plt_todo、ast_asset_sync_item", "/todos", events="NotificationRequested、NotificationDelivered/Failed", integration="短信/邮件、钉钉", workflow="节点通知、受理/送达分离和兜底", authorization="业务对象接收人范围；模板变量白名单")),
    (("INT-12", "NFR-02"), contract("DeviceCredential、CredentialGrant、CollectionTask、CollectionResultReference", "plt_device_credential、plt_credential_grant、plt_collection_task、plt_collection_result_consumption", "/device-credentials、/collection-tasks、/internal/collection-tasks/{id}/actions/confirm-consumption", events="CollectionTaskRequested、CollectionResultAvailable、CollectionResultConsumed、CollectionCompleted", integration="现有采集平台子应用", files="FileArtifact", workflow="授权校验→下发→回调→业务消费→完成/失败；独立中心按成功回调终态", authorization="BusinessObjectDeviceCredentialScope；创建人默认权限和五元组授权")),
    (("NFR-01",), contract("AuditRecord、MetricSnapshot", "plt_operation_audit、ana_metric_snapshot", "/analytics/metrics", workflow="性能水位、安全审计和兼容性验证", authorization="TenantOrganizationProjectScope；服务端强制范围")),
    (("AUT-01", "AUT-02"), contract("AuthorizationGrant", "plt_authorization_grant", "/authorization-grants", integration="OA、授权系统", files="FileArtifact", workflow="申请、OA审批引用、外部授权确认和查询", authorization="申请对象/设备/产品范围；授权码脱敏")),
    (("CHG-01",), contract("ChangeRequest", "plt_change_request", "/change-requests", files="FileArtifact", workflow="变更申请、审批、目标版本校验和执行", authorization="TenantOrganizationProjectScope；变更对象权限")),
    (("PLT-01",), contract("Todo", "plt_todo", "/todos、/{id}/actions/complete", events="TodoRequested、TodoCompleted", workflow="统一待办接入、完成回调Owner再校验", authorization="TenantOrganizationProjectScope；待办责任人范围")),
    (("PLT-02",), contract("FileArtifact", "plt_file_artifact、plt_file_version、plt_file_reference", "/files:init-upload、/files/{id}:complete-upload、/file-references", events="FileVersionCommitted、FileReferenceAttached/Detached、FileArchived", files="FileArtifact", workflow="初始化上传、内容校验、版本提交、引用和归档", authorization="FileBusinessScope；下载实时回源业务权限")),
    (("COM-01",), contract("Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail", "com_contract、com_sales_order、com_order_line、com_delivery_scope、com_delivery_scope_detail", "/contracts、/sales-orders、/order-lines、/delivery-scopes", events="DeliveryScopeAssigned/Released", integration="ERP、CRM", workflow="ERP订单行同步、范围主记录及明细分配/释放、明细合计一致性和超分配门禁", authorization="ContractProjectScope；ERP核心字段只读")),
    (("COM-02",), contract("FulfillmentSnapshot、ReconciliationRecord", "com_fulfillment_snapshot、com_reconciliation_record", "/fulfillment-reconciliations", events="FulfillmentSnapshotPublished", integration="CRM", workflow="履约回写、业务回执、差异确认和关闭", authorization="ContractProjectScope；交付事实与经营状态分域")),
    (("RES-01",), contract("Supplier", "res_supplier、res_qualification", "/suppliers", integration="OA", files="FileArtifact", workflow="服务商档案、资质版本和可用状态", authorization="OrganizationSupplierScope；资质文件字段权限")),
]


def load_requirements(prd: Path) -> list[dict[str, str]]:
    generator = Path(__file__).with_name("generate_requirement_traceability.py")
    spec = importlib.util.spec_from_file_location("requirement_generator", generator)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module.extract_requirements(module.read(prd))


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

    if spec.events != NA_EVENT:
        tests.append("事件Outbox/Inbox、重复/乱序/重放测试")
        evidence.append("事件消息ID、Outbox/Inbox及消费水位证据")
    if spec.integration != NA_INTEGRATION:
        tests.append("外部集成映射、超时/重试/对账/降级测试")
        evidence.append("脱敏请求响应、幂等键、重试/对账与降级记录")
    if spec.files != NA_FILE:
        tests.append("文件上传/下载/版本/恶意内容与权限回源测试")
        evidence.append("文件哈希、版本、扫描、引用与权限拒绝记录")

    exact: dict[str, tuple[list[str], list[str]]] = {
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


def render(prd: Path) -> str:
    requirements = load_requirements(prd)
    catalog = build_catalog(requirements)
    lines = [
        "# SDS Phase 2 显式需求契约映射",
        "",
        "> 文档状态：`BASELINE`",
        "> 适用基线：PRD V1.7（`docs/baseline/prd-v1.7.md`）",
        "> Requirement ID：附录 A.1 全部 103 项 V1/V2 正式需求",
        "> Owner：SDS Phase 2 追溯治理；具体业务 Owner 以 `requirement-matrix.md` 为准",
        "> Phase 3验证注记状态：`BASELINE`（不改变已批准的Phase 2契约基线）",
        "",
        "本文件逐项声明可实施的数据对象、表、API、事件/集成/文件、工作流和授权落点。相同基础契约可被多个相关 Requirement 复用，但每个 Requirement 必须显式登记；`N/A` 必须说明为何该类契约不适用。",
        "",
    ]
    for item in requirements:
        identifier = item["id"]
        spec = catalog[identifier]
        phase3_tests, phase3_evidence = phase3_verification(identifier, spec)
        lines.extend([
            f"### {identifier}",
            "",
            f"- 需求名称：{item['name']}",
            f"- 数据对象：{spec.data}",
            f"- 数据表：{spec.tables}",
            f"- API：{spec.apis}",
            f"- 事件：{spec.events}",
            f"- 外部集成：{spec.integration}",
            f"- 文件契约：{spec.files}",
            f"- 工作流/状态：{spec.workflow}",
            f"- 授权与数据范围：{spec.authorization}",
            f"- Phase 3测试类别：{phase3_tests}",
            f"- Phase 3证据类型：{phase3_evidence}",
            "",
        ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prd", type=Path, default=Path("docs/baseline/prd-v1.7.md"))
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
