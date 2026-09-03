#!/usr/bin/env python3
"""Validate Phase 2 SDS completeness and requirement traceability links."""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path


PHASE2_DOCS = (
    "08-data-model.md",
    "08a-domain-entity-migration-alignment.md",
    "09-database-design.md",
    "10-api-design.md",
    "11-event-design.md",
    "12-integration-design.md",
    "13-file-design.md",
    "15-cache-and-concurrency.md",
    "16-exception-and-idempotency.md",
)
EXPECTED_REQUIREMENT_COUNT = 103
EXPECTED_SCOPE_COUNTS = {"V1": 55, "V2": 48, "V1/V2": 103, "V3": 30, "OUT_OF_SCOPE": 9}
SCOPE_STATISTICS_MARKER = (
    "范围统计：V1 55 项、V2 48 项、V1/V2 103 项；"
    "V3 30 项、OUT_OF_SCOPE 9 项。"
)
REQUIREMENT_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)(?:@V[12])?\s*\|", re.M)
VERSION_SLICE_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+@V[12])\s*\|", re.M)
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*$", re.M)
CONTRACT_HEADING = re.compile(r"^###\s+([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*$", re.M)
CONTRACT_FIELD = re.compile(r"^-\s+(需求名称|数据对象|数据表|API|事件|外部集成|文件契约|工作流/状态|授权与数据范围)：(.+?)\s*$", re.M)
CONTRACT_FIELDS = (
    "需求名称", "数据对象", "数据表", "API", "事件", "外部集成",
    "文件契约", "工作流/状态", "授权与数据范围",
)
FULL_REQUIREMENT_ID = re.compile(r"[A-Z]+(?:-[A-Z0-9]+)?-\d+")
ACTIVE_REQUIREMENT_LINE = re.compile(r"^(?:>\s*)?(?:适用\s+)?Requirement(?: ID)?：(.+?)\s*$", re.M)
PRD_REQUIREMENT_ROW = re.compile(r"^\|\s*需求编号\s*\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*$", re.M)
NON_ACTIVE_ROW_LABELS = {"历史排除"}
NON_ACTIVE_FIELD_NAMES = {
    "disposition", "status", "处置", "迁移处置", "范围状态", "契约状态", "证据状态",
}
NON_ACTIVE_FIELD_VALUES = {
    "EXCLUDED", "COMPATIBILITY_ONLY",
    "历史排除", "不属于当前", "不进入当前",
}
DEFERRED_PHASE3_MARKERS = {
    "13-file-design.md": "| 保留期限和灾备数值 | DEFERRED_TO_PHASE_3 |",
    "15-cache-and-concurrency.md": "| 容量和 TTL 数值 | DEFERRED_TO_PHASE_3 |",
}
FORBIDDEN_HISTORICAL_USER_APIS = (
    "/historical-work-orders",
    "/historical-time-records",
)

