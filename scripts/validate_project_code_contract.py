#!/usr/bin/env python3
"""Validate ADR-0020's project identity and code namespace contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/project-code-contract.json")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
EXPECTED_REQUIREMENTS = {"PM-01", "PM-02", "INT-01", "COM-01"}
EXPECTED_COLUMNS = {"code_root_id", "project_sequence", "code_rule_version"}
EXPECTED_DDL_ITEMS = {
    "COLUMN:proj_project:code_root_id",
    "COLUMN:proj_project:project_sequence",
    "COLUMN:proj_project:code_rule_version",
    "CONSTRAINT:proj_project:uk_project_code",
    "CONSTRAINT:proj_project:uk_project_code_sequence",
    "CONSTRAINT:proj_project:fk_project_code_root",
    "CONSTRAINT:proj_project:chk_project_code_namespace",
}
EXPECTED_RULES = {
    "crmProjectCodeIsDefault": True,
    "multipleContractsOrOrdersCreateNewCode": False,
    "splitRequiresIndependentDeliveryBoundary": True,
    "projectCodeMutableAfterCreation": False,
    "hierarchyEncodedInProjectCode": False,
    "childCodeFormat": "<code-root-project-code>-SP<permanent-sequence>",
    "sequenceScope": ["tenant_id", "code_root_id"],
    "sequenceReusable": False,
}


def load_contract(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def project_table_body(ddl: str) -> str:
    match = re.search(r"CREATE\s+TABLE\s+proj_project\s*\((.*?)\)\s*ENGINE\s*=", ddl, re.I | re.S)
    return match.group(1) if match else ""


def validate_payload(payload: dict[str, object]) -> list[str]:
    errors: list[str] = []
    if payload.get("schemaVersion") != 1 or payload.get("status") != "ACCEPTED" or payload.get("decisionRef") != "ADR-0020":
        errors.append("project code contract metadata mismatch")
    if set(payload.get("requirementIds", [])) != EXPECTED_REQUIREMENTS:
        errors.append("project code requirement set mismatch")
    if payload.get("rules") != EXPECTED_RULES:
        errors.append("project code rules differ from ADR-0020")
    columns = payload.get("columns", [])
    if not isinstance(columns, list):
        errors.append("project code columns must be a list")
    else:
        names = {item.get("name") for item in columns if isinstance(item, dict)}
        tables = {item.get("table") for item in columns if isinstance(item, dict)}
        if names != EXPECTED_COLUMNS or tables != {"proj_project"}:
            errors.append("project code column contract mismatch")
    if set(payload.get("acceptedDdlItems", [])) != EXPECTED_DDL_ITEMS:
        errors.append("project code accepted DDL item set mismatch")
    return errors


def validate_ddl(ddl: str) -> list[str]:
    errors: list[str] = []
    body = project_table_body(ddl)
    if not body:
        return ["proj_project table not found"]
    for column in EXPECTED_COLUMNS:
        if re.search(rf"(?m)^\s*{column}\s+", body) is None:
            errors.append(f"missing project code column: {column}")

    required_patterns = {
        "project code tenant uniqueness": r"UNIQUE\s+KEY\s+uk_project_code\s*\(\s*tenant_id\s*,\s*project_code\s*\)",
        "permanent namespace sequence uniqueness": r"UNIQUE\s+KEY\s+uk_project_code_sequence\s*\(\s*tenant_id\s*,\s*code_root_id\s*,\s*project_sequence\s*\)",
        "code namespace root foreign key": r"CONSTRAINT\s+fk_project_code_root\s+FOREIGN\s+KEY\s*\(\s*tenant_id\s*,\s*code_root_id\s*\)\s+REFERENCES\s+proj_project\s*\(\s*tenant_id\s*,\s*id\s*\)",
        "code namespace check": r"CONSTRAINT\s+chk_project_code_namespace\s+CHECK\s*\(\s*\(\s*project_sequence\s*=\s*0\s+AND\s+code_root_id\s*=\s*id\s*\)\s+OR\s+project_sequence\s*>\s*0\s*\)",
    }
    for description, pattern in required_patterns.items():
        if re.search(pattern, body, re.I | re.S) is None:
            errors.append(f"missing or invalid {description}")
    if re.search(r"UNIQUE\s+KEY\s+uk_project_code\s*\([^)]*project_type", body, re.I | re.S):
        errors.append("project_type must not participate in project code uniqueness")
    return errors


def validate_contract(root: Path) -> list[str]:
    payload = load_contract(root / CONTRACT)
    ddl = (root / DDL).read_text(encoding="utf-8-sig")
    return validate_payload(payload) + validate_ddl(ddl)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    root = args.root.resolve()
    errors = validate_contract(root)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] project code identity and namespace contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
