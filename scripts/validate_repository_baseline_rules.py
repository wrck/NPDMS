#!/usr/bin/env python3
"""Validate that NPDMS uses one repository for specifications and implementation."""

from __future__ import annotations

import sys
from pathlib import Path


AGENTS_MARKERS = (
    "本仓库是业务、设计、实现、测试与验收证据的唯一事实源",
    "PRD > Engineering Constitution > SDS > Feature Spec > Technical Plan > Task > Code > Test / Runtime Evidence",
    "specs/features/",
    "tasks/features/",
    "BLOCKED_BY_SPEC",
    "JDK 25",
    "宿主机",
    "真实浏览器",
)
TASK_MARKERS = (
    "状态：SUPERSEDED",
    "仅用于历史追溯",
    "不再生成或驱动新开发任务",
    "specs/features/",
    "tasks/features/",
)
REQUIRED_PATHS = (
    "docs/baseline/prd-v1.8.md",
    "docs/engineering/00-engineering-chain.md",
    "docs/README.md",
    "specs/features/README.md",
    "tasks/features",
)
OBSOLETE_PATHS = (
    "docs/specification-baseline",
    ".spec-repo-f-ast-001",
    "scripts/sync_specification_baseline.py",
    "scripts/specification_baseline.py",
    "scripts/validate_specification_baseline.py",
)


def _read(path: Path, errors: list[str]) -> str:
    try:
        return path.read_text(encoding="utf-8-sig")
    except (OSError, UnicodeError) as exc:
        errors.append(f"cannot read {path.as_posix()}: {exc}")
        return ""


def validate_repository_rules(repository: Path) -> list[str]:
    errors: list[str] = []
    agents = _read(repository / "AGENTS.md", errors)
    for marker in AGENTS_MARKERS:
        if marker not in agents:
            errors.append(f"AGENTS.md missing required rule: {marker}")
    if "规格仓库是业务与设计唯一事实源" in agents or "禁止在NPDMS直接修改受管快照" in agents:
        errors.append("AGENTS.md still declares the retired external specification workflow")

    for relative in REQUIRED_PATHS:
        if not (repository / relative).exists():
            errors.append(f"required same-repository source missing: {relative}")
    for relative in OBSOLETE_PATHS:
        if (repository / relative).exists():
            errors.append(f"retired specification synchronization path still exists: {relative}")

    for relative in ("tasks/plan.md", "tasks/todo.md"):
        content = _read(repository / relative, errors)
        prefix = "\n".join(content.splitlines()[:14])
        for marker in TASK_MARKERS:
            if marker not in prefix:
                errors.append(f"{relative} header missing SUPERSEDED rule: {marker}")
        if "状态：FEATURE_READY" in prefix:
            errors.append(f"{relative} must not claim current FEATURE_READY")
    return errors


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    errors = validate_repository_rules(repository)
    for error in errors:
        print(f"FAIL {error}")
    if errors:
        print(f"SUMMARY FAIL errors={len(errors)}")
        return 1
    print("SUMMARY PASS repository uses same-repository specification governance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
