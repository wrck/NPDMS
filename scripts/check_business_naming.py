#!/usr/bin/env python3
"""Check active Phase 1 documents for obsolete Field=implementation names."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


OBSOLETE = re.compile(r"\bField(?:Execution|QualityCheck|SafetyCheck)(?:State|Workflow|Resource|Action|Permission|Module|Context)?\b|Field Execution|field-execution|field_execution")
TECHNICAL_FIELD = re.compile(r"\bfield\b|字段权限|字段映射|字段定义|字段校验|字段类型|字段值|数据库字段|表单字段|接口字段|自定义字段|必填字段|敏感字段")
DEFAULT_ACTIVE = (
    "docs/design/02-domain-model.md",
    "docs/design/02a-context-map.md",
    "docs/design/02b-aggregate-boundary-decisions.md",
    "docs/design/02c-data-ownership-matrix.md",
    "docs/design/02d-cross-context-contracts.md",
    "docs/design/02e-version-scope-matrix.md",
    "docs/design/04-module-design.md",
    "docs/design/05-state-machine.md",
    "docs/design/06-workflow-design.md",
    "docs/design/07-authorization-design.md",
    "docs/traceability/requirement-matrix.md",
    "scripts/generate_requirement_traceability.py",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()
    obsolete: list[str] = []
    technical = 0
    for relative in DEFAULT_ACTIVE:
        path = args.root / relative
        if not path.exists():
            obsolete.append(f"MISSING {relative}")
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
            if OBSOLETE.search(line):
                obsolete.append(f"{relative}:{number}: {line.strip()}")
            technical += len(TECHNICAL_FIELD.findall(line))
    if obsolete:
        print("NAMING_GATE=FAIL")
        print("\n".join(obsolete))
        return 1
    print("NAMING_GATE=PASS")
    print(f"active_business_field_identifiers=0 technical_field_matches={technical}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
