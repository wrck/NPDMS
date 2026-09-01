#!/usr/bin/env python3
"""Validate Delivery Unit claims and render their master projection.

Feature Spec and Feature Task remain the Ready and Implementation-status
authorities.  This module validates only the orthogonal write-ownership and
integration facts stored in ``tasks/delivery-units/DU-*.md``.
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


FIELD_RE = re.compile(r"^> ([^：]+)：`([^`]*)`$")
FEATURE_RE = re.compile(r"^F-[A-Z]+-\d{3}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
REQUIRED_FIELDS = (
    "DU状态",
    "DU类型",
    "Feature协调",
    "Task范围",
    "Owner",
    "分支",
    "Worktree",
    "认领基线",
    "认领提交",
    "修改边界",
    "串行资源",
    "旧功能范围",
    "验证",
    "集成记录",
)
ALLOWED_STATUSES = frozenset(
    {
        "PLANNED",
        "CLAIMED",
        "IN_PROGRESS",
        "HANDOFF_READY",
        "INTEGRATION_CANDIDATE",
        "INTEGRATED_PARTIAL",
        "INTEGRATED_COMPLETE",
        "BLOCKED",
        "QUARANTINED",
        "RELEASED",
    }
)
ACTIVE_WRITE_STATUSES = frozenset({"CLAIMED", "IN_PROGRESS", "HANDOFF_READY"})
ALLOWED_TYPES = frozenset({"FEATURE", "TASK", "MULTI_FEATURE_SLICE", "GOVERNANCE"})
ALLOWED_MODES = frozenset({"FEATURE_EXCLUSIVE", "TASK_COORDINATED"})
NO_VALUE = frozenset({"NONE", "N/A", "UNRESOLVED", "UNCONFIRMED"})


@dataclass(frozen=True)
class DeliveryUnit:
    path: Path
    unit_id: str
    fields: dict[str, str]

    @property
    def status(self) -> str:
        return self.fields.get("DU状态", "")

    @property
    def branch(self) -> str:
        return self.fields.get("分支", "")

    @property
    def active(self) -> bool:
        return self.status in ACTIVE_WRITE_STATUSES

    @property
    def feature_modes(self) -> dict[str, str]:
        raw = self.fields.get("Feature协调", "")
        if raw in NO_VALUE:
            return {}
        result: dict[str, str] = {}
        for item in split_values(raw):
            if "=" not in item:
                continue
            feature_id, mode = (value.strip() for value in item.split("=", 1))
            result[feature_id] = mode
        return result

    @property
    def boundaries(self) -> tuple[str, ...]:
        raw = self.fields.get("修改边界", "")
        return () if raw in NO_VALUE else tuple(split_values(raw))

    @property
    def legacy_scopes(self) -> frozenset[str]:
        raw = self.fields.get("旧功能范围", "")
        return frozenset() if raw in NO_VALUE else frozenset(split_values(raw))


def split_values(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(";") if item.strip()]


def parse_fields(content: str, source: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    for line in content.splitlines():
        match = FIELD_RE.match(line)
        if match:
            key, value = match.groups()
            if key in fields:
                raise ValueError(f"{source}: duplicate field {key}")
            fields[key] = value.strip()
    return fields


def parse_delivery_unit(path: Path) -> DeliveryUnit:
    fields = parse_fields(path.read_text(encoding="utf-8"), str(path))
    return DeliveryUnit(path=path, unit_id=path.stem, fields=fields)


def load_delivery_units(root: Path) -> list[DeliveryUnit]:
    return [parse_delivery_unit(path) for path in sorted(root.glob("DU-*.md"))]


def _valid_relative_pattern(raw: str) -> bool:
    if not raw or raw.startswith("/") or "\\" in raw:
        return False
    return ".." not in PurePosixPath(raw.replace("/**", "")).parts


def _patterns_overlap(left: str, right: str) -> bool:
    left_prefix = left.removesuffix("/**").rstrip("/")
    right_prefix = right.removesuffix("/**").rstrip("/")
    return (
        left_prefix == right_prefix
        or left_prefix.startswith(right_prefix + "/")
        or right_prefix.startswith(left_prefix + "/")
    )


def path_matches(pattern: str, path: str) -> bool:
    if pattern.endswith("/**"):
        prefix = pattern[:-3].rstrip("/")
        return path == prefix or path.startswith(prefix + "/")
    return fnmatch.fnmatchcase(path, pattern)


def _git(repository: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip()


def _resolve_claim_commit(repository: Path, unit: DeliveryUnit) -> str | None:
    raw = unit.fields.get("认领提交", "")
    if raw != "SELF":
        return raw if COMMIT_RE.fullmatch(raw) else None
    try:
        relative = unit.path.resolve().relative_to(repository.resolve()).as_posix()
    except ValueError:
        return None
    history = _git(
        repository,
        "log",
        "--reverse",
        "--format=%H",
        "--",
        relative,
        check=False,
    )
    commits = [line for line in history.splitlines() if COMMIT_RE.fullmatch(line)]
    previous_active = False
    claim_commit: str | None = None
    for commit in commits:
        content = _git(repository, "show", f"{commit}:{relative}", check=False)
        fields = parse_fields(content, f"{commit}:{relative}")
        current_active = fields.get("DU状态") in ACTIVE_WRITE_STATUSES
        if current_active and not previous_active and fields.get("认领提交") == "SELF":
            claim_commit = commit
        previous_active = current_active
    return claim_commit


def validate_delivery_units(
    repository: Path,
    units: list[DeliveryUnit],
    *,
    check_git: bool = True,
) -> list[str]:
    errors: list[str] = []
    ids = [unit.unit_id for unit in units]
    if ids != sorted(ids) or len(ids) != len(set(ids)):
        errors.append("Delivery Unit IDs must be sorted and unique")

    active_by_feature: dict[str, list[tuple[DeliveryUnit, str]]] = {}
    active_boundaries: list[tuple[DeliveryUnit, str]] = []
    for unit in units:
        missing = [field for field in REQUIRED_FIELDS if field not in unit.fields]
        unknown = sorted(set(unit.fields) - set(REQUIRED_FIELDS))
        if missing:
            errors.append(f"{unit.unit_id}: missing fields: {', '.join(missing)}")
        if unknown:
            errors.append(f"{unit.unit_id}: unknown fields: {', '.join(unknown)}")
        if unit.status not in ALLOWED_STATUSES:
            errors.append(f"{unit.unit_id}: invalid status {unit.status}")
        if unit.fields.get("DU类型") not in ALLOWED_TYPES:
            errors.append(f"{unit.unit_id}: invalid type {unit.fields.get('DU类型', '')}")

        feature_modes = unit.feature_modes
        if unit.fields.get("DU类型") != "GOVERNANCE" and not feature_modes:
            errors.append(f"{unit.unit_id}: at least one Feature coordination mapping is required")
        for feature_id, mode in feature_modes.items():
            if not FEATURE_RE.fullmatch(feature_id):
                errors.append(f"{unit.unit_id}: invalid Feature ID {feature_id}")
            if mode not in ALLOWED_MODES:
                errors.append(f"{unit.unit_id}: invalid coordination mode {mode}")
            if unit.active:
                active_by_feature.setdefault(feature_id, []).append((unit, mode))
                if not (repository / "tasks" / "features" / f"{feature_id}.md").is_file():
                    errors.append(f"{unit.unit_id}: active claim has no master Feature Task {feature_id}")

        for boundary in unit.boundaries:
            if not _valid_relative_pattern(boundary):
                errors.append(f"{unit.unit_id}: invalid write boundary {boundary}")
            if unit.active:
                active_boundaries.append((unit, boundary))

        if unit.active:
            for field in ("Owner", "分支", "Worktree", "认领基线", "认领提交", "修改边界"):
                if unit.fields.get(field, "") in NO_VALUE:
                    errors.append(f"{unit.unit_id}: active claim requires {field}")
            base = unit.fields.get("认领基线", "")
            if not COMMIT_RE.fullmatch(base):
                errors.append(f"{unit.unit_id}: claim base must be a full Git commit")
            claim = _resolve_claim_commit(repository, unit) if check_git else None
            raw_claim = unit.fields.get("认领提交", "")
            if raw_claim != "SELF" and not COMMIT_RE.fullmatch(raw_claim):
                errors.append(f"{unit.unit_id}: claim commit must be SELF or a full Git commit")
            if check_git and claim:
                branch_ref = f"refs/heads/{unit.branch}"
                branch_exists = bool(_git(repository, "show-ref", "--verify", branch_ref, check=False))
                if not branch_exists:
                    errors.append(f"{unit.unit_id}: claimed branch does not exist: {unit.branch}")
                elif subprocess.run(
                    ["git", "merge-base", "--is-ancestor", claim, unit.branch],
                    cwd=repository,
                    capture_output=True,
                ).returncode != 0:
                    errors.append(f"{unit.unit_id}: branch does not contain claim commit {claim}")

    for feature_id, claims in active_by_feature.items():
        if len(claims) > 1 and any(mode == "FEATURE_EXCLUSIVE" for _, mode in claims):
            errors.append(
                f"{feature_id}: FEATURE_EXCLUSIVE conflicts with another active Delivery Unit: "
                + ", ".join(unit.unit_id for unit, _ in claims)
            )

    for index, (left_unit, left_path) in enumerate(active_boundaries):
        for right_unit, right_path in active_boundaries[index + 1 :]:
            if left_unit.unit_id != right_unit.unit_id and _patterns_overlap(left_path, right_path):
                errors.append(
                    f"write boundary conflict: {left_unit.unit_id}:{left_path} <> "
                    f"{right_unit.unit_id}:{right_path}"
                )
    return errors


def validate_changed_paths(
    units: list[DeliveryUnit],
    *,
    branch: str,
    changed_paths: list[str],
    legacy_cutovers: list[dict],
) -> list[str]:
    """Reject unclaimed writes and writes based on undeclared deprecated surfaces."""
    if branch == "master":
        return []
    claims = [unit for unit in units if unit.active and unit.branch == branch]
    if changed_paths and not claims:
        return [f"branch {branch} has changes but no active Delivery Unit claim"]
    errors: list[str] = []
    boundaries = [boundary for unit in claims for boundary in unit.boundaries]
    legacy_scopes = frozenset(scope for unit in claims for scope in unit.legacy_scopes)
    for path in changed_paths:
        if not any(path_matches(pattern, path) for pattern in boundaries):
            errors.append(f"changed path is outside claimed boundaries: {path}")
        for cutover in legacy_cutovers:
            if any(path_matches(pattern, path) for pattern in cutover.get("legacyPaths", [])):
                legacy_key = cutover.get("legacyKey", "")
                if legacy_key not in legacy_scopes:
                    errors.append(
                        f"deprecated path requires explicit legacy scope {legacy_key}: {path}"
                    )
    return errors


def render_index(units: list[DeliveryUnit]) -> str:
    lines = [
        "# Delivery Unit认领与集成矩阵",
        "",
        "本文件由`scripts/validate_delivery_units.py --write-index`从同目录`DU-*.md`生成。",
        "它只投影认领、写边界和集成状态；Feature Ready与Implementation状态仍分别由Feature Spec和Feature Task维护。",
        "",
        "| Delivery Unit | 状态 | 类型 | Feature协调 | Owner | 分支 | 认领提交 | 集成记录 |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for unit in units:
        fields = unit.fields
        lines.append(
            "| [{id}]({file}) | {status} | {type_} | {features} | {owner} | `{branch}` | `{claim}` | {integration} |".format(
                id=unit.unit_id,
                file=unit.path.name,
                status=fields.get("DU状态", ""),
                type_=fields.get("DU类型", ""),
                features=fields.get("Feature协调", ""),
                owner=fields.get("Owner", ""),
                branch=fields.get("分支", ""),
                claim=fields.get("认领提交", ""),
                integration=fields.get("集成记录", ""),
            )
        )
    lines.extend(
        [
            "",
            "## 判读规则",
            "",
            "- `CLAIMED / IN_PROGRESS / HANDOFF_READY`才占用写边界；其他状态不构成当前实施认领。",
            "- `QUARANTINED`是审计隔离，不追认历史分支的实施授权。",
            "- `INTEGRATION_CANDIDATE`只表示存在可复核提交范围，不产生Feature Done。",
            "- 一个Delivery Unit可以覆盖多个Feature；每个Feature仍只能在Feature Task中产生一次Implementation Done。",
            "",
        ]
    )
    return "\n".join(lines)


def _load_legacy_cutovers(repository: Path) -> list[dict]:
    path = repository / "tasks" / "implementation-baseline-inventory.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    value = payload.get("legacyCutovers", [])
    return value if isinstance(value, list) else []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--base-ref")
    parser.add_argument("--write-index", action="store_true")
    parser.add_argument("--check-index", action="store_true")
    args = parser.parse_args()

    repository = (args.repository or Path(__file__).resolve().parents[1]).resolve()
    unit_root = repository / "tasks" / "delivery-units"
    try:
        units = load_delivery_units(unit_root)
        errors = validate_delivery_units(repository, units)
        index_path = unit_root / "README.md"
        rendered = render_index(units)
        if args.write_index:
            index_path.write_text(rendered, encoding="utf-8", newline="\n")
        if args.check_index and (
            not index_path.is_file() or index_path.read_text(encoding="utf-8") != rendered
        ):
            errors.append("tasks/delivery-units/README.md is stale")
        if args.base_ref:
            branch = _git(repository, "branch", "--show-current")
            changed = _git(repository, "diff", "--name-only", args.base_ref, "--").splitlines()
            errors.extend(
                validate_changed_paths(
                    units,
                    branch=branch,
                    changed_paths=[path.replace("\\", "/") for path in changed if path],
                    legacy_cutovers=_load_legacy_cutovers(repository),
                )
            )
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as exc:
        errors = [str(exc)]

    for error in errors:
        print(f"FAIL {error}")
    if errors:
        print(f"SUMMARY FAIL errors={len(errors)}")
        return 1
    print(f"SUMMARY PASS delivery_units={len(units)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
