#!/usr/bin/env python3
"""Regenerate target DDL catalog and rewrite target-only migration references."""

from __future__ import annotations

import argparse
import copy
import fnmatch
import hashlib
import importlib.util
import json
import re
import sys
from collections import Counter
from pathlib import Path


DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
CONTRACT = Path("docs/traceability/database-naming-contract.json")
PROJECT_CODE_CONTRACT = Path("docs/traceability/project-code-contract.json")
MARKET_RELATION_CONTRACT = Path("docs/traceability/market-relation-contract.json")
CORE_MIGRATION_SCHEMA_CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
DOMAIN_MIGRATION_CONTRACT = Path("docs/traceability/domain-entity-migration-contract.json")
SCHEMA_RECORDS = Path("specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl")
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
USER_CONFIRMED_EXCLUDED_SOURCE_TABLES = {"pm_project_maintenance"}


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
    tables.update({item["target"]: item["target"] for item in contract.get("modelExtensions", [])})
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
    excluded = set(contract.get("implementationScope", {}).get("excludedTargets", []))
    excluded_reference = False
    if "targets" in result and isinstance(result["targets"], list):
        retained_targets = []
        for value in result["targets"]:
            table = value.split(".", 1)[0] if isinstance(value, str) else None
            if table in excluded:
                excluded_reference = True
            else:
                retained_targets.append(value)
        result["targets"] = retained_targets
    if "targetBindings" in result and isinstance(result["targetBindings"], list):
        retained_bindings = []
        for binding in result["targetBindings"]:
            if binding.get("tableName") in excluded:
                excluded_reference = True
            else:
                retained_bindings.append(binding)
        result["targetBindings"] = retained_bindings
    if excluded_reference and not result.get("targets") and not result.get("targetBindings"):
        result["decisionStatus"] = "V3_TARGET_EXCLUDED"
        result["disposition"] = "SOURCE_ONLY"
        result["targets"] = ["plt_migration_source_record.source_payload"]
        result["targetBindings"] = [{"tableName": "plt_migration_source_record", "columnName": "source_payload", "jsonPath": None}]
        result["rawPreservedBy"] = "plt_migration_source_record.source_payload"
        result["decisionBasis"] = "ADR-0022 excludes V3 governance tables from the V1/V2 core migration DDL; preserve source evidence for the later feature migration"
    if result.get("sourceTable") in USER_CONFIRMED_EXCLUDED_SOURCE_TABLES or result.get("tableName") in USER_CONFIRMED_EXCLUDED_SOURCE_TABLES:
        result["decisionStatus"] = "USER_CONFIRMED_EXCLUDED"
        result["disposition"] = "EXCLUDED"
        result["targets"] = []
        result["targetBindings"] = []
        result.pop("target", None)
        result.pop("rawPreservedBy", None)
        result["transform"] = "NO_MIGRATION; retain source extraction audit metadata only"
        result["decisionBasis"] = "requirement owner confirmation on 2026-08-13: exclude the complete pm_project_maintenance table"
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
            if new_field_metadata is not None:
                current_metadata = new_field_metadata.get((table_name, column_name))
                if current_metadata is not None:
                    metadata = {**(metadata or {}), **current_metadata}
            if metadata is None:
                raise ValueError(f"target catalog metadata missing for {source_key[0]}.{source_key[1]}")
            row = {
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
            }
            if metadata.get("dataElementRefs"):
                row["dataElementRefs"] = metadata["dataElementRefs"]
            if metadata.get("basisRefs"):
                row["basisRefs"] = metadata["basisRefs"]
            if metadata.get("migrationMappingStatus"):
                row["migrationMappingStatus"] = metadata["migrationMappingStatus"]
            if signature.get("generatedExpression") is not None:
                row["generatedExpression"] = signature["generatedExpression"]
            rows.append(row)
    return rows


