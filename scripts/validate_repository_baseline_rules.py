#!/usr/bin/env python3
"""Validate that NPDMS reads the locked specification snapshot, not legacy tasks."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


AGENTS_MARKERS = (
    "规格仓库是业务与设计唯一事实源",
    "本地规格快照是锁定实现输入",
    "docs/specification-baseline/manifest.json",
    "source.commit",
    "禁止在NPDMS直接修改受管快照",
    "JDK 25",
    "宿主机",
    "真实浏览器",
)
TASK_MARKERS = (
    "状态：SUPERSEDED",
    "仅用于历史追溯",
    "不再生成或驱动新开发任务",
    "docs/specification-baseline/manifest.json",
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
    if "specs/001-project-delivery-platform/` 是需求与接口的唯一事实来源" in agents:
        errors.append("AGENTS.md still declares the local specs directory as the sole source")

    manifest_path = repository / "docs/specification-baseline/manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        commit = manifest["source"]["commit"]
        if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-f]{40}", commit):
            errors.append("manifest source.commit must be a lowercase full commit id")
    except (OSError, UnicodeError, json.JSONDecodeError, KeyError, TypeError) as exc:
        errors.append(f"invalid specification manifest: {exc}")

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
    print("SUMMARY PASS repository reads the locked specification baseline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
