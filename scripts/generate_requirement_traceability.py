#!/usr/bin/env python3
"""Generate the V1.8 requirement-to-engineering traceability index and SDS mappings."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path


REQ_ID = re.compile(r"^[A-Z]+(?:-[A-Z0-9]+)?-\d+$")
REQ_HEADER = re.compile(r"^#{3,4}\s+(?:\d+(?:\.\d+)*\s+)?([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s+(.+?)\s*$")
INDEX_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|", re.M)
SUPPLEMENTAL_SLICE_ROW = re.compile(
    r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)@(V[12])\s*\|\s*"
    r"([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*(V[12])\s*\|\s*"
    r"([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$",
    re.M,
)
REQUIREMENT_BODY_ROW = re.compile(
    r"^\|\s*需求编号\s*\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*$",
    re.M,
)
ACCEPTANCE_HEADING = re.compile(r"^\*\*(?:业务)?验收标准：\*\*\s*$", re.M)
POST_ACCEPTANCE_HEADING = re.compile(
    r"^\*\*(?:涉及数据字段|权限与数据范围|异常、降级及留痕要求|依赖关系)：\*\*",
    re.M,
)
FEATURE_ID = re.compile(r"^(F-[A-Z]+-\d+)")
FEATURE_COVERAGE_LINE = re.compile(r"^>\s*Requirement切片覆盖：`([^`]+)`\s*$", re.M)
FEATURE_COVERAGE_ITEM = re.compile(
    r"^([A-Z]+(?:-[A-Z0-9]+)?-\d+)@(V[12])=(FULL|PARTIAL)$"
)
TASK_STATUS_LINE = re.compile(
    r"^>\s*(?:Feature\s*实施状态|功能实施状态)：`([^`]+)`",
    re.M,
)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def fields(block: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in block.splitlines():
        match = re.match(r"^\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*$", line)
        if match:
            result[match.group(1).strip()] = match.group(2).strip()
    return result


def normalize_acceptance(text: str) -> str:
    parts: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.startswith("- "):
            line = line[2:].strip()
        line = line.replace("**", "")
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            parts.append(line)
    return "；".join(parts)


def acceptance_section(block: str, identifier: str) -> str:
    heading = ACCEPTANCE_HEADING.search(block)
    if not heading:
        raise SystemExit(f"formal requirement acceptance heading not found: {identifier}")
    tail = block[heading.end():]
    end = POST_ACCEPTANCE_HEADING.search(tail)
    acceptance = normalize_acceptance(tail[:end.start() if end else len(tail)])
    if not acceptance or "WHEN" not in acceptance or "THEN" not in acceptance:
        raise SystemExit(f"formal requirement acceptance is not observable: {identifier}")
    return acceptance


def extract_requirements(text: str) -> list[dict[str, str]]:
    # Appendix A.1 is the authoritative set/order of formal requirements.
    # Heading-based extraction misses requirements whose正文 heading is not a
    # conventional numbered heading (for example integration and NFR blocks).
    index_start = re.search(r"(?m)^###\s+A\.1\b.*$", text)
    index_end = re.search(r"(?m)^###\s+A\.2\b.*$", text)
    if not index_start or not index_end or index_end.start() <= index_start.end():
        raise SystemExit("cannot locate Appendix A.1/A.2 formal requirement index")
    index_text = text[index_start.end():index_end.start()]
    index_rows = [match for match in INDEX_ROW.finditer(index_text)]
    if not index_rows:
        raise SystemExit("Appendix A.1 contains no formal requirement rows")

    body_rows = list(REQUIREMENT_BODY_ROW.finditer(text, 0, index_start.start()))
    body_row_positions: dict[str, int] = {}
    for position, body_row in enumerate(body_rows):
        identifier = body_row.group(1)
        if identifier in body_row_positions:
            raise SystemExit(f"duplicate formal requirement body: {identifier}")
        body_row_positions[identifier] = position

    requirements: list[dict[str, str]] = []
    for match in index_rows:
        identifier = match.group(1).strip()
        name = match.group(2).strip()
        target_version = match.group(4).strip()
        version_match = re.search(r"V1|V2", target_version)
        version = version_match.group(0) if version_match else ""
        if not version:
            raise SystemExit(f"formal requirement target version is invalid: {identifier}: {target_version}")
        body_position = body_row_positions.get(identifier)
        if body_position is None:
            raise SystemExit(f"formal requirement body not found: {identifier}")
        body_row = body_rows[body_position]
        marker_index = body_row.start()
        previous_marker = body_rows[body_position - 1].start() if body_position > 0 else -1
        # Include the nearest requirement heading, when present, for source context.
        heading_index = text.rfind("\n#### ", 0, marker_index)
        if heading_index < previous_marker:
            heading_index = text.rfind("\n### ", 0, marker_index)
        start = heading_index + 1 if heading_index >= 0 else marker_index
        end = body_rows[body_position + 1].start() if body_position + 1 < len(body_rows) else index_start.start()
        block = text[start:end]
        value = fields(block)
        requirements.append(
            {
                "id": identifier,
                "name": name,
                "stage": value.get("所属阶段", ""),
                "priority": value.get("优先级", ""),
                "version": version,
                "target_version": target_version,
                "roles": value.get("用户角色", ""),
                "source": value.get("来源追溯", ""),
                "acceptance": acceptance_section(block, identifier),
            }
        )
    if len(requirements) != 100:
        raise SystemExit(f"Appendix A.1 formal requirement count is {len(requirements)}, expected 100")
    counts = Counter(item["version"] for item in requirements)
    if counts != Counter({"V1": 53, "V2": 47}):
        raise SystemExit(f"Appendix A.1 main version counts are {dict(counts)}, expected V1=53/V2=47")
    return requirements


def extract_version_slices(text: str, requirements: list[dict[str, str]]) -> list[dict[str, str]]:
    requirement_by_id = {item["id"]: item for item in requirements}
    slices: list[dict[str, str]] = [
        {
            "key": f"{item['id']}@{item['version']}",
            "requirement_id": item["id"],
            "version": item["version"],
            "business_result": f"{item['name']}的{item['version']}主交付业务结果",
            "boundary": item["target_version"],
        }
        for item in requirements
    ]

    slice_start = re.search(r"(?m)^####\s+A\.1\.1\b.*$", text)
    slice_end = re.search(r"(?m)^###\s+A\.2\b.*$", text)
    if not slice_start or not slice_end or slice_end.start() <= slice_start.end():
        raise SystemExit("cannot locate Appendix A.1.1/A.2 Requirement version slices")
    supplemental_text = text[slice_start.end():slice_end.start()]
    for match in SUPPLEMENTAL_SLICE_ROW.finditer(supplemental_text):
        key_requirement, key_version, column_requirement, column_version, result, boundary = (
            value.strip() for value in match.groups()
        )
        if key_requirement != column_requirement or key_version != column_version:
            raise SystemExit(
                f"Requirement slice row is inconsistent: {key_requirement}@{key_version} / "
                f"{column_requirement}@{column_version}"
            )
        if key_requirement not in requirement_by_id:
            raise SystemExit(f"Requirement slice references unknown requirement: {key_requirement}@{key_version}")
        slices.append(
            {
                "key": f"{key_requirement}@{key_version}",
                "requirement_id": key_requirement,
                "version": key_version,
                "business_result": result,
                "boundary": boundary,
            }
        )

    keys = [item["key"] for item in slices]
    duplicates = sorted(key for key, count in Counter(keys).items() if count > 1)
    if duplicates:
        raise SystemExit(f"duplicate Requirement version slices: {', '.join(duplicates)}")
    counts = Counter(item["version"] for item in slices)
    if len(slices) != 111 or counts != Counter({"V1": 53, "V2": 58}):
        raise SystemExit(
            f"Requirement version slices are total={len(slices)}, counts={dict(counts)}; "
            "expected total=111, V1=53, V2=58"
        )
    return slices


DOMAIN_NAMES = {
    "PROJ": "项目治理", "SOL": "交付准备与方案", "IMP": "现场实施",
    "ACC": "验收与项目闭环", "CUT": "变更切换与稳定治理", "SRV": "服务运营",
    "CUS": "客户与服务关系", "AST": "资产管理", "COM": "合同订单履约",
    "RES": "资源与外包", "ANA": "经营分析", "PLT": "平台公共能力",
    "KNO": "技术知识治理",
}

# PRD-derived mapping. This intentionally does not read the historical specs;
# their IDs are reference material only and cannot decide current ownership.
PREFIX_OWNER = {
    "PM": "PROJ", "PROJ": "PROJ", "INT-01": "PROJ",
    "PRE": "SOL", "PLN": "SOL", "SCH": "SOL", "SOL": "SOL",
    "EXE": "IMP", "IMP": "IMP",
    "ACC": "ACC", "CLO": "ACC",
    "CUT": "CUT",
    "INS": "SRV", "SRV": "SRV", "WO": "SRV",
    "CUS": "CUS", "INT-03": "CUS",
    "EQP": "AST", "AST": "AST", "INT-02": "AST", "INT-06": "AST",
    "COM": "COM",
    "RES": "RES", "SUB": "RES", "INT-07": "RES",
    "RPT": "ANA", "ANA": "ANA",
    "PLT": "PLT", "AUT": "PLT", "CHG": "PLT", "NFR": "PLT",
    "INT-05": "PLT", "INT-09": "PLT", "INT-10": "PLT", "INT-12": "PLT",
    "INT-04": "KNO",
}

PHASE1_DESIGN = {
    "PROJ": ("项目治理", "Project / ProjectTask / TaskWorkBinding / ProjectTemplate", "Project或Task状态机；WorkBinding统一必填且默认TASK_NATIVE，其他类型按关系装载与事实完成", "ProjectTreeScope", "ProjectApplicationService", "Project、ProjectTask、TaskWorkBinding、TaskCompletionRule、ProjectTemplate", "业务规则+权限+树查询+统一工作台投影"),
    "SOL": ("交付准备与方案", "Preparation / ConstructionPlan / Solution", "Plan或Solution状态机；计划/方案审批流", "ProjectStageScope", "PreparationApplicationService", "Preparation、Plan、Solution、File", "业务规则+审批+文件"),
    "IMP": ("实施执行", "ArrivalAcceptance / InstallationRecord / ConfigurationCollectionResult / JointDebuggingResult / ImplementationRisk / ImplementationQualityCheck / DeliveryEvidence", "实施执行聚合状态机；质量整改复核工作流；阶段门禁工作流", "ImplementationProjectBatchScope", "ImplementationExecutionApplicationService", "ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、DeliveryEvidence", "业务规则+证据+权限+整改"),
    "ACC": ("验收与项目闭环", "Acceptance / SatisfactionCollection / Artifact / ProjectClosure", "满意度、Artifact与ProjectClosure状态机；验收/闭环审批流", "ProjectStageScope", "AcceptanceApplicationService", "Acceptance、SatisfactionCollection、DeliveryArtifact、ProjectClosure、ServiceHandover", "业务规则+审批+门禁"),
    "CUT": ("变更切换与稳定治理", "CutoverTask / CutoverAssessment / CutoverChecklist / CutoverPlan / CutoverClosure", "CutoverTask阶段状态机；P3同工作台匹配与采集结果回填；P5分级审批与P6闭环归档流", "CutoverTaskScope", "CutoverApplicationService", "CutoverTask、CutoverAssessment、CutoverChecklist、CutoverPlan、CutoverClosure", "业务规则+采集证据+审批+闭环+幂等"),
    "SRV": ("Inspection / Service Operations", "InspectionTask / ServiceIssue / ServiceStatus", "Inspection与Service Operations分别维护状态机和闭环流", "AssignedProjectDeviceScope", "ServiceApplicationService", "InspectionTask、InspectionRule、ServiceIssue、ServiceStatus", "业务规则+权限+异常"),
    "CUS": ("Customer & Relationship", "Customer / Contact / AssetRelation", "Customer同步状态机；主数据同步流", "OrganizationCustomerScope", "CustomerApplicationService", "Customer、Contact、AssetRelation、CustomerSyncSnapshot", "数据同步+权限"),
    "AST": ("Asset Management", "Device / DeviceArchive / RMAReplacement", "Device服务状态机；设备同步流", "ProjectDeviceScope", "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot", "数据一致性+归属+安全"),
    "COM": ("合同订单履约", "Contract / SalesOrder / OrderLine / DeliveryScope / DeliveryScopeDetail", "ERP权威事实同步；平台交付范围分配与释放", "ContractProjectScope", "ContractApplicationService", "Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail", "数量约束+权威来源+幂等"),
    "RES": ("资源与外包", "Supplier / SubcontractRequest / PaymentGate", "Subcontract与PaymentGate状态机；转包审批流", "OrganizationSupplierScope", "SubcontractApplicationService", "Supplier、SubcontractRequest、PaymentGate", "审批+门禁+财务集成"),
    "ANA": ("经营分析", "MetricSnapshot / PortfolioView", "指标快照生成流（只读）", "OrganizationReportScope", "AnalyticsQueryService", "MetricSnapshot、PortfolioView", "口径+数据范围+性能"),
    "PLT": ("基础平台能力 / Device Access & Collection", "Todo / FileArtifact / AuthorizationGrant / ChangeRequest / DeviceCredential / CredentialGrant / CollectionTask", "公共能力状态机、授权审批流和采集任务授权/回调流", "TenantOrganizationProjectScope / BusinessObjectDeviceCredentialScope", "PlatformApplicationService / CollectionOrchestrationService", "Todo、FileArtifact、AuthorizationGrant、ChangeRequest、AuditRecord、DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord", "安全+权限+审计+幂等"),
    "KNO": ("技术知识治理", "TechnicalNoticeReference", "来源同步状态机；版本映射流", "ProductDeviceProjectScope", "KnowledgeApplicationService", "TechnicalNoticeReference、SourceMapping", "来源一致性+版本追溯"),
}

EXACT_PHASE1_DESIGN = {
    "EXE-01": ("实施执行", "ArrivalAcceptance", "ArrivalAcceptance状态机；到货差异处理", "ImplementationProjectBatchScope", "ArrivalAcceptanceApplicationService", "ArrivalAcceptance、DeliveryEvidence", "业务规则+数量/序列号+权限"),
    "EXE-02": ("实施执行", "InstallationRecord", "InstallationRecord状态机；安装整改流程", "ImplementationProjectDeviceScope", "InstallationApplicationService", "InstallationRecord、DeliveryEvidence", "业务规则+照片证据+权限"),
    "EXE-03": ("实施执行", "ConfigurationCollectionResult", "ConfigurationCollectionResult状态机；采集回调消费", "ImplementationProjectDeviceCollectionScope", "ConfigurationCollectionApplicationService", "ConfigurationCollectionResult、CollectionTask引用、DeliveryEvidence", "幂等+解析异常+凭证权限"),
    "EXE-04": ("实施执行", "JointDebuggingResult", "JointDebuggingResult状态机；采集回调消费", "ImplementationProjectDeviceCollectionScope", "JointDebuggingApplicationService", "JointDebuggingResult、CollectionTask引用、DeliveryEvidence", "幂等+结果解释+权限"),
    "EXE-05": ("实施执行", "ImplementationRisk", "ImplementationRisk状态机；风险处置工作流", "ImplementationProjectDeviceScope", "ImplementationRiskApplicationService", "ImplementationRisk、RiskEvidence", "风险规则+门禁+权限"),
    "EXE-06": ("实施执行", "CutoverReadinessContract（跨域契约）", "实施门禁状态机；CUT执行前置校验", "ImplementationProjectCutoverScope", "ImplementationReadinessApplicationService", "ReadinessSnapshot、CutoverTask引用", "门禁+跨域契约+权限"),
    "IMP-01": ("实施执行", "ImplementationQualityCheck", "ImplementationQualityCheck状态机；提交→复核→整改→再复核", "ImplementationProjectBatchScope", "ImplementationQualityApplicationService", "ImplementationQualityCheck、Remediation、QualityEvidence", "整改复核+权限+审计"),
    "INT-01": PHASE1_DESIGN["PROJ"], "INT-02": PHASE1_DESIGN["AST"], "INT-03": PHASE1_DESIGN["CUS"],
    "INT-04": PHASE1_DESIGN["KNO"], "INT-05": PHASE1_DESIGN["PLT"], "INT-06": PHASE1_DESIGN["AST"],
    "INT-07": PHASE1_DESIGN["RES"], "INT-09": PHASE1_DESIGN["PLT"], "INT-10": PHASE1_DESIGN["PLT"],
    "INT-12": ("Device Access & Collection", "DeviceCredential / CredentialGrant / CollectionTask / CallbackRecord", "凭证授权与采集任务状态机；任务下发和回调消费流", "BusinessObjectDeviceCredentialScope", "CollectionOrchestrationService", "DeviceCredential、CredentialGrant、CollectionTask、CallbackRecord", "安全+权限+幂等"),
}

# Context-level refinements keep the 13-domain Owner unchanged while making
# the internal bounded-context mapping explicit in the working matrix.
for _identifier in ("INS-01", "INS-02", "INS-03", "INS-04", "INS-05", "INS-06", "INS-07", "INS-08", "INS-09"):
    EXACT_PHASE1_DESIGN[_identifier] = (
        "Inspection", "InspectionTask / InspectionRule / ServiceIssue",
        "Inspection状态机；巡检执行与问题闭环工作流", "AssignedProjectDeviceScope",
        "InspectionApplicationService", "InspectionTask、InspectionRule、InspectionReport、ServiceIssue",
        "业务规则+权限+采集结果",
    )
EXACT_PHASE1_DESIGN["SRV-01"] = (
    "Service Operations", "ServiceStatus / ServiceHandoverReference",
    "ServiceStatus状态机；服务状态同步与提示流", "ProjectDeviceScope",
    "ServiceOperationsApplicationService", "ServiceStatus、ServiceHandoverReference、DeviceServiceSnapshot",
    "来源同步+权限+提示",
)
for _identifier in ("CUS-01", "CUS-02", "CUS-03", "CUS-04", "INT-03"):
    EXACT_PHASE1_DESIGN[_identifier] = (
        "Customer & Relationship", "Customer / Contact / AssetRelation / CustomerSyncSnapshot",
        "Customer同步状态机；主数据同步流", "OrganizationCustomerScope",
        "CustomerApplicationService", "Customer、Contact、AssetRelation、CustomerSyncSnapshot",
        "数据同步+权限+来源版本",
    )
for _identifier in ("EQP-01", "EQP-02", "EQP-03", "EQP-04", "EQP-05", "EQP-07", "AST-01", "AST-02", "INT-02", "INT-06"):
    EXACT_PHASE1_DESIGN[_identifier] = (
        "Asset Management", "Device / DeviceArchive / RMAReplacement / AssetSyncSnapshot",
        "Device状态机；资产主数据同步流", "ProjectDeviceScope",
        "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot",
        "数据一致性+归属+来源版本",
    )
EXACT_PHASE1_DESIGN["EQP-02"] = (
    "Asset Management", "ConfigurationLog / Device / DeviceArchive",
    "ConfigurationLog不可变版本状态机；实施结果接收与设备关联流", "ProjectDeviceScope",
    "AssetApplicationService", "ConfigurationLog、Device、DeviceArchive、FileReference、ParseVersion",
    "原始文件不可覆盖+解析版本+设备关联+来源追溯",
)
EXACT_PHASE1_DESIGN["CUS-03"] = (
    "Customer & Relationship", "Customer / Contact / AssetRelation / CustomerSyncSnapshot",
    "Customer本地生命周期命令；CRM来源同步处理流", "OrganizationCustomerScope",
    "CustomerApplicationService", "Customer、Contact、AssetRelation、CustomerSyncSnapshot",
    "数据同步+权限+来源版本",
)
EXACT_PHASE1_DESIGN["INT-03"] = (
    "Customer & Relationship", "Customer / Contact / AssetRelation / CustomerSyncSnapshot",
    "CRM同步批次/单项处理流；不直接改写Customer本地生命周期", "OrganizationCustomerScope",
    "CustomerApplicationService", "Customer、Contact、AssetRelation、CustomerSyncSnapshot",
    "数据同步+权限+来源版本",
)
EXACT_PHASE1_DESIGN["EQP-01"] = (
    "Asset Management", "Device / DeviceArchive / RMAReplacement / AssetSyncSnapshot",
    "Device无独立生命周期状态机；来源同步状态与归属时态命令", "ProjectDeviceScope",
    "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot",
    "数据一致性+归属+来源版本",
)
EXACT_PHASE1_DESIGN["EQP-04"] = (
    "Asset Management", "Device / DeviceArchive / RMAReplacement / AssetSyncSnapshot",
    "MES来源同步批次/映射处理流；不直接改写Device业务状态", "ProjectDeviceScope",
    "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot",
    "数据一致性+归属+来源版本",
)
EXACT_PHASE1_DESIGN["INT-02"] = (
    "Asset Management", "Device / DeviceArchive / RMAReplacement / AssetSyncSnapshot",
    "ITR来源同步批次/映射处理流；不直接改写Device业务状态", "ProjectDeviceScope",
    "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot",
    "数据一致性+归属+来源版本",
)
EXACT_PHASE1_DESIGN["INT-04"] = (
    "技术知识治理", "TechnicalNoticeReference",
    "ITR技术公告来源同步批次/版本映射流", "ProductDeviceProjectScope",
    "KnowledgeApplicationService", "TechnicalNoticeReference、SourceMapping",
    "来源一致性+版本追溯",
)
for _identifier in ("COM-01",):
    EXACT_PHASE1_DESIGN[_identifier] = (
        "Contract & Delivery Scope", "Contract / SalesOrder / OrderLine / DeliveryScope / DeliveryScopeDetail",
        "ERP权威事实同步；平台范围分配与释放", "ContractProjectScope",
        "ContractApplicationService", "Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail",
        "数量约束+来源版本+分配幂等",
    )
EXACT_PHASE1_DESIGN["PM-07"] = (
    "项目治理", "Project / ProjectTemplateMatchHistory / ProjectTemplate",
    "正式Project不新增分类状态；首次匹配原子记录，创建后只追加影响识别", "ProjectTreeScope + 属性Owner",
    "ProjectAttributeResolutionService / ProjectAttributeClassificationApplicationApi / ProjectAttributeSourceCorrectionCommand",
    "proj_project既有四属性列、proj_project_template_match_history",
    "模板前判定+原子创建+append-only历史+权限+幂等并发+响应式UI",
)
EXACT_PHASE1_DESIGN["PM-10"] = (
    "项目治理", "Project / ProjectStageSnapshot / ProjectMemberAssignment",
    "回退保持ACTIVE；异常关闭写EXCEPTION_CLOSED；仅异常关闭可受控重开", "ProjectTreeScope + 状态命令权限",
    "ProjectGovernanceApplicationService / ProjectGovernanceGuardApi",
    "proj_project、proj_project_stage_snapshot、proj_project_member_assignment",
    "状态守卫+完整树+跨域阻断+幂等并发+响应式UI",
)
EXACT_PHASE1_DESIGN["SOL-01"] = (
    "交付准备与方案 / 基础平台",
    "Preparation / DynamicFormTemplate / DynamicFormTemplateRevision / DynamicFormInstance",
    "PLT修订DRAFT→PUBLISHED且不可变；实例CAS；消费Context拥有提交/完成/审批/历史",
    "Owner业务范围 + PLT模板/实例权限",
    "消费Context应用服务 / DynamicFormBusinessInstanceApi",
    "Preparation、DynamicFormTemplateRevision、DynamicFormInstance、FileArtifact",
    "动态schema+值+文件组合+领域状态",
)
EXACT_PHASE1_DESIGN["PRE-04"] = (
    "交付准备与方案 / PLT动态表单组合",
    "Preparation / DynamicFormInstance",
    "SOL DRAFT→COMPLETED与草稿/有效版双轴；PLT实例冻结修订且不拥有业务状态",
    "ProjectStageScope + SOL Owner策略",
    "PreparationApplicationService / DynamicFormBusinessInstanceApi",
    "Preparation、DynamicFormInstance、FileArtifact",
    "业务规则+动态表单+版本+文件",
)


def domain_owners(requirements: list[dict[str, str]]) -> dict[str, tuple[str, str]]:
    result: dict[str, tuple[str, str]] = {}
    for item in requirements:
        identifier = item["id"]
        prefix = identifier.split("-")[0]
        exact = identifier if prefix == "INT" else prefix
        domain = PREFIX_OWNER.get(exact) or PREFIX_OWNER.get(prefix)
        if not domain:
            raise SystemExit(f"no PRD-derived domain owner rule: {identifier}")
        result[identifier] = (domain, DOMAIN_NAMES[domain])
    return result


def phase1_design(identifier: str, domain: str) -> tuple[str, ...]:
    return EXACT_PHASE1_DESIGN.get(identifier) or PHASE1_DESIGN[domain]


CROSS_CONTEXT_REQUIREMENT_IDS = {
    "ACC-02", "ACC-04", "ACC-06", "CLO-01", "CLO-02", "CUS-03", "CUT-01", "CUT-03", "CUT-06",
    "EQP-01", "EQP-02", "EQP-03", "EQP-04", "EXE-01", "EXE-02", "EXE-03", "EXE-04",
    "EXE-05", "EXE-06", "IMP-01", "INS-02", "INS-04", "INT-01", "INT-02", "INT-03",
    "INT-06", "INT-12", "SRV-01", "SUB-03",
}


def sds_reference(identifier: str) -> str:
    """Return stable, requirement-specific Phase 1 and Phase 2 SDS links."""
    if identifier == "PM-07":
        return " / ".join([
            "[01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链)",
            "[02领域](../design/02-domain-model.md)",
            "[02c Owner](../design/02c-data-ownership-matrix.md)",
            "[04模块](../design/04-module-design.md)",
            "[05状态](../design/05-state-machine.md#2-核心状态机)",
            "[07权限](../design/07-authorization-design.md#4-pm-07业务属性权限)",
            "[08数据](../design/08-data-model.md#41-pm-07属性判定与模板匹配历史)",
            "[09数据库](../design/09-database-design.md#45-pm-07模板匹配决策历史前向表)",
            "[10接口](../design/10-api-design.md#55-pm-07属性判定与匹配历史契约)",
            "[12集成](../design/12-integration-design.md#5-crm--erp项目客户合同订单与履约)",
            "[15并发](../design/15-cache-and-concurrency.md#56-pm-07属性修正与匹配历史并发)",
            "[16异常](../design/16-exception-and-idempotency.md)",
            "[P2契约](phase2-contract-map.md#pm-07)",
        ])
    references = [
        "[01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链)",
        "[02领域](../design/02-domain-model.md)",
        "[04模块](../design/04-module-design.md)",
        "[05状态](../design/05-state-machine.md#2-核心状态机)",
        "[06流程](../design/06-workflow-design.md#2-核心审批流)",
        "[07权限](../design/07-authorization-design.md#2-权限层次)",
    ]
    if identifier in CROSS_CONTEXT_REQUIREMENT_IDS:
        references.insert(2, "[02d契约](../design/02d-cross-context-contracts.md)")
    domain = PREFIX_OWNER.get(identifier) or PREFIX_OWNER.get(identifier.split("-")[0])
    phase2_sections = {
        "PROJ": ("4-project-delivery-数据模型", "4-project-delivery-表设计", "5-proj项目治理-api"),
        "SOL": ("5-preparation--solution-数据模型", "45-preparation--solution", "6-sol交付准备与方案-api"),
        "IMP": ("6-implementation-execution-数据模型", "6-implementation-execution-与-acceptance-表设计", "7-imp现场实施-api"),
        "ACC": ("7-acceptance--closure-数据模型", "6-implementation-execution-与-acceptance-表设计", "8-acc验收与项目闭环-api"),
        "CUT": ("8-cutoverinspection-与-service-operations", "7-cutoverinspection-与服务状态", "9-cut割接-api"),
        "SRV": ("8-cutoverinspection-与-service-operations", "7-cutoverinspection-与服务状态", "10-srv巡检与服务状态-api"),
        "CUS": ("9-customerassetcommerce-与-resource", "8-customercommerceresource-与-knowledge", "11-cusastcomres-与-kno-api"),
        "AST": ("9-customerassetcommerce-与-resource", "5-asset-设备归属与维保基本事实", "11-cusastcomres-与-kno-api"),
        "COM": ("9-customerassetcommerce-与-resource", "8-customercommerceresource-与-knowledge", "11-cusastcomres-与-kno-api"),
        "RES": ("9-customerassetcommerce-与-resource", "8-customercommerceresource-与-knowledge", "11-cusastcomres-与-kno-api"),
        "ANA": ("10-analytics基础平台与-knowledge-reference", "10-文件事件幂等和状态历史支撑表", "12-ana-与公共能力-api"),
        "PLT": ("10-analytics基础平台与-knowledge-reference", "10-文件事件幂等和状态历史支撑表", "12-ana-与公共能力-api"),
        "KNO": ("10-analytics基础平台与-knowledge-reference", "8-customercommerceresource-与-knowledge", "11-cusastcomres-与-kno-api"),
    }
    data_anchor, db_anchor, api_anchor = phase2_sections[domain]
    if identifier == "INT-12":
        data_anchor, db_anchor, api_anchor = (
            "11-device-access--collection-数据模型",
            "9-device-access--collection-关键表",
            "13-device-access--collection-api",
        )
    elif identifier == "PM-05":
        data_anchor, db_anchor, api_anchor = (
            "4-project-delivery-数据模型",
            "44-pm-05-转销与-pm-06-多期关系",
            "51-pm-05-借货项目转销契约",
        )
    elif identifier == "PM-06":
        data_anchor, db_anchor, api_anchor = (
            "4-project-delivery-数据模型",
            "44-pm-05-转销与-pm-06-多期关系",
            "52-pm-06-多期项目契约",
        )
    references.extend([
        f"[08数据](../design/08-data-model.md#{data_anchor})",
        f"[09数据库](../design/09-database-design.md#{db_anchor})",
        f"[10接口](../design/10-api-design.md#{api_anchor})",
    ])
    event_anchors: list[str] = []
    if identifier in {"PM-01", "PM-02", "PM-03", "PM-04", "PM-09", "PM-10", "PM-11", "PROJ-12"}:
        event_anchors.append("5-projectasset-与-analytics-事件")
    if identifier.startswith(("EXE-", "IMP-", "ACC-", "CLO-", "CUT-")):
        event_anchors.append("6-impacc-与-cut-事件")
    if identifier in {"EXE-03", "EXE-04", "CUT-06", "INS-02", "INS-04", "INT-12", "NFR-02"}:
        event_anchors.append("7-collection-事件链")
    if identifier.startswith(("WO-", "INS-", "SRV-")) or identifier == "INT-05":
        event_anchors.append("8-inspection-与-service-事件")
    if domain in {"CUS", "COM", "RES", "KNO"} or identifier in {"INT-01", "INT-02", "INT-03", "INT-04", "INT-06", "INT-07", "INT-10", "NFR-03"}:
        event_anchors.append("9-主数据商务资源与知识事件")
    if domain == "AST" and identifier not in {"INT-02", "INT-06"}:
        event_anchors.append("5-projectasset-与-analytics-事件")
    if domain == "ANA":
        event_anchors.append("5-projectasset-与-analytics-事件")
    if identifier in {"PLT-01", "PLT-02"}:
        event_anchors.append("10-文件与待办事件")
    for event_anchor in dict.fromkeys(event_anchors):
        references.append(f"[11事件](../design/11-event-design.md#{event_anchor})")
    integration_requirements = {"COM-01", "EQP-04", "CUT-08", "INS-05", "AUT-01", "AUT-02"}
    integration_requirements.add("PRE-03")
    if identifier.startswith("INT-") or identifier in integration_requirements:
        references.append("[12集成](../design/12-integration-design.md)")
    file_prefixes = ("PRE-", "PLN-", "SCH-", "SOL-", "EXE-", "IMP-", "ACC-", "CLO-", "CUT-", "WO-", "INS-", "RES-", "SUB-")
    file_requirements = {"PLT-02", "INT-06", "INT-07", "INT-12"}
    if identifier.startswith(file_prefixes) or identifier in file_requirements:
        references.append("[13文件](../design/13-file-design.md)")
    references.extend([
        "[15并发](../design/15-cache-and-concurrency.md)",
        "[16异常](../design/16-exception-and-idempotency.md)",
        f"[P2契约](phase2-contract-map.md#{identifier.lower()})",
    ])
    return " / ".join(references)


def task_states(task_root: Path) -> dict[str, dict[str, str]]:
    result: dict[str, dict[str, str]] = {}
    for path in sorted(task_root.glob("F-*.md")):
        feature_match = FEATURE_ID.match(path.stem)
        if not feature_match:
            continue
        feature_id = feature_match.group(1)
        if feature_id in result:
            raise SystemExit(f"duplicate Feature task record: {feature_id}")
        status_match = TASK_STATUS_LINE.search(read(path))
        if not status_match:
            raise SystemExit(f"Feature task has no machine-readable implementation status: {path}")
        raw_status = status_match.group(1).strip()
        if "IMPLEMENTATION_COMPLETE" in raw_status or "IMPLEMENTATION_DONE" in raw_status:
            status = "COMPLETE"
        elif "NOT_STARTED" in raw_status:
            status = "NOT_STARTED"
        else:
            status = "IN_PROGRESS"
        result[feature_id] = {
            "status": status,
            "raw_status": raw_status,
            "path": path.as_posix(),
        }
    return result


def feature_coverages(
    feature_root: Path,
    task_root: Path,
    valid_slice_keys: set[str],
) -> tuple[dict[str, list[dict[str, str]]], list[dict[str, object]]]:
    tasks = task_states(task_root)
    by_slice: dict[str, list[dict[str, str]]] = {key: [] for key in valid_slice_keys}
    features: list[dict[str, object]] = []
    seen_features: set[str] = set()
    for path in sorted(feature_root.glob("F-*.md")):
        coverage_match = FEATURE_COVERAGE_LINE.search(read(path))
        if not coverage_match:
            continue
        feature_match = FEATURE_ID.match(path.stem)
        if not feature_match:
            raise SystemExit(f"cannot derive Feature ID from coverage declaration: {path}")
        feature_id = feature_match.group(1)
        if feature_id in seen_features:
            raise SystemExit(f"duplicate Feature coverage declaration: {feature_id}")
        seen_features.add(feature_id)
        task = tasks.get(feature_id)
        mappings: list[dict[str, str]] = []
        for raw_item in re.split(r"[；;]", coverage_match.group(1)):
            value = raw_item.strip()
            if not value:
                continue
            item_match = FEATURE_COVERAGE_ITEM.fullmatch(value)
            if not item_match:
                raise SystemExit(f"invalid Feature coverage item in {path}: {value}")
            requirement_id, version, coverage = item_match.groups()
            slice_key = f"{requirement_id}@{version}"
            if slice_key not in valid_slice_keys:
                raise SystemExit(f"Feature coverage references unknown Requirement slice: {feature_id}: {slice_key}")
            if any(item["slice_key"] == slice_key for item in mappings):
                raise SystemExit(f"duplicate Feature coverage item: {feature_id}: {slice_key}")
            mapping = {
                "feature_id": feature_id,
                "slice_key": slice_key,
                "coverage": coverage,
                "feature_path": path.as_posix(),
                "task_path": task["path"] if task else "",
                "task_status": task["status"] if task else "NO_TASK",
                "task_status_raw": task["raw_status"] if task else "",
            }
            mappings.append(mapping)
            by_slice[slice_key].append(mapping)
        features.append(
            {
                "featureId": feature_id,
                "featurePath": path.as_posix(),
                "taskPath": task["path"] if task else None,
                "taskStatus": task["status"] if task else "NO_TASK",
                "taskStatusRaw": task["raw_status"] if task else None,
                "mappings": [
                    {"sliceKey": item["slice_key"], "coverage": item["coverage"]}
                    for item in mappings
                ],
            }
        )
    return by_slice, features


def derived_status(mappings: list[dict[str, str]]) -> str:
    completed = [item for item in mappings if item["task_status"] == "COMPLETE"]
    if any(item["coverage"] == "FULL" for item in completed):
        return "IMPLEMENTATION_COMPLETE"
    if completed:
        return "IMPLEMENTATION_PARTIAL"
    return "NOT_STARTED"


def feature_links(mappings: list[dict[str, str]]) -> str:
    if not mappings:
        return "NOT_STARTED"
    return " / ".join(
        f"[{item['feature_id']}](../../specs/features/{Path(item['feature_path']).name})"
        f"（{item['coverage']}）"
        for item in mappings
    )


def evidence_links(mappings: list[dict[str, str]]) -> str:
    if not mappings:
        return "NOT_STARTED"
    values: list[str] = []
    for item in mappings:
        if item["task_path"]:
            values.append(
                f"[{item['feature_id']} Task](../../tasks/features/{Path(item['task_path']).name})"
                f"（{item['task_status']}）"
            )
        else:
            values.append(f"{item['feature_id']}（NO_TASK，不派生完成）")
    return " / ".join(values)


def render(
    prd: Path,
    domain_root: Path,
    feature_root: Path,
    task_root: Path,
) -> tuple[str, str]:
    if not domain_root.is_dir():
        raise SystemExit(f"domain specification root not found: {domain_root}")
    prd_text = read(prd)
    requirements = extract_requirements(prd_text)
    requirement_by_id = {item["id"]: item for item in requirements}
    slices = extract_version_slices(prd_text, requirements)
    owners = domain_owners(requirements)
    missing = [item["id"] for item in requirements if item["id"] not in owners]
    if missing:
        raise SystemExit(f"missing domain owner: {', '.join(missing)}")
    coverage_by_slice, feature_records = feature_coverages(
        feature_root,
        task_root,
        {item["key"] for item in slices},
    )
    requirement_counts = Counter(item["version"] for item in requirements)
    slice_counts = Counter(item["version"] for item in slices)
    status_counts: Counter[str] = Counter()
    coverage_slices: list[dict[str, object]] = []
    for slice_item in slices:
        requirement = requirement_by_id[slice_item["requirement_id"]]
        domain, owner = owners[requirement["id"]]
        mappings = coverage_by_slice[slice_item["key"]]
        status = derived_status(mappings)
        status_counts[status] += 1
        coverage_slices.append(
            {
                "sliceKey": slice_item["key"],
                "requirementId": requirement["id"],
                "requirementName": requirement["name"],
                "version": slice_item["version"],
                "owner": domain,
                "ownerName": owner,
                "businessResult": slice_item["business_result"],
                "versionBoundary": slice_item["boundary"],
                "implementationStatus": status,
                "features": [
                    {
                        "featureId": item["feature_id"],
                        "coverage": item["coverage"],
                        "taskStatus": item["task_status"],
                        "featurePath": item["feature_path"],
                        "taskPath": item["task_path"] or None,
                    }
                    for item in mappings
                ],
            }
        )
    coverage_document = {
        "schemaVersion": 1,
        "baseline": "PRD V1.8 / CHG-PRD-2026-08-30-008",
        "sources": {
            "prd": "需求/PRD-项目实施交付管理平台.md",
            "featureCoverage": "specs/features/F-*.md#Requirement切片覆盖",
            "taskStatus": "tasks/features/F-*.md#Feature实施状态",
        },
        "counts": {
            "requirements": len(requirements),
            "mainVersions": {"V1": requirement_counts["V1"], "V2": requirement_counts["V2"]},
            "versionSlices": len(slices),
            "slicesByVersion": {"V1": slice_counts["V1"], "V2": slice_counts["V2"]},
            "slicesByImplementationStatus": dict(sorted(status_counts.items())),
        },
        "derivationRules": [
            "Feature Spec FULL + authoritative completed task => IMPLEMENTATION_COMPLETE",
            "Feature Spec PARTIAL + authoritative completed task => IMPLEMENTATION_PARTIAL",
            "No completed authoritative task mapping => NOT_STARTED",
            "Feature association, dependency and support text do not create coverage",
        ],
        "features": feature_records,
        "slices": coverage_slices,
    }
    lines = [
        "# V1.8需求追溯矩阵",
        "",
        "> 本文件是111个正式Requirement目标版本切片到工程资产的自动派生索引，不复制PRD正文。Owner按PRD V1.8业务事实和数据责任推导；旧specs不参与Owner生成。",
        "> 源基线：`需求/PRD-项目实施交付管理平台.md` V1.8修订008；领域决策：`docs/design/phase-1-domain-ownership.md`；结构化同源投影：`docs/traceability/requirement-version-coverage.json`。",
        "> 批准增量：`CHG-PRD-2026-08-21-001`（PM-01、PM-03手动创建失败不持久化Project或创建草稿）。",
        "> 批准增量：`CHG-PRD-2026-08-23-002`（PM-01、PM-08、EXE-02、EQP-01、CUS-01、INT-09组织主数据与AST地点所有权）。",
        "> 批准增量：`CHG-PRD-2026-08-25-003`（PM-07模板匹配决策历史与影响识别最小边界）。",
        "> 批准增量：`CHG-PRD-2026-08-27-004`（PLT-02文件安全扫描默认关闭；关闭时真实`SKIPPED`，开启时失败关闭）。",
        "> 批准修订：`CHG-PRD-2026-08-28-005`（59项需求方裁决正式入稿，统一角色权限、数据状态、引用追溯与表达边界）。",
        "> 批准修订：`CHG-PRD-2026-08-29-006`（关闭PRE-04选择性映射歧义，完整回写修订004扫描规则，并恢复完整目录及清理已解决重复说明）。",
        "> 批准修订：`CHG-PRD-2026-08-29-007`（完成100项Requirement版本复核，建立111个正式目标版本切片及Feature/Task自动派生边界）。",
        "> 批准修订：`CHG-PRD-2026-08-30-008`（五类双机部署规范性检查按17/25/23/24/8明细校准为97项；124不再作为发布或验收数量）。",
        "> V1.6旧编号、并入、后置和重编号关系：`docs/traceability/business-feedback-change-map.md`。",
        "> Feature覆盖只读取Feature Spec的`Requirement切片覆盖`声明；实施状态只读取对应Feature任务记录。关联、支撑、依赖、历史说明和脚本人工例外均不产生完成状态。",
        "",
        f"- 正式需求：{len(requirements)}项（主版本V1 {requirement_counts['V1']}项，V2 {requirement_counts['V2']}项）",
        f"- 正式目标版本切片：{len(slices)}个（V1 {slice_counts['V1']}个，V2 {slice_counts['V2']}个）",
        "- 领域Owner：13个PRD-derived映射，一项正式需求唯一归属一个Owner",
        "- 当前状态：PRD V1.8修订008重新基线化；既有Feature实施结论只关闭其机器可读声明覆盖的切片或子闭环，不因Feature完成自动关闭整个Requirement",
        "- 当前规格阻断：无；VS-001～VS-011均已裁决关闭，配置基础前置原则适用，但正文明确V2、V3或延后的内容保持原版本",
        "",
        "## 字段状态约定",
        "",
        "| 状态 | 含义 |",
        "|---|---|",
        "| `IMPLEMENTATION_COMPLETE` | 当前Requirement目标版本切片的已知业务义务均已映射，且全部必需Feature已完成；不代表Deployment、SIT、UAT或Release通过 |",
        "| `IMPLEMENTATION_PARTIAL` | 至少一个合法Feature子闭环已完成，但该Requirement目标版本切片仍有未完成或未映射业务义务 |",
        "| `NOT_STARTED` | 没有已完成的权威Feature任务覆盖；可包含未启动Feature、缺任务记录的Feature或尚未声明覆盖的切片，不代表需求缺失 |",
        "| `BLOCKED_BY_SPEC` | 存在业务语义冲突，必须回到CHG-01或决策记录 |",
        "| `BLOCKED_BY_EVIDENCE` | 缺少数据、接口、迁移或测试证据 |",
        "",
        "## V1.8批准增量002追溯",
        "",
        "| 增量范围 | 关联Requirement | 增量契约 | 正式设计与决策 |",
        "|---|---|---|---|",
        "| 项目创建与指派 | PM-01、PM-08 | 公司/办事处部门同一范围校验；项目多站点；V1按区划映射提示并人工确认服务经理 | 04模块、07权限、08数据、09数据库、10 API、ADR-0033、F-PROJ-001 |",
        "| 安装与设备地点 | EXE-02、EQP-01 | 工勘/安装维护结构化地点；安装/迁移/拆除确认后驱动设备当前位置 | 02d契约、04模块、08数据、09数据库、10 API、ADR-0033 |",
        "| 客户地点引用 | CUS-01 | CUS只引用AST Address/Site，不拥有物理地点 | 02c Owner、04模块、08数据、10 API、ADR-0033 |",
        "| 组织主数据 | INT-09 | Company与Department独立；`system_dept.code`；用户公司—部门同一有效范围行 | 04模块、07权限、08数据、09数据库、10 API、ADR-0033 |",
        "",
        "## 正式需求追溯",
        "",
        "| Requirement切片 | 名称 | 切片业务结果 | 版本边界 | Owner | 业务模块 | 聚合 | 状态机/工作流 | 权限模型 | 计划API | 计划数据对象 | 测试类别 | 所属阶段 | 版本 | 优先级 | 来源追溯 | SDS | Feature覆盖 | Task证据 | Release | 自动派生实施状态 |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for slice_item in slices:
        item = requirement_by_id[slice_item["requirement_id"]]
        domain, owner = owners[item["id"]]
        module, aggregate, lifecycle, permission, api, data, test_category = phase1_design(item["id"], domain)
        mappings = coverage_by_slice[slice_item["key"]]
        status = derived_status(mappings)
        owner_label = "PROJ（项目治理；INT传输后置）" if item["id"] == "PM-07" else f"{domain}（{owner}）"
        values = [
            slice_item["key"], item["name"], slice_item["business_result"], slice_item["boundary"],
            owner_label, module, aggregate, lifecycle, permission, api, data, test_category,
            item["stage"], slice_item["version"], item["priority"], item["source"],
            sds_reference(item["id"]), feature_links(mappings), evidence_links(mappings),
            "NOT_STARTED", status,
        ]
        lines.append("| " + " | ".join(value.replace("|", "\\|") for value in values) + " |")
    return (
        "\n".join(lines) + "\n",
        json.dumps(coverage_document, ensure_ascii=False, indent=2) + "\n",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prd", type=Path, required=True)
    parser.add_argument("--domains", type=Path, required=True)
    parser.add_argument("--features", type=Path, default=Path("specs/features"))
    parser.add_argument("--tasks", type=Path, default=Path("tasks/features"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--coverage-output", type=Path)
    parser.add_argument("--check", action="store_true", help="compare generated content without writing")
    args = parser.parse_args()
    coverage_output = args.coverage_output or args.output.with_name("requirement-version-coverage.json")
    generated_matrix, generated_coverage = render(
        args.prd,
        args.domains,
        args.features,
        args.tasks,
    )
    if args.check:
        if not args.output.is_file():
            print(f"[FAIL] DRIFT: missing generated output {args.output}")
            return 1
        if not coverage_output.is_file():
            print(f"[FAIL] DRIFT: missing generated output {coverage_output}")
            return 1
        if read(args.output) != generated_matrix:
            print(f"[FAIL] DRIFT: {args.output} does not match generator-owned content")
            return 1
        if read(coverage_output) != generated_coverage:
            print(f"[FAIL] DRIFT: {coverage_output} does not match generator-owned content")
            return 1
        print(f"[PASS] requirement traceability is current: {args.output} / {coverage_output}")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    coverage_output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(generated_matrix, encoding="utf-8", newline="\n")
    coverage_output.write_text(generated_coverage, encoding="utf-8", newline="\n")
    print(f"WROTE {args.output}")
    print(f"WROTE {coverage_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