V18_PHYSICAL_CARRIER_TABLES = (
    "proj_project_template_task_definition",
    "proj_project_task_execution_contract",
    "proj_project_task_completion_evaluation",
    "cut_cutover_checklist",
    "cut_cutover_checklist_item",
    "cut_cutover_checklist_item_result",
)
V18_PHYSICAL_CARRIER_OBJECTS = (
    "TaskWorkBinding",
    "TaskCompletionRule",
    "TaskCompletionEvaluation",
    "CutoverChecklist",
)
FCOM001_V70_REQUIRED_TARGET_MAPPINGS = {
    "OrderLine": {
        "com_sales_order_line.status": "APPROVED_CONSTANT:ENABLED;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
    },
    "DeliveryScope": {
        "com_delivery_scope.project_code": "proj_project.project_code:EXACT_SAME_TENANT_VERSION;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.order_source_system": "com_sales_order_line.source_system:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.order_company_code": "com_sales_order_line.company_code:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.order_type": "com_sales_order_line.order_type:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.order_no": "com_sales_order_line.order_no:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.line_no": "com_sales_order_line.line_no:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.allocation_source": "APPROVED_CONSTANT:LEGACY;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
        "com_delivery_scope.status": "APPROVED_CONSTANT:ENABLED;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
    },
    "DeliveryScopeDetail": {
        "com_delivery_scope_detail.detail_sequence": "ROW_NUMBER() OVER (PARTITION BY tenant_id,delivery_scope_id ORDER BY id) ON FROZEN_INPUT_WATERMARK;FAIL_BATCH_ON_OVERFLOW_OR_INPUT_CHANGE",
    },
}
FCOM001_ACCEPTANCE_STAGE_REQUIRED_SNIPPETS = {
    "docs/design/08-data-model.md": (
        "项目验收阶段快照对DeliveryScope及其分配版本的锁定事实",
        "独立于初验/终验报告",
        "Q-FCOM-002`关闭前不自动关闭或解锁",
    ),
    "docs/design/09-database-design.md": (
        "project_stage_snapshot_id bigint NOT NULL",
        "binding_trigger varchar(32) NOT NULL",
        "uk(tenant_id, project_id, project_stage_snapshot_id, delivery_scope_id, scope_allocation_version)",
        "不含`acceptance_id`",
    ),
    "docs/design/10-api-design.md": (
        "ProjectAcceptanceStageFactApi.lockAndRead(query)",
        "DeliveryScopeAcceptanceLockApi.lockCurrentByProject(command)",
        "AcceptanceScopeBindingApi.bindForStageEntry(command)",
        "AcceptanceScopeBindingApi.bindEffectiveScope(command)",
        "PROJ项目当前行→COM订单行（适用时）→COM `DeliveryScope`当前行（按稳定ID）→ACC `AcceptanceScopeBinding`",
    ),
    "docs/design/11-event-design.md": (
        "事件只作提交后通知/投影，不触发、不补建也不反推`AcceptanceScopeBinding`",
    ),
    "docs/design/15-cache-and-concurrency.md": (
        "统一锁顺序为PROJ项目当前行→COM订单行（适用时）→COM范围当前行（稳定ID）→ACC绑定",
        "初验/终验报告行不进入该锁链",
    ),
    "docs/design/16-exception-and-idempotency.md": (
        "阶段快照、绑定和`current_stage`整体回滚",
        "新范围版本、历史切换、Outbox和绑定整体回滚",
        "对应验收活动完成命令返回BUSINESS_GATE",
    ),
    "docs/traceability/phase2-contract-map.md": (
        "ProjectAcceptanceStageFactApi",
        "AcceptanceScopeBindingApi",
        "ProjectStageChanged仅作提交后通知，不触发绑定",
    ),
    "docs/traceability/domain-entity-migration-contract.json": (
        "bind ProjectStageSnapshot plus exact DeliveryScope allocation version",
        "never create or infer bindings from preliminary/final Acceptance reports",
        "Q-FCOM-002 forbids automatic close or unlock",
    ),
}
FCOM001_CONTRACT_ADMIN_SCOPE_REQUIRED_SNIPPETS = {
    "docs/design/02d-cross-context-contracts.md": (
        "OrganizationScopeApi.getActiveScopes(userId)",
        "COM只按`companyCode`与ERP合同所属公司编码精确匹配",
    ),
    "docs/design/07-authorization-design.md": (
        "COM-01合同管理员公司范围",
        "pms:commerce:contract:sensitive-read",
        "AuditRecord.authorizationSnapshot",
        "列表返回空并记录Owner不可用审计",
    ),
    "docs/design/09-database-design.md": (
        "F-COM-001合同管理员授权物理结论",
        "NO_PHYSICAL_DELTA",
        "不新增合同授权表",
    ),
    "docs/design/10-api-design.md": (
        "OrganizationScopeApi.getActiveScopes(subjectUserId)",
        "SQL必须保持精确字符串相等",
        "pms:commerce:contract:sensitive-read",
        "重验失败不写关系、成功幂等、Outbox或成功审计",
    ),
    "docs/design/14-security-design.md": (
        "SYSTEM当前有效UserCompanyDepartmentScope",
        "空范围/Owner不可用时列表空、详情和写拒绝",
    ),
    "docs/design/15-cache-and-concurrency.md": (
        "合同列表、详情和项目—合同关系维护不使用正向公司范围缓存",
        "OrganizationScopeApi.getActiveScopes",
    ),
    "docs/design/16-exception-and-idempotency.md": (
        "CONTRACT_SCOPE_OWNER_UNAVAILABLE",
        "同一幂等键后续重试必须重新读取当前scope",
    ),
    "docs/decisions/0038-commerce-contract-administrator-company-scope.md": (
        "ACCEPTED",
        "不修改Yudao基础平台",
        "NO_PHYSICAL_DELTA",
    ),
}
FCOM001_CONTRACT_ADMIN_SCOPE_FORBIDDEN_SNIPPETS = {
    "docs/design/10-api-design.md": (
        "【BLOCKED_BY_SPEC：Q-FCOM-001】",
        "Q-FCOM-001关闭前不可实施",
    ),
    "docs/design/14-security-design.md": (
        "Q-FCOM-001关闭前合同管理员查询和关联写入保持`BLOCKED_BY_SPEC`",
    ),
}
FCOM001_ACCEPTANCE_STAGE_FORBIDDEN_SNIPPETS = {
    "docs/design/08-data-model.md": (
        "验收单对DeliveryScope及其分配版本的锁定事实",
        "退出或作废时关闭区间",
    ),
    "docs/design/09-database-design.md": (
        "`acceptance_id bigint NOT NULL`、`project_id bigint NOT NULL`、`delivery_scope_id bigint NOT NULL`",
        "uk(tenant_id, acceptance_id, delivery_scope_id, scope_allocation_version)",
    ),
    "docs/design/15-cache-and-concurrency.md": (
        "统一锁顺序为COM范围→ACC绑定",
    ),
}
FACC001_REPORT_CONTRACT_REQUIRED_SNIPPETS = {
    "docs/design/08-data-model.md": (
        "活动根以PROJ ProjectTask/WorkBinding为唯一外部身份",
        "`acc_project_deliverable`应交实例",
        "归档失败保留报告版本且标记待补偿",
        "artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/sha256",
        "发布时认证用户作为不可变归档操作者",
    ),
    "docs/design/08a-domain-entity-migration-alignment.md": (
        "COMPATIBILITY_ONLY+NONE_NEW+FEATURE_FORWARD_MIGRATION",
        "不得由名称、任务名或`D-ACCEPT-REPORT`推断类型",
    ),
    "docs/design/09-database-design.md": (
        "F-ACC-001的P3-E09聚焦差量为`FEATURE_FORWARD_DELTA_REQUIRED`",
        "`acc_acceptance_report_version`",
        "`publisher_user_id bigint NULL`",
        "`acc_acceptance_report_attachment`",
        "report_status='EFFECTIVE' and effective_to is null",
        "relation_status='CURRENT' then 1 else null",
        "`acc_project_deliverable_source_version`",
        "`acc_project_deliverable_source_attachment`",
        "`D-INITIAL-REPORT/D-FINAL-REPORT`",
        "不保存PLT内部`FileVersion.id/FileReference.id`",
        "两项均处`DONE/CLOSED`时该项目全部保持旧契约和历史且不创建活动",
        "终态/非终态混合或未知状态整批失败",
    ),
    "docs/design/10-api-design.md": (
        "AcceptanceActivityCompletionFactApi.lockAndComplete",
        "AcceptanceActivityInitializationApi.initialize",
        "FileArtifactApi.archiveReferenceSets",
        "ArchiveFileReferenceSetsCommand",
        "PermissionApi.hasAnyPermissions(actorUserId, \"pms:file:archive\")",
        "ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}/ACCEPTANCE_REPORT_ATTACHMENT",
        "ExistingFileReferenceTarget`加性支持唯一ACC目标",
        "ACCEPTANCE_REPORT_ARCHIVE",
        "报告附件ACTIVE引用保持不变",
        "`pms-module-project-api`的ACC契约包",
        "既有`pms:file:archive`服务端权限和租户校验",
        "`revoke-current-version`",
        "pms:acceptance:report:query/write/complete/download",
        "当前来源保持`PENDING_COMPENSATION`并重试",
        "两项均为`DONE/CLOSED`时整项目保持旧契约和历史且不创建活动",
        "终态/非终态混合时整批失败",
    ),
    "docs/design/11-event-design.md": (
        "AcceptanceReportVersionChanged",
        "publisherActorUserId",
        "AcceptanceReportOutboxDeliveryJob",
        "PlatformCommandExecutionApi",
        "PlatformOutboxDeliveryApi",
        "只领取`AcceptanceReportVersionChanged`",
        "不得领取或标记`ClosureGateRecheckRequested`已投递",
        "changeType(`EFFECTIVE/REPLACED/REVOKED`)",
        "attachments[{sequence,artifactId,versionNo,referenceKey,fileFactVersion,scopeVersion,sha256}]",
        "ClosureGateRecheckRequested",
        "不触发范围绑定",
    ),
    "docs/design/13-file-design.md": (
        "ACC/ACCEPTANCE_REPORT_VERSION/{reportVersionId}/ACCEPTANCE_REPORT_ATTACHMENT",
        "archiveReferenceSets",
        "发布时冻结的`publisherActorUserId`",
        "PLT `FileArchiveRecord`是文件归档真值",
        "报告附件引用持续保持`ACTIVE`",
        "ACCEPTANCE_REPORT_ARCHIVE",
    ),
    "docs/design/15-cache-and-concurrency.md": (
        "PROJ项目任务/执行契约→ACC活动根→当前报告版本",
        "DRAFT的生成`current_marker=NULL`",
        "撤销不恢复旧版",
        "只有两项均非终态且当前契约均为V63 `TASK_NATIVE`才原子换绑",
        "markDelivered",
        "scheduleRetry",
    ),
    "docs/design/16-exception-and-idempotency.md": (
        "ACC活动、报告历史、TaskCompletionEvaluation和PROJ任务状态零写入",
        "报告状态/历史不回滚、不删除",
        "不得伪造Web登录上下文",
        "两项均不存在保持不变",
        "两项均DONE/CLOSED时保持历史不变且不创建活动",
        "终态/非终态混合整批失败",
    ),
    "docs/decisions/0040-acceptance-file-fact-and-activity-initialization.md": (
        "`ACCEPTED`",
        "FileBusinessObjectPolicyProvider",
        "publisherActorUserId",
        "PlatformOutboxDeliveryApi",
        "AcceptanceActivityInitializationApi.initialize",
        "两项均为`PENDING_ASSIGN/PENDING_START/IN_PROGRESS/PENDING_ACCEPT`",
        "两项精确任务均为`DONE/CLOSED`时整项目保持旧契约和历史不变且不创建活动",
        "终态与非终态混合时整批失败",
    ),
    "docs/decisions/0039-acceptance-report-version-and-deliverable-index.md": (
        "`ACCEPTED`",
        "`DO_NOT_REUSE`",
        "`DIRECT_REUSE` + `COPY_THEN_ENHANCE`",
        "F-COM-001 AcceptanceScopeBinding真实Provider",
    ),
    "docs/traceability/phase2-contract-map.md": (
        "内部AcceptanceActivityCompletionFactApi",
        "精确D-INITIAL-REPORT/D-FINAL-REPORT更新既有应交根及只追加来源版本/附件集合",
        "F-ACC-001只实现初验/终验来源切片",
    ),
    "docs/traceability/domain-entity-migration-contract.json": (
        '"gate": "F-ACC-001-LEGACY"',
        '"sourceObject": "acc_project_deliverable"',
        "never infer type or a primary file",
    ),
}
FACC001_REPORT_CONTRACT_FORBIDDEN_SNIPPETS = {
    "docs/design/09-database-design.md": (
        "| Acceptance | `acc_acceptance` | `acc_acceptance_item`",
        "| DeliveryArtifact | `acc_delivery_artifact`",
        "file_artifact_id/file_version_id/file_hash",
    ),
    "docs/design/11-event-design.md": (
        "attachments[{sequence,fileArtifactId,fileVersionId,fileHash}]",
    ),
    "docs/decisions/0040-acceptance-file-fact-and-activity-initialization.md": (
        "仅creator",
        "终态任务原子创建PENDING活动",
        "任一精确任务为`DONE/CLOSED`时整项目保持旧契约和历史不变且不创建活动",
    ),
    "docs/traceability/phase2-contract-map.md": (
        "- 数据表：acc_delivery_artifact、acc_artifact_review、acc_archive_record",
    ),
}


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def github_slug(value: str) -> str:
    value = re.sub(r"[`*_~]", "", value.strip().lower())
    value = re.sub(r"[^\w\- ]", "", value, flags=re.UNICODE)
    return value.replace(" ", "-")


