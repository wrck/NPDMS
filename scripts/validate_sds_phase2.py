#!/usr/bin/env python3
"""Validate Phase 2 SDS completeness and requirement traceability links."""

from __future__ import annotations

import argparse
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
REQUIREMENT_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|", re.M)
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


def validate(root: Path) -> list[str]:
    errors: list[str] = []
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
    print(f"[PASS] SDS Phase 2 documents and {EXPECTED_REQUIREMENT_COUNT} requirement trace links")
    return 0


if __name__ == "__main__":
    sys.exit(main())
