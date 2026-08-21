#!/usr/bin/env python3
"""Validate coverage and blocking semantics of the NPDMS implementation inventory."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath


ALLOWED_CLASSIFICATIONS = frozenset(
    {
        "CURRENT_52",
        "CURRENT_57",
        "VALID_V2_POSTPONED",
        "EXCLUDED_CURRENT",
        "RUNTIME_RETIRED_DATA_PENDING_EVIDENCE",
        "SEMANTIC_REWORK",
        "PLATFORM_UPSTREAM_UNCHANGED",
        "BLOCKED_BY_SPEC",
    }
)
BLOCKING_CLASSIFICATIONS = frozenset(
    {
        "EXCLUDED_CURRENT",
        "RUNTIME_RETIRED_DATA_PENDING_EVIDENCE",
        "SEMANTIC_REWORK",
        "BLOCKED_BY_SPEC",
    }
)
REQ_PATTERN = re.compile(
    r"^(?:PM|PRE|PLN|SCH|EXE|ACC|CLO|WO|SUB|CUS|EQP|RPT|CUT|INS|INT|AUT|CHG|NFR)-\d{2}$"
)
RUNTIME_SOURCE_ROOTS = (
    "pms-module-cutover/src/main",
    "pms-module-service/src/main",
    "pms-module-project/src/main",
    "yudao-ui/yudao-ui-admin-vue3/src",
)
RUNTIME_SOURCE_SUFFIXES = frozenset(
    {
        ".java",
        ".kt",
        ".xml",
        ".yml",
        ".yaml",
        ".properties",
        ".ts",
        ".tsx",
        ".js",
        ".jsx",
        ".vue",
        ".json",
    }
)
RETIRED_CUTOVER_PATTERNS = (
    ("execution-or-observation type", re.compile(r"\bCut(?:Execution|Observation)\w*\b")),
    (
        "execution-or-observation route",
        re.compile(r"\bcut-(?:execution|observation)\b", re.IGNORECASE),
    ),
    (
        "execution-or-observation permission",
        re.compile(r"\bpms:cut-(?:execution|observation):[A-Za-z0-9:_*-]+", re.IGNORECASE),
    ),
    ("cut-task named bypass action", re.compile(r"\b(?:rollbackCutTask|terminateCutTask)\b")),
)
CUT_TASK_CONTEXT_PATTERN = re.compile(
    r"\bCutTask\w*\b|\bcut-task\b|\bpms:cut-task\b", re.IGNORECASE
)
CUT_TASK_BYPASS_PATTERN = re.compile(
    r"/(?:start-execution|complete-execution|start-observation|complete-observation|rollback|terminate)\b"
    r"|\b(?:startExecution|completeExecution|startObservation|completeObservation)\b"
    r"|\b(?:rollback|terminate)\s*\(",
    re.IGNORECASE,
)
RETIRED_MAINTENANCE_PATTERNS = (
    (
        "maintenance type",
        re.compile(r"\b(?:SrvMaintenance|MaintenanceTransition)\w*\b"),
    ),
    (
        "maintenance route",
        re.compile(r"\b(?:srv-maintenance|maintenance-transition)\b", re.IGNORECASE),
    ),
    (
        "maintenance permission",
        re.compile(
            r"\bpms:(?:srv-maintenance|acc-maintenance-transition):[A-Za-z0-9:_*-]+",
            re.IGNORECASE,
        ),
    ),
)
RETIRED_TEMPLATE_PATTERNS = (
    (
        "template type",
        re.compile(r"\b(?:ProjectPhaseTemplate|ProjectCreateFromTemplate)\w*\b"),
    ),
    (
        "template route",
        re.compile(
            r"(?<!:)\b(?:project-template|phase-template|instantiate-from-template)\b"
        ),
    ),
    (
        "template permission",
        re.compile(r"\bpms:phase-template:[A-Za-z0-9:_*-]+"),
    ),
    (
        "template table",
        re.compile(r"\bpms_project_(?:template|phase_template)\b"),
    ),
)
RETIRED_PROJECT_WRITE_PATTERNS = (
    (
        "route",
        re.compile(r"\bpms/project/(?:create|update|delete|classify|assign-manager)\b"),
    ),
)
PROJECT_SINGULAR_BASE_PATTERN = re.compile(r"""["'`]/pms/project["'`]""")
PROJECT_WRITE_FRAGMENT_PATTERN = re.compile(
    r"""/(?:create|update|delete|classify|assign-manager)["'`]"""
)
PROJECT_WRITE_PERMISSION_PATTERN = re.compile(
    r"\bpms:project:(?:create|update|delete|assign)\b"
)
PROJECT_WRITE_PERMISSION_ALLOWED_PREFIXES = (
    "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/",
    "yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/",
    "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/",
    "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/",
)
RETIRED_PROJECT_TREE_WRITE_PATTERNS = (
    (
        "route",
        re.compile(r"\bpms/project-tree/(?:create-child|move)\b"),
    ),
)
PROJECT_TREE_SINGULAR_BASE_PATTERN = re.compile(r"""["'`]/pms/project-tree["'`]""")
PROJECT_TREE_WRITE_FRAGMENT_PATTERN = re.compile(r"""/(?:create-child|move)["'`]""")
PROJECT_TREE_WRITE_PERMISSION_PATTERN = re.compile(r"\bpms:project-tree:move\b")


def load_inventory(path: Path) -> dict:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read implementation inventory: {exc}") from exc
    if not isinstance(payload, dict):
        raise ValueError("implementation inventory must be an object")
    return payload


def _tracked_files(repository: Path) -> tuple[str, ...]:
    result = subprocess.run(
        ["git", "ls-files"],
        cwd=repository,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return tuple(line.replace("\\", "/") for line in result.stdout.splitlines() if line)


def _candidate_files(repository: Path) -> tuple[str, ...]:
    candidates: list[str] = []
    for path in _tracked_files(repository):
        if path.startswith("pms-module-") and (
            path.endswith("Controller.java")
            or ("/dal/dataobject/" in path and path.endswith("DO.java"))
        ):
            candidates.append(path)
        elif "/src/api/pms/" in path and path.endswith(".ts"):
            candidates.append(path)
        elif "/src/views/pms/" in path and path.endswith("index.vue"):
            candidates.append(path)
        elif path.startswith("sql/migrations/") and path.endswith(".sql"):
            content = (repository / path).read_text(encoding="utf-8", errors="ignore")
            if "pms_" in content.lower():
                candidates.append(path)
    return tuple(sorted(set(candidates)))


def _requirement_ids(repository: Path) -> set[str]:
    matrix = (repository / "docs/traceability/requirement-matrix.md").read_text(
        encoding="utf-8-sig"
    )
    return set(re.findall(REQ_PATTERN.pattern[1:-1], matrix))


def _iter_runtime_source_files(repository: Path):
    for raw_root in RUNTIME_SOURCE_ROOTS:
        source_root = repository / raw_root
        if not source_root.is_dir():
            continue
        for path in sorted(
            candidate for candidate in source_root.rglob("*") if candidate.is_file()
        ):
            if path.suffix.lower() in RUNTIME_SOURCE_SUFFIXES:
                yield path


def _match_retired_patterns(repository: Path, patterns, prefix: str) -> list[str]:
    errors: list[str] = []
    for path in _iter_runtime_source_files(repository):
        content = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository).as_posix()
        for label, pattern in patterns:
            if pattern.search(content):
                errors.append(f"{prefix} {label}: {relative_path}")
    return errors


def find_retired_cutover_runtime_surfaces(repository: Path) -> list[str]:
    """Find retired cutover surfaces in current runtime source, independent of inventory paths."""
    errors = _match_retired_patterns(
        repository, RETIRED_CUTOVER_PATTERNS, "retired cutover"
    )
    for path in _iter_runtime_source_files(repository):
        content = path.read_text(encoding="utf-8", errors="ignore")
        if CUT_TASK_CONTEXT_PATTERN.search(content) and CUT_TASK_BYPASS_PATTERN.search(
            content
        ):
            relative_path = path.relative_to(repository).as_posix()
            errors.append(f"retired cutover cut-task bypass route or method: {relative_path}")
    return errors


def find_retired_maintenance_runtime_surfaces(repository: Path) -> list[str]:
    """Find retired SrvMaintenance/MaintenanceTransition surfaces in current runtime source."""
    return _match_retired_patterns(
        repository, RETIRED_MAINTENANCE_PATTERNS, "retired maintenance"
    )


def find_retired_template_runtime_surfaces(repository: Path) -> list[str]:
    """Find retired legacy project/phase template surfaces in current runtime source."""
    return _match_retired_patterns(
        repository, RETIRED_TEMPLATE_PATTERNS, "retired template"
    )


def find_retired_project_write_runtime_surfaces(repository: Path) -> list[str]:
    """Find retired singular /pms/project write surfaces in current runtime source.

    The legacy chain keeps read-only endpoints, so retirement is enforced on write
    surfaces only: full literal write routes, singular-base + write-fragment
    composition inside one file, and write permission codes outside the rebuilt
    plural-chain whitelist.
    """
    errors = _match_retired_patterns(
        repository, RETIRED_PROJECT_WRITE_PATTERNS, "retired project write"
    )
    for path in _iter_runtime_source_files(repository):
        content = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository).as_posix()
        if PROJECT_SINGULAR_BASE_PATTERN.search(content) and (
            PROJECT_WRITE_FRAGMENT_PATTERN.search(content)
        ):
            errors.append(f"retired project write route composition: {relative_path}")
        if PROJECT_WRITE_PERMISSION_PATTERN.search(content) and not any(
            relative_path.startswith(prefix)
            for prefix in PROJECT_WRITE_PERMISSION_ALLOWED_PREFIXES
        ):
            errors.append(f"retired project write permission: {relative_path}")
    return errors


