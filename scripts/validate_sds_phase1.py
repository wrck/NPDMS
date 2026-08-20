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

    matrix, matrix_duplicates = matrix_mapping(read(root / "docs/traceability/requirement-matrix.md"))
    if matrix_duplicates or matrix != owners:
        errors.append(
            "requirement matrix Owner mapping must exactly equal Phase 1 Owner mapping; "
            f"duplicates={sorted(set(matrix_duplicates))} mismatched="
            f"{sorted(identifier for identifier in set(matrix) | set(owners) if matrix.get(identifier) != owners.get(identifier))}"
        )

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

    gate = read(root / "docs/engineering/gates/phase-1/gate-status.md")
    require_markers(
        errors,
        "Phase 1 gate",
        gate,
        (
            "审查状态：`IN_REVIEW`",
            "结论：`NOT_READY_FOR_PHASE_2_V1.8`",
            "PENDING_FRESH_REVIEW",
            "机器门禁：`PASS`",
        ),
    )
    if "> 独立复审：`PENDING_FRESH_REVIEW`" not in gate:
        errors.append("fresh-context independent review must remain pending until an external GO is recorded")
    if "READY_FOR_PHASE_2" in gate and "NOT_READY_FOR_PHASE_2" not in gate:
        errors.append("Phase 1 gate must not claim READY before fresh-context independent review")

    self_review = read(root / "docs/engineering/gates/phase-1/self-review.md")
    require_markers(
        errors,
        "Phase 1 self-review",
        self_review,
        ("MACHINE_PASS", "PENDING_FRESH_REVIEW", "100/100", "13 个 Owner"),
    )
    independent = read(root / "docs/engineering/gates/phase-1/independent-review.md")
    require_markers(
        errors,
        "fresh-context independent review record",
        independent,
        ("当前状态：`IN_REVIEW`", "当前结论：`PENDING_FRESH_REVIEW`", "不得据此放行Phase 2"),
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
