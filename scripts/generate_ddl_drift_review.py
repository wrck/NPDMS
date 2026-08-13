#!/usr/bin/env python3
"""Generate a read-only, decision-neutral DDL drift report for AI-MIG-000."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path


CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?\s*\((.*?)\)\s*(ENGINE\s*=.*?);",
    re.I | re.S,
)
ALTER_TABLE_ADD = re.compile(
    r"ALTER\s+TABLE\s+`?([a-zA-Z0-9_]+)`?\s+ADD\s+((?:CONSTRAINT|UNIQUE\s+KEY|KEY|CHECK|FOREIGN\s+KEY).*?);",
    re.I | re.S,
)
CONSTRAINT_PREFIXES = ("PRIMARY KEY", "UNIQUE KEY", "KEY ", "CONSTRAINT ", "CHECK ", "FOREIGN KEY")


@dataclass(frozen=True)
class Table:
    columns: dict[str, dict[str, object]]
    constraints: set[str]
    options: str


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def normalize(fragment: str) -> str:
    return re.sub(r"\s+", " ", fragment.strip()).replace("`", "")


def generated_expression(definition: str) -> str | None:
    marker = re.search(r"\bGENERATED\s+ALWAYS\s+AS\s*\(", definition, re.I)
    if not marker:
        return None
    start = marker.end()
    depth = 1
    quote: str | None = None
    for index in range(start, len(definition)):
        char = definition[index]
        if quote:
            if char == quote:
                quote = None
            continue
        if char in ("'", '"'):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return normalize(definition[start:index])
    raise ValueError(f"unterminated generated column expression: {definition}")


def column_signature(definition: str) -> dict[str, object]:
    value = normalize(definition)
    type_match = re.match(r"^([A-Z]+(?:\([^)]*\))?(?:\s+UNSIGNED)?)(?=\s|$)", value, re.I)
    if not type_match:
        raise ValueError(f"cannot parse column type: {value}")
    default_match = re.search(r"\bDEFAULT\s+((?:'[^']*')|(?:CURRENT_TIMESTAMP(?:\(\d+\))?)|(?:NULL)|(?:-?\d+(?:\.\d+)?))", value, re.I)
    comment_match = re.search(r"\bCOMMENT\s+'((?:''|[^'])*)'", value, re.I)
    signature: dict[str, object] = {
        "dataType": type_match.group(1).upper(),
        "nullable": " NOT NULL" not in f" {value.upper()}",
        "defaultValue": default_match.group(1) if default_match else None,
        "generated": "GENERATED ALWAYS AS" in value.upper(),
        "description": comment_match.group(1).replace("''", "'") if comment_match else None,
    }
    expression = generated_expression(value)
    if expression is not None:
        signature["generatedExpression"] = expression
    return signature


def split_items(body: str) -> list[str]:
    items: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for index, char in enumerate(body):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ("'", '"'):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "," and depth == 0:
            items.append(body[start:index].strip())
            start = index + 1
    tail = body[start:].strip()
    if tail:
        items.append(tail)
    return items


def parse_ddl(data: bytes) -> dict[str, Table]:
    text = data.decode("utf-8-sig")
    tables: dict[str, Table] = {}
    for match in CREATE_TABLE.finditer(text):
        table_name, body, options = match.groups()
        columns: dict[str, dict[str, object]] = {}
        constraints: set[str] = set()
        for raw in split_items(body):
            item = normalize(raw)
            upper = item.upper()
            if upper.startswith(CONSTRAINT_PREFIXES):
                constraints.add(item)
                continue
            column_match = re.match(r"^([a-zA-Z0-9_]+)\s+(.+)$", item)
            if not column_match:
                raise ValueError(f"cannot parse table item in {table_name}: {item}")
            column_name, definition = column_match.groups()
            columns[column_name] = column_signature(definition)
        tables[table_name] = Table(columns, constraints, normalize(options))
    for match in ALTER_TABLE_ADD.finditer(text):
        table_name, definition = match.groups()
        if table_name not in tables:
            raise ValueError(f"ALTER TABLE references unknown table: {table_name}")
        tables[table_name].constraints.add(normalize(definition))
    if not tables:
        raise ValueError("no CREATE TABLE statements found")
    return tables


def git_blob(repo: Path, commit: str, relative_path: str) -> bytes:
    return subprocess.run(
        ["git", "show", f"{commit}:{relative_path}"],
        cwd=repo,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    ).stdout


def locate_baseline(repo: Path, relative_path: str, expected_hash: str) -> tuple[str, bytes] | None:
    commits = subprocess.run(
        ["git", "log", "--all", "--format=%H", "--", relative_path],
        cwd=repo,
        check=True,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
    ).stdout.splitlines()
    for commit in commits:
        data = git_blob(repo, commit, relative_path)
        if sha256(data) == expected_hash.upper():
            return commit, data
    return None


def load_catalog(path: Path) -> dict[str, Table]:
    tables: dict[str, dict[str, dict[str, object]]] = {}
    with path.open(encoding="utf-8") as source:
        for line in source:
            item = json.loads(line)
            table = tables.setdefault(item["tableName"], {})
            signature = {
                "dataType": item["dataType"].upper(),
                "nullable": item["nullable"],
                "defaultValue": item.get("defaultValue"),
                "generated": item.get("generated", False),
                "description": item.get("description"),
            }
            if item.get("generatedExpression") is not None:
                signature["generatedExpression"] = item["generatedExpression"]
            table[item["columnName"]] = signature
    return {name: Table(columns, set(), "UNAVAILABLE_IN_TARGET_FIELD_CATALOG") for name, columns in tables.items()}


def load_catalog_bytes(data: bytes) -> dict[str, Table]:
    tables: dict[str, dict[str, dict[str, object]]] = {}
    for line in data.decode("utf-8-sig").splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        table = tables.setdefault(item["tableName"], {})
        signature = {
            "dataType": item["dataType"].upper(),
            "nullable": item["nullable"],
            "defaultValue": item.get("defaultValue"),
            "generated": item.get("generated", False),
            "description": item.get("description"),
        }
        if item.get("generatedExpression") is not None:
            signature["generatedExpression"] = item["generatedExpression"]
        table[item["columnName"]] = signature
    return {name: Table(columns, set(), "UNAVAILABLE_IN_TARGET_FIELD_CATALOG") for name, columns in tables.items()}


def locate_catalog_baseline(repo: Path, catalog: Path, expected_ddl_hash: str) -> tuple[str, dict[str, Table]]:
    relative_catalog = catalog.relative_to(repo).as_posix()
    relative_summary = catalog.with_name("target-field-catalog-summary.json").relative_to(repo).as_posix()
    commits = subprocess.run(
        ["git", "log", "--all", "--format=%H", "--", relative_summary],
        cwd=repo, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE,
    ).stdout.splitlines()
    for commit in commits:
        try:
            summary = json.loads(git_blob(repo, commit, relative_summary))
        except (subprocess.CalledProcessError, json.JSONDecodeError):
            continue
        if summary.get("ddlSha256", "").upper() == expected_ddl_hash.upper():
            return commit, load_catalog_bytes(git_blob(repo, commit, relative_catalog))
    raise ValueError(f"cannot locate historical target field catalog for DDL hash {expected_ddl_hash}")


def normalize_baseline_names(tables: dict[str, Table], contract: dict[str, object]) -> dict[str, Table]:
    table_map = {item["source"]: item["target"] for item in contract["tables"]}
    field_map: dict[str, dict[str, str]] = {}
    for item in contract["fields"]:
        field_map.setdefault(item["sourceTable"], {})[item["sourceColumn"]] = item["targetColumn"]
    result: dict[str, Table] = {}
    for source_table, table in tables.items():
        target_table = table_map.get(source_table, source_table)
        columns = {field_map.get(source_table, {}).get(column, column): value for column, value in table.columns.items()}
        result[target_table] = Table(columns, table.constraints, table.options)
    return result


def named_constraints(constraints: set[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for value in constraints:
        match = re.match(r"^(?:CONSTRAINT|UNIQUE KEY|KEY)\s+([a-zA-Z0-9_]+)", value, re.I)
        if match:
            key = match.group(1)
        elif value.upper().startswith("PRIMARY KEY"):
            key = "PRIMARY"
        else:
            key = hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]
        result[key] = value
    return result


def compare(old: dict[str, Table], current: dict[str, Table], *, compare_constraints: bool = True, compare_options: bool = True) -> dict[str, object]:
    old_names, current_names = set(old), set(current)
    table_diffs: list[dict[str, object]] = []
    column_diffs: list[dict[str, object]] = []
    constraint_diffs: list[dict[str, object]] = []
    option_diffs: list[dict[str, object]] = []

    for table in sorted(old_names | current_names):
        if table not in old:
            table_diffs.append({"table": table, "change": "ADDED", "decision": "DEFER"})
            continue
        if table not in current:
            table_diffs.append({"table": table, "change": "REMOVED", "decision": "DEFER"})
            continue
        old_table, current_table = old[table], current[table]
        for column in sorted(set(old_table.columns) | set(current_table.columns)):
            before = old_table.columns.get(column)
            after = current_table.columns.get(column)
            if before != after:
                change = "ADDED" if before is None else "REMOVED" if after is None else "MODIFIED"
                column_diffs.append({"table": table, "column": column, "change": change, "before": before, "after": after, "decision": "DEFER"})
        if compare_constraints:
            old_constraints, current_constraints = named_constraints(old_table.constraints), named_constraints(current_table.constraints)
            for name in sorted(set(old_constraints) | set(current_constraints)):
                before = old_constraints.get(name)
                after = current_constraints.get(name)
                if before != after:
                    change = "ADDED" if before is None else "REMOVED" if after is None else "MODIFIED"
                    constraint_diffs.append({"table": table, "constraint": name, "change": change, "before": before, "after": after, "decision": "DEFER"})
        if compare_options and old_table.options != current_table.options:
            option_diffs.append({"table": table, "change": "MODIFIED", "before": old_table.options, "after": current_table.options, "decision": "DEFER"})

    return {
        "tableDiff": table_diffs,
        "columnDiff": column_diffs,
        "constraintDiff": constraint_diffs,
        "tableOptionDiff": option_diffs,
    }


def current_constraint_inventory(ddl_sha256: str, tables: dict[str, Table]) -> dict[str, object]:
    records = [
        {"table": name, "constraints": sorted(table.constraints), "tableOptions": table.options, "decision": "DEFER"}
        for name, table in sorted(tables.items())
    ]
    canonical = json.dumps(records, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "schemaVersion": 1,
        "status": "DEFER",
        "purpose": "AI-MIG-000_CURRENT_CONSTRAINT_REVIEW_INPUT",
        "currentDdlSha256": ddl_sha256,
        "tableCount": len(records),
        "constraintCount": sum(len(record["constraints"]) for record in records),
        "inventorySha256": sha256(canonical),
        "records": records,
        "approval": {"approvedDdlSha256": None, "decisionOwner": None, "reviewOwner": None, "evidenceRefs": []},
    }


def column_comparison_status(before: dict[str, object] | None, after: dict[str, object] | None) -> str:
    if before is None:
        return "ADDED"
    if after is None:
        return "REMOVED"
    before_without_expression = {key: value for key, value in before.items() if key != "generatedExpression"}
    after_without_expression = {key: value for key, value in after.items() if key != "generatedExpression"}
    if (
        before_without_expression == after_without_expression
        and before.get("generated") is True
        and before.get("generatedExpression") is None
        and after.get("generatedExpression") is not None
    ):
        return "UNVERIFIED_BASELINE_MISSING"
    return "MATCH" if before == after else "MODIFIED"


def ddl_item_decision_register(
    baseline_sha256: str,
    current_sha256: str,
    baseline_tables: dict[str, Table],
    current_tables: dict[str, Table],
    *,
    constraints_comparable: bool,
    options_comparable: bool,
) -> dict[str, object]:
    """Build a decision-neutral item register; never infer Owner approval."""
    items: list[dict[str, object]] = []
    for table_name in sorted(set(baseline_tables) | set(current_tables)):
        before_table = baseline_tables.get(table_name)
        after_table = current_tables.get(table_name)
        table_status = "MATCH" if before_table and after_table else "ADDED" if after_table else "REMOVED"
        items.append({
            "itemId": f"TABLE:{table_name}", "itemType": "TABLE", "table": table_name,
            "comparisonStatus": table_status, "baselineValue": before_table is not None,
            "currentValue": after_table is not None, "decision": "DEFER",
            "decisionOwner": None, "reviewOwner": None, "evidenceRefs": [],
        })
        before_columns = before_table.columns if before_table else {}
        after_columns = after_table.columns if after_table else {}
        for column_name in sorted(set(before_columns) | set(after_columns)):
            before = before_columns.get(column_name)
            after = after_columns.get(column_name)
            status = column_comparison_status(before, after)
            items.append({
                "itemId": f"COLUMN:{table_name}:{column_name}", "itemType": "COLUMN",
                "table": table_name, "name": column_name, "comparisonStatus": status,
                "baselineValue": before, "currentValue": after, "decision": "DEFER",
                "decisionOwner": None, "reviewOwner": None, "evidenceRefs": [],
            })
        current_constraints = named_constraints(after_table.constraints) if after_table else {}
        baseline_constraints = named_constraints(before_table.constraints) if before_table and constraints_comparable else {}
        for constraint_name in sorted(set(baseline_constraints) | set(current_constraints)):
            before = baseline_constraints.get(constraint_name)
            after = current_constraints.get(constraint_name)
            if not constraints_comparable:
                status = "UNVERIFIED_BASELINE_MISSING"
            else:
                status = "MATCH" if before == after else "ADDED" if before is None else "REMOVED" if after is None else "MODIFIED"
            items.append({
                "itemId": f"CONSTRAINT:{table_name}:{constraint_name}", "itemType": "CONSTRAINT",
                "table": table_name, "name": constraint_name, "comparisonStatus": status,
                "baselineValue": before, "currentValue": after, "decision": "DEFER",
                "decisionOwner": None, "reviewOwner": None, "evidenceRefs": [],
            })
        if before_table or after_table:
            before_option = before_table.options if before_table and options_comparable else None
            after_option = after_table.options if after_table else None
            if not options_comparable:
                option_status = "UNVERIFIED_BASELINE_MISSING"
            else:
                option_status = "MATCH" if before_option == after_option else "ADDED" if before_option is None else "REMOVED" if after_option is None else "MODIFIED"
            items.append({
                "itemId": f"TABLE_OPTION:{table_name}", "itemType": "TABLE_OPTION", "table": table_name,
                "comparisonStatus": option_status, "baselineValue": before_option, "currentValue": after_option,
                "decision": "DEFER", "decisionOwner": None, "reviewOwner": None, "evidenceRefs": [],
            })
    counts: dict[str, int] = {}
    statuses: dict[str, int] = {}
    for item in items:
        counts[item["itemType"]] = counts.get(item["itemType"], 0) + 1
        statuses[item["comparisonStatus"]] = statuses.get(item["comparisonStatus"], 0) + 1
    canonical = json.dumps(items, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "schemaVersion": 1,
        "status": "DEFER",
        "purpose": "AI-MIG-000_ITEM_BY_ITEM_DECISION_REGISTER",
        "baselineDdlSha256": baseline_sha256,
        "currentDdlSha256": current_sha256,
        "allowedDecisions": ["ACCEPT_CURRENT", "RESTORE_APPROVED_BASELINE", "AMEND_CURRENT", "DEFER"],
        "summary": {"itemCount": len(items), "byType": counts, "byComparisonStatus": statuses, "approvedCount": 0},
        "itemsSha256": sha256(canonical),
        "items": items,
        "approval": {"approvedDdlSha256": None, "decisionOwner": None, "reviewOwner": None, "evidenceRefs": []},
    }


def apply_accepted_naming_decisions(register: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    """Record requirement-owner naming decisions without fabricating reviewer approval."""
    excluded = set(contract.get("implementationScope", {}).get("excludedTargets", []))
    table_ids = {f"TABLE:{item['target']}" for item in contract["tables"] if item["target"] not in excluded}
    column_ids = {f"COLUMN:{item['targetTable']}:{item['targetColumn']}" for item in contract["fields"]}
    decided = table_ids | column_ids
    for item in register["items"]:
        if item["itemId"] in decided:
            item["decision"] = "AMEND_CURRENT"
            item["decisionOwner"] = "REQUIREMENT_OWNER"
            item["reviewOwner"] = None
            item["evidenceRefs"] = ["docs/decisions/0019-domain-coded-database-naming.md"]
    register["summary"]["approvedCount"] = 0
    canonical = json.dumps(register["items"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    register["itemsSha256"] = sha256(canonical)
    register["namingDecision"] = {
        "decisionRef": "ADR-0019",
        "decidedItemCount": len(decided),
        "reviewStatus": "REVIEW_PENDING",
    }
    return register


def apply_accepted_project_code_decisions(register: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    """Record ADR-0020 requirement-owner decisions without reviewer approval."""
    decided = set(contract["acceptedDdlItems"])
    actual = {item["itemId"] for item in register["items"]}
    missing = sorted(decided - actual)
    if missing:
        raise ValueError(f"ADR-0020 references missing DDL items: {missing}")
    for item in register["items"]:
        if item["itemId"] in decided:
            item["decision"] = "AMEND_CURRENT"
            item["decisionOwner"] = "REQUIREMENT_OWNER"
            item["reviewOwner"] = None
            item["evidenceRefs"] = ["docs/decisions/0020-project-code-identity-and-namespace.md"]
    register["summary"]["approvedCount"] = 0
    canonical = json.dumps(register["items"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    register["itemsSha256"] = sha256(canonical)
    register["projectCodeDecision"] = {
        "decisionRef": "ADR-0020",
        "decidedItemCount": len(decided),
        "reviewStatus": "REVIEW_PENDING",
    }
    return register


def apply_accepted_market_relation_decisions(register: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    """Record ADR-0021 market-relation model decisions without reviewer approval."""
    target_table = contract["targetTable"]
    business_fields = set(contract["businessFields"])
    decided: set[str] = set()
    for item in register["items"]:
        if item["table"] == target_table:
            decided.add(item["itemId"])
        elif item["itemType"] == "COLUMN" and item["table"] in {"proj_project", "cus_customer"} and item["name"] in business_fields:
            decided.add(item["itemId"])
        elif item["itemType"] == "CONSTRAINT" and item["name"] in {"idx_project_market_relation", "idx_customer_market_relation"}:
            decided.add(item["itemId"])
    for item in register["items"]:
        if item["itemId"] in decided:
            item["decision"] = "AMEND_CURRENT"
            item["decisionOwner"] = "REQUIREMENT_OWNER"
            item["reviewOwner"] = None
            item["evidenceRefs"] = ["docs/decisions/0021-customer-market-relation-classification.md"]
    register["summary"]["approvedCount"] = 0
    canonical = json.dumps(register["items"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    register["itemsSha256"] = sha256(canonical)
    register["marketRelationDecision"] = {
        "decisionRef": "ADR-0021",
        "decidedItemCount": len(decided),
        "reviewStatus": "REVIEW_PENDING",
    }
    return register


def apply_accepted_core_schema_decisions(register: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    """Record ADR-0022 current DDL items and explicit removals without reviewer approval."""
    decided = set(contract["acceptedDdlItems"])
    actual = {item["itemId"] for item in register["items"]}
    missing = sorted(decided - actual)
    if missing:
        raise ValueError(f"ADR-0022 references missing DDL items: {missing}")
    for item in register["items"]:
        if item["itemId"] in decided:
            item["decision"] = "AMEND_CURRENT"
            item["decisionOwner"] = "REQUIREMENT_OWNER"
            item["reviewOwner"] = None
            item["evidenceRefs"] = ["docs/decisions/0022-core-migration-schema-and-key-policy.md"]
    register["summary"]["approvedCount"] = 0
    canonical = json.dumps(register["items"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    register["itemsSha256"] = sha256(canonical)
    register["coreMigrationSchemaDecision"] = {
        "decisionRef": "ADR-0022",
        "decidedItemCount": len(decided),
        "removedV3TableCount": len(contract["v3DesignOnlyTables"]),
        "removedCrossDomainForeignKeyCount": 26,
        "reviewStatus": "REVIEW_PENDING",
    }
    return register


def apply_accepted_q03_decisions(register: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    """Record ADR-0023 business-fact decisions without deciding Q07/Q08 physical details."""
    expected_facts = {
        "deviceProjectAssignment": "ONE_CURRENT_DIRECT_PROJECT_PER_DEVICE",
        "customerPrimaryContact": "ONE_CURRENT_PRIMARY_CONTACT_PER_CUSTOMER",
        "projectPrimaryCompanyDepartment": "ONE_CURRENT_PRIMARY_RELATION_PER_PROJECT_ROLE",
        "deliveryScope": "ONE_CURRENT_HEADER_PER_PROJECT_ORDER_LINE_WITH_DETAILS",
        "orderExecution": "MULTIPLE_PRIMARY_EXECUTIONS_ALLOWED",
    }
    if contract.get("q03CurrentBusinessFacts") != expected_facts:
        raise ValueError("ADR-0023 Q03 business facts do not match the accepted decision set")

    decided = {
        "COLUMN:ast_device_project_assignment:current_device_id",
        "CONSTRAINT:ast_device_project_assignment:uk_device_current_assignment",
        "COLUMN:cus_customer_contact:primary_customer_id",
        "CONSTRAINT:cus_customer_contact:uk_customer_primary_contact",
        "COLUMN:proj_project_company_department_relation:primary_project_id",
        "CONSTRAINT:proj_project_company_department_relation:uk_project_primary_company_department",
        "COLUMN:com_delivery_scope:current_order_line_id",
        "CONSTRAINT:com_delivery_scope:uk_scope_current",
        "TABLE:com_delivery_scope_detail",
        "COLUMN:com_delivery_scope_detail:delivery_scope_id",
        "COLUMN:com_delivery_scope_detail:detail_sequence",
        "COLUMN:com_delivery_scope_detail:product_code",
        "COLUMN:com_delivery_scope_detail:product_name",
        "COLUMN:com_delivery_scope_detail:device_type_code",
        "COLUMN:com_delivery_scope_detail:device_type_name",
        "COLUMN:com_delivery_scope_detail:allocated_qty",
        "COLUMN:com_delivery_scope_detail:implementation_location",
        "COLUMN:com_delivery_scope_detail:delivery_batch_no",
        "COLUMN:com_delivery_scope_detail:source_record_key",
        "COLUMN:com_delivery_scope_detail:remark",
        "CONSTRAINT:com_delivery_scope_detail:uk_delivery_scope_detail_sequence",
        "CONSTRAINT:com_delivery_scope_detail:fk_delivery_scope_detail_scope",
        "CONSTRAINT:com_delivery_scope_detail:chk_delivery_scope_detail_subject",
        "COLUMN:com_order_execution_relation:is_primary",
        "COLUMN:com_order_execution_relation:primary_order_id",
    }
    actual = {item["itemId"] for item in register["items"]}
    missing = sorted(decided - actual)
    if missing:
        raise ValueError(f"ADR-0023 references missing DDL items: {missing}")
    for item in register["items"]:
        if item["itemId"] in decided:
            item["decision"] = "AMEND_CURRENT"
            item["decisionOwner"] = "REQUIREMENT_OWNER"
            item["reviewOwner"] = None
            item["evidenceRefs"] = ["docs/decisions/0023-p3-e09-key-collation-and-state-guard-policy.md"]
    register["summary"]["approvedCount"] = 0
    canonical = json.dumps(register["items"], ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    register["itemsSha256"] = sha256(canonical)
    register["q03Decision"] = {
        "decisionRef": "ADR-0023-Q03",
        "decidedItemCount": len(decided),
        "deferredPhysicalDecisionRefs": ["P3-E09-Q07", "P3-E09-Q08"],
        "reviewStatus": "REVIEW_PENDING",
    }
    return register


def build_report(repo: Path, ddl: Path, baseline_hash: str, catalog: Path, naming_contract: Path | None = None) -> dict[str, object]:
    relative_path = ddl.relative_to(repo).as_posix()
    current_data = ddl.read_bytes()
    located = locate_baseline(repo, relative_path, baseline_hash)
    current_tables = parse_ddl(current_data)
    if located:
        baseline_commit, baseline_data = located
        old_tables = parse_ddl(baseline_data)
        baseline_source = "GIT_DDL"
        constraints_comparable = True
        options_comparable = True
    else:
        baseline_commit = None
        baseline_catalog_commit, old_tables = locate_catalog_baseline(repo, catalog, baseline_hash)
        if naming_contract is not None:
            contract = json.loads(naming_contract.read_text(encoding="utf-8"))
            old_tables = normalize_baseline_names(old_tables, contract)
        baseline_source = "TARGET_FIELD_CATALOG"
        constraints_comparable = False
        options_comparable = False
    differences = compare(old_tables, current_tables, compare_constraints=constraints_comparable, compare_options=options_comparable)
    counts = {key: len(value) for key, value in differences.items()}
    return {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "status": "DEFER",
        "purpose": "AI-MIG-000_READ_ONLY_DDL_DRIFT_FACTS",
        "inputs": {
            "ddlPath": relative_path,
            "baselineSource": baseline_source,
            "baselineCommit": baseline_commit,
            "baselineCatalogCommit": baseline_catalog_commit if baseline_source == "TARGET_FIELD_CATALOG" else None,
            "baselineDdlSha256": baseline_hash.upper(),
            "baselineCatalogPath": catalog.relative_to(repo).as_posix() if baseline_source == "TARGET_FIELD_CATALOG" else None,
            "currentDdlSha256": sha256(current_data),
            "baselineNameNormalization": "ADR-0019" if naming_contract is not None else None,
        },
        "summary": {
            "baselineTableCount": len(old_tables),
            "currentTableCount": len(current_tables),
            "baselineColumnCount": sum(len(table.columns) for table in old_tables.values()),
            "currentColumnCount": sum(len(table.columns) for table in current_tables.values()),
            **counts,
            "allDifferencesDecision": "DEFER",
            "constraintsComparable": constraints_comparable,
            "tableOptionsComparable": options_comparable,
        },
        **differences,
        "decisionPolicy": {
            "allowed": ["ACCEPT_CURRENT", "RESTORE_APPROVED_BASELINE", "AMEND_CURRENT", "DEFER"],
            "current": "DEFER",
            "approvedDdlSha256": None,
            "approvalEvidence": [],
        },
        "limitations": [] if constraints_comparable else [
            "No committed DDL blob matches the historical catalog hash; column facts are compared against target-field-catalog.jsonl.",
            "The catalog has no index, foreign-key, CHECK-expression or table-option definitions; those differences remain UNVERIFIED and cannot be approved from this report.",
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--ddl", type=Path, default=Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql"))
    parser.add_argument("--baseline-sha256", default="2B206992BA5580E776060F9D4ED177A7BD8C34DB614FD65EC9560DAF38F8BF33")
    parser.add_argument("--catalog", type=Path, default=Path("specs/001-project-delivery-platform/evidence/migration/target-field-catalog.jsonl"))
    parser.add_argument("--naming-contract", type=Path, default=Path("docs/traceability/database-naming-contract.json"))
    parser.add_argument("--project-code-contract", type=Path, default=Path("docs/traceability/project-code-contract.json"))
    parser.add_argument("--market-relation-contract", type=Path, default=Path("docs/traceability/market-relation-contract.json"))
    parser.add_argument("--core-migration-schema-contract", type=Path, default=Path("docs/traceability/core-migration-schema-contract.json"))
    parser.add_argument("--output", type=Path, default=Path("specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json"))
    parser.add_argument("--constraint-inventory-output", type=Path, default=Path("specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json"))
    parser.add_argument("--decision-register-output", type=Path, default=Path("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json"))
    args = parser.parse_args()
    repo = args.repo.resolve()
    ddl = args.ddl if args.ddl.is_absolute() else repo / args.ddl
    output = args.output if args.output.is_absolute() else repo / args.output
    constraint_output = args.constraint_inventory_output if args.constraint_inventory_output.is_absolute() else repo / args.constraint_inventory_output
    decision_output = args.decision_register_output if args.decision_register_output.is_absolute() else repo / args.decision_register_output
    catalog = args.catalog if args.catalog.is_absolute() else repo / args.catalog
    naming_contract = args.naming_contract if args.naming_contract.is_absolute() else repo / args.naming_contract
    project_code_contract = args.project_code_contract if args.project_code_contract.is_absolute() else repo / args.project_code_contract
    market_relation_contract = args.market_relation_contract if args.market_relation_contract.is_absolute() else repo / args.market_relation_contract
    core_migration_schema_contract = args.core_migration_schema_contract if args.core_migration_schema_contract.is_absolute() else repo / args.core_migration_schema_contract
    report = build_report(repo, ddl, args.baseline_sha256, catalog, naming_contract)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    inventory = current_constraint_inventory(report["inputs"]["currentDdlSha256"], parse_ddl(ddl.read_bytes()))
    constraint_output.write_text(json.dumps(inventory, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    current_tables = parse_ddl(ddl.read_bytes())
    if report["inputs"]["baselineSource"] == "GIT_DDL":
        baseline_tables = parse_ddl(git_blob(repo, report["inputs"]["baselineCommit"], report["inputs"]["ddlPath"]))
    else:
        _commit, baseline_tables = locate_catalog_baseline(repo, catalog, report["inputs"]["baselineDdlSha256"])
        baseline_tables = normalize_baseline_names(baseline_tables, json.loads(naming_contract.read_text(encoding="utf-8")))
    decision_register = ddl_item_decision_register(
        report["inputs"]["baselineDdlSha256"], report["inputs"]["currentDdlSha256"],
        baseline_tables, current_tables,
        constraints_comparable=report["summary"]["constraintsComparable"],
        options_comparable=report["summary"]["tableOptionsComparable"],
    )
    decision_register = apply_accepted_naming_decisions(
        decision_register, json.loads(naming_contract.read_text(encoding="utf-8"))
    )
    decision_register = apply_accepted_project_code_decisions(
        decision_register, json.loads(project_code_contract.read_text(encoding="utf-8"))
    )
    decision_register = apply_accepted_market_relation_decisions(
        decision_register, json.loads(market_relation_contract.read_text(encoding="utf-8"))
    )
    decision_register = apply_accepted_core_schema_decisions(
        decision_register, json.loads(core_migration_schema_contract.read_text(encoding="utf-8"))
    )
    decision_register = apply_accepted_q03_decisions(
        decision_register, json.loads(core_migration_schema_contract.read_text(encoding="utf-8"))
    )
    decision_output.write_text(json.dumps(decision_register, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report["summary"], ensure_ascii=False))
    print(f"WROTE {output.relative_to(repo).as_posix()}")
    print(f"WROTE {constraint_output.relative_to(repo).as_posix()}; constraints={inventory['constraintCount']}")
    print(f"WROTE {decision_output.relative_to(repo).as_posix()}; items={decision_register['summary']['itemCount']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