def anchors(path: Path) -> set[str]:
    seen: dict[str, int] = {}
    result: set[str] = set()
    for heading in HEADING.findall(read(path)):
        base = github_slug(heading)
        count = seen.get(base, 0)
        seen[base] = count + 1
        result.add(base if count == 0 else f"{base}-{count}")
    return result


def parse_contract_map(path: Path) -> tuple[dict[str, dict[str, str]], list[str]]:
    text = read(path)
    matches = list(CONTRACT_HEADING.finditer(text))
    result: dict[str, dict[str, str]] = {}
    errors: list[str] = []
    for index, match in enumerate(matches):
        identifier = match.group(1)
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        fields = dict(CONTRACT_FIELD.findall(text[match.end():end]))
        if identifier in result:
            errors.append(f"duplicate Phase 2 contract block: {identifier}")
        result[identifier] = fields
    return result, errors


def requirement_ids(fragment: str) -> set[str]:
    result: set[str] = set()
    result.update(
        identifier
        for identifier in FULL_REQUIREMENT_ID.findall(fragment)
        if not identifier.startswith("ADR-")
    )
    for match in re.finditer(
        r"([A-Z]+(?:-[A-Z0-9]+)?-)(\d+)\s*～\s*(?:([A-Z]+(?:-[A-Z0-9]+)?-))?(\d+)",
        fragment,
    ):
        start_prefix, start_value, end_prefix, end_value = match.groups()
        prefix = end_prefix or start_prefix
        if prefix != start_prefix:
            continue
        width = max(len(start_value), len(end_value))
        result.update(
            f"{prefix}{number:0{width}d}"
            for number in range(int(start_value), int(end_value) + 1)
        )
    for match in re.finditer(r"([A-Z]+(?:-[A-Z0-9]+)?-)(\d+(?:\s*/\s*\d+)+)", fragment):
        prefix, values = match.groups()
        result.update(f"{prefix}{int(value):02d}" for value in re.findall(r"\d+", values))
    return result


def markdown_cells(line: str) -> list[str]:
    return [cell.strip().strip("`") for cell in line.strip().strip("|").split("|")]


def is_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(set(cell) <= {"-", ":"} for cell in cells)


def is_non_active_contract_row(cells: list[str], headers: list[str] | None = None) -> bool:
    """Recognize exclusions only from explicit table structure, never free text."""
    if cells and cells[0] in NON_ACTIVE_ROW_LABELS:
        return True
    if not headers:
        return False
    for header, value in zip(headers, cells):
        if header.strip().lower() not in NON_ACTIVE_FIELD_NAMES:
            continue
        normalized_value = value.strip().strip("`")
        if normalized_value in NON_ACTIVE_FIELD_VALUES:
            return True
    return False


def contract_table_rows(text: str):
    """Yield Markdown table rows with headers when an explicit separator establishes them."""
    headers: list[str] | None = None
    header_candidate: list[str] | None = None
    for line in text.splitlines():
        if not line.startswith("|"):
            headers = None
            header_candidate = None
            continue
        cells = markdown_cells(line)
        if is_separator_row(cells):
            headers = header_candidate
            continue
        yield headers, cells, line
        if headers is None:
            header_candidate = cells


def work_order_time_forbidden_tables(root: Path) -> tuple[set[str], list[str]]:
    path = root / "docs" / "traceability" / "core-migration-schema-contract.json"
    if not path.is_file():
        return set(), ["missing core migration schema contract for Phase 2 forbidden-table validation"]
    try:
        contract = json.loads(read(path))
    except (json.JSONDecodeError, OSError) as error:
        return set(), [f"invalid core migration schema contract: {error}"]
    forbidden = contract.get("forbiddenV1V2Tables")
    if not isinstance(forbidden, list) or not all(isinstance(table, str) for table in forbidden):
        return set(), ["core migration schema contract missing forbiddenV1V2Tables string list"]
    tables = {
        table
        for table in forbidden
        if table.startswith("srv_")
        and ("work_order" in table or re.search(r"(?:^|_)time(?:_|$)", table))
    }
    if not tables:
        return set(), ["core migration schema contract has no WorkOrder/time forbidden tables"]
    return tables, []


def forbidden_model_tokens(tables: set[str]) -> set[str]:
    tokens = set(tables)
    for table in tables:
        parts = table.split("_")[1:] if table.startswith("srv_") else table.split("_")
        tokens.add("".join(part.capitalize() for part in parts))
    return tokens


def prd_formal_requirement_records(text: str) -> list[tuple[str, str]]:
    """Read formal V1/V2 IDs and their primary versions from PRD blocks."""
    matches = list(PRD_REQUIREMENT_ROW.finditer(text))
    result: list[tuple[str, str]] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        block = text[match.end():end]
        version = re.search(r"^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", block, re.M)
        if version and version.group(1) in {"V1", "V2"}:
            result.append((match.group(1), version.group(1)))
    return result


