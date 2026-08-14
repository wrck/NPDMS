#!/usr/bin/env python3
"""Validate the executable, source-specific migration contract for every domain entity."""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from p3e09_approval_policy import item_ids_sha256, validate_model_baseline
from generate_domain_entity_migration_contract import git_sql_blobs


ALLOWED_SOURCE_TYPES = {"CURRENT_TABLE", "CURRENT_FIELD_PATTERN", "LEGACY_TABLE", "LEGACY_FIELD_PATTERN", "EXTERNAL_SYSTEM", "DERIVED_TARGET", "NONE_NEW", "PENDING_SOURCE_IDENTIFICATION"}
ALLOWED_DISPOSITIONS = {"STRUCTURED", "RELATION", "SNAPSHOT", "EXTERNAL_SYNC", "CURRENT_FORWARD", "REBUILD", "COMPATIBILITY_ONLY", "NEW_ONLY", "PENDING_SOURCE_CONFIRMATION", "PENDING_SOURCE_IDENTIFICATION", "EXCLUDED"}
USER_EXCLUDED_LEGACY_TABLES = {"pm_project_maintenance"}
EXTERNAL_MARKERS = {
    "CRM": ("CRM",), "ITR": ("ITR",), "MES": ("MES",),
    "HR": ("HR",),
    "DingTalk": ("钉钉", "DingTalk"),
    "ExistingCollectionPlatform": ("现有采集平台", "ExistingCollectionPlatform"),
}
MULTI_OWNER_OBJECT_OWNER = {
    "FileArtifact": "PLT", "CollectionTask": "PLT",
    "DeviceComponentRelation": "AST", "SatisfactionCollection": "ACC",
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
    "CutoverSupportArrangement": ("CUT", {"CUT-04"}),
}
MODEL_GOVERNANCE_CONTRACTS = {}


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


def git_sql_table_catalog(repository: Path, commit: str) -> tuple[dict[str, str], dict[str, str]]:
    tables: dict[str, str] = {}
    definitions: dict[str, str] = {}
    pattern = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", re.I)
    for path, text in git_sql_blobs(repository, commit).items():
        for table in pattern.findall(text):
            tables[table] = path
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


