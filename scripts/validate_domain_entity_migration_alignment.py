#!/usr/bin/env python3
"""Validate the executable, source-specific migration contract for every domain entity."""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import subprocess
from pathlib import Path


ALLOWED_SOURCE_TYPES = {"CURRENT_TABLE", "CURRENT_FIELD_PATTERN", "LEGACY_TABLE", "LEGACY_FIELD_PATTERN", "EXTERNAL_SYSTEM", "DERIVED_TARGET", "NONE_NEW"}
ALLOWED_DISPOSITIONS = {"STRUCTURED", "RELATION", "SNAPSHOT", "EXTERNAL_SYNC", "CURRENT_FORWARD", "REBUILD", "COMPATIBILITY_ONLY", "NEW_ONLY", "PENDING_SOURCE_CONFIRMATION", "EXCLUDED"}
EXTERNAL_MARKERS = {
    "CRM": ("CRM",), "ITR": ("ITR",), "MES": ("MES",),
    "DingTalk": ("钉钉", "DingTalk"),
    "ExistingCollectionPlatform": ("现有采集平台", "ExistingCollectionPlatform"),
}
MULTI_OWNER_OBJECT_OWNER = {
    "FileArtifact": "PLT", "CollectionTask": "PLT", "WorkOrder": "SRV",
    "MetricSnapshot": "ANA", "InspectionReport": "SRV", "Contract": "COM",
    "SalesOrder": "COM", "AuthorizationGrant": "PLT", "MaintenanceFact": "AST",
    "ServiceStatus": "SRV",
}
MODEL_ENTITY_CONTRACTS = {
    "DeliveryEvidence": ("IMP", {"IMP-01", "IMP-02"}),
    "DeviceAssignmentHistory": ("AST", {"EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"}),
    "DeviceAncestorProjection": ("AST", {"EQP-01", "EQP-03"}),
    "MetricDefinition": ("ANA", {"ANA-01"}),
    "NoticeBusinessReference": ("KNO", {"INT-04"}),
    "DispatchAttempt": ("PLT", {"INT-12"}),
    "CallbackRecord": ("PLT", {"INT-12"}),
}


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def phase2_contracts(path: Path) -> dict[str, dict[str, set[str]]]:
    text = path.read_text(encoding="utf-8")
    result: dict[str, dict[str, set[str]]] = {}
    for block in re.split(r"(?=^### [A-Z]+-\d+\s*$)", text, flags=re.M):
        requirement = re.search(r"^### ([A-Z]+-\d+)\s*$", block, re.M)
        objects = re.search(r"^- 数据对象：(.+?)\s*$", block, re.M)
        tables = re.search(r"^- 数据表：(.+?)\s*$", block, re.M)
        if not requirement or not objects or not tables:
            continue
        table_names = {item.strip() for item in re.split(r"[、/]", tables.group(1)) if item.strip() and item.strip() != "N/A"}
        for object_name in (item.strip() for item in re.split(r"[、/]", objects.group(1)) if item.strip()):
            entry = result.setdefault(object_name, {"requirements": set(), "tables": set()})
            entry["requirements"].add(requirement.group(1))
            entry["tables"].update(table_names)
    return result


def requirement_owners(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) >= 3 and re.fullmatch(r"[A-Z]+-\d+", cells[0]):
            result[cells[0]] = cells[2].split("（", 1)[0].strip()
    return result


def sql_table_catalog(sql_root: Path) -> tuple[dict[str, str], dict[str, str]]:
    tables: dict[str, str] = {}
    definitions: dict[str, str] = {}
    pattern = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", re.I)
    for path in sorted(sql_root.rglob("*.sql")):
        text = path.read_text(encoding="utf-8-sig")
        for table in pattern.findall(text):
            tables[table] = path.relative_to(sql_root.parent.parent).as_posix()
            definitions[table] = text
    return tables, definitions


def legacy_catalog(path: Path) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        item = json.loads(line)
        table = item.get("tableName")
        if table:
            result.setdefault(table, set())
            if item.get("fieldName"):
                result[table].add(item["fieldName"])
    return result


def wildcard_matches(pattern: str, values: set[str]) -> bool:
    return any(fnmatch.fnmatch(value, pattern) for value in values)


def expected_object_owner(object_name: str, requirement_owner_set: set[str]) -> str | None:
    if object_name in MODEL_ENTITY_CONTRACTS:
        return MODEL_ENTITY_CONTRACTS[object_name][0]
    if object_name in MULTI_OWNER_OBJECT_OWNER:
        return MULTI_OWNER_OBJECT_OWNER[object_name]
    return next(iter(requirement_owner_set)) if len(requirement_owner_set) == 1 else None


