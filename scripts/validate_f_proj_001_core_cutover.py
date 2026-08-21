#!/usr/bin/env python3
"""Validate the F-PROJ-001 additive core-cutover prerequisite.

Requirement refs: PM-01, PM-03.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


REQUIRED_TABLES = (
    "proj_project",
    "proj_project_task",
    "proj_project_template_task_definition",
    "proj_project_task_execution_contract",
    "proj_project_member_assignment",
    "acc_project_deliverable",
)
FORBIDDEN_WRITE_PATTERNS = (
    re.compile(r'@TableName\(\s*"pms_project[^"]*"'),
    re.compile(r"\bINSERT\s+INTO\s+pms_project", re.IGNORECASE),
    re.compile(r"\bUPDATE\s+pms_project", re.IGNORECASE),
)
SOURCE_ROOTS = ("pms-module-project", "pms-module-engineering", "yudao-server")


def validate_repository(repository: Path) -> list[str]:
    errors: list[str] = []
    migration = repository / "sql/migrations/V51__f_proj_001_core_project_write_model.sql"
    if not migration.is_file():
        errors.append(f"missing migration: {migration.relative_to(repository)}")
    else:
        sql = migration.read_text(encoding="utf-8")
        for table in REQUIRED_TABLES:
            creates_table = re.search(
                rf"\bCREATE\s+TABLE\s+{re.escape(table)}\b", sql, re.IGNORECASE
            )
            renames_to_table = re.search(rf"\bTO\s+{re.escape(table)}\b", sql, re.IGNORECASE)
            if not creates_table and not renames_to_table:
                errors.append(f"missing formal table: {table}")
        if re.search(r"\bDROP\s+TABLE\b", sql, re.IGNORECASE):
            errors.append("V51 must preserve data: DROP TABLE is forbidden")
        if re.search(r"\bCREATE\s+TABLE\s+pms_project", sql, re.IGNORECASE):
            errors.append("V51 must not create a new pms_project* write model")

    for root_name in SOURCE_ROOTS:
        source_root = repository / root_name
        if not source_root.exists():
            continue
        for path in source_root.rglob("*"):
            if path.suffix not in {".java", ".xml"} or not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            for pattern in FORBIDDEN_WRITE_PATTERNS:
                if pattern.search(text):
                    errors.append(
                        f"legacy project write reference: {path.relative_to(repository).as_posix()}"
                    )
                    break
    return errors


def main() -> int:
    repository = Path(__file__).resolve().parents[1]
    errors = validate_repository(repository)
    if errors:
        for error in errors:
            print(f"FAIL {error}")
        return 1
    print("PASS F-PROJ-001 core cutover contract")
    return 0


if __name__ == "__main__":
    sys.exit(main())