def jsonl_content(rows: list[dict[str, object]]) -> str:
    return "".join(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n" for row in rows)


def json_content(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2) + "\n"


def expanded_source_fields(source_field: str) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    inherited_table = ""
    for token in source_field.split("|"):
        if "." in token:
            inherited_table, field = token.rsplit(".", 1)
        else:
            field = token
        if inherited_table and field:
            result.append((inherited_table, field))
    return result


def exact_binding_coordinates(binding: dict[str, str], schema_records: list[dict[str, object]]) -> list[str]:
    prefix = "data-elements://schema-records.jsonl#"
    evidence_ref = binding.get("evidenceRef", "")
    if not evidence_ref.startswith(prefix):
        return []
    fragment = evidence_ref[len(prefix):]
    coordinate = re.fullmatch(r"(.+)!([A-Z]+)(\d+)(?::[A-Z]+(\d+))?", fragment)
    if coordinate:
        sheet = coordinate.group(1)
        start = int(coordinate.group(3))
        end = int(coordinate.group(4) or coordinate.group(3))
        candidates = [
            record for record in schema_records
            if record.get("sheet") == sheet and start <= int(record.get("row", -1)) <= end
        ]
    else:
        query = dict(part.split("=", 1) for part in fragment.split("&") if "=" in part)
        candidates = [
            record for record in schema_records
            if fnmatch.fnmatch(str(record.get("tableName", "")), query.get("table", ""))
            and fnmatch.fnmatch(str(record.get("fieldName", "")), query.get("field", "*"))
        ]
    refs: list[str] = []
    for table_pattern, field_pattern in expanded_source_fields(binding.get("sourceField", "")):
        matches = [
            record for record in candidates
            if fnmatch.fnmatch(str(record.get("tableName", "")), table_pattern)
            and fnmatch.fnmatch(str(record.get("fieldName", "")), field_pattern)
        ]
        if not matches:
            raise ValueError(f"binding evidence does not resolve exact source field: {table_pattern}.{field_pattern}")
        refs.extend(f"{record['sheet']}!{record['cell']}" for record in matches)
    return sorted(set(refs))


def expected_outputs(root: Path) -> dict[Path, str]:
    ddl_path = root / DDL
    contract = json.loads((root / CONTRACT).read_text(encoding="utf-8"))
    project_code_contract = json.loads((root / PROJECT_CODE_CONTRACT).read_text(encoding="utf-8"))
    market_relation_contract = json.loads((root / MARKET_RELATION_CONTRACT).read_text(encoding="utf-8"))
    core_migration_schema_contract = json.loads((root / CORE_MIGRATION_SCHEMA_CONTRACT).read_text(encoding="utf-8"))
    domain_migration_contract = json.loads((root / DOMAIN_MIGRATION_CONTRACT).read_text(encoding="utf-8"))
    schema_records = [
        json.loads(line) for line in (root / SCHEMA_RECORDS).read_text(encoding="utf-8").splitlines() if line
    ]
    source_mapping_overrides = {
        (item["sourceTable"], item["sourceColumn"]): item
        for item in market_relation_contract["sourceMappingOverrides"]
    }
    new_field_metadata = {
        (item["table"], item["name"]): item
        for item in project_code_contract["columns"]
    }
    new_field_metadata.update({
        ("plt_external_key_mapping", "target_role"): {
            "table": "plt_external_key_mapping", "name": "target_role", "domain": "基础平台",
            "fieldClass": "RELATION", "dataElementRefs": [],
        },
        ("plt_external_key_mapping", "target_sequence"): {
            "table": "plt_external_key_mapping", "name": "target_sequence", "domain": "基础平台",
            "fieldClass": "RELATION", "dataElementRefs": [],
        },
    })
    if set(contract.get("implementationScope", {}).get("excludedTargets", [])) != set(core_migration_schema_contract["v3DesignOnlyTables"]):
        raise ValueError("database naming scope and ADR-0022 V3 exclusions differ")
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
    extension_domains = {
        "COM": "合同订单履约",
        "IMP": "方案与实施",
        "ACC": "验收与闭环",
        "CUT": "割接管理",
        "SRV": "服务支持",
        "AST": "资产与设备",
        "PLT": "基础平台",
        "PROJ": "项目与任务",
    }
    v17_table_basis = {
        "imp_configuration_collection_result": ["PRD:EXE-03", "SDS:08-data-model#ConfigurationCollectionResult", "SDS:09-database-design#ConfigurationCollectionResult", "ADR-0025"],
        "imp_configuration_collection_parse_attempt": ["PRD:EXE-03", "SDS:08-data-model#ConfigurationCollectionResult", "ADR-0025"],
        "imp_configuration_component_candidate": ["PRD:EXE-03", "SDS:08-data-model#ConfigurationCollectionResult", "ADR-0025"],
        "acc_satisfaction_collection_task": ["PRD:ACC-02", "PRD:SUB-03", "PRD:CLO-01", "SDS:08-data-model#SatisfactionCollection", "ADR-0025"],
        "acc_satisfaction_questionnaire": ["PRD:ACC-02", "PRD:SUB-03", "SDS:08-data-model#SatisfactionCollection", "ADR-0025"],
        "acc_satisfaction_response": ["PRD:ACC-02", "SDS:08-data-model#SatisfactionCollection", "ADR-0025"],
        "acc_satisfaction_result": ["PRD:ACC-02", "PRD:SUB-03", "PRD:CLO-01", "SDS:08-data-model#SatisfactionCollection", "ADR-0025"],
        "cut_cutover_support_arrangement": ["PRD:CUT-04", "ADR-0026#support-arrangement", "SDS:09-database-design#CutoverSupportArrangement", "ADR-0027"],
        "cut_cutover_closure": ["PRD:CUT-06", "ADR-0026#p6-closure", "SDS:09-database-design#CutoverClosure", "ADR-0027"],
        "ast_device_component_relation": ["PRD:EXE-03", "PRD:EQP-01", "SDS:08-data-model#DeviceComponentRelation", "ADR-0025"],
    }
    v18_table_basis = {
        "proj_project_template_task_definition": ["PRD:PM-03", "PRD:PM-11", "SDS:09-database-design#ProjectTask", "ADR-0030"],
        "proj_project_task_execution_contract": ["PRD:PM-03", "PRD:PM-10", "PRD:PM-11", "SDS:09-database-design#WorkBinding", "ADR-0030"],
        "proj_project_task_completion_evaluation": ["PRD:PM-10", "PRD:PM-11", "SDS:09-database-design#TaskCompletionEvaluation", "ADR-0030"],
        "cut_cutover_checklist": ["PRD:CUT-01", "PRD:CUT-03", "SDS:09-database-design#CutoverChecklist", "ADR-0030"],
        "cut_cutover_checklist_item": ["PRD:CUT-03", "SDS:09-database-design#CutoverChecklist", "ADR-0030"],
        "cut_cutover_checklist_item_result": ["PRD:CUT-03", "PRD:INT-12", "SDS:09-database-design#CutoverChecklist", "ADR-0030"],
    }
    physical_table_basis = v17_table_basis | v18_table_basis
    # Derive data-element evidence exclusively from maintained source-to-target
    # bindings. Ranges in the contract are resolved back to the exact source
    # field coordinates, so design-only columns never inherit table-wide refs.
    legacy_data_element_basis: dict[tuple[str, str], set[str]] = {}
    for record in domain_migration_contract.get("records", []):
        for source_entry in record.get("sources", []):
            if source_entry.get("sourceType") not in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}:
                continue
            for binding_entry in source_entry.get("targetFieldBindings", []):
                target_field = binding_entry.get("targetField", "")
                if "." not in target_field:
                    continue
                table_name, column_name = target_field.split(".", 1)
                if table_name not in physical_table_basis:
                    continue
                legacy_data_element_basis.setdefault((table_name, column_name), set()).update(
                    exact_binding_coordinates(binding_entry, schema_records)
                )
    pending_migration_fields = {
        ("acc_satisfaction_questionnaire", "required_question_count"),
        ("acc_satisfaction_response", "response_valid"),
        ("acc_satisfaction_response", "signature_valid"),
        ("acc_satisfaction_response", "required_validation_summary"),
        ("acc_satisfaction_response", "item_validation_summary"),
        ("acc_satisfaction_response", "signature_ref"),
        ("acc_satisfaction_result", "signature_valid"),
        ("acc_satisfaction_result", "required_items_valid"),
        ("cut_cutover_support_arrangement", "person_type_code"),
        ("cut_cutover_support_arrangement", "role_code"),
        ("cut_cutover_closure", "result_code"),
    }
    for extension in contract.get("modelExtensions", []):
        table_name = extension["target"]
        table = ddl_tables[table_name]
        domain = extension_domains[extension["owner"]]
        for column_name in table.columns:
            field_class = "BUSINESS"
            if column_name in {"id", "tenant_id"}:
                field_class = "IDENTITY"
            elif column_name in {"status", "version", "deleted"}:
                field_class = "CONTROL"
            elif column_name in {"creator", "create_time", "updater", "update_time"}:
                field_class = "AUDIT"
            elif column_name.startswith("source_"):
                field_class = "LINEAGE"
            elif column_name.endswith("_id"):
                field_class = "RELATION"
            new_field_metadata[(table_name, column_name)] = {
                "table": table_name,
                "name": column_name,
                "domain": domain,
                "fieldClass": field_class,
                "dataElementRefs": sorted(legacy_data_element_basis.get((table_name, column_name), set())),
                "basisRefs": physical_table_basis.get(table_name, [f"ADR:{extension['decisionRef']}"]),
            }
            if (table_name, column_name) in pending_migration_fields:
                new_field_metadata[(table_name, column_name)]["migrationMappingStatus"] = "PENDING_FIELD_MAPPING"
            elif table_name in v18_table_basis:
                new_field_metadata[(table_name, column_name)]["migrationMappingStatus"] = "NEW_ONLY"
    for table_name in physical_table_basis:
        missing = [
            column for column in ddl_tables[table_name].columns
            if not new_field_metadata[(table_name, column)].get("basisRefs")
        ]
        if missing:
            raise ValueError(f"physical carrier field basis missing: {table_name}.{missing}")
    prior = [json.loads(line) for line in (root / CATALOG).read_text(encoding="utf-8").splitlines() if line]
    # Idempotent generation can use either the old or already-renamed catalog.
    naming_tables = contract["tables"] + contract.get("tableExtensions", []) + [
        {"source": item["target"], "target": item["target"]}
        for item in contract.get("modelExtensions", [])
    ]
    if prior and prior[0]["tableName"] not in {item["source"] for item in naming_tables}:
        reverse_tables = {item["target"]: item["source"] for item in naming_tables}
        reverse_fields = {(item["targetTable"], item["targetColumn"]): (item["sourceTable"], item["sourceColumn"]) for item in contract["fields"]}
        restored = []
        for item in prior:
            current_table, current_column = item["tableName"], item["columnName"]
            if current_table not in reverse_tables:
                # A prior generated catalog can contain a table that a later
                # scope decision removed. It must not be resurrected as input.
                continue
            source_table, source_column = reverse_fields.get(
                (current_table, current_column),
                (reverse_tables[current_table], current_column),
            )
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
        "v17FieldBasisCoverage": {
            "tableCount": len(v17_table_basis),
            "fieldCount": sum(1 for item in catalog if item["tableName"] in v17_table_basis),
            "missingBasisCount": sum(1 for item in catalog if item["tableName"] in v17_table_basis and not item.get("basisRefs")),
            "dataElementReferencedFieldCount": sum(1 for item in catalog if item["tableName"] in v17_table_basis and item.get("dataElementRefs")),
        },
    }
    outputs[root / CATALOG_SUMMARY] = json_content(summary)
    generated_jsonl_rows: dict[str, list[dict[str, object]]] = {}
    for name in JSONL_TARGET_FILES:
        path = root / MIGRATION / name
        rows = [rewrite_target_references(json.loads(line), contract, ddl_hash, source_mapping_overrides) for line in path.read_text(encoding="utf-8").splitlines() if line]
        generated_jsonl_rows[name] = rows
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
            payload["physicalDispositionCounts"] = dict(sorted(Counter(
                row.get("disposition", "UNCLASSIFIED")
                for row in generated_jsonl_rows["legacy-physical-field-mapping.jsonl"]
            ).items()))
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
