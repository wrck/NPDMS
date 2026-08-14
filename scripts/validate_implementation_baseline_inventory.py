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


def validate_inventory(repository: Path, inventory: dict) -> list[str]:
    errors: list[str] = []
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