def legacy_schema_records(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def expanded_source_fields(source_field: str) -> list[tuple[str, str]]:
    """Expand `table.field|field2|other.field` without losing table context."""
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


def binding_evidence_records(evidence_ref: str, records: list[dict]) -> list[dict]:
    prefix = "data-elements://schema-records.jsonl#"
    if not evidence_ref.startswith(prefix):
        return []
    fragment = evidence_ref[len(prefix):]
    coordinate = re.fullmatch(r"(.+)!([A-Z]+)(\d+)(?::[A-Z]+(\d+))?", fragment)
    if coordinate:
        sheet, start, end = coordinate.group(1), int(coordinate.group(3)), int(coordinate.group(4) or coordinate.group(3))
        return [record for record in records if record.get("sheet") == sheet and start <= int(record.get("row", -1)) <= end]
    query = dict(part.split("=", 1) for part in fragment.split("&") if "=" in part)
    table_pattern = query.get("table")
    field_pattern = query.get("field", "*")
    if table_pattern:
        return [
            record for record in records
            if fnmatch.fnmatch(record.get("tableName", ""), table_pattern)
            and fnmatch.fnmatch(record.get("fieldName", ""), field_pattern)
        ]
    return []


def validate_legacy_binding_semantics(
    object_name: str,
    source_object: str,
    binding: dict,
    schema_records: list[dict],
) -> list[str]:
    errors: list[str] = []
    source_field = binding.get("sourceField", "")
    target_field = binding.get("targetField", "")
    target_column = target_field.rsplit(".", 1)[-1]
    declared_patterns = source_object.split("|")
    expanded = expanded_source_fields(source_field)
    evidence_records = binding_evidence_records(binding.get("evidenceRef", ""), schema_records)
    for table_pattern, field_pattern in expanded:
        if not any(
            fnmatch.fnmatch(table_pattern, declared) or fnmatch.fnmatch(declared, table_pattern)
            for declared in declared_patterns
        ):
            errors.append(
                f"{object_name} source binding field outside declared sourceObject: {table_pattern}.{field_pattern}"
            )
        if not any(
            fnmatch.fnmatch(record.get("tableName", ""), table_pattern)
            and fnmatch.fnmatch(record.get("fieldName", ""), field_pattern)
            for record in evidence_records
        ):
            errors.append(
                f"{object_name} binding evidence does not contain source field: {table_pattern}.{field_pattern}"
            )
        normalized_source = re.sub(r"[^a-z0-9]", "", field_pattern.lower())
        normalized_target = target_column.lower()
        if "name" in normalized_source and normalized_target.endswith("code"):
            errors.append(
                f"{object_name} name field cannot bind code column: {table_pattern}.{field_pattern} -> {target_field}"
            )
        if any(marker in normalized_source for marker in ("hour", "duration")) and normalized_target in {
            "direction_code", "signed_adjustment_hours"
        }:
            errors.append(
                f"{object_name} duration field cannot bind adjustment/direction: {table_pattern}.{field_pattern} -> {target_field}"
            )
    if target_column.endswith("_id"):
        transform = binding.get("transform", "").upper()
        if not any(marker in transform for marker in ("EXTERNAL_KEY_MAPPING", "NEW_GENERATED", "TARGET_KEY_LOOKUP")):
            errors.append(f"{object_name} target reference {target_field} requires explicit target-key resolution")
    return errors


def wildcard_matches(pattern: str, values: set[str]) -> bool:
    return any(fnmatch.fnmatch(value, pattern) for value in values)


def ddl_column_catalog(ddl: str) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    for match in re.finditer(r"CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*?)\)\s*ENGINE", ddl, re.I | re.S):
        columns = set()
        for line in match.group(2).splitlines():
            column = re.match(r"\s*`?(\w+)`?\s+", line)
            if column and column.group(1).upper() not in {"PRIMARY", "UNIQUE", "KEY", "CONSTRAINT", "FOREIGN", "CHECK"}:
                columns.add(column.group(1))
        result[match.group(1)] = columns
    return result


def expected_object_owner(object_name: str, requirement_owner_set: set[str]) -> str | None:
    if object_name in MODEL_ENTITY_CONTRACTS:
        return MODEL_ENTITY_CONTRACTS[object_name][0]
    if object_name in MULTI_OWNER_OBJECT_OWNER:
        return MULTI_OWNER_OBJECT_OWNER[object_name]
    return next(iter(requirement_owner_set)) if len(requirement_owner_set) == 1 else None


def validate_target_table_policy(
    object_name: str,
    owner: str | None,
    requirement_ids: set[str],
    target_tables: list[str],
    map_entry: dict,
    database_design: str,
) -> list[str]:
    errors: list[str] = []
    policy = map_entry.get("targetTablePolicy", "CURRENT_PHYSICAL_TARGET")
    if policy == "CURRENT_PHYSICAL_TARGET":
        if not target_tables or len(target_tables) != len(set(target_tables)):
            errors.append(f"{object_name} targetTables must be non-empty and unique")
    elif policy == "FEATURE_FORWARD_MIGRATION":
        feature_requirement = map_entry.get("featureRequirementId")
        if target_tables:
            errors.append(f"{object_name} feature-forward targetTables must remain empty until its Feature migration is approved")
        if not feature_requirement or feature_requirement not in requirement_ids:
            errors.append(f"{object_name} featureRequirementId must reference one of its Requirement IDs")
        elif f"物理表由{feature_requirement} Feature前向迁移确定" not in database_design:
            errors.append(f"{object_name} feature-forward policy is not declared by 09 database design")
    else:
        errors.append(f"{object_name} has unsupported targetTablePolicy: {policy}")
    for table in target_tables:
        if table.startswith("pms_"):
            errors.append(f"{object_name} target table retains legacy system prefix: {table}")
        if owner and not table.startswith(owner.lower() + "_"):
            errors.append(f"{object_name} target table owner prefix mismatch: {table} owner={owner}")
        if f"`{table}`" not in database_design:
            errors.append(f"{object_name} target table not declared by 09 database design: {table}")
    return errors


def validate(root: Path, implementation_override: Path | None = None) -> list[str]:
    errors: list[str] = []
    traceability = root / "docs" / "traceability"
    contract_path = traceability / "domain-entity-migration-contract.json"
    required = [
        contract_path,
        traceability / "domain-entity-migration-contract.md",
        traceability / "domain-object-table-map.json",
        traceability / "core-migration-schema-contract.json",
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
    legacy_schema_path = root / "specs" / "001-project-delivery-platform" / "evidence" / "data-elements" / "schema-records.jsonl"
    legacy = legacy_catalog(legacy_schema_path)
    legacy_records = legacy_schema_records(legacy_schema_path)
    ddl_review = load_json(root / "specs" / "001-project-delivery-platform" / "evidence" / "migration" / "ddl-drift-review.json")
    ddl_path = root / ddl_review["inputs"]["ddlPath"]
    physical_target_columns = ddl_column_catalog(ddl_path.read_text(encoding="utf-8"))
    physical_target_tables = set(physical_target_columns)
    implementation = implementation_override or Path(payload.get("implementationRepo", ""))
    frozen_commit = payload.get("implementationCommit", "")
    if not implementation.is_dir():
        errors.append(f"implementation repository unavailable: {implementation}")
        current_tables, current_definitions = {}, {}
    else:
        try:
            if payload.get("implementationEvidenceMode") != "PINNED_GIT_COMMIT":
                errors.append("implementation evidence mode must be PINNED_GIT_COMMIT")
            current_tables, current_definitions = git_sql_table_catalog(implementation, frozen_commit)
        except ValueError as exc:
            errors.append(str(exc))
            current_tables, current_definitions = {}, {}

    records = payload.get("records", [])
    excluded_sources = payload.get("excludedSources", [])
    if not isinstance(excluded_sources, list) or len(excluded_sources) != 1:
        errors.append("excludedSources must contain exactly the confirmed pm_project_maintenance exclusion")
        excluded_sources = []
    for excluded in excluded_sources:
        audit = excluded.get("exclusionAudit", {}) if isinstance(excluded, dict) else {}
        if (
            excluded.get("sourceObject") != "pm_project_maintenance"
            or excluded.get("sourceType") != "LEGACY_TABLE"
            or excluded.get("disposition") != "EXCLUDED"
            or excluded.get("mappingStatus") != "NO_MIGRATION"
            or excluded.get("targetFieldBindings", []) != []
            or audit.get("decisionDate") != "2026-08-13"
            or audit.get("decisionSource") != "REQUIREMENT_OWNER_CONFIRMATION"
            or audit.get("sourceTable") != "pm_project_maintenance"
            or audit.get("auditStatus") not in {"PENDING_EXTRACTION_AUDIT", "CAPTURED"}
            or set(audit) != {
                "decisionDate", "decisionSource", "sourceTable", "rowCount",
                "extractionBatchSha256", "auditStatus",
            }
        ):
            errors.append("pm_project_maintenance top-level exclusion audit is incomplete")
    core_schema_contract = load_json(traceability / "core-migration-schema-contract.json")
    v17_target_tables = {
        table
        for tables in core_schema_contract["v17Delta"]["objectTargetTables"].values()
        for table in tables
    }
    all_binding_count = 0
    v17_binding_count = 0
    v17_legacy_binding_count = 0
    v17_legacy_source_tables: set[str] = set()
    v17_legacy_source_fields: list[str] = []
    for contract_record in records:
        for source_entry in contract_record.get("sources", []):
            bindings = source_entry.get("targetFieldBindings", [])
            all_binding_count += len(bindings)
            for binding_entry in bindings:
                if binding_entry.get("targetField", "").split(".", 1)[0] not in v17_target_tables:
                    continue
                v17_binding_count += 1
                if source_entry.get("sourceType") not in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}:
                    continue
                v17_legacy_binding_count += 1
                for table_pattern, field_pattern in expanded_source_fields(binding_entry.get("sourceField", "")):
                    v17_legacy_source_tables.add(table_pattern)
                    v17_legacy_source_fields.append(f"{table_pattern}.{field_pattern}")
    expected_binding_statistics = {
        "allBindingCount": all_binding_count,
        "v17BindingCount": v17_binding_count,
        "v17LegacyBindingCount": v17_legacy_binding_count,
        "v17LegacySourceTableCount": len(v17_legacy_source_tables),
        "v17LegacySourceFieldCount": len(v17_legacy_source_fields),
        "v17LegacyUniqueSourceFieldCount": len(set(v17_legacy_source_fields)),
    }
    if payload.get("bindingStatistics") != expected_binding_statistics:
        errors.append("binding statistics must be derived from generated source-to-target bindings")
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
        if not owner or owner != expected_owner or (requirement_owner_set and owner not in requirement_owner_set):
            errors.append(f"{object_name} Owner is not backed by its Phase 1 requirement ownership: {owner}")
        governance_contract = MODEL_GOVERNANCE_CONTRACTS.get(object_name)
        if governance_contract and any(
            record.get(key) != value for key, value in governance_contract.items()
        ):
            errors.append(f"{object_name} governance/decision references mismatch")
        if object_name not in domain_model and object_name not in data_model and object_name not in alignment:
            errors.append(f"{object_name} has no domain-model/alignment evidence")
        target_tables = record.get("targetTables", [])
        map_entry = object_table_map.get(object_name, {})
        if map_entry.get("owner") != owner or set(map_entry.get("requirementIds", [])) != actual_requirements:
            errors.append(f"{object_name} object-table map Owner/Requirement mismatch")
        if governance_contract and any(
            map_entry.get(key) != value for key, value in governance_contract.items()
        ):
            errors.append(f"{object_name} object-table map governance/decision mismatch")
        if target_tables != map_entry.get("targetTables"):
            errors.append(f"{object_name} target tables do not exactly match the machine object-table map")
        if record.get("targetTablePolicy", "CURRENT_PHYSICAL_TARGET") != map_entry.get("targetTablePolicy", "CURRENT_PHYSICAL_TARGET"):
            errors.append(f"{object_name} target table policy does not match the machine object-table map")
        if record.get("featureRequirementId") != map_entry.get("featureRequirementId"):
            errors.append(f"{object_name} feature Requirement does not match the machine object-table map")
        errors.extend(validate_target_table_policy(object_name, owner, actual_requirements, target_tables, map_entry, database_design))
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
            excluded_parts = USER_EXCLUDED_LEGACY_TABLES.intersection(source_parts)
            bindings = source.get("targetFieldBindings", [])
            if excluded_parts:
                errors.append(f"{object_name} must not attach pm_project_maintenance to a business object")
            if disposition == "EXCLUDED" and bindings:
                errors.append(f"{object_name} EXCLUDED source must have zero target field bindings")
            requires_field_bindings = disposition in {"STRUCTURED", "RELATION"} and any(
                table in physical_target_tables for table in target_tables
            )
            if requires_field_bindings:
                if not isinstance(bindings, list) or not bindings:
                    errors.append(f"{object_name} source {source_object} has zero target field bindings for {disposition}")
                    bindings = []
                for binding in bindings:
                    source_field = binding.get("sourceField", "") if isinstance(binding, dict) else ""
                    target_field = binding.get("targetField", "") if isinstance(binding, dict) else ""
                    target_table = target_field.split(".", 1)[0] if "." in target_field else ""
                    if not source_field or target_table not in target_tables or not binding.get("transform") or not binding.get("evidenceRef"):
                        errors.append(f"{object_name} source {source_object} has invalid target field binding")
                    if target_field.endswith(".id"):
                        errors.append(f"{object_name} source {source_object} must not bind a source business key to generated target id")
                    target_column = target_field.split(".", 1)[1] if "." in target_field else ""
                    if target_table in physical_target_columns and target_column not in physical_target_columns[target_table]:
                        errors.append(f"{object_name} source {source_object} binds missing physical target field: {target_field}")
                    if source_type in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"} and source_field:
                        inherited_table = ""
                        for token in source_field.split("|"):
                            if "." in token:
                                inherited_table, field = token.rsplit(".", 1)
                            else:
                                field = token
                            matching_tables = [table for table in legacy if fnmatch.fnmatch(table, inherited_table)]
                            if not inherited_table or not matching_tables or not any(
                                fnmatch.fnmatch(candidate, field) for table in matching_tables for candidate in legacy[table]
                            ):
                                errors.append(f"{object_name} source binding field absent from structured data elements: {token}")
                        errors.extend(validate_legacy_binding_semantics(
                            object_name, source_object, binding, legacy_records
                        ))
                if not isinstance(source.get("statusMapping"), dict) or not source.get("statusMapping"):
                    errors.append(f"{object_name} source {source_object} lacks explicit status mapping")
                if not source.get("terminalDisposition"):
                    errors.append(f"{object_name} source {source_object} lacks terminal disposition")
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
                if disposition not in {"REBUILD", "RELATION", "NEW_ONLY"}:
                    errors.append(f"{object_name} derived source must be REBUILD, RELATION or NEW_ONLY")
            elif source_type == "NONE_NEW":
                if source_object != object_name or disposition != "NEW_ONLY":
                    errors.append(f"{object_name} NONE_NEW must be an object-local NEW_ONLY source")
            elif source_type == "PENDING_SOURCE_IDENTIFICATION":
                if disposition != "PENDING_SOURCE_IDENTIFICATION" or bindings:
                    errors.append(f"{object_name} unidentified source must remain pending with zero target bindings")
                if evidence != "decision://2026-08-13/legacy-source-identification-pending":
                    errors.append(f"{object_name} unidentified source evidence is unstable")

    gate = load_json(root / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json")
    actual_ddl_sha = hashlib.sha256(ddl_path.read_bytes()).hexdigest().upper()
    if actual_ddl_sha != ddl_review["inputs"].get("currentDdlSha256"):
        errors.append("DDL drift review current hash does not match the current DDL")
    p3e09 = next((item for item in gate.get("items", []) if item.get("id") == "P3-E09"), None)
    if not p3e09:
        errors.append("domain migration alignment requires P3-E09 evidence")
    else:
        facts = p3e09.get("confirmedFacts", {})
        blocks = set(p3e09.get("blocks", []))
        required_migration_blocks = {"HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"}
        if not required_migration_blocks <= blocks:
            errors.append("P3-E09 must keep historical migration and data cutover blocked")
        if facts.get("modelDecisionStatus") == "MODEL_BASELINE_READY":
            register_path = root / "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json"
            try:
                register = load_json(register_path)
            except (OSError, json.JSONDecodeError) as exc:
                errors.append(f"P3-E09 READY requires the DDL decision register: {exc}")
            else:
                errors.extend(validate_model_baseline(
                    register,
                    {
                        **facts,
                        "decisionOwner": p3e09.get("decisionOwner"),
                        "reviewOwner": p3e09.get("reviewOwner"),
                        "evidenceRefs": p3e09.get("evidenceRefs"),
                    },
                    root=root,
                ))
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
