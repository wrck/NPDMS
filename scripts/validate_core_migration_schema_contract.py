#!/usr/bin/env python3
"""Validate ADR-0022's core migration schema and key policies."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
EXECUTION_EVIDENCE = Path("specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json")
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
EXPECTED_Q03_FACTS = {
    "deviceProjectAssignment": "ONE_CURRENT_DIRECT_PROJECT_PER_DEVICE",
    "customerPrimaryContact": "ONE_CURRENT_PRIMARY_CONTACT_PER_CUSTOMER",
    "projectPrimaryCompanyDepartment": "ONE_CURRENT_PRIMARY_RELATION_PER_PROJECT_ROLE",
    "deliveryScope": "ONE_CURRENT_HEADER_PER_PROJECT_ORDER_LINE_WITH_DETAILS",
    "orderExecution": "MULTIPLE_PRIMARY_EXECUTIONS_ALLOWED",
}
Q03_CURRENT_MARKERS = {
    "ast_device_project_assignment": (
        "current_device_id", "uk_device_current_assignment", {"tenant_id", "current_device_id"}
    ),
    "cus_customer_contact": (
        "primary_customer_id", "uk_customer_primary_contact", {"tenant_id", "primary_customer_id"}
    ),
    "proj_project_company_department_relation": (
        "primary_project_id", "uk_project_primary_company_department", {"tenant_id", "primary_project_id", "relation_role"}
    ),
    "com_delivery_scope": (
        "current_order_line_id", "uk_scope_current", {"tenant_id", "project_id", "current_order_line_id"}
    ),
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

    for match in re.finditer(r",\s*\)\s*ENGINE\s*=", ddl, re.IGNORECASE | re.DOTALL):
        line = ddl.count("\n", 0, match.start()) + 1
        errors.append(f"DDL trailing comma before table close at line {line}")

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
    if contract.get("q03CurrentBusinessFacts") != EXPECTED_Q03_FACTS:
        errors.append("ADR-0023 Q03 current business fact set mismatch")

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
    for table, (marker, unique_name, expected_columns) in Q03_CURRENT_MARKERS.items():
        body = tables.get(table, "")
        if not body:
            errors.append(f"Q03 current relation table missing: {table}")
            continue
        generated = re.search(
            rf"\b{re.escape(marker)}\b[^\n]*GENERATED\s+ALWAYS\s+AS\s*\((.*?)\)\s*STORED",
            body,
            re.IGNORECASE | re.DOTALL,
        )
        if not generated:
            errors.append(f"Q03 generated current marker missing: {table}.{marker}")
        elif re.search(r"\b(?:status|scope_status|assignment_status)\b", generated.group(1), re.IGNORECASE):
            errors.append(f"Q03 current marker must not depend on extendable status: {table}.{marker}")
        unique_columns = dict(unique_keys(body)).get(unique_name)
        if unique_columns is None:
            errors.append(f"Q03 current unique key missing: {table}.{unique_name}")
        else:
            actual_columns = {value.strip().lower() for value in unique_columns.split(",")}
            if actual_columns != expected_columns:
                errors.append(f"Q03 current unique key grain mismatch: {table}.{unique_name}")

    if "com_delivery_scope_detail" not in tables:
        errors.append("Q03 delivery scope detail table missing")

    order_execution = tables.get("com_order_execution_relation", "")
    if not order_execution:
        errors.append("Q03 order execution relation table missing")
    else:
        if re.search(r"(?m)^\s*primary_order_id\s+", order_execution, re.IGNORECASE):
            errors.append("Q03 multiple primary executions must not use a unique primary marker")
        if not re.search(r"is_primary\s+TINYINT\s+NOT\s+NULL\s+DEFAULT\s+1", order_execution, re.IGNORECASE):
            errors.append("Q03 order execution relation must default the execution relation as primary")
        order_unique = dict(unique_keys(order_execution))
        if set(value.strip().lower() for value in order_unique.get("uk_order_execution", "").split(",")) != {"tenant_id", "order_id", "execution_id"}:
            errors.append("Q03 order execution relation grain mismatch")
        for name, columns in order_unique.items():
            if name == "uk_order_primary_execution" or "is_primary" in columns.lower() or "primary_order_id" in columns.lower():
                errors.append("Q03 multiple primary executions must not be constrained as unique")
    if contract.get("historicalAnomalyPolicy") != "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE":
        errors.append("historical anomaly policy mismatch")
    return errors


def validate_execution_evidence(evidence: dict[str, object], ddl_bytes: bytes, table_count: int) -> list[str]:
    errors: list[str] = []
    ddl_sha = hashlib.sha256(ddl_bytes).hexdigest().upper()
    if evidence.get("status") != "PASS" or evidence.get("purpose") != "P3_E09_ISOLATED_MYSQL_DDL_EXECUTION":
        errors.append("isolated MySQL DDL execution evidence is not PASS")
    if evidence.get("ddlSha256") != ddl_sha:
        errors.append("isolated MySQL DDL execution evidence hash is stale")
    if evidence.get("tableCount") != table_count or evidence.get("expectedTableCount") != table_count:
        errors.append("isolated MySQL DDL execution table count mismatch")
    if not str(evidence.get("mysqlVersion", "")).startswith("8.4."):
        errors.append("isolated DDL execution must use MySQL 8.4.x")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=CONTRACT)
    parser.add_argument("--ddl", type=Path, default=DDL)
    parser.add_argument("--execution-evidence", type=Path, default=EXECUTION_EVIDENCE)
    args = parser.parse_args()
    ddl_bytes = args.ddl.read_bytes()
    ddl_text = ddl_bytes.decode("utf-8")
    errors = validate_contract(
        json.loads(args.contract.read_text(encoding="utf-8")),
        ddl_text,
    )
    errors.extend(validate_execution_evidence(
        json.loads(args.execution_evidence.read_text(encoding="utf-8")),
        ddl_bytes,
        len(parse_tables(ddl_text)),
    ))
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] ADR-0022 core migration schema contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