def prd_formal_requirement_ids(text: str) -> list[str]:
    return [identifier for identifier, _ in prd_formal_requirement_records(text)]


def prd_version_slice_keys(root: Path) -> list[str]:
    generator = root / "scripts" / "generate_requirement_traceability.py"
    spec = importlib.util.spec_from_file_location("requirement_generator", generator)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    prd_text = module.read(root / "docs" / "baseline" / "prd-v1.8.md")
    requirements = module.extract_requirements(prd_text)
    return [item["key"] for item in module.extract_version_slices(prd_text, requirements)]


def appendix_fragment(text: str, start: str, end: str | None) -> str:
    start_match = re.search(start, text, re.M)
    if not start_match:
        return ""
    if end is None:
        return text[start_match.end():]
    end_match = re.search(end, text[start_match.end():], re.M)
    if not end_match:
        return ""
    return text[start_match.end():start_match.end() + end_match.start()]


def scope_index_ids(fragment: str) -> set[str]:
    result: set[str] = set()
    for line in fragment.splitlines():
        if not line.startswith("|"):
            continue
        cells = markdown_cells(line)
        if not cells or is_separator_row(cells):
            continue
        result.update(requirement_ids(cells[0]))
    return result


def active_requirement_ids(text: str) -> set[str]:
    """Extract IDs from formal scope declarations and Requirement table columns."""
    result: set[str] = set()
    for declaration in ACTIVE_REQUIREMENT_LINE.findall(text):
        result.update(requirement_ids(declaration))

    headers: list[str] | None = None
    requirement_column: int | None = None
    for line in text.splitlines():
        if not line.startswith("|"):
            headers = None
            requirement_column = None
            continue
        cells = markdown_cells(line)
        if "Requirement" in cells:
            headers = cells
            requirement_column = cells.index("Requirement")
            continue
        if requirement_column is None or requirement_column >= len(cells):
            continue
        if is_non_active_contract_row(cells, headers):
            continue
        if is_separator_row(cells):
            continue
        result.update(requirement_ids(cells[requirement_column]))
    return result


def validate_v18_physical_carriers(root: Path) -> list[str]:
    """Validate the Phase 2 carrier design without pretending its DDL already exists."""
    errors: list[str] = []
    design = root / "docs" / "design"
    paths = {
        "model": design / "08-data-model.md",
        "alignment": design / "08a-domain-entity-migration-alignment.md",
        "database": design / "09-database-design.md",
        "api": design / "10-api-design.md",
        "event": design / "11-event-design.md",
        "concurrency": design / "15-cache-and-concurrency.md",
        "exception": design / "16-exception-and-idempotency.md",
        "contract": root / "docs" / "traceability" / "phase2-contract-map.md",
        "decision": root / "docs" / "decisions" / "0030-project-task-execution-contract-and-cutover-checklist-carriers.md",
        "gate": root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md",
    }
    texts: dict[str, str] = {}
    for name, path in paths.items():
        if not path.is_file():
            errors.append(f"missing V1.8 physical carrier document: {path.relative_to(root)}")
            texts[name] = ""
        else:
            texts[name] = read(path)

    for table in V18_PHYSICAL_CARRIER_TABLES:
        if table not in texts["database"]:
            errors.append(f"V1.8 database design missing physical carrier table: {table}")
        if table not in texts["contract"]:
            errors.append(f"V1.8 Phase 2 contract missing physical carrier table: {table}")

    object_sources = texts["model"] + "\n" + texts["alignment"] + "\n" + texts["contract"]
    for object_name in V18_PHYSICAL_CARRIER_OBJECTS:
        if object_name not in object_sources:
            errors.append(f"V1.8 physical carrier object is not traceable: {object_name}")

    required_rules = {
        "database": (
            "TASK_NATIVE",
            "current_marker",
            "idempotency_key",
            "stable_item_key",
            "input_snapshot_hash",
        ),
        "api": (
            "executionContractId/contractVersion",
            "factObjectKey/factVersion",
            "Idempotency-Key",
        ),
        "concurrency": (
            "TaskCompletionEvaluation",
            "checklistVersion + inputSnapshotHash",
        ),
        "exception": (
            "TASK_NATIVE",
            "VERSION_CONFLICT/BUSINESS_GATE",
            "IDEMPOTENCY_CONFLICT",
        ),
    }
    for document_name, markers in required_rules.items():
        for marker in markers:
            if marker not in texts[document_name]:
                errors.append(f"V1.8 {document_name} carrier rule missing: {marker}")

    for marker in ("ADR-0030", "PM-03", "PM-11", "CUT-03", "INT-12", "不授权历史迁移或数据切换"):
        if marker not in texts["decision"]:
            errors.append(f"V1.8 physical carrier decision missing marker: {marker}")
    gate_state_match = re.search(r"^> 审查状态：`([^`]+)`", texts["gate"], re.MULTILINE)
    gate_state = gate_state_match.group(1) if gate_state_match else None
    if gate_state in {"REVALIDATION_REQUIRED", "APPROVED"}:
        if "`ACCEPTED`" not in texts["decision"]:
            errors.append("ADR-0030 must remain ACCEPTED; PRD delta review does not reverse an accepted carrier decision")
    else:
        errors.append("V1.8 physical carrier gate state is not recognized")
    if "ADR-0030" not in texts["database"]:
        errors.append("V1.8 database design must reference ADR-0030")

    cutover_section = texts["database"]
    section_start = cutover_section.find("## 7. Cutover")
    section_end = cutover_section.find("\n## 8.", section_start + 1) if section_start >= 0 else -1
    if section_start < 0 or section_end < 0:
        errors.append("V1.8 database design missing bounded CUT-03 carrier section")
    else:
        cutover_section = cutover_section[section_start:section_end]
        result_row = next(
            (line for line in cutover_section.splitlines() if line.startswith("| `cut_cutover_checklist_item_result`")),
            "",
        )
        result_cells = markdown_cells(result_row)
        result_fields = [] if len(result_cells) < 2 else [field.strip(" `") for field in result_cells[1].split("/")]
        for selection_field in ("selection_started_at", "selection_ended_at"):
            if selection_field not in result_fields:
                errors.append(f"CUT-03 result carrier missing selection interval field: {selection_field}")
        if "selection_ended_at is null" not in result_row or "checklist_item_id, current_marker" not in result_row:
            errors.append("CUT-03 result carrier selection interval is not protected by the current-marker constraint")
        for forbidden_status in result_fields:
            if re.search(r"(?:status|state|dispatch|schedule)", forbidden_status, re.IGNORECASE):
                errors.append(
                    "CUT-03 result carrier must not copy DAC technical status: "
                    f"{forbidden_status}"
                )

    formal_carrier_documents = (
        "model", "alignment", "database", "api", "event", "concurrency", "exception", "decision", "contract",
    )
    for document_name in formal_carrier_documents:
        if "BLOCKED_BY_DESIGN" in texts[document_name]:
            errors.append(
                "V1.8 physical carrier design still contains BLOCKED_BY_DESIGN: "
                f"{paths[document_name].relative_to(root)}"
            )
    return errors


