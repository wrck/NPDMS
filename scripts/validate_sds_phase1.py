#!/usr/bin/env python3
"""Validate the PRD V1.8 Phase 1 SDS review candidate without self-approving it."""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path


PHASE1_DOCS = (
    "docs/design/01-requirement-traceability.md",
    "docs/design/02-domain-model.md",
    "docs/design/02a-context-map.md",
    "docs/design/02b-aggregate-boundary-decisions.md",
    "docs/design/02c-data-ownership-matrix.md",
    "docs/design/02d-cross-context-contracts.md",
    "docs/design/02e-version-scope-matrix.md",
    "docs/design/03-system-architecture.md",
    "docs/design/04-module-design.md",
    "docs/design/05-state-machine.md",
    "docs/design/06-workflow-design.md",
    "docs/design/07-authorization-design.md",
    "docs/design/phase-1-domain-ownership.md",
)
REQUIRED_FILES = PHASE1_DOCS + (
    "docs/baseline/prd-v1.8.md",
    "docs/traceability/requirement-matrix.md",
    "docs/engineering/gates/phase-1/gate-status.md",
    "docs/engineering/gates/phase-1/self-review.md",
    "docs/engineering/gates/phase-1/independent-review.md",
)
OWNER_CODES = {
    "PROJ", "SOL", "IMP", "ACC", "CUT", "SRV", "CUS",
    "AST", "COM", "RES", "ANA", "PLT", "KNO",
}
REMOVED_OR_DEFERRED = {"ACC-05", "COM-02", "IMP-02"}
REQ_ID = re.compile(r"[A-Z]+(?:-[A-Z0-9]+)?-\d+")
PRD_REQUIREMENT_ROW = re.compile(
    r"^\|\s*需求编号\s*\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|\s*$",
    re.M,
)
MATRIX_ROW = re.compile(
    r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|[^|]*\|\s*([A-Z]+)",
    re.M,
)
OWNER_ROW = re.compile(r"^\|\s*([A-Z]{3,4})\s*\|\s*([^|]+?)\s*\|", re.M)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def requirement_ids(fragment: str) -> list[str]:
    result = set(REQ_ID.findall(fragment))
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
    return sorted(result)


def formal_prd_ids(text: str) -> list[str]:
    matches = list(PRD_REQUIREMENT_ROW.finditer(text))
    result: list[str] = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        block = text[match.end():end]
        version = re.search(r"^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", block, re.M)
        if version and version.group(1) in {"V1", "V2"}:
            result.append(match.group(1))
    return result


def owner_mapping(text: str) -> tuple[dict[str, str], list[str]]:
    mapping: dict[str, str] = {}
    duplicates: list[str] = []
    for owner, declaration in OWNER_ROW.findall(text):
        if owner not in OWNER_CODES:
            continue
        for identifier in requirement_ids(declaration):
            if identifier in mapping:
                duplicates.append(identifier)
            else:
                mapping[identifier] = owner
    return mapping, duplicates


def matrix_mapping(text: str) -> tuple[dict[str, str], list[str]]:
    mapping: dict[str, str] = {}
    duplicates: list[str] = []
    for identifier, owner in MATRIX_ROW.findall(text):
        if identifier in mapping:
            duplicates.append(identifier)
        else:
            mapping[identifier] = owner
    return mapping, duplicates


def markdown_row(text: str, key: str) -> list[str]:
    rows = markdown_rows(text, key)
    return rows[0] if rows else []


def normalize_markdown_cell(value: str) -> str:
    normalized = re.sub(r"</?[^>]+>", "", value.strip())
    for marker in ("**", "__", "~~", "`"):
        normalized = normalized.replace(marker, "")
    return re.sub(r"\s+", " ", normalized).strip()


def parse_markdown_row(line: str) -> list[str]:
    if not line.lstrip().startswith("|"):
        return []
    return [normalize_markdown_cell(cell) for cell in line.strip().strip("|").split("|")]