def validate(root: Path, implementation_override: Path | None = None) -> list[str]:
    errors: list[str] = []
    traceability = root / "docs" / "traceability"
    contract_path = traceability / "domain-entity-migration-contract.json"
    required = [
        contract_path,
        traceability / "domain-entity-migration-contract.md",
        traceability / "domain-object-table-map.json",
        traceability / "phase2-contract-map.md",
        traceability / "requirement-matrix.md",
        root / "docs" / "design" / "02-domain-model.md",
        root / "docs" / "design" / "08-data-model.md",
        root / "docs" / "design" / "09-database-design.md",
        root / "docs" / "design" / "12-integration-design.md",
        root / "docs" / "design" / "08a-domain-entity-migration-alignment.md",
        root / "specs" / "001-project-delivery-platform" / "evidence" / "data-elements" / "schema-records.jsonl",
        root / "specs" / "001-project-delivery-platform" / "evidence" / "migration" / "ddl-drift-review.json",
        root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json",
    ]
    missing = [str(path.relative_to(root)) for path in required if not path.exists()]
    if missing:
        return [f"missing migration validation input: {item}" for item in missing]

    payload = load_json(contract_path)
    phase2 = phase2_contracts(traceability / "phase2-contract-map.md")
    owners = requirement_owners(traceability / "requirement-matrix.md")
    domain_model = (root / "docs" / "design" / "02-domain-model.md").read_text(encoding="utf-8")
    data_model = (root / "docs" / "design" / "08-data-model.md").read_text(encoding="utf-8")
    database_design = (root / "docs" / "design" / "09-database-design.md").read_text(encoding="utf-8")
    object_table_map = load_json(traceability / "domain-object-table-map.json").get("objects", {})
    integration_design = (root / "docs" / "design" / "12-integration-design.md").read_text(encoding="utf-8")
    alignment = (root / "docs" / "design" / "08a-domain-entity-migration-alignment.md").read_text(encoding="utf-8")
    legacy = legacy_catalog(root / "specs" / "001-project-delivery-platform" / "evidence" / "data-elements" / "schema-records.jsonl")
    implementation = implementation_override or Path(payload.get("implementationRepo", ""))
    if not implementation.is_dir():
        errors.append(f"implementation repository unavailable: {implementation}")
        current_tables, current_definitions = {}, {}
    else:
        current_tables, current_definitions = sql_table_catalog(implementation / "sql" / "migrations")
        try:
            actual_commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
            if actual_commit != payload.get("implementationCommit"):
                errors.append(f"implementation commit mismatch: contract={payload.get('implementationCommit')} actual={actual_commit}")
            tree_state = subprocess.run(["git", "status", "--porcelain"], cwd=implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
            if tree_state or payload.get("implementationTreeState") != "CLEAN":
                errors.append("implementation source evidence must be generated from a clean locked tree")
        except (OSError, subprocess.CalledProcessError) as exc:
            errors.append(f"cannot verify implementation commit: {exc}")

    records = payload.get("records", [])
    names = [record.get("object") for record in records]
    if len(names) != len(set(names)):
        errors.append("duplicate domain entity migration records")
    active_model_contracts = {name for name in MODEL_ENTITY_CONTRACTS if name in data_model}
    expected_objects = set(phase2) | active_model_contracts
    if set(names) != expected_objects:
        errors.append(f"domain entity coverage mismatch; missing={sorted(expected_objects-set(names))}, extra={sorted(set(names)-expected_objects)}")
    if set(object_table_map) != expected_objects:
        errors.append("object-table map coverage does not equal the complete domain entity set")
    if payload.get("objectTableMap") != object_table_map:
        errors.append("embedded and standalone object-table maps differ")

    all_objects = set(names)
    for record in records:
        object_name = record.get("object", "")
        expected_requirements = phase2.get(object_name, {}).get("requirements", set())
        if object_name in active_model_contracts:
            expected_requirements = MODEL_ENTITY_CONTRACTS[object_name][1]
        actual_requirements = set(record.get("requirementIds", []))
        if actual_requirements != expected_requirements:
            errors.append(f"{object_name} requirementIds mismatch")
        owner = record.get("owner")
        requirement_owner_set = {owners[item] for item in actual_requirements if item in owners}
        expected_owner = expected_object_owner(object_name, requirement_owner_set)
        if not owner or owner not in requirement_owner_set or owner != expected_owner:
            errors.append(f"{object_name} Owner is not backed by its Phase 1 requirement ownership: {owner}")
        if object_name not in domain_model and object_name not in data_model and object_name not in alignment:
            errors.append(f"{object_name} has no domain-model/alignment evidence")
        target_tables = record.get("targetTables", [])
        map_entry = object_table_map.get(object_name, {})
        if map_entry.get("owner") != owner or set(map_entry.get("requirementIds", [])) != actual_requirements:
            errors.append(f"{object_name} object-table map Owner/Requirement mismatch")
        if target_tables != map_entry.get("targetTables"):
            errors.append(f"{object_name} target tables do not exactly match the machine object-table map")
        if not target_tables or len(target_tables) != len(set(target_tables)):
            errors.append(f"{object_name} targetTables must be non-empty and unique")
        for table in target_tables:
            if f"`{table}`" not in database_design:
                errors.append(f"{object_name} target table not declared by 09 database design: {table}")
        sources = record.get("sources", [])
        if not sources:
            errors.append(f"{object_name} has no source-specific disposition")
        for source in sources:
            source_type = source.get("sourceType")
            source_object = source.get("sourceObject", "")
            disposition = source.get("disposition", "")
            evidence = source.get("evidenceRef", "")
            if source_type not in ALLOWED_SOURCE_TYPES:
                errors.append(f"{object_name} invalid source type: {source_type}")
                continue
            if disposition not in ALLOWED_DISPOSITIONS or "+" in disposition:
                errors.append(f"{object_name} source {source_object} has invalid or combined disposition: {disposition}")
            if not source.get("transform") or not source.get("mappingStatus") or not source.get("gate"):
                errors.append(f"{object_name} source {source_object} lacks transform/status/gate")
            source_parts = source_object.split("|")
            if source_type == "CURRENT_TABLE":
                for table in source_parts:
                    if table not in current_tables:
                        errors.append(f"{object_name} current source table not found: {table}")
                    expected_ref = f"implementation://{payload.get('implementationCommit', '')}/{current_tables.get(table, '')}#table={table}"
                    if expected_ref not in evidence:
                        errors.append(f"{object_name} current source evidence is unstable: {table}")
            elif source_type == "CURRENT_FIELD_PATTERN":
                table, field_pattern = source_object.split(".", 1) if "." in source_object else (source_object, "")
                if table not in current_tables:
                    errors.append(f"{object_name} current field source table not found: {table}")
                elif field_pattern and not re.search(re.escape(field_pattern.rstrip("*")), current_definitions[table], re.I):
                    errors.append(f"{object_name} current field pattern not found: {source_object}")
                expected_ref = f"implementation://{payload.get('implementationCommit', '')}/{current_tables.get(table, '')}#field-pattern={source_object}"
                if expected_ref not in evidence:
                    errors.append(f"{object_name} current field evidence is unstable: {source_object}")
            elif source_type in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}:
                for part in source_parts:
                    table_pattern, field_pattern = part.split(".", 1) if "." in part else (part, "")
                    matched_tables = {table for table in legacy if fnmatch.fnmatch(table, table_pattern)}
                    if not matched_tables:
                        errors.append(f"{object_name} legacy source absent from structured data elements: {part}")
                    elif field_pattern and not any(wildcard_matches(field_pattern, legacy[table]) for table in matched_tables):
                        errors.append(f"{object_name} legacy field pattern absent from structured data elements: {part}")
                    if f"data-elements://schema-records.jsonl#table={table_pattern}" not in evidence:
                        errors.append(f"{object_name} legacy evidence is unstable: {part}")
            elif source_type == "EXTERNAL_SYSTEM":
                for system in source_parts:
                    markers = EXTERNAL_MARKERS.get(system)
                    if not markers or not any(marker in integration_design for marker in markers):
                        errors.append(f"{object_name} external source is not declared by 12 integration design: {system}")
                if "design://12-integration-design.md#systems=" not in evidence:
                    errors.append(f"{object_name} external source evidence is unstable")
            elif source_type == "DERIVED_TARGET":
                missing_objects = set(source_parts) - all_objects
                if missing_objects:
                    errors.append(f"{object_name} derived source objects do not exist: {sorted(missing_objects)}")
                if disposition not in {"REBUILD", "NEW_ONLY"}:
                    errors.append(f"{object_name} derived source must be REBUILD or NEW_ONLY")
            elif source_type == "NONE_NEW":
                if source_object != object_name or disposition != "NEW_ONLY":
                    errors.append(f"{object_name} NONE_NEW must be an object-local NEW_ONLY source")

    ddl_review = load_json(root / "specs" / "001-project-delivery-platform" / "evidence" / "migration" / "ddl-drift-review.json")
    gate = load_json(root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json")
    ddl_path = root / ddl_review["inputs"]["ddlPath"]
    actual_ddl_sha = hashlib.sha256(ddl_path.read_bytes()).hexdigest().upper()
    if actual_ddl_sha != ddl_review["inputs"].get("currentDdlSha256"):
        errors.append("DDL drift review current hash does not match the current DDL")
    p3e09 = next((item for item in gate.get("items", []) if item.get("id") == "P3-E09"), None)
    drift_unapproved = ddl_review["decisionPolicy"].get("current") == "DEFER" or not ddl_review["decisionPolicy"].get("approvedDdlSha256")
    if drift_unapproved and (not p3e09 or p3e09.get("status") != "OPEN" or gate.get("overallStatus") == "READY_FOR_SDS_BASELINE"):
        errors.append("unapproved DDL drift must keep P3-E09 OPEN and Phase 3 not ready")
    if p3e09 and p3e09.get("confirmedFacts", {}).get("currentDdlSha256") not in {None, actual_ddl_sha}:
        errors.append("P3-E09 current DDL hash conflicts with the drift review")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--implementation", type=Path)
    args = parser.parse_args()
    errors = validate(args.root.resolve(), args.implementation)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    payload = load_json(args.root.resolve() / "docs" / "traceability" / "domain-entity-migration-contract.json")
    print(f"[PASS] {len(payload.get('records', []))} domain entities have source-specific, evidence-backed migration contracts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
