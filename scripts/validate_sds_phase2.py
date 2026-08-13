#!/usr/bin/env python3
"""Validate Phase 2 SDS completeness and requirement traceability links."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


PHASE2_DOCS = (
    "08-data-model.md",
    "09-database-design.md",
    "10-api-design.md",
    "11-event-design.md",
    "12-integration-design.md",
    "13-file-design.md",
    "15-cache-and-concurrency.md",
    "16-exception-and-idempotency.md",
)
EXPECTED_REQUIREMENT_COUNT = 104
REQUIREMENT_ROW = re.compile(r"^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|", re.M)
MARKDOWN_LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^#{1,6}\s+(.+?)\s*$", re.M)
CONTRACT_HEADING = re.compile(r"^###\s+([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*$", re.M)
CONTRACT_FIELD = re.compile(r"^-\s+(需求名称|数据对象|数据表|API|事件|外部集成|文件契约|工作流/状态|授权与数据范围)：(.+?)\s*$", re.M)
CONTRACT_FIELDS = (
    "需求名称", "数据对象", "数据表", "API", "事件", "外部集成",
    "文件契约", "工作流/状态", "授权与数据范围",
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


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    design = root / "docs" / "design"
    matrix_path = root / "docs" / "traceability" / "requirement-matrix.md"
    contract_path = root / "docs" / "traceability" / "phase2-contract-map.md"

    for name in PHASE2_DOCS:
        path = design / name
        if not path.is_file():
            errors.append(f"missing Phase 2 document: {path.relative_to(root)}")
            continue
        text = read(path)
        for marker in ("文档状态：`BASELINE`", "适用基线：PRD V1.7", "Requirement ID：", "Owner："):
            if marker not in text:
                errors.append(f"{path.relative_to(root)} missing metadata: {marker}")

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

    combined_design = "\n".join(read(design / name) for name in PHASE2_DOCS if (design / name).is_file())
    database_design = read(design / "09-database-design.md") if (design / "09-database-design.md").is_file() else ""
    api_design = read(design / "10-api-design.md") if (design / "10-api-design.md").is_file() else ""
    event_design = read(design / "11-event-design.md") if (design / "11-event-design.md").is_file() else ""
    integration_design = read(design / "12-integration-design.md") if (design / "12-integration-design.md").is_file() else ""
    file_design = read(design / "13-file-design.md") if (design / "13-file-design.md").is_file() else ""

    for identifier, contract in contracts.items():
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
