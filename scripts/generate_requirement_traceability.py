#!/usr/bin/env python3
"""Generate the V1.7 requirement-to-engineering traceability index and SDS mappings."""

from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path


REQ_ID = re.compile(r"^[A-Z]+(?:-[A-Z0-9]+)?-\d+$")
REQ_HEADER = re.compile(r"^#{3,4}\s+(?:\d+(?:\.\d+)*\s+)?([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s+(.+?)\s*$")
INDEX_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|", re.M)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def fields(block: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in block.splitlines():
        match = re.match(r"^\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*$", line)
        if match:
            result[match.group(1).strip()] = match.group(2).strip()
    return result


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

    requirements: list[dict[str, str]] = []
    for match in index_rows:
        identifier = match.group(1).strip()
        name = match.group(2).strip()
        version = match.group(4).strip()
        if version not in {"V1", "V2"}:
            # A.1 may use a compound target version such as “V1手动指派；V2...”.
            version = "V1" if version.startswith("V1") else "V2" if version.startswith("V2") else ""
        marker = f"| 需求编号 | {identifier} |"
        marker_index = text.find(marker)
        if marker_index < 0:
            raise SystemExit(f"formal requirement body not found: {identifier}")
        previous_marker = text.rfind("\n| 需求编号 | ", 0, marker_index)
        # Include the nearest requirement heading, when present, for source context.
        heading_index = text.rfind("\n#### ", 0, marker_index)
        if heading_index < previous_marker:
            heading_index = text.rfind("\n### ", 0, marker_index)
        start = heading_index + 1 if heading_index >= 0 else marker_index
        next_marker = text.find("\n| 需求编号 | ", marker_index + len(marker))
        end = next_marker if next_marker >= 0 else len(text)
        block = text[start:end]
        value = fields(block)
        requirements.append(
            {
                "id": identifier,
                "name": name,
                "stage": value.get("所属阶段", ""),
                "priority": value.get("优先级", ""),
                "version": version,
                "roles": value.get("用户角色", ""),
                "source": value.get("来源追溯", ""),
            }
        )
    if len(requirements) != 103:
        raise SystemExit(f"Appendix A.1 formal requirement count is {len(requirements)}, expected 103")
    return requirements


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
    "PROJ": ("项目治理", "Project / ProjectTask / ProjectTemplate", "Project或Task状态机；模板加载工作流", "ProjectTreeScope", "ProjectApplicationService", "Project、ProjectTask、ProjectTemplate", "业务规则+权限+树查询"),
    "SOL": ("交付准备与方案", "Preparation / ConstructionPlan / Solution", "Plan或Solution状态机；计划/方案审批流", "ProjectStageScope", "PreparationApplicationService", "Preparation、Plan、Solution、File", "业务规则+审批+文件"),
    "IMP": ("实施执行", "ArrivalAcceptance / InstallationRecord / ConfigurationCollectionResult / JointDebuggingResult / ImplementationRisk / ImplementationQualityCheck / ImplementationSafetyCheck / DeliveryEvidence", "实施执行聚合状态机；质量/安全整改复核工作流；阶段门禁工作流", "ImplementationProjectBatchScope", "ImplementationExecutionApplicationService", "ArrivalAcceptance、InstallationRecord、ConfigurationCollectionResult、JointDebuggingResult、ImplementationRisk、ImplementationQualityCheck、ImplementationSafetyCheck、DeliveryEvidence", "业务规则+证据+权限+整改"),
    "ACC": ("验收与项目闭环", "Acceptance / SatisfactionCollection / Artifact / ProjectClosure", "满意度、Artifact与ProjectClosure状态机；验收/闭环审批流", "ProjectStageScope", "AcceptanceApplicationService", "Acceptance、SatisfactionCollection、DeliveryArtifact、ProjectClosure、ServiceHandover", "业务规则+审批+门禁"),
    "CUT": ("变更切换与稳定治理", "CutoverTask / CutoverAssessment / CutoverPlan / CutoverClosure", "CutoverTask阶段状态机；P5分级审批与P6闭环归档流", "CutoverTaskScope", "CutoverApplicationService", "CutoverTask、CutoverAssessment、CutoverPlan、CutoverClosure", "业务规则+审批+闭环+幂等"),
    "SRV": ("Inspection / Service Operations", "InspectionTask / ServiceIssue / ServiceStatus", "Inspection与Service Operations分别维护状态机和闭环流", "AssignedProjectDeviceScope", "ServiceApplicationService", "InspectionTask、InspectionRule、ServiceIssue、ServiceStatus", "业务规则+权限+异常"),
    "CUS": ("Customer & Relationship", "Customer / Contact / AssetRelation", "Customer同步状态机；主数据同步流", "OrganizationCustomerScope", "CustomerApplicationService", "Customer、Contact、AssetRelation、CustomerSyncSnapshot", "数据同步+权限"),
    "AST": ("Asset Management", "Device / DeviceArchive / RMAReplacement", "Device服务状态机；设备同步流", "ProjectDeviceScope", "AssetApplicationService", "Device、DeviceArchive、MaintenanceFact、RMAReplacement、AssetSyncSnapshot", "数据一致性+归属+安全"),
    "COM": ("合同订单履约", "Contract / OrderLine / DeliveryScope / DeliveryScopeDetail", "Scope状态机；履约回写工作流", "ContractProjectScope", "ContractApplicationService", "Contract、OrderLine、DeliveryScope、DeliveryScopeDetail、FulfillmentRecord", "数量约束+对账+幂等"),
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
    "IMP-02": ("实施执行", "ImplementationSafetyCheck", "ImplementationSafetyCheck状态机；提交→复核→整改/豁免", "ImplementationProjectBatchScope", "ImplementationSafetyApplicationService", "ImplementationSafetyCheck、SafetyRemediation、SafetyExemption", "安全阻断+豁免审批+审计"),
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
    "Service Operations", "ServiceStatus / ServiceHandover",
    "ServiceStatus状态机；服务状态同步与提示流", "ProjectDeviceScope",
    "ServiceOperationsApplicationService", "ServiceStatus、ServiceHandover、DeviceServiceSnapshot",
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
for _identifier in ("COM-01", "COM-02"):
    EXACT_PHASE1_DESIGN[_identifier] = (
        "Contract & Fulfillment", "Contract / SalesOrder / OrderLine / DeliveryScope / DeliveryScopeDetail / FulfillmentSnapshot",
        "Contract/Order同步状态机；范围分配与履约对账流", "ContractProjectScope",
        "ContractApplicationService", "Contract、SalesOrder、OrderLine、DeliveryScope、DeliveryScopeDetail、FulfillmentSnapshot、ReconciliationRecord",
        "数量约束+同步版本+对账幂等",
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


def sds_reference(identifier: str) -> str:
    """Return stable, requirement-specific Phase 1 and Phase 2 SDS links."""
    references = [
        "[01追溯](../design/01-requirement-traceability.md#2-phase-1-追溯链)",
        "[02领域](../design/02-domain-model.md)",
        "[04模块](../design/04-module-design.md)",
        "[05状态](../design/05-state-machine.md#2-核心状态机)",
        "[06流程](../design/06-workflow-design.md#2-核心审批流)",
        "[07权限](../design/07-authorization-design.md#2-权限层次)",
    ]
    if identifier.startswith(("EXE-", "IMP-", "CUT-", "INS-", "INT-")):
        references.insert(2, "[02d契约](../design/02d-cross-context-contracts.md)")
    domain = PREFIX_OWNER.get(identifier) or PREFIX_OWNER.get(identifier.split("-")[0])
    phase2_sections = {
        "PROJ": ("4-project-delivery-数据模型", "4-project-delivery-表设计", "5-proj项目治理-api"),
        "SOL": ("5-preparation--solution-数据模型", "45-preparation--solution", "6-sol交付准备与方案-api"),
        "IMP": ("6-implementation-execution-数据模型", "6-implementation-execution-与-acceptance-表设计", "7-imp现场实施-api"),
        "ACC": ("7-acceptance--closure-数据模型", "6-implementation-execution-与-acceptance-表设计", "8-acc验收与项目闭环-api"),
        "CUT": ("8-cutoverinspection-与-service-operations", "7-cutoverinspection-与服务状态", "9-cut割接-api"),
        "SRV": ("8-cutoverinspection-与-service-operations", "7-cutoverinspection-与服务状态", "10-srv巡检服务状态与历史资料-api"),
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
    integration_requirements = {"COM-01", "COM-02", "EQP-04", "CUT-08", "INS-05", "AUT-01", "AUT-02"}
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


def render(prd: Path, domain_root: Path) -> str:
    requirements = extract_requirements(read(prd))
    owners = domain_owners(requirements)
    missing = [item["id"] for item in requirements if item["id"] not in owners]
    if missing:
        raise SystemExit(f"missing domain owner: {', '.join(missing)}")
    counts = Counter(item["version"] for item in requirements)
    lines = [
        "# V1.7需求追溯矩阵",
        "",
        "> 本文件是需求到工程资产的索引，不复制PRD正文。Owner按PRD V1.7业务事实和数据责任推导；旧specs不参与生成。SDS、Feature、API、数据和测试列在对应阶段生成后更新。",
        "> 源基线：`需求/PRD-项目实施交付管理平台.md` V1.7；领域决策：`docs/design/phase-1-domain-ownership.md`。",
        "> V1.6旧编号、并入、后置和重编号关系：`docs/traceability/business-feedback-change-map.md`。",
        "",
        f"- 正式需求：{len(requirements)}项（V1 {counts['V1']}项，V2 {counts['V2']}项）",
        "- 领域Owner：13个PRD-derived映射，一项正式需求唯一归属一个Owner",
        "- 当前状态：SDS Phase 1与Phase 2均已通过独立复审并转为BASELINE；SDS Phase 3处于IN_REVIEW，尚未达到BASELINE",
        "",
        "## 字段状态约定",
        "",
        "| 状态 | 含义 |",
        "|---|---|",
        "| `BASELINE` | 已纳入PRD V1.7正式基线 |",
        "| `NOT_STARTED` | 下游工程资产尚未生成，不代表需求缺失 |",
        "| `BLOCKED_BY_SPEC` | 存在业务语义冲突，必须回到CHG-01或决策记录 |",
        "| `BLOCKED_BY_EVIDENCE` | 缺少数据、接口、迁移或测试证据 |",
        "",
        "## 正式需求追溯",
        "",
        "| Requirement | 名称 | Owner | 业务模块 | 聚合 | 状态机/工作流 | 权限模型 | 计划API | 计划数据对象 | 测试类别 | 所属阶段 | 版本 | 优先级 | 来源追溯 | SDS | Feature | Evidence | Release | 状态 |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for item in requirements:
        domain, owner = owners[item["id"]]
        module, aggregate, lifecycle, permission, api, data, test_category = phase1_design(item["id"], domain)
        values = [
            item["id"], item["name"], f"{domain}（{owner}）", module, aggregate, lifecycle,
            permission, api, data, test_category, item["stage"], item["version"], item["priority"],
            item["source"], sds_reference(item["id"]), "NOT_STARTED", "PRD/SDS-P1-BASELINE/SDS-P2-BASELINE", "NOT_STARTED", "BASELINE",
        ]
        lines.append("| " + " | ".join(value.replace("|", "\\|") for value in values) + " |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prd", type=Path, required=True)
    parser.add_argument("--domains", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(args.prd, args.domains), encoding="utf-8", newline="\n")
    print(f"WROTE {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