def is_markdown_separator(row: list[str]) -> bool:
    return bool(row) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in row)


def markdown_rows(text: str, key: str) -> list[list[str]]:
    result: list[list[str]] = []
    normalized_key = normalize_markdown_cell(key)
    for line in text.splitlines():
        cells = parse_markdown_row(line)
        if cells and cells[0] == normalized_key:
            result.append(cells)
    return result


def has_inspection_precheck_bypass_claim(text: str) -> bool:
    failure = re.compile(r"预检[^。；\n|]{0,20}(?:未通过|失败|未成功|不成功)")
    permission = re.compile(r"(?:管理员|强制|仍可|允许|可以|可进入|可继续)")
    progression = re.compile(r"(?:执行|巡检中|继续)")
    prohibition = re.compile(r"(?:不得|不允许|禁止|不可|不能)")
    for line in text.splitlines():
        normalized = normalize_markdown_cell(line)
        if failure.search(normalized) and permission.search(normalized) and progression.search(normalized):
            if not prohibition.search(normalized):
                return True
    return False


def has_project_manager_close_or_reopen_grant(text: str) -> bool:
    positive = re.compile(
        r"项目经理[^。\n|]{0,40}(?:可|允许|有权)[^。\n|]{0,20}(?:关闭|重开)[^。\n|]{0,20}项目"
        r"|项目经理[^。\n|]{0,40}(?:关闭|重开)[^。\n|]{0,20}任意项目"
    )
    prohibition = re.compile(r"(?:无[^。\n|]{0,10}权限|只读|不得|不允许|禁止|不可|不能)")
    for line in text.splitlines():
        normalized = normalize_markdown_cell(line)
        if positive.search(normalized) and not prohibition.search(normalized):
            return True
    return False


def has_runtime_evidence_claim(text: str) -> bool:
    patterns = (
        re.compile(r"(?:运行提交|实现提交|baseCommit|implementationCommit|提交)\s*[:：=]?\s*[0-9a-f]{7,40}\b", re.I),
        re.compile(r"(?:运行批次|证据批次|releaseId|batchId)\s*[:：=]?\s*[A-Z][A-Z0-9_-]{3,}\b", re.I),
        re.compile(r"(?:测试结果|构建结果|测试|构建)\s*[:：=]?\s*(?:PASS|SUCCESS|通过)\b", re.I),
        re.compile(r"(?:放行结论\s*[:：=]?\s*(?:APPROVED|GO|READY|PASS)|准予放行|已经放行|已放行)", re.I),
    )
    for line in text.splitlines():
        normalized = normalize_markdown_cell(line)
        if sum(bool(pattern.search(normalized)) for pattern in patterns) >= 2:
            return True
    return False


def has_conflicting_gate_release_claim(text: str) -> bool:
    release = re.compile(r"\bAPPROVED\b|\bREADY_FOR_PHASE_2(?:_V1\.8)?\b", re.I)
    conditional = re.compile(r"(?:方可|之后才能|后才能|若|如果|不得|不能|不代表|尚未|仍未)")
    for line in text.splitlines():
        normalized = normalize_markdown_cell(line)
        if release.search(normalized) and not conditional.search(normalized):
            return True
    return False


def metadata_values(text: str, label: str) -> list[str]:
    return re.findall(rf"^>\s*{re.escape(label)}：`([^`]+)`", text, re.M)


