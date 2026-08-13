#!/usr/bin/env python3
"""Validate ADR-0019's machine-readable database naming contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/database-naming-contract.json")
ADR = Path("docs/decisions/0019-domain-coded-database-naming.md")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
EXPECTED_DOMAINS = {"ACC", "ANA", "AST", "COM", "CUS", "CUT", "IMP", "KNO", "PLT", "PROJ", "RES", "SOL", "SRV"}
FORBIDDEN_TABLE_TOKENS = {"rel", "ref", "map"}
EXPECTED_MODEL_EXTENSIONS = [
    {"target": "com_delivery_scope_detail", "owner": "COM", "decisionRef": "ADR-0023", "requirementRefs": ["COM-01", "PM-02"]},
    {"target": "imp_configuration_collection_result", "owner": "IMP", "decisionRef": "ADR-0025", "requirementRefs": ["EXE-03", "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
    {"target": "imp_configuration_collection_parse_attempt", "owner": "IMP", "decisionRef": "ADR-0025", "requirementRefs": ["EXE-03", "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
    {"target": "imp_configuration_component_candidate", "owner": "IMP", "decisionRef": "ADR-0025", "requirementRefs": ["EXE-03", "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
    {"target": "acc_satisfaction_collection_task", "owner": "ACC", "decisionRef": "ADR-0025", "requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    {"target": "acc_satisfaction_questionnaire", "owner": "ACC", "decisionRef": "ADR-0025", "requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    {"target": "acc_satisfaction_response", "owner": "ACC", "decisionRef": "ADR-0025", "requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    {"target": "acc_satisfaction_result", "owner": "ACC", "decisionRef": "ADR-0025", "requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    {"target": "cut_cutover_support_task", "owner": "CUT", "decisionRef": "ADR-0025", "requirementRefs": ["CUT-11"]},
    {"target": "cut_cutover_support_history", "owner": "CUT", "decisionRef": "ADR-0025", "requirementRefs": ["CUT-11"]},
    {"target": "cut_cutover_support_responsibility_interval", "owner": "CUT", "decisionRef": "ADR-0025", "requirementRefs": ["CUT-11"]},
    {"target": "ast_device_component_relation", "owner": "AST", "decisionRef": "ADR-0025", "requirementRefs": ["EXE-03", "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
]


def load_contract(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def parse_adr_tables(text: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for line in text.splitlines():
        match = re.match(r"^\|(\d+)\|`([^`]+)`\|`([^`]+)`\|([A-Z]+)\|", line)
        if match:
            rows.append({"source": match.group(2), "target": match.group(3), "owner": match.group(4)})
    return rows


def validate_payload(payload: dict[str, object], adr_tables: list[dict[str, str]] | None = None) -> list[str]:
    errors: list[str] = []
    if payload.get("schemaVersion") != 1 or payload.get("status") != "ACCEPTED" or payload.get("decisionRef") != "ADR-0019":
        errors.append("database naming contract metadata mismatch")
    if set(payload.get("domainCodes", [])) != EXPECTED_DOMAINS:
        errors.append("database naming contract domain code set mismatch")
    if payload.get("allowedTableAbbreviations") != {"configuration": "config", "serial_number": "sn"}:
        errors.append("allowed table abbreviations must be exactly config and sn")
    if set(payload.get("forbiddenTableTokens", [])) != FORBIDDEN_TABLE_TOKENS:
        errors.append("forbidden table token set mismatch")

    tables = payload.get("tables", [])
    extensions = payload.get("tableExtensions", [])
    model_extensions = payload.get("modelExtensions", [])
    implementation_scope = payload.get("implementationScope", {})
    fields = payload.get("fields", [])
    if not isinstance(tables, list) or not isinstance(fields, list):
        return errors + ["tables and fields must be lists"]
    if len(tables) != 52:
        errors.append(f"database naming contract must contain 52 ADR-0019 tables, found {len(tables)}")
    if extensions != [{"source": "pm_project_market_relations_from_sms", "target": "cus_market_relation", "owner": "CUS", "decisionRef": "ADR-0021"}]:
        errors.append("database naming contract ADR-0021 extension mismatch")
    if model_extensions != EXPECTED_MODEL_EXTENSIONS:
        errors.append("database naming contract ADR-0023/ADR-0025 model extension mismatch")
    if not isinstance(implementation_scope, dict) or implementation_scope.get("coverage") != "CORE_MIGRATION_SUBSET" or implementation_scope.get("decisionRef") != "ADR-0022":
        errors.append("database naming contract implementation scope mismatch")
    if len(fields) != 6:
        errors.append(f"database naming contract must contain 6 field decisions, found {len(fields)}")
    all_tables = tables + extensions + model_extensions
    sources = [item.get("source") for item in all_tables if item.get("source")]
    targets = [item.get("target") for item in all_tables]
    if len(sources) != len(set(sources)):
        errors.append("duplicate source table")
    if len(targets) != len(set(targets)):
        errors.append("duplicate target table")
    table_by_source = {item.get("source"): item for item in all_tables if item.get("source")}
    target_set = set(targets)
    excluded_targets = set(implementation_scope.get("excludedTargets", [])) if isinstance(implementation_scope, dict) else set()
    if not excluded_targets <= target_set:
        errors.append(f"implementation scope excludes unknown target tables: {sorted(excluded_targets-target_set)}")
    for item in all_tables:
        source, target, owner = item.get("source"), item.get("target"), item.get("owner")
        if owner not in EXPECTED_DOMAINS:
            errors.append(f"invalid table owner: {source} -> {owner}")
            continue
        if not isinstance(target, str) or target.startswith("pms_"):
            errors.append(f"legacy system prefix in target table: {target}")
            continue
        if not target.startswith(owner.lower() + "_"):
            errors.append(f"target table owner prefix mismatch: {target} owner={owner}")
        tokens = set(target.split("_"))
        forbidden = sorted(tokens & FORBIDDEN_TABLE_TOKENS)
        if forbidden:
            errors.append(f"unapproved table abbreviation in {target}: {','.join(forbidden)}")
    identifiers: set[str] = set()
    for item in fields:
        identifier = item.get("id")
        if not isinstance(identifier, str) or not re.fullmatch(r"NAM-00[1-6]", identifier):
            errors.append(f"invalid field decision id: {identifier}")
        elif identifier in identifiers:
            errors.append(f"duplicate field decision id: {identifier}")
        identifiers.add(str(identifier))
        source_table = item.get("sourceTable")
        target_table = item.get("targetTable")
        if source_table not in table_by_source:
            errors.append(f"field decision references unknown source table: {source_table}")
        if target_table not in target_set:
            errors.append(f"field decision references unknown target table: {target_table}")
        elif source_table in table_by_source and table_by_source[source_table].get("target") != target_table:
            errors.append(f"field decision table mapping mismatch: {identifier}")
    if adr_tables is not None and tables != adr_tables:
        errors.append("ADR-0019 table mapping differs from machine contract")
    return errors


def validate_ddl(payload: dict[str, object], ddl: str) -> list[str]:
    errors: list[str] = []
    blocks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"CREATE\s+TABLE\s+([a-zA-Z0-9_]+)\s*\((.*?)\)\s*ENGINE\s*=", ddl, re.I | re.S)
    }
    excluded = set(payload.get("implementationScope", {}).get("excludedTargets", []))
    expected = {
        item["target"]
        for item in payload["tables"] + payload.get("tableExtensions", []) + payload.get("modelExtensions", [])
    } - excluded
    if set(blocks) != expected:
        errors.append(f"DDL table set differs from naming contract; missing={sorted(expected-set(blocks))}, extra={sorted(set(blocks)-expected)}")
    for item in payload["fields"]:
        body = blocks.get(item["targetTable"], "")
        source_present = re.search(rf"(?m)^\s*{re.escape(item['sourceColumn'])}\s+", body) is not None
        target_present = re.search(rf"(?m)^\s*{re.escape(item['targetColumn'])}\s+", body) is not None
        if source_present or not target_present:
            errors.append(f"DDL field naming decision not applied: {item['id']}")
    return errors


def validate_contract(root: Path) -> list[str]:
    payload = load_contract(root / CONTRACT)
    adr_tables = parse_adr_tables((root / ADR).read_text(encoding="utf-8"))
    return validate_payload(payload, adr_tables) + validate_ddl(payload, (root / DDL).read_text(encoding="utf-8-sig"))


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
    payload = load_contract(root / CONTRACT)
    print(f"[PASS] database naming contract; tables={len(payload['tables'])} fields={len(payload['fields'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
