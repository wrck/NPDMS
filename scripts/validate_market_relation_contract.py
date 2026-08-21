#!/usr/bin/env python3
"""Validate ADR-0021 customer market-relation classification contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/market-relation-contract.json")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
MAPPING = Path("specs/001-project-delivery-platform/evidence/migration/legacy-physical-field-canonical.jsonl")
FIELDS = {
    "market_code", "market_name", "system_code", "system_name",
    "expend_code", "expend_name", "industry_code", "industry_name",
}


def table_body(ddl: str, table: str) -> str:
    match = re.search(rf"CREATE\s+TABLE\s+{table}\s*\((.*?)\)\s*ENGINE\s*=", ddl, re.I | re.S)
    return match.group(1) if match else ""


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    contract = json.loads((root / CONTRACT).read_text(encoding="utf-8"))
    if contract.get("schemaVersion") != 1 or contract.get("status") != "ACCEPTED" or contract.get("decisionRef") != "ADR-0021":
        errors.append("market relation contract metadata mismatch")
    if contract.get("owner") != "CUS" or contract.get("targetTable") != "cus_market_relation":
        errors.append("market relation owner or table mismatch")
    if set(contract.get("businessFields", [])) != FIELDS:
        errors.append("market relation business field set mismatch")
    storage = contract.get("objectStorage", {})
    if storage.get("project", {}).get("relationIdStored") is not False or storage.get("customer", {}).get("relationIdStored") is not False:
        errors.append("project and customer must not store market relation id")

    ddl = (root / DDL).read_text(encoding="utf-8-sig")
    for table in ("cus_market_relation", "cus_customer", "proj_project"):
        body = table_body(ddl, table)
        if not body:
            errors.append(f"missing table: {table}")
            continue
        for field in FIELDS:
            if re.search(rf"(?m)^\s*{field}\s+", body) is None:
                errors.append(f"missing market relation field: {table}.{field}")
        if re.search(r"(?m)^\s*(?:market_)?relation_id\s+", body):
            errors.append(f"relation id must not be stored in {table}")

    rows = [json.loads(line) for line in (root / MAPPING).read_text(encoding="utf-8").splitlines() if line]
    by_source = {(row.get("sourceTable"), row.get("sourceColumn")): row for row in rows}
    expected = {
        (item["sourceTable"], item["sourceColumn"]): item["target"]
        for item in contract["sourceMappingOverrides"]
    }
    for key, target in expected.items():
        row = by_source.get(key)
        if row is None or row.get("targets") != [target] or row.get("decisionBasis") != "ADR-0021 and source data-element evidence":
            errors.append(f"market relation source mapping mismatch: {key[0]}.{key[1]}")
    forbidden = {
        "proj_project_company_department_relation.department_code",
        "proj_project_company_department_relation.department_id",
        "proj_project_company_department_relation.department_name",
    }
    for column in ("column004", "column005", "column006"):
        row = by_source.get(("pm_project", column), {})
        if forbidden & set(row.get("targets", [])):
            errors.append(f"market classification still mapped as organization: pm_project.{column}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] ADR-0021 customer market relation contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