def validate_v18_migration_gate_evidence(root: Path) -> list[str]:
    """Bind Phase 2 gate summaries to the generated migration contract facts."""
    errors: list[str] = []
    contract_path = root / "docs" / "traceability" / "domain-entity-migration-contract.json"
    if not contract_path.is_file():
        return ["missing V1.8 domain entity migration contract"]
    try:
        contract = json.loads(read(contract_path))
    except (json.JSONDecodeError, OSError) as exc:
        return [f"invalid V1.8 domain entity migration contract: {exc}"]

    records = contract.get("records")
    excluded_sources = contract.get("excludedSources")
    if not isinstance(records, list) or not isinstance(excluded_sources, list):
        return ["V1.8 domain entity migration contract is missing records or excludedSources"]
    object_count = len(records)
    source_count = sum(
        len(record.get("sources", []))
        for record in records
        if isinstance(record, dict) and isinstance(record.get("sources"), list)
    )
    excluded_count = len(excluded_sources)
    expected_summary = f"{object_count}对象/{source_count}来源绑定/{excluded_count}排除源"
    evidence_paths = (
        root / "docs" / "engineering" / "gates" / "phase-2" / "README.md",
        root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md",
        root / "docs" / "engineering" / "gates" / "phase-2" / "self-review.md",
    )
    for path in evidence_paths:
        if not path.is_file():
            errors.append(f"missing Phase 2 migration gate evidence: {path.relative_to(root)}")
            continue
        if expected_summary not in read(path):
            errors.append(
                f"Phase 2 migration gate evidence does not match current contract: "
                f"{path.relative_to(root)} expected={expected_summary}"
            )
    return errors


def validate_fcom001_v70_required_mappings(root: Path) -> list[str]:
    """Reject F-COM-001 contracts that leave V70 required target fields to a later plan."""
    path = root / "docs" / "traceability" / "domain-entity-migration-contract.json"
    if not path.is_file():
        return ["missing F-COM-001 managed migration contract"]
    try:
        contract = json.loads(read(path))
    except (json.JSONDecodeError, OSError) as exc:
        return [f"invalid F-COM-001 managed migration contract: {exc}"]
    records = {
        record.get("object"): record
        for record in contract.get("records", [])
        if isinstance(record, dict)
    }
    errors: list[str] = []
    for object_name, expected in FCOM001_V70_REQUIRED_TARGET_MAPPINGS.items():
        record = records.get(object_name, {})
        source = next(
            (
                item for item in record.get("sources", [])
                if isinstance(item, dict)
                and item.get("sourceType") == "CURRENT_TABLE"
                and item.get("gate") == "F-COM-001"
            ),
            None,
        )
        mappings = source.get("requiredTargetMappings", {}) if source else {}
        for target_field, rule in expected.items():
            if mappings.get(target_field) != rule:
                errors.append(f"F-COM-001 V70 required target mapping missing or changed: {target_field}")
    return errors


def validate_fcom001_acceptance_stage_binding(root: Path) -> list[str]:
    """Keep revision-009 stage-driven acceptance binding complete and report-independent."""
    errors: list[str] = []
    for relative, snippets in FCOM001_ACCEPTANCE_STAGE_REQUIRED_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"missing F-COM-001 acceptance-stage contract: {relative}")
            continue
        content = read(path)
        for snippet in snippets:
            if snippet not in content:
                errors.append(f"F-COM-001 acceptance-stage contract missing: {relative}: {snippet}")
    for relative, snippets in FCOM001_ACCEPTANCE_STAGE_FORBIDDEN_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            continue
        content = read(path)
        for snippet in snippets:
            if snippet in content:
                errors.append(f"F-COM-001 acceptance-stage contract retains superseded rule: {relative}: {snippet}")
    return errors


def validate_fcom001_contract_admin_scope(root: Path) -> list[str]:
    """Keep the approved SYSTEM company-scope decision complete and fail-closed."""
    errors: list[str] = []
    for relative, snippets in FCOM001_CONTRACT_ADMIN_SCOPE_REQUIRED_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"missing F-COM-001 contract-admin scope contract: {relative}")
            continue
        content = read(path)
        for snippet in snippets:
            if snippet not in content:
                errors.append(f"F-COM-001 contract-admin scope missing: {relative}: {snippet}")
    for relative, snippets in FCOM001_CONTRACT_ADMIN_SCOPE_FORBIDDEN_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            continue
        content = read(path)
        for snippet in snippets:
            if snippet in content:
                errors.append(f"F-COM-001 contract-admin scope retains blocked rule: {relative}: {snippet}")
    return errors


def validate_facc001_report_contract(root: Path) -> list[str]:
    """Keep the ACC report/version/deliverable delta Owner-safe and compensation-safe."""
    errors: list[str] = []
    for relative, snippets in FACC001_REPORT_CONTRACT_REQUIRED_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            errors.append(f"missing F-ACC-001 report contract: {relative}")
            continue
        content = read(path)
        for snippet in snippets:
            if snippet not in content:
                errors.append(f"F-ACC-001 report contract missing: {relative}: {snippet}")
    for relative, snippets in FACC001_REPORT_CONTRACT_FORBIDDEN_SNIPPETS.items():
        path = root / relative
        if not path.is_file():
            continue
        content = read(path)
        for snippet in snippets:
            if snippet in content:
                errors.append(f"F-ACC-001 report contract retains parallel or legacy truth: {relative}: {snippet}")
    return errors


def validate_facc002_satisfaction_contract(root: Path) -> list[str]:
    """Reject an F-ACC-002 delta that leaves positive Owner/file/history facts undefined."""
    errors: list[str] = []
    design = root / "docs" / "design"
    required = {
        "02d-cross-context-contracts.md": (
            "SatisfactionQuestionnaireTemplateApi.resolvePublished",
            "SatisfactionTaskInitializationApi.initialize",
            "SatisfactionResultFactApi.inspect/lockAndRevalidate",
            "FileArtifactApi.initializeBusinessGrantUpload/completeBusinessGrantUpload",
        ),
        "09-database-design.md": (
            "acc_satisfaction_access_grant",
            "acc_satisfaction_response_file",
            "acc_satisfaction_result_file",
            "source_object_type=SatisfactionResult",
        ),
        "10-api-design.md": (
            "/satisfaction-tasks/{id}/access-grants",
            "/satisfaction-questionnaires/{token}/files",
            "pms:acceptance:satisfaction:query/manage/collect/export/download",
        ),
        "11-event-design.md": ("SatisfactionResultVersionChanged", "SatisfactionResultOutboxDeliveryJob"),
        "13-file-design.md": ("SATISFACTION_SIGNATURE", "SATISFACTION_RESULT_DOCUMENT", "SATISFACTION_ARCHIVE"),
    }
    for name, tokens in required.items():
        path = design / name
        content = read(path) if path.is_file() else ""
        for token in tokens:
            if token not in content:
                errors.append(f"F-ACC-002 missing focused contract token in {name}: {token}")
    alignment = design / "08a-domain-entity-migration-alignment.md"
    content = read(alignment) if alignment.is_file() else ""
    row = next((line for line in content.splitlines() if line.startswith("| `SatisfactionCollection` |")), "")
    for token in ("PRESERVE_RAW", "不迁为有效业务事实", "不得从回访/审批状态推断"):
        if token not in row:
            errors.append(f"F-ACC-002 legacy satisfaction boundary missing: {token}")
    return errors