def find_retired_project_tree_write_runtime_surfaces(repository: Path) -> list[str]:
    """Find retired legacy /pms/project-tree write surfaces in current runtime source.

    The legacy project-tree chain writes to the frozen `pms_project` table, so its
    write surfaces (create-child / move) and the `pms:project-tree:move` permission
    must disappear once F-PM02 rebuilds tree capabilities on the plural /pms/projects
    chain. Full literal write routes and base-route + write-fragment composition are
    both covered.
    """
    errors = _match_retired_patterns(
        repository, RETIRED_PROJECT_TREE_WRITE_PATTERNS, "retired project tree write"
    )
    for path in _iter_runtime_source_files(repository):
        content = path.read_text(encoding="utf-8", errors="ignore")
        relative_path = path.relative_to(repository).as_posix()
        if PROJECT_TREE_SINGULAR_BASE_PATTERN.search(content) and (
            PROJECT_TREE_WRITE_FRAGMENT_PATTERN.search(content)
        ):
            errors.append(f"retired project tree write route composition: {relative_path}")
        if PROJECT_TREE_WRITE_PERMISSION_PATTERN.search(content):
            errors.append(f"retired project tree write permission: {relative_path}")
    return errors


def validate_inventory(repository: Path, inventory: dict) -> list[str]:
    errors = find_retired_cutover_runtime_surfaces(repository)
    errors.extend(find_retired_maintenance_runtime_surfaces(repository))
    errors.extend(find_retired_project_write_runtime_surfaces(repository))
    errors.extend(find_retired_project_tree_write_runtime_surfaces(repository))
    if set(inventory) != {"schemaVersion", "status", "items"}:
        errors.append("inventory must contain only schemaVersion, status, and items")
        return errors
    if inventory.get("schemaVersion") != 1:
        errors.append("inventory schemaVersion must be 1")
    items = inventory.get("items")
    if not isinstance(items, list):
        return [*errors, "inventory items must be a list"]

    keys: list[str] = []
    coverage: list[tuple[str, str, str]] = []
    known_requirements = _requirement_ids(repository)
    for item in items:
        if not isinstance(item, dict) or set(item) != {
            "objectKey",
            "classification",
            "requirementRefs",
            "codePaths",
            "requiredAction",
        }:
            errors.append("each inventory item has an invalid shape")
            continue
        key = item["objectKey"]
        classification = item["classification"]
        refs = item["requirementRefs"]
        paths = item["codePaths"]
        action = item["requiredAction"]
        if not isinstance(key, str) or not key:
            errors.append("inventory objectKey must be a non-empty string")
            continue
        keys.append(key)
        if classification not in ALLOWED_CLASSIFICATIONS:
            errors.append(f"{key} has invalid classification: {classification}")
        if not isinstance(refs, list) or any(not isinstance(ref, str) for ref in refs):
            errors.append(f"{key} requirementRefs must be a string list")
        else:
            for ref in refs:
                if not REQ_PATTERN.fullmatch(ref) or ref not in known_requirements:
                    errors.append(f"{key} has unknown Requirement: {ref}")
            if classification != "PLATFORM_UPSTREAM_UNCHANGED" and not refs:
                errors.append(f"{key} must reference at least one current Requirement")
        if not isinstance(action, str) or not action:
            errors.append(f"{key} requiredAction must be non-empty")
        if not isinstance(paths, list) or not paths:
            errors.append(f"{key} codePaths must be a non-empty list")
            continue
        for raw_path in paths:
            if not isinstance(raw_path, str) or "\\" in raw_path or raw_path.startswith("/"):
                errors.append(f"{key} has invalid codePath: {raw_path}")
                continue
            if ".." in PurePosixPath(raw_path).parts:
                errors.append(f"{key} has invalid codePath: {raw_path}")
                continue
            resolved_path = repository / raw_path
            path_exists = resolved_path.exists()
            if classification == "RUNTIME_RETIRED_DATA_PENDING_EVIDENCE":
                has_runtime_content = resolved_path.is_file() or (
                    resolved_path.is_dir()
                    and any(path.is_file() for path in resolved_path.rglob("*"))
                )
                if has_runtime_content:
                    errors.append(f"{key} retired runtime codePath still exists: {raw_path}")
                continue
            if not path_exists:
                errors.append(f"{key} codePath does not exist: {raw_path}")
                continue
            coverage.append((raw_path, key, classification))

    if keys != sorted(keys) or len(keys) != len(set(keys)):
        errors.append("inventory objectKey values must be sorted and unique")

    for candidate in _candidate_files(repository):
        matches = [entry for entry in coverage if candidate.startswith(entry[0])]
        if not matches:
            errors.append(f"uncovered implementation artifact: {candidate}")
            continue
        longest = max(len(entry[0]) for entry in matches)
        winners = {(entry[1], entry[2]) for entry in matches if len(entry[0]) == longest}
        if len(winners) != 1:
            errors.append(f"ambiguous implementation classification: {candidate}")

    has_blockers = any(
        isinstance(item, dict) and item.get("classification") in BLOCKING_CLASSIFICATIONS
        for item in items
    )
    if has_blockers and inventory.get("status") == "FEATURE_READY":
        errors.append("FEATURE_READY is forbidden while reconciliation items exist")
    if inventory.get("status") != "BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED":
        errors.append("inventory status must remain reconciliation-required")
    return errors


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    try:
        inventory = load_inventory(repository / "tasks/implementation-baseline-inventory.json")
        errors = validate_inventory(repository, inventory)
    except (OSError, UnicodeError, ValueError, subprocess.CalledProcessError) as exc:
        errors = [str(exc)]
    for error in errors:
        print(f"FAIL {error}")
    if errors:
        print(f"SUMMARY FAIL errors={len(errors)}")
        return 1
    print("SUMMARY PASS implementation inventory covers current tracked surfaces")
    return 0


if __name__ == "__main__":
    sys.exit(main())
