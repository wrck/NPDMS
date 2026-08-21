#!/usr/bin/env python3
"""Static F-PROJ-001 schema contract validator (PM-01, PM-03)."""

from __future__ import annotations

import re
import sys
from pathlib import Path


REQUIRED_TABLES = {
    "proj_project",
    "proj_project_template_revision",
    "proj_project_template_task_definition",
    "proj_project_stage_snapshot",
    "proj_project_task",
    "proj_project_task_execution_contract",
    "proj_project_member_assignment",
    "acc_project_deliverable",
    "plt_business_code_rule",
    "plt_idempotency_record",
    "plt_operation_audit",
    "plt_outbox_event",
}
FORBIDDEN_TABLES = {"proj_project_creation_draft", "pms_project_creation_draft"}
REQUIRED_V60_COLUMNS = {
    "proj_project_template_revision": {
        "template_id", "template_code", "template_name", "applicability_snapshot",
        "business_scene_code", "match_priority", "default_flag",
        "workflow_definition_key", "workflow_definition_version", "definition_snapshot",
        "content_sha256", "effective_from", "effective_to", "status",
    },
    "proj_project_stage_snapshot": {
        "project_id", "stage_code", "stage_name", "snapshot_no", "sort_order",
        "template_revision_id", "workflow_definition_key", "workflow_definition_version",
        "entry_rule_snapshot", "exit_rule_snapshot", "stage_status",
    },
    "proj_project": {
        "template_revision_id", "workflow_definition_key", "workflow_definition_version",
        "current_stage_code", "assignment_status", "create_reason",
    },
    "proj_project_task": {
        "stage_definition_key", "task_definition_key", "task_kind_code",
        "milestone_definition_key", "template_task_definition_id", "status_machine_version",
    },
    "acc_project_deliverable": {
        "template_requirement_key", "source_template_revision_id",
        "applicable_stage_code", "required_flag",
    },
}
REQUIRED_V60_INDEXES = {
    "uk_project_template_revision",
    "uk_project_template_revision_code",
    "uk_project_stage_snapshot",
    "uk_acc_project_deliverable_requirement",
    "uk_business_code_rule_version",
    "uk_business_code_rule_current",
    "uk_idempotency_record_key",
    "uk_outbox_event_id",
}


def parse_tables(sql: str) -> set[str]:
    created = set(re.findall(r"\bCREATE\s+TABLE\s+`?([a-z0-9_]+)`?", sql, re.IGNORECASE))
    renamed = set(re.findall(r"\bTO\s+`?([a-z0-9_]+)`?", sql, re.IGNORECASE))
    return {table.lower() for table in created | renamed}


def parse_v60_columns(sql: str) -> dict[str, set[str]]:
    columns: dict[str, set[str]] = {}
    for statement in sql.split(";"):
        table_match = re.search(r"\b(?:CREATE|ALTER)\s+TABLE\s+`?([a-z0-9_]+)`?", statement, re.IGNORECASE)
        if not table_match:
            continue
        table = table_match.group(1).lower()
        table_columns = columns.setdefault(table, set())
        table_columns.update(
            name.lower()
            for name in re.findall(r"\bADD\s+COLUMN\s+`?([a-z0-9_]+)`?", statement, re.IGNORECASE)
        )
        table_columns.update(
            name.lower()
            for name in re.findall(
                r"\bCHANGE\s+COLUMN\s+`?[a-z0-9_]+`?\s+`?([a-z0-9_]+)`?",
                statement,
                re.IGNORECASE,
            )
        )
        table_columns.update(
            name.lower()
            for name in re.findall(r"\bMODIFY\s+COLUMN\s+`?([a-z0-9_]+)`?", statement, re.IGNORECASE)
        )
        if re.search(r"\bCREATE\s+TABLE\b", statement, re.IGNORECASE):
            table_columns.update(
                name.lower()
                for name in re.findall(r"^\s*`?([a-z][a-z0-9_]*)`?\s+[A-Z]+", statement, re.IGNORECASE | re.MULTILINE)
                if name.lower() not in {"create", "primary", "unique", "key", "constraint"}
            )
    return columns


def validate_repository(repository: Path) -> list[str]:
    migrations = repository / "sql/migrations"
    v60 = migrations / "V60__f_proj_001_manual_project_creation.sql"
    if not v60.is_file():
        return ["missing migration: sql/migrations/V60__f_proj_001_manual_project_creation.sql"]
    v60_sql = v60.read_text(encoding="utf-8")
    feature_sql = "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(migrations.glob("V5[1-3]__*.sql")) + [v60]
    )
    tables = parse_tables(feature_sql)
    errors = [f"missing required table: {table}" for table in sorted(REQUIRED_TABLES - tables)]
    errors.extend(f"forbidden draft table: {table}" for table in sorted(FORBIDDEN_TABLES & tables))
    v60_columns = parse_v60_columns(v60_sql)
    for table, required_columns in REQUIRED_V60_COLUMNS.items():
        missing_columns = required_columns - v60_columns.get(table, set())
        errors.extend(f"missing required column: {table}.{column}" for column in sorted(missing_columns))
    index_names = {
        name.lower()
        for name in re.findall(r"\b(?:KEY|INDEX)\s+`?([a-z0-9_]+)`?", v60_sql, re.IGNORECASE)
    }
    index_names.update(
        name.lower()
        for name in re.findall(
            r"\bRENAME\s+INDEX\s+`?[a-z0-9_]+`?\s+TO\s+`?([a-z0-9_]+)`?",
            v60_sql,
            re.IGNORECASE,
        )
    )
    errors.extend(f"missing required index: {name}" for name in sorted(REQUIRED_V60_INDEXES - index_names))
    if re.search(r"\b(?:CREATE|RENAME)\s+TABLE\b[^;]*\bpms_project", v60_sql, re.IGNORECASE | re.DOTALL):
        errors.append("V60 must not reintroduce a pms_project* carrier")
    if re.search(r"\b(?:MP|NPMS)[-_]?\d*\b", v60_sql, re.IGNORECASE):
        errors.append("V60 must not seed a production business-code prefix")
    return errors


def main() -> int:
    errors = validate_repository(Path(__file__).resolve().parents[1])
    if errors:
        for error in errors:
            print(f"FAIL {error}")
        return 1
    print("PASS F-PROJ-001 schema contract")
    return 0


if __name__ == "__main__":
    sys.exit(main())
