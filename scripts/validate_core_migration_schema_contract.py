#!/usr/bin/env python3
"""Validate ADR-0022's core migration schema and key policies."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
V3_DESIGN_ONLY_TABLES = {
    "kno_device_technical_advisory_match",
    "kno_technical_advisory",
    "kno_technical_advisory_product_relation",
    "kno_technical_advisory_read_record",
}
EXPECTED_NORMALIZATION = {
    "businessCode": "TRIM_UPPERCASE",
    "opaqueExternalKey": "BINARY_EXACT",
    "hash": "BINARY_EXACT",
    "displayName": "UNICODE_CASE_INSENSITIVE",
}
EXPECTED_ACCEPTED_DDL_ITEMS = {
    "COLUMN:plt_external_key_mapping:target_role",
    "COLUMN:plt_external_key_mapping:target_sequence",
    "CONSTRAINT:plt_external_key_mapping:uk_external_key_source_target",
    "CONSTRAINT:plt_external_key_mapping:chk_external_key_target_sequence",
}


def parse_tables(ddl: str) -> dict[str, str]:
    return {
        match.group(1): match.group(2)
        for match in re.finditer(
            r"CREATE\s+TABLE\s+(\w+)\s*\((.*?)\)\s*ENGINE\s*=",
            ddl,
            re.IGNORECASE | re.DOTALL,
        )
    }


def unique_keys(body: str) -> list[tuple[str, str]]:
    return [
        (match.group(1), " ".join(match.group(2).split()))
        for match in re.finditer(r"UNIQUE\s+KEY\s+(\w+)\s*\((.*?)\)", body, re.IGNORECASE | re.DOTALL)
    ]


def validate_contract(contract: dict[str, object], ddl: str) -> list[str]:
    errors: list[str] = []
    tables = parse_tables(ddl)

    if contract.get("schemaVersion") != 1 or contract.get("decisionRef") != "ADR-0022":
        errors.append("core migration contract metadata mismatch")
    if contract.get("coverage") != "CORE_MIGRATION_SUBSET":
        errors.append("coverage must be CORE_MIGRATION_SUBSET, never a full-platform claim")
    if contract.get("crossDomainReferencePolicy") != "LOGICAL_REFERENCE":
        errors.append("cross-domain reference policy must be LOGICAL_REFERENCE")
    if set(contract.get("v3DesignOnlyTables", [])) != V3_DESIGN_ONLY_TABLES:
        errors.append("V3 design-only table set mismatch")
    if set(contract.get("acceptedDdlItems", [])) != EXPECTED_ACCEPTED_DDL_ITEMS:
        errors.append("ADR-0022 accepted DDL item set mismatch")

    present_v3 = sorted(V3_DESIGN_ONLY_TABLES & tables.keys())
    if present_v3:
        errors.append(f"V3 design-only tables must not appear in core DDL: {present_v3}")

    for table, body in tables.items():
        owner = table.split("_", 1)[0]
        for match in re.finditer(
            r"CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)",
            body,
            re.IGNORECASE | re.DOTALL,
        ):
            target = match.group(2)
            if owner != target.split("_", 1)[0]:
                errors.append(f"cross-domain foreign key {match.group(1)}: {table} -> {target}")

    mapping = contract.get("externalKeyMapping", {})
    mapping_body = tables.get(str(mapping.get("table", "")), "") if isinstance(mapping, dict) else ""
    for column in ("target_role", "target_sequence"):
        if not re.search(rf"(?m)^\s*{column}\s+", mapping_body, re.IGNORECASE):
            errors.append(f"plt_external_key_mapping missing {column}")
    if mapping_body:
        if not re.search(r"target_role\s+VARCHAR\(32\)\s+NOT\s+NULL\s+DEFAULT\s+'PRIMARY'", mapping_body, re.IGNORECASE):
            errors.append("target_role must default to PRIMARY")
        if not re.search(r"target_sequence\s+INT\s+UNSIGNED\s+NOT\s+NULL\s+DEFAULT\s+0", mapping_body, re.IGNORECASE):
            errors.append("target_sequence must be unsigned and default to 0")
        mapping_unique = dict(unique_keys(mapping_body)).get("uk_external_key_source_target", "")
        for column in ("target_role", "target_sequence", "target_table", "target_id"):
            if column not in mapping_unique:
                errors.append(f"external key unique constraint missing {column}")
        if not re.search(r"CHECK\s*\(\s*target_sequence\s*>=\s*0\s*\)", mapping_body, re.IGNORECASE):
            errors.append("target_sequence non-negative CHECK missing")

    normalization = contract.get("normalization", {})
    if not isinstance(normalization, dict) or any(normalization.get(k) != v for k, v in EXPECTED_NORMALIZATION.items()):
        errors.append("normalization policy mismatch")
    permanent = contract.get("permanentKeys", {})
    if not isinstance(permanent, dict) or permanent.get("reuseAllowed") is not False or permanent.get("uniqueKeyIncludesDeleted") is not False:
        errors.append("permanent business key policy mismatch")

    permanent_names = set(permanent.get("ddlUniqueKeys", [])) if isinstance(permanent, dict) else set()
    for table, body in tables.items():
        for name, columns in unique_keys(body):
            if (name in permanent_names or "source" in name) and re.search(r"\bdeleted\b", columns, re.IGNORECASE):
                errors.append(f"permanent/source unique key must not include deleted: {table}.{name}")
            if re.search(r"\beffective_to\b", columns, re.IGNORECASE):
                errors.append(f"current uniqueness must not depend on nullable effective_to: {table}.{name}")

    if contract.get("currentRecordUniqueness") != "GENERATED_CURRENT_MARKER":
        errors.append("current record uniqueness policy mismatch")
    if contract.get("historicalAnomalyPolicy") != "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE":
        errors.append("historical anomaly policy mismatch")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=CONTRACT)
    parser.add_argument("--ddl", type=Path, default=DDL)
    args = parser.parse_args()
    errors = validate_contract(
        json.loads(args.contract.read_text(encoding="utf-8")),
        args.ddl.read_text(encoding="utf-8"),
    )
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] ADR-0022 core migration schema contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