def require_markers(errors: list[str], label: str, text: str, markers: tuple[str, ...]) -> None:
    missing = [marker for marker in markers if marker not in text]
    if missing:
        errors.append(f"{label} missing markers: {missing}")


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    for relative in REQUIRED_FILES:
        if not (root / relative).is_file():
            errors.append(f"missing Phase 1 review file: {relative}")
    if errors:
        return errors

    for relative in PHASE1_DOCS:
        text = read(root / relative)
        status_marker = "> 状态：`IN_REVIEW`" if relative.endswith("phase-1-domain-ownership.md") else "> 文档状态：`IN_REVIEW`"
        for marker in (status_marker, "PRD V1.8", "Requirement ID：", "Owner"):
            if marker not in text:
                errors.append(f"{relative} missing current Phase 1 metadata: {marker}")

    prd_ids = formal_prd_ids(read(root / "docs/baseline/prd-v1.8.md"))
    prd_counts = Counter(prd_ids)
    if len(prd_ids) != 100 or len(prd_counts) != 100 or any(count != 1 for count in prd_counts.values()):
        errors.append(f"PRD V1.8 formal scope must be 100 unique requirements; got rows={len(prd_ids)} unique={len(prd_counts)}")

    ownership_text = read(root / "docs/design/phase-1-domain-ownership.md")
    owners, owner_duplicates = owner_mapping(ownership_text)
    missing = sorted(set(prd_ids) - set(owners))
    extra = sorted(set(owners) - set(prd_ids))
    if len(owners) != 100 or owner_duplicates or missing or extra:
        errors.append(
            "Owner mapping must cover every formal requirement exactly once; "
            f"owners={len(owners)} duplicates={sorted(set(owner_duplicates))} missing={missing} extra={extra}"
        )
    if set(owners.values()) != OWNER_CODES:
        errors.append(f"Owner mapping must use the confirmed 13 owners; actual={sorted(set(owners.values()))}")
    leaked = sorted(REMOVED_OR_DEFERRED & set(owners))
    if leaked:
        errors.append(f"removed/deferred requirements returned to Phase 1 Owner scope: {leaked}")

    matrix_text = read(root / "docs/traceability/requirement-matrix.md")
    matrix, matrix_duplicates = matrix_mapping(matrix_text)
    if matrix_duplicates or matrix != owners:
        errors.append(
            "requirement matrix Owner mapping must exactly equal Phase 1 Owner mapping; "
            f"duplicates={sorted(set(matrix_duplicates))} mismatched="
            f"{sorted(identifier for identifier in set(matrix) | set(owners) if matrix.get(identifier) != owners.get(identifier))}"
        )

    version_scope = read(root / "docs/design/02e-version-scope-matrix.md")
    project_scope_row = markdown_row(version_scope, "项目状态分层")
    notice_scope_row = markdown_row(version_scope, "技术公告治理")
    if (
        len(project_scope_row) < 4
        or "PM-10异常关闭和受控重开、CLO-02正常闭环" not in project_scope_row[1]
        or "PM-10" in project_scope_row[2]
        or "CLO-02" in project_scope_row[2]
        or len(notice_scope_row) < 4
        or "INT-04基础同步" not in notice_scope_row[2]
        or "INT-04" in notice_scope_row[1]
    ):
        errors.append("version scope must keep PM-10/CLO-02 in V1 and INT-04 in V2")

    eqp02_row = markdown_row(matrix_text, "EQP-02")
    if len(eqp02_row) < 9 or "ConfigurationLog" not in eqp02_row[4] or "ConfigurationLog" not in eqp02_row[8]:
        errors.append("EQP-02 traceability must assign ConfigurationLog aggregate and data ownership")
    srv01_row = markdown_row(matrix_text, "SRV-01")
    if (
        len(srv01_row) < 9
        or "ServiceHandoverReference" not in {item.strip() for item in srv01_row[4].split("/")}
        or "ServiceHandoverReference" not in {item.strip() for item in srv01_row[8].split("、")}
        or "ServiceHandover" in {item.strip() for item in srv01_row[4].split("/")}
        or "ServiceHandover" in {item.strip() for item in srv01_row[8].split("、")}
    ):
        errors.append("SRV-01 traceability must use read-only ServiceHandoverReference, not own ServiceHandover")

    domain = read(root / "docs/design/02-domain-model.md")
    state = read(root / "docs/design/05-state-machine.md")
    require_markers(
        errors,
        "project state layers",
        domain + "\n" + state,
        (
            "`current_stage`仅取S0～S6",
            "`lifecycle_status`独立取ACTIVE/NORMAL_CLOSED/EXCEPTION_CLOSED",
            "`assignment_status`独立维护",
            "`display_status`只读派生",
            "CLO-02唯一产生NORMAL_CLOSED",
            "PM-10唯一产生EXCEPTION_CLOSED",
        ),
    )
    aggregate = read(root / "docs/design/02b-aggregate-boundary-decisions.md")
    workflow = read(root / "docs/design/06-workflow-design.md")
    authorization = read(root / "docs/design/07-authorization-design.md")
    expected_inspection_states = {
        "待准备", "待预检", "巡检中", "待报告", "待标注", "待办跟踪中", "已闭环", "已归档", "已取消",
    }
    inspection_state_rows = markdown_rows(state, "InspectionTask")
    inspection_workflow_rows = markdown_rows(workflow, "巡检任务主流程")
    inspection_contradiction = has_inspection_precheck_bypass_claim(state + "\n" + workflow)
    if (
        len(inspection_state_rows) != 1
        or len(inspection_state_rows[0]) < 4
        or {item.strip() for item in inspection_state_rows[0][1].split("、")} != expected_inspection_states
        or "在线分支进入待预检且仅INS-04通过后进入巡检中" not in inspection_state_rows[0][2]
        or "离线分支直接进入巡检中" not in inspection_state_rows[0][2]
        or len(inspection_workflow_rows) != 1
        or len(inspection_workflow_rows[0]) < 5
        or "在线进入待预检并经INS-04通过后执行，离线直接执行" not in inspection_workflow_rows[0][2]
        or "预检未通过保持待预检" not in inspection_workflow_rows[0][4]
        or inspection_contradiction
    ):
        errors.append("Inspection state and workflow must preserve all PRD states and the INS-04 online guard")
    require_markers(
        errors,
        "TASK_NATIVE WorkBinding boundary",
        domain + "\n" + aggregate + "\n" + workflow + "\n" + authorization,
        (
            "必须且只能冻结一个当前WorkBinding",
            "未指定其他业务绑定时使用TASK_NATIVE",
            "不以通用完成命令绕过目标业务事实",
            "`TASK_NATIVE`使用ProjectTask自身的通用详情与状态命令",
            "WorkBinding不授予新权限",
        ),
    )
    if "不以通用完成命令绕过目标业务事实" not in aggregate:
        errors.append("ProjectTask completion guard must reject generic completion for non-native business facts")

    ownership = read(root / "docs/design/02c-data-ownership-matrix.md")
    modules = read(root / "docs/design/04-module-design.md")
    config_contract = read(root / "docs/design/02d-cross-context-contracts.md")
    config_owner_checks = (
        (domain, "ConfigurationLog原始文件、不可变解析版本和设备关联"),
        (aggregate, "不拥有ConfigurationLog原始文件和不可变解析版本"),
        (ownership, "ConfigurationLog原始文件、不可变解析版本和设备关联"),
        (config_contract, "| ConfigurationLogPublished | EXE-03、EXE-04、EQP-02 | Implementation Execution | Asset Management |"),
        (modules, "| 资产管理 | 设备档案、归属、维保基本信息、ConfigurationLog原始文件/不可变解析版本"),
    )
    missing_config_owner = [marker for text, marker in config_owner_checks if marker not in text]
    config_aggregate_rows = markdown_rows(aggregate, "ConfigurationLog")
    if (
        missing_config_owner
        or len(config_aggregate_rows) != 1
        or len(config_aggregate_rows[0]) < 2
        or config_aggregate_rows[0][1] != "Asset Management"
    ):
        errors.append(f"ConfigurationLog Owner boundary missing markers: {missing_config_owner}")

    contract_header = markdown_row(config_contract, "契约")
    contract_rows = []
    malformed_contract_rows = []
    for line in config_contract.splitlines():
        cells = parse_markdown_row(line)
        if not cells or cells[0] == "契约" or is_markdown_separator(cells):
            continue
        contract_rows.append(cells)
        if len(cells) != 5:
            malformed_contract_rows.append(cells[0])
    contract_names = Counter(row[0] for row in contract_rows)
    declared_contract_requirements: set[str] = set()
    invalid_contract_requirements: list[str] = []
    for row in contract_rows:
        ids = set(requirement_ids(row[1])) if len(row) > 1 else set()
        declared_contract_requirements.update(ids)
        if not ids or not ids <= set(prd_ids):
            invalid_contract_requirements.append(row[0])
    expected_contracts = {
        "ConfigurationLogPublished": ({"EXE-03", "EXE-04", "EQP-02"}, "Implementation Execution", "Asset Management"),
        "ServiceHandoverCreated": ({"ACC-06", "SRV-01"}, "Acceptance & Closure", "Service Operations"),
    }
    contract_errors: list[str] = []
    for name, (expected_ids, producer, consumer) in expected_contracts.items():
        rows = [row for row in contract_rows if row[0] == name]
        if (
            len(rows) != 1
            or len(rows[0]) != 5
            or set(requirement_ids(rows[0][1])) != expected_ids
            or rows[0][2] != producer
            or rows[0][3] != consumer
        ):
            contract_errors.append(name)
    missing_contract_links = sorted(
        identifier
        for identifier in declared_contract_requirements
        if "[02d契约]" not in " | ".join(markdown_row(matrix_text, identifier))
    )
    if (
        contract_header[:5] != ["契约", "Requirement ID", "Producer", "Consumer", "语义"]
        or len(contract_header) != 5
        or malformed_contract_rows
        or invalid_contract_requirements
        or any(count != 1 for count in contract_names.values())
        or contract_errors
        or missing_contract_links
    ):
        errors.append(
            "cross-context contracts must have unique Producer rows and exact Requirement traceability; "
            f"malformed={malformed_contract_rows} invalid={invalid_contract_requirements} "
            f"contractErrors={contract_errors} missingLinks={missing_contract_links}"
        )

    require_markers(
        errors,
        "CUT-03 P3 boundary",
        domain + "\n" + aggregate,
        (
            "CUT-03的清单和CollectionTask关联仍从属于CUT-01的P3业务阶段",
            "界面合并不产生新的业务阶段或聚合Owner",
            "CUT-03在P3内引用CollectionTask并消费结果",
            "DAC不进入CutoverTask事务",
        ),
    )

    context_map = read(root / "docs/design/02a-context-map.md")
    ownership = read(root / "docs/design/02c-data-ownership-matrix.md")
    modules = read(root / "docs/design/04-module-design.md")
    require_markers(
        errors,
        "removed context and external Owner boundary",
        context_map + "\n" + ownership + "\n" + modules,
        (
            "V1.8当前不包含`Work Order & Time` Context",
            "ERP为权威来源",
            "不建立COM-02履约回写/对账聚合",
            "IMP-02安全检查不属于当前模块",
            "ACC-05持续服务跟踪仅为V3",
        ),
    )
    acceptance_module_rows = markdown_rows(modules, "验收与闭环")
    service_module_rows = markdown_rows(modules, "服务运营")
    if (
        len(acceptance_module_rows) != 1
        or len(service_module_rows) != 1
        or len(acceptance_module_rows[0]) < 5
        or "ServiceHandoverCreated" not in acceptance_module_rows[0][4]
        or len(service_module_rows[0]) < 5
        or "ServiceHandoverCreated" not in service_module_rows[0][3]
        or "ServiceHandoverCreated" in service_module_rows[0][4]
        or contract_names.get("ServiceHandoverCreated") != 1
    ):
        errors.append("ServiceHandoverCreated must have Acceptance & Closure as its single Producer")
    pm10_rows = [
        row for line in authorization.splitlines()
        if line.startswith("|")
        for row in [[cell.strip() for cell in line.strip().strip("|").split("|")]]
        if row and "PM-10" in row[0]
    ]
    pm10_state_markers = (
        "记录重开原因",
        "恢复关闭前最后一个可恢复阶段",
        "创建新的责任处理事项",
        "不得自动恢复已终止的外部任务",
    )
    if (
        len(pm10_rows) != 1
        or len(pm10_rows[0]) < 5
        or pm10_rows[0][0] != "Project/PM-10"
        or "服务经理对本人主责且满足条件的项目发起回退" not in pm10_rows[0][1]
        or "无关闭或重开权限" not in pm10_rows[0][2]
        or pm10_rows[0][3] != "工程管理部关闭岗"
        or "关闭、重开仅限授权项目" not in pm10_rows[0][4]
        or "重开仅限EXCEPTION_CLOSED" not in pm10_rows[0][4]
        or any(marker not in state for marker in pm10_state_markers)
        or has_project_manager_close_or_reopen_grant(authorization)
    ):
        errors.append("PM-10 authorization must separate service-manager rollback from engineering close/reopen")

    architecture = read(root / "docs/design/03-system-architecture.md")
    runtime_evidence_patterns = (
        r"(?:运行提交|实现提交|baseCommit|implementationCommit)\s*[:：=]\s*[0-9A-Za-z_-]{6,}",
        r"(?:证据批次|releaseId|batchId)\s*[:：=]\s*[0-9A-Za-z_-]{4,}",
        r"(?:测试结果|构建结果|buildResult)\s*[:：=]\s*(?:PASS|SUCCESS|通过)",
        r"(?:放行结论|门禁结论)\s*[:：=]\s*(?:APPROVED|GO|READY|PASS)",
    )
    if (
        "运行提交、证据批次、构建结果和放行结论只登记在对应工程门禁" not in architecture
        or "实现工作包门禁已解除" in architecture
        or "NPDMS-SDS-P1-" in architecture
        or any(re.search(pattern, architecture, re.I) for pattern in runtime_evidence_patterns)
        or has_runtime_evidence_claim(architecture)
    ):
        errors.append("formal architecture must not embed mutable runtime evidence or gate-release claims")

    gate = read(root / "docs/engineering/gates/phase-1/gate-status.md")
    require_markers(
        errors,
        "Phase 1 gate",
        gate,
        (
            "审查状态：`IN_REVIEW`",
            "结论：`NOT_READY_FOR_PHASE_2_V1.8`",
            "RE_REVIEW_REQUIRED",
            "机器门禁：`PASS`",
        ),
    )
    expected_gate_metadata = {
        "审查状态": "IN_REVIEW",
        "结论": "NOT_READY_FOR_PHASE_2_V1.8",
        "机器门禁": "PASS",
        "独立复审": "RE_REVIEW_REQUIRED",
        "已评审候选": "9b56dae",
        "修复候选": "0fac3ab",
    }
    if (
        any(metadata_values(gate, label) != [value] for label, value in expected_gate_metadata.items())
        or has_conflicting_gate_release_claim(gate)
    ):
        errors.append("fresh-context Phase 1 gate must keep one exact IN_REVIEW/NOT_READY/RE_REVIEW_REQUIRED metadata set")

    self_review = read(root / "docs/engineering/gates/phase-1/self-review.md")
    require_markers(
        errors,
        "Phase 1 self-review",
        self_review,
        ("MACHINE_PASS_AFTER_REPAIR", "RE_REVIEW_REQUIRED", "100/100", "13 个 Owner"),
    )
    independent = read(root / "docs/engineering/gates/phase-1/independent-review.md")
    require_markers(
        errors,
        "fresh-context independent review record",
        independent,
        ("当前状态：`IN_REVIEW`", "当前结论：`NO_GO`", "已评审候选：`9b56dae`", "修复候选：`0fac3ab`", "不得据此放行Phase 2"),
    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] PRD V1.8 Phase 1 machine gate: 100 requirements, 13 unique Owners; fresh review still pending")
    return 0


if __name__ == "__main__":
    sys.exit(main())
