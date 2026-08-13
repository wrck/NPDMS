#!/usr/bin/env python3
"""Regenerate target DDL catalog and rewrite target-only migration references."""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import sys
from collections import Counter
from pathlib import Path


DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
CONTRACT = Path("docs/traceability/database-naming-contract.json")
PROJECT_CODE_CONTRACT = Path("docs/traceability/project-code-contract.json")
MARKET_RELATION_CONTRACT = Path("docs/traceability/market-relation-contract.json")
MIGRATION = Path("specs/001-project-delivery-platform/evidence/migration")
CATALOG = MIGRATION / "target-field-catalog.jsonl"
CATALOG_SUMMARY = MIGRATION / "target-field-catalog-summary.json"
JSONL_TARGET_FILES = [
    "core-field-mapping.jsonl",
    "legacy-physical-field-canonical.jsonl",
    "legacy-physical-field-mapping.jsonl",
    "schema-business-element-mapping.jsonl",
    "semantic-data-element-canonical.jsonl",
    "semantic-data-element-mapping.jsonl",
]
JSON_SUMMARY_FILES = ["core-field-mapping-summary.json", "complete-migration-summary.json", "migration-validation.json"]


def load_parser(root: Path):
    path = root / "scripts" / "generate_ddl_drift_review.py"
    spec = importlib.util.spec_from_file_location("ddl_parser_for_target_catalog", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def contract_maps(contract: dict[str, object]) -> tuple[dict[str, str], dict[tuple[str, str], tuple[str, str]]]:
    tables = {item["source"]: item["target"] for item in contract["tables"] + contract.get("tableExtensions", [])}
    fields = {
        (item["sourceTable"], item["sourceColumn"]): (item["targetTable"], item["targetColumn"])
        for item in contract["fields"]
    }
    return tables, fields


def rewrite_reference(value: str, tables: dict[str, str], fields: dict[tuple[str, str], tuple[str, str]]) -> str:
    if "." in value:
        table, column = value.split(".", 1)
        if (table, column) in fields:
            target_table, target_column = fields[(table, column)]
            return f"{target_table}.{target_column}"
        if table in tables:
            return f"{tables[table]}.{column}"
    return tables.get(value, value)


def rewrite_target_references(
    row: dict[str, object],
    contract: dict[str, object],
    ddl_sha256: str | None = None,
    source_mapping_overrides: dict[tuple[str, str], dict[str, str]] | None = None,
) -> dict[str, object]:
    result = copy.deepcopy(row)
    override = None
    if source_mapping_overrides is not None:
        override = source_mapping_overrides.get((result.get("sourceTable"), result.get("sourceColumn")))
    if override is not None:
        target_table, target_column = override["target"].split(".", 1)
        result["decisionStatus"] = "EVIDENCE_MAPPED"
        result["disposition"] = "STRUCTURED"
        result["targets"] = [override["target"]]
        result["targetBindings"] = [{"tableName": target_table, "columnName": target_column, "jsonPath": None}]
        result["transform"] = override["transform"]
        result["decisionBasis"] = "ADR-0021 and source data-element evidence"
    tables, fields = contract_maps(contract)
    if "target" in result and isinstance(result["target"], str):
        result["target"] = rewrite_reference(result["target"], tables, fields)
    if "targets" in result and isinstance(result["targets"], list):
        result["targets"] = [rewrite_reference(value, tables, fields) if isinstance(value, str) else value for value in result["targets"]]
    if "rawPreservedBy" in result and isinstance(result["rawPreservedBy"], str):
        result["rawPreservedBy"] = rewrite_reference(result["rawPreservedBy"], tables, fields)
    if "targetBindings" in result and isinstance(result["targetBindings"], list):
        bindings = []
        for binding in result["targetBindings"]:
            updated = copy.deepcopy(binding)
            source_table = updated.get("tableName")
            source_column = updated.get("columnName")
            if (source_table, source_column) in fields:
                updated["tableName"], updated["columnName"] = fields[(source_table, source_column)]
            elif source_table in tables:
                updated["tableName"] = tables[source_table]
            bindings.append(updated)
        result["targetBindings"] = bindings
    if ddl_sha256 is not None and "targetDdlSha256" in result:
        result["targetDdlSha256"] = ddl_sha256
    return result


def remap_catalog_item(item: dict[str, object], contract: dict[str, object]) -> dict[str, object]:
    tables, fields = contract_maps(contract)
    result = copy.deepcopy(item)
    source_table, source_column = result["tableName"], result["columnName"]
    if (source_table, source_column) in fields:
        result["tableName"], result["columnName"] = fields[(source_table, source_column)]
    else:
        result["tableName"] = tables[source_table]
    return result


def build_catalog(
    ddl_tables: dict[str, object],
    prior: list[dict[str, object]],
    contract: dict[str, object],
    new_field_metadata: dict[tuple[str, str], dict[str, object]] | None = None,
) -> list[dict[str, object]]:
    tables, fields = contract_maps(contract)
    reverse_tables = {target: source for source, target in tables.items()}
    reverse_fields = {target: source for source, target in fields.items()}
    prior_by_key = {(item["tableName"], item["columnName"]): item for item in prior}
    rows: list[dict[str, object]] = []
    for table_name, table in ddl_tables.items():
        source_table = reverse_tables.get(table_name)
        if source_table is None:
            raise ValueError(f"DDL table missing from naming contract: {table_name}")
        for ordinal, (column_name, signature) in enumerate(table.columns.items(), 1):
            source_key = reverse_fields.get((table_name, column_name), (source_table, column_name))
            metadata = prior_by_key.get(source_key)
            if metadata is None and new_field_metadata is not None:
                metadata = new_field_metadata.get((table_name, column_name))
            if metadata is None:
                raise ValueError(f"target catalog metadata missing for {source_key[0]}.{source_key[1]}")
            rows.append({
                "tableName": table_name,
                "ordinal": ordinal,
                "columnName": column_name,
                "dataType": signature["dataType"],
                "nullable": signature["nullable"],
                "defaultValue": signature["defaultValue"],
                "generated": signature["generated"],
                "description": signature["description"],
                "domain": metadata["domain"],
                "fieldClass": metadata["fieldClass"],
                "dataElementRefs": metadata.get("dataElementRefs", []),
            })
    return rows


def jsonl_content(rows: list[dict[str, object]]) -> str:
    return "".join(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n" for row in rows)


def json_content(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def expected_outputs(root: Path) -> dict[Path, str]:
    ddl_path = root / DDL
    contract = json.loads((root / CONTRACT).read_text(encoding="utf-8"))
    project_code_contract = json.loads((root / PROJECT_CODE_CONTRACT).read_text(encoding="utf-8"))
    market_relation_contract = json.loads((root / MARKET_RELATION_CONTRACT).read_text(encoding="utf-8"))
    source_mapping_overrides = {
        (item["sourceTable"], item["sourceColumn"]): item
        for item in market_relation_contract["sourceMappingOverrides"]
    }
    new_field_metadata = {
        (item["table"], item["name"]): item
        for item in project_code_contract["columns"]
    }
    parser = load_parser(root)
    ddl_tables = parser.parse_ddl(ddl_path.read_bytes())
    ddl_hash = sha256(ddl_path.read_bytes())
    business_fields = set(market_relation_contract["businessFields"])
    for table_name, domain in (("cus_market_relation", "客户管理"), ("cus_customer", "客户管理"), ("proj_project", "项目管理")):
        table = ddl_tables[table_name]
        selected = set(table.columns) if table_name == "cus_market_relation" else business_fields
        for column_name in selected:
            if column_name not in table.columns:
                continue
            field_class = "BUSINESS"
            if column_name in {"id", "tenant_id"}:
                field_class = "IDENTITY"
            elif column_name in {"status", "version", "deleted"}:
                field_class = "CONTROL"
            elif column_name in {"creator", "create_time", "updater", "update_time"}:
                field_class = "AUDIT"
            elif column_name.startswith("source_"):
                field_class = "LINEAGE"
            new_field_metadata.setdefault((table_name, column_name), {
                "table": table_name,
                "name": column_name,
                "domain": domain,
                "fieldClass": field_class,
                "dataElementRefs": ["系统支撑!A1279:A1287"] if table_name == "cus_market_relation" and column_name in business_fields else [],
            })
    prior = [json.loads(line) for line in (root / CATALOG).read_text(encoding="utf-8").splitlines() if line]
    # Idempotent generation can use either the old or already-renamed catalog.
    naming_tables = contract["tables"] + contract.get("tableExtensions", [])
    if prior and prior[0]["tableName"] not in {item["source"] for item in naming_tables}:
        reverse_tables = {item["target"]: item["source"] for item in naming_tables}
        reverse_fields = {(item["targetTable"], item["targetColumn"]): (item["sourceTable"], item["sourceColumn"]) for item in contract["fields"]}
        restored = []
        for item in prior:
            current_table, current_column = item["tableName"], item["columnName"]
            source_table, source_column = reverse_fields.get((current_table, current_column), (reverse_tables[current_table], current_column))
            value = copy.deepcopy(item)
            value["tableName"], value["columnName"] = source_table, source_column
            restored.append(value)
        prior = restored
    catalog = build_catalog(ddl_tables, prior, contract, new_field_metadata)
    outputs: dict[Path, str] = {root / CATALOG: jsonl_content(catalog)}
    domains = Counter(item["domain"] for item in catalog)
    classes = Counter(item["fieldClass"] for item in catalog)
    summary = {
        "ddlSha256": ddl_hash,
        "tableCount": len(ddl_tables),
        "columnCount": len(catalog),
        "commentedColumnCount": sum(item["description"] is not None for item in catalog),
        "invalidCommentCount": sum(item["description"] is None for item in catalog),
        "domains": dict(domains),
        "fieldClasses": dict(classes),
    }
    outputs[root / CATALOG_SUMMARY] = json_content(summary)
    for name in JSONL_TARGET_FILES:
        path = root / MIGRATION / name
        rows = [rewrite_target_references(json.loads(line), contract, ddl_hash, source_mapping_overrides) for line in path.read_text(encoding="utf-8").splitlines() if line]
        outputs[path] = jsonl_content(rows)
    for name in JSON_SUMMARY_FILES:
        path = root / MIGRATION / name
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload = rewrite_target_references(payload, contract, ddl_hash)
        if "ddlSha256" in payload:
            payload["ddlSha256"] = ddl_hash
        if "targetDdlSha256" in payload:
            payload["targetDdlSha256"] = ddl_hash
        if name == "complete-migration-summary.json":
            payload["ddlTableCount"] = len(ddl_tables)
            payload["ddlColumnCount"] = len(catalog)
            payload["commentedColumnCount"] = summary["commentedColumnCount"]
            payload["invalidCommentCount"] = summary["invalidCommentCount"]
        if name == "core-field-mapping-summary.json" and isinstance(payload.get("tables"), list):
            for item in payload["tables"]:
                if isinstance(item.get("rawPreservedBy"), str):
                    item["rawPreservedBy"] = rewrite_reference(item["rawPreservedBy"], *contract_maps(contract))
        outputs[path] = json_content(payload)
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    outputs = expected_outputs(root)
    drift = [path for path, content in outputs.items() if not path.exists() or path.read_text(encoding="utf-8") != content]
    if args.check:
        if drift:
            for path in drift:
                print(f"[FAIL] generated migration evidence drift: {path.relative_to(root).as_posix()}")
            return 1
        print(f"[PASS] target field catalog and migration target references; files={len(outputs)}")
        return 0
    for path, content in outputs.items():
        path.write_text(content, encoding="utf-8", newline="\n")
    print(f"[PASS] regenerated target field catalog and migration target references; files={len(outputs)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