def validate_v18_revalidation(root: Path, gate: str, approved: bool = False) -> list[str]:
    """Validate the V1.8 contract in either review-pending or approved state."""
    errors: list[str] = []
    prd_path = root / "docs" / "baseline" / "prd-v1.8.md"
    matrix_path = root / "docs" / "traceability" / "requirement-matrix.md"
    contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"
    gate_state_match = re.search(r"^> 审查状态：`([^`]+)`", gate, re.MULTILINE)
    gate_conclusion_match = re.search(r"^> 结论：`([^`]+)`", gate, re.MULTILINE)
    expected_gate_state = "APPROVED" if approved else "REVALIDATION_REQUIRED"
    expected_gate_conclusion = "READY_FOR_PHASE_3_V1.8" if approved else "NOT_READY_FOR_PHASE_3_REVISION_007"
    if not gate_state_match or gate_state_match.group(1) != expected_gate_state:
        errors.append(f"V1.8 Phase 2 gate state must be: {expected_gate_state}")
    if not gate_conclusion_match or gate_conclusion_match.group(1) != expected_gate_conclusion:
        errors.append(f"V1.8 Phase 2 gate conclusion must be: {expected_gate_conclusion}")
    required_gate_tokens = ("100项", "111个目标版本切片", "V1 53个", "V2 58个", "AI-MIG-000")
    for token in required_gate_tokens:
        if token not in gate:
            errors.append(f"V1.8 Phase 2 gate missing token: {token}")

    if not prd_path.is_file():
        return errors + ["missing PRD V1.8 baseline"]
    records = prd_formal_requirement_records(read(prd_path))
    prd_ids = [identifier for identifier, _ in records]
    expected_slice_keys = prd_version_slice_keys(root)
    expected_counts = {"V1": 53, "V2": 47}
    actual_counts = {version: sum(item_version == version for _, item_version in records) for version in expected_counts}
    if len(prd_ids) != 100 or len(set(prd_ids)) != 100 or actual_counts != expected_counts:
        errors.append(f"PRD V1.8 scope mismatch: rows={len(prd_ids)} unique={len(set(prd_ids))} versions={actual_counts}")

    if not matrix_path.is_file():
        errors.append("missing V1.8 requirement matrix")
        matrix_ids: list[str] = []
        matrix_slice_keys: list[str] = []
    else:
        matrix = read(matrix_path)
        matrix_slice_keys = VERSION_SLICE_ROW.findall(matrix)
        matrix_ids = [key.split("@", 1)[0] for key in matrix_slice_keys]
    if (
        set(matrix_ids) != set(prd_ids)
        or len(matrix_slice_keys) != 111
        or len(set(matrix_slice_keys)) != 111
        or set(matrix_slice_keys) != set(expected_slice_keys)
    ):
        errors.append("V1.8 requirement matrix must exactly contain the PRD-derived 100 Requirements and 111 version slices")

    if not contract_path.is_file():
        errors.append("missing V1.8 Phase 2 contract map")
    else:
        contract_text = read(contract_path)
        contracts, contract_errors = parse_contract_map(contract_path)
        errors.extend(contract_errors)
        if set(contracts) != set(prd_ids) or len(contracts) != 100:
            errors.append("V1.8 Phase 2 contract map must contain the same 100 IDs")
        contract_slice_keys = VERSION_SLICE_ROW.findall(contract_text)
        if (
            len(contract_slice_keys) != 111
            or len(set(contract_slice_keys)) != 111
            or set(contract_slice_keys) != set(expected_slice_keys)
        ):
            errors.append("V1.8 Phase 2 contract map must exactly declare all 111 PRD-derived version slices")
        contract_markers = (
            ("文档状态：`BASELINE`", "适用基线：PRD V1.8", "Phase 3验证注记状态：`READY_FOR_PHASE_3_V1.8`")
            if approved
            else ("文档状态：`REVALIDATION_REQUIRED`", "适用基线：PRD V1.8", "Phase 3验证注记状态：`REVALIDATION_REQUIRED`")
        )
        for marker in contract_markers:
            if marker not in contract_text:
                errors.append(f"V1.8 Phase 2 contract map missing marker: {marker}")
        required_contract_semantics = (
            "V2仅在冻结规则唯一匹配时自动形成并生效主责指派",
            "甘特展示和受控依赖新增、更新、删除",
            "送达不等于确认，渠道失败回退V1链接/扫码",
            "V2仅增加自动触达",
            "V2按授权范围聚合首页KPI",
            "V2增加授权清单导出及受控流程跳转配置优化",
            "V2校验A/B级专项提前时间",
            "V1首批配置基础：动态模板、表单、风险/调研矩阵和匹配规则",
            "技术公告唯一归INT-04",
            "V2创建OA领料/外采流程引用",
            "V2在线巡检复用同一凭证、任务和采集执行引擎",
            "后续命令是否继续由任务冻结的已发布规则决定并留痕",
        )
        for marker in required_contract_semantics:
            if marker not in contract_text:
                errors.append(f"V1.8 Phase 2 contract map missing revision 007 semantics: {marker}")
        for stale_marker in ("V2规则候选确认", "不影响后续命令执行"):
            if stale_marker in contract_text:
                errors.append(f"V1.8 Phase 2 contract map retains superseded semantics: {stale_marker}")

    if approved:
        for name in PHASE2_DOCS:
            path = root / "docs" / "design" / name
            if not path.is_file() or "文档状态：`BASELINE`" not in read(path):
                errors.append(f"approved V1.8 Phase 2 document is not BASELINE: {name}")
        for relative in (
            "docs/traceability/domain-entity-migration-contract.json",
            "docs/traceability/domain-object-table-map.json",
        ):
            path = root / relative
            if not path.is_file() or json.loads(read(path)).get("status") != "BASELINE":
                errors.append(f"approved V1.8 migration artifact is not BASELINE: {relative}")

    leaked = {"ACC-05", "COM-02", "IMP-02"} & (set(prd_ids) | set(matrix_ids))
    if leaked:
        errors.append(f"V1.8 removed/deferred requirements leaked into formal contracts: {sorted(leaked)}")
    errors.extend(validate_v18_migration_gate_evidence(root))
    errors.extend(validate_fcom001_v70_required_mappings(root))
    errors.extend(validate_fcom001_acceptance_stage_binding(root))
    errors.extend(validate_fcom001_contract_admin_scope(root))
    errors.extend(validate_facc001_report_contract(root))
    errors.extend(validate_facc002_satisfaction_contract(root))
    errors.extend(validate_v18_physical_carriers(root))
    return errors


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    gate_path = root / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
    if gate_path.is_file():
        gate = read(gate_path)
        gate_state_match = re.search(r"^> 审查状态：`([^`]+)`", gate, re.MULTILINE)
        gate_state = gate_state_match.group(1) if gate_state_match else None
        if gate_state == "REVALIDATION_REQUIRED":
            return validate_v18_revalidation(root, gate)
        if gate_state == "APPROVED" and "READY_FOR_PHASE_3_V1.8" in gate:
            return validate_v18_revalidation(root, gate, approved=True)
    design = root / "docs" / "design"
    prd_path = root / "docs" / "baseline" / "prd-v1.7.md"
    matrix_path = root / "docs" / "traceability" / "requirement-matrix.md"
    contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"

    for name in PHASE2_DOCS:
        path = design / name
        if not path.is_file():
            errors.append(f"missing Phase 2 document: {path.relative_to(root)}")
            continue
        text = read(path)
        status_marker = "文档状态：`BASELINE ADDENDUM`" if name == "08a-domain-entity-migration-alignment.md" else "文档状态：`BASELINE`"
        for marker in (status_marker, "适用基线：PRD V1.7", "Requirement ID：", "Owner："):
            if marker not in text:
                errors.append(f"{path.relative_to(root)} missing metadata: {marker}")
        if "IN_REVIEW" in text:
            errors.append(f"{path.relative_to(root)} has unresolved IN_REVIEW marker in BASELINE Phase 2 design")
        deferred_marker = DEFERRED_PHASE3_MARKERS.get(name)
        if deferred_marker and deferred_marker not in text:
            errors.append(f"{path.relative_to(root)} missing Phase 3 deferral marker: {deferred_marker}")

    if not prd_path.is_file():
        errors.append("missing PRD V1.7 baseline")
        prd_identifiers: list[str] = []
    else:
        prd_text = read(prd_path)
        prd_records = prd_formal_requirement_records(prd_text)
        prd_identifiers = [identifier for identifier, _ in prd_records]
        if len(prd_identifiers) != EXPECTED_REQUIREMENT_COUNT or len(set(prd_identifiers)) != EXPECTED_REQUIREMENT_COUNT:
            errors.append(
                f"PRD baseline expected {EXPECTED_REQUIREMENT_COUNT} unique formal V1/V2 IDs, "
                f"got rows={len(prd_identifiers)} unique={len(set(prd_identifiers))}"
            )
        actual_scope_counts = {
            "V1": sum(version == "V1" for _, version in prd_records),
            "V2": sum(version == "V2" for _, version in prd_records),
            "V1/V2": len(prd_records),
            "V3": len(scope_index_ids(appendix_fragment(
                prd_text,
                r"^####\s+A\.3\.1\b.*$",
                r"^####\s+A\.3\.2\b.*$",
            ))),
            "OUT_OF_SCOPE": len(scope_index_ids(appendix_fragment(
                prd_text,
                r"^###\s+A\.4\b.*$",
                r"^##\s+附录B\b.*$",
            ))),
        }
        if actual_scope_counts != EXPECTED_SCOPE_COUNTS:
            errors.append(
                "PRD scope statistics mismatch: "
                f"expected={EXPECTED_SCOPE_COUNTS} actual={actual_scope_counts}"
            )

    phase1_traceability_path = design / "01-requirement-traceability.md"
    if not phase1_traceability_path.is_file():
        errors.append("missing Phase 1 requirement traceability for scope statistics")
    elif SCOPE_STATISTICS_MARKER not in read(phase1_traceability_path):
        errors.append(
            "Phase 1 scope statistics mismatch: expected marker "
            f"{SCOPE_STATISTICS_MARKER}"
        )

    if not matrix_path.is_file():
        return errors + ["missing requirement matrix"]

    matrix = read(matrix_path)
    identifiers = REQUIREMENT_ROW.findall(matrix)
    if len(identifiers) != EXPECTED_REQUIREMENT_COUNT or len(set(identifiers)) != EXPECTED_REQUIREMENT_COUNT:
        errors.append(
            f"requirement matrix expected {EXPECTED_REQUIREMENT_COUNT} unique rows, "
            f"got rows={len(identifiers)} unique={len(set(identifiers))}"
        )
    if matrix.count("SDS-P2-BASELINE") != EXPECTED_REQUIREMENT_COUNT:
        errors.append("every requirement row must carry SDS-P2-BASELINE evidence")
    if set(prd_identifiers) != set(identifiers):
        errors.append(
            "PRD formal Requirement IDs must exactly match requirement matrix; "
            f"missing={sorted(set(prd_identifiers) - set(identifiers))} "
            f"extra={sorted(set(identifiers) - set(prd_identifiers))}"
        )

    if not contract_path.is_file():
        errors.append("missing explicit Phase 2 contract map")
        contracts: dict[str, dict[str, str]] = {}
    else:
        contracts, contract_errors = parse_contract_map(contract_path)
        errors.extend(contract_errors)
        if set(contracts) != set(identifiers):
            errors.append(
                "explicit Phase 2 contract IDs must exactly match requirement matrix; "
                f"missing={sorted(set(identifiers) - set(contracts))} "
                f"extra={sorted(set(contracts) - set(identifiers))}"
            )
        if set(contracts) != set(prd_identifiers):
            errors.append(
                "PRD formal Requirement IDs must exactly match explicit Phase 2 contract IDs; "
                f"missing={sorted(set(prd_identifiers) - set(contracts))} "
                f"extra={sorted(set(contracts) - set(prd_identifiers))}"
            )

    combined_design = "\n".join(read(design / name) for name in PHASE2_DOCS if (design / name).is_file())
    database_design = read(design / "09-database-design.md") if (design / "09-database-design.md").is_file() else ""
    api_design = read(design / "10-api-design.md") if (design / "10-api-design.md").is_file() else ""
    event_design = read(design / "11-event-design.md") if (design / "11-event-design.md").is_file() else ""
    integration_design = read(design / "12-integration-design.md") if (design / "12-integration-design.md").is_file() else ""
    file_design = read(design / "13-file-design.md") if (design / "13-file-design.md").is_file() else ""

    forbidden_tables, forbidden_contract_errors = work_order_time_forbidden_tables(root)
    errors.extend(forbidden_contract_errors)
    forbidden_tokens = forbidden_model_tokens(forbidden_tables)

    for api in FORBIDDEN_HISTORICAL_USER_APIS:
        if api in api_design:
            errors.append(f"V1/V2 must not expose historical user API: {api}")

    for name in PHASE2_DOCS:
        path = design / name
        if not path.is_file():
            continue
        content = read(path)
        for headers, cells, line in contract_table_rows(content):
            if is_non_active_contract_row(cells, headers):
                continue
            for token in sorted(forbidden_tokens, key=len, reverse=True):
                if token in line:
                    errors.append(f"{name} forbidden active WorkOrder/time model token: {token}")
                    break
        for line in content.splitlines():
            if re.search(r"(?:当前对象|当前数据对象)\s*[:：]", line):
                for token in sorted(forbidden_tokens, key=len, reverse=True):
                    if token in line:
                        errors.append(f"{name} forbidden active WorkOrder/time model token: {token}")
                        break

    for declaration in ACTIVE_REQUIREMENT_LINE.findall(file_design):
        if re.search(r"(?:^|[、，/\s])WO(?:$|[、，/\s])", declaration):
            errors.append("13-file-design.md must not declare a current Work Order file context")
    for headers, cells, line in contract_table_rows(file_design):
        if not is_non_active_contract_row(cells, headers) and (
            re.search(r"\bWork\s+Order\b", line, re.I)
            or (cells and re.search(r"(?:^|[/、])工单(?:$|[/、])", cells[0]))
        ):
            errors.append("13-file-design.md must not declare a current Work Order file context")

    formal_id_set = set(prd_identifiers)
    for name in PHASE2_DOCS:
        path = design / name
        if not path.is_file():
            continue
        for identifier in sorted(active_requirement_ids(read(path)) - formal_id_set):
            errors.append(f"{name} active scope contains non-formal Requirement: {identifier}")

    for headers, cells, line in contract_table_rows(event_design):
        if not is_non_active_contract_row(cells, headers) and "ProjectConversionCompleted" in line and re.search(r"(?:^|[/|\s])WO(?:$|[/|\s])", line):
            errors.append("ProjectConversionCompleted must not declare WO as a V1/V2 consumer")
    for name in ("12-integration-design.md", "16-exception-and-idempotency.md"):
        path = design / name
        if not path.is_file():
            continue
        for headers, cells, line in contract_table_rows(read(path)):
            if not is_non_active_contract_row(cells, headers) and ("打卡原始事实" in line or "钉钉打卡" in line):
                errors.append(f"{name} contains active DingTalk clock-in fact contract")

    for identifier, contract in contracts.items():
        for field in ("数据对象", "数据表"):
            for token in sorted(forbidden_tokens, key=len, reverse=True):
                if token in contract.get(field, ""):
                    errors.append(f"{identifier} forbidden active WorkOrder/time model token: {token}")
                    break
        for field in CONTRACT_FIELDS:
            value = contract.get(field, "").strip()
            if not value:
                errors.append(f"{identifier} explicit Phase 2 contract missing field: {field}")
        for field in ("数据对象", "数据表", "API", "工作流/状态", "授权与数据范围"):
            value = contract.get(field, "")
            if value.startswith("N/A") or len(value) < 4:
                errors.append(f"{identifier} has non-implementable Phase 2 contract field: {field}")
        if any(marker in contract.get("工作流/状态", "") for marker in ("通用流程", "按需处理", "同领域")):
            errors.append(f"{identifier} has generic workflow placeholder")

        for table in re.findall(r"\b(?:proj|sol|imp|acc|cut|srv|cus|ast|com|res|ana|plt|kno)_[a-z0-9_]+\b", contract.get("数据表", "")):
            if table not in database_design:
                errors.append(f"{identifier} references undefined table contract: {table}")
        for api in re.findall(r"/[A-Za-z0-9_{}:|./-]+", contract.get("API", "")):
            if api not in api_design:
                errors.append(f"{identifier} references undefined API contract: {api}")
        if not contract.get("事件", "").startswith("N/A"):
            for event in re.findall(r"[A-Z][A-Za-z]+(?:/[A-Z][A-Za-z]+)?", contract.get("事件", "")):
                if event not in event_design:
                    errors.append(f"{identifier} references undefined event contract: {event}")
        if not contract.get("外部集成", "").startswith("N/A"):
            for system in re.split(r"[、，]", contract.get("外部集成", "")):
                if system.strip() and system.strip() not in integration_design:
                    errors.append(f"{identifier} references undefined integration contract: {system.strip()}")
        if not contract.get("文件契约", "").startswith("N/A") and contract.get("文件契约", "") not in file_design:
            errors.append(f"{identifier} references undefined file contract: {contract.get('文件契约')}")
        for data_object in re.split(r"[、，]", contract.get("数据对象", "")):
            if data_object.strip() and data_object.strip() not in combined_design:
                errors.append(f"{identifier} references undefined data object: {data_object.strip()}")

    for identifier, required_tokens in {
        "PM-05": ("BorrowedProjectConversion", "proj_project_conversion_item", "/project-conversions/{id}/actions/retry-failed"),
        "PM-06": ("MultiPhaseProjectGroup", "proj_multi_phase_project_member", "/project-phase-groups/{id}/actions/derive-content"),
        "INT-12": ("CollectionTask", "plt_collection_result_consumption", "/internal/collection-tasks/{id}/actions/confirm-consumption"),
    }.items():
        if identifier not in contracts:
            continue
        block = "\n".join(contracts.get(identifier, {}).values())
        for token in required_tokens:
            if token not in block:
                errors.append(f"{identifier} missing dedicated Phase 2 contract token: {token}")

    required_links = tuple(f"../design/{name}" for name in ("08-data-model.md", "09-database-design.md", "10-api-design.md", "15-cache-and-concurrency.md", "16-exception-and-idempotency.md"))
    for line in matrix.splitlines():
        match = REQUIREMENT_ROW.match(line)
        if not match:
            continue
        identifier = match.group(1)
        if f"phase2-contract-map.md#{identifier.lower()}" not in line:
            errors.append(f"{identifier} missing explicit Phase 2 contract link")
        for target in required_links:
            if target not in line:
                errors.append(f"{identifier} missing required Phase 2 trace link: {target}")

    # Event trace is semantic, not a completeness checkbox. Preparation/Solution
    # currently has no public event catalog in 11; linking it to IMP/ACC/CUT would
    # be a mechanically valid but false trace.
    for line in matrix.splitlines():
        match = REQUIREMENT_ROW.match(line)
        if match and match.group(1).startswith(("PRE-", "PLN-", "SCH-", "SOL-")) and "[11事件]" in line:
            errors.append(f"{match.group(1)} has unsupported event trace")

    anchor_cache: dict[Path, set[str]] = {}
    for target in MARKDOWN_LINK.findall(matrix):
        if target.startswith(("http://", "https://")):
            continue
        relative, _, anchor = target.partition("#")
        target_path = (matrix_path.parent / relative).resolve()
        try:
            target_path.relative_to(root.resolve())
        except ValueError:
            errors.append(f"trace link escapes repository: {target}")
            continue
        if not target_path.is_file():
            errors.append(f"trace link target missing: {target}")
            continue
        if anchor:
            anchor_cache.setdefault(target_path, anchors(target_path))
            if anchor not in anchor_cache[target_path]:
                errors.append(f"trace anchor missing: {target}")

    return sorted(set(errors))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        print(f"SUMMARY: {len(errors)} Phase 2 validation issues")
        return 1
    gate_path = args.root.resolve() / "docs" / "engineering" / "gates" / "phase-2" / "gate-status.md"
    if gate_path.is_file():
        gate = read(gate_path)
        gate_state_match = re.search(r"^> 审查状态：`([^`]+)`", gate, re.MULTILINE)
        gate_state = gate_state_match.group(1) if gate_state_match else None
        if gate_state == "REVALIDATION_REQUIRED":
            print("[PASS] PRD V1.8 Phase 2 revalidation gate: 100 requirements; not released for Phase 3")
            return 0
        if gate_state == "APPROVED" and "READY_FOR_PHASE_3_V1.8" in gate:
            print("[PASS] PRD V1.8 Phase 2 baseline: 100 requirements; ready for Phase 3 design")
            return 0
    print(f"[PASS] SDS Phase 2 documents and {EXPECTED_REQUIREMENT_COUNT} requirement trace links")
    return 0


if __name__ == "__main__":
    sys.exit(main())
