from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]


def load_module(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


validator = load_module(
    ROOT / "scripts" / "validate_core_migration_schema_contract.py",
    "core_contract_validator",
)

core_path = ROOT / "docs" / "traceability" / "core-migration-schema-contract.json"
core = json.loads(core_path.read_text(encoding="utf-8"))
core["v17Delta"] = {
    "decisionRef": "ADR-0027",
    "status": "BLOCKED_BY_REVIEW",
    "requirementRefs": sorted(validator.EXPECTED_V17_REQUIREMENTS),
    "objectTargetTables": {
        name: sorted(tables)
        for name, tables in validator.EXPECTED_V17_OBJECT_TABLES.items()
    },
    "historicalReadOnlyTables": [],
    "appendOnlyTables": [
        "acc_satisfaction_questionnaire",
        "acc_satisfaction_response",
        "acc_satisfaction_result",
        "cut_cutover_closure",
    ],
    "tableContracts": validator.v17_table_contract_payload(),
}
core["forbiddenV1V2Tables"] = sorted(validator.EXPECTED_FORBIDDEN_V1V2_TABLES)
for stale in (
    "cut_cutover_support_task",
    "cut_cutover_support_history",
    "cut_cutover_support_responsibility_interval",
):
    core["currentTableScope"].pop(stale, None)
core["currentTableScope"]["cut_cutover_support_arrangement"] = {
    "requirementRefs": ["CUT-04"]
}
core["currentTableScope"]["cut_cutover_closure"] = {
    "requirementRefs": ["CUT-06"]
}
core_path.write_text(
    json.dumps(core, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

naming_path = ROOT / "docs" / "traceability" / "database-naming-contract.json"
naming = json.loads(naming_path.read_text(encoding="utf-8"))
naming["modelExtensions"] = [
    item
    for item in naming["modelExtensions"]
    if item["target"]
    not in {
        "cut_cutover_support_task",
        "cut_cutover_support_history",
        "cut_cutover_support_responsibility_interval",
        "cut_cutover_support_arrangement",
        "cut_cutover_closure",
    }
]
naming_extensions = [
        {
            "target": "cut_cutover_support_arrangement",
            "owner": "CUT",
            "decisionRef": "ADR-0027",
            "requirementRefs": ["CUT-04"],
        },
        {
            "target": "cut_cutover_closure",
            "owner": "CUT",
            "decisionRef": "ADR-0027",
            "requirementRefs": ["CUT-06"],
        },
]
ast_index = next(
    index
    for index, item in enumerate(naming["modelExtensions"])
    if item["target"] == "ast_device_component_relation"
)
naming["modelExtensions"][ast_index:ast_index] = naming_extensions
naming_path.write_text(
    json.dumps(naming, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)

drift = load_module(
    ROOT / "scripts" / "generate_ddl_drift_review.py",
    "ddl_drift_generator",
)
ddl_path = ROOT / "specs" / "001-project-delivery-platform" / "appendices" / "project-order-physical-schema.mysql.sql"
ddl_bytes = ddl_path.read_bytes()
tables = drift.parse_ddl(ddl_bytes)
ddl_sha256 = drift.sha256(ddl_bytes)
register = drift.ddl_item_decision_register(
    ddl_sha256,
    ddl_sha256,
    tables,
    tables,
    constraints_comparable=True,
    options_comparable=True,
)
_q07_ids, _q08_ids, counts = drift.q07_q08_item_ids(register)
primary_shapes = {
    "singleId": sum(
        1 for table in tables.values()
        if any(item.strip().upper() == "PRIMARY KEY (ID)" for item in table.constraints)
    ),
    "compositeProjection": sum(
        1 for table in tables.values()
        if any(item.strip().upper().startswith("PRIMARY KEY") and item.strip().upper() != "PRIMARY KEY (ID)" for item in table.constraints)
    ),
}
core = json.loads(core_path.read_text(encoding="utf-8"))
core["q07TechnicalConstraintPolicy"].update(
    {
        "ddlSha256": ddl_sha256,
        "primaryKeyCount": counts["primaryKeyCount"],
        "primaryKeyShape": primary_shapes,
        "tenantReferenceKeyCount": counts["tenantReferenceKeyCount"],
        "sameDomainForeignKeyCount": counts["sameDomainForeignKeyCount"],
        "stableTechnicalCheckGroups": {
            key: counts[key]
            for key in ("softDelete", "temporalOrder", "booleanFlag", "noSelf", "nonnegativeCount")
        },
    }
)
core["q08OrdinaryIndexPolicy"].update(
    {"ddlSha256": ddl_sha256, "candidateIndexCount": counts["candidateIndexCount"]}
)
core_path.write_text(
    json.dumps(core, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
)
print(json.dumps({"ddlSha256": ddl_sha256, "q07": counts, "primaryKeyShape": primary_shapes}, ensure_ascii=False))

register_path = ROOT / "docs" / "engineering" / "gates" / "phase-3" / "phase3-evidence-register.json"
gate_register = json.loads(register_path.read_text(encoding="utf-8"))
p3e09 = next(item for item in gate_register["items"] if item["id"] == "P3-E09")
facts = p3e09["confirmedFacts"]
facts["currentDdlSha256"] = ddl_sha256
decision_register = json.loads(
    (ROOT / "specs" / "001-project-delivery-platform" / "evidence" / "migration" / "ddl-item-decision-register.json").read_text(encoding="utf-8")
)
facts["decisionRegisterItemCount"] = decision_register["summary"]["itemCount"]
stable_check_count = sum(counts[key] for key in ("softDelete", "temporalOrder", "booleanFlag", "noSelf", "nonnegativeCount"))
facts["q07Decision"].update(
    {
        "technicalConstraintCount": counts["primaryKeyCount"] + counts["tenantReferenceKeyCount"] + counts["sameDomainForeignKeyCount"] + stable_check_count,
        "primaryKeyCount": counts["primaryKeyCount"],
        "primaryKeyShape": primary_shapes,
        "tenantReferenceKeyCount": counts["tenantReferenceKeyCount"],
        "sameDomainForeignKeyCount": counts["sameDomainForeignKeyCount"],
        "stableTechnicalCheckCount": stable_check_count,
    }
)
facts["q08Decision"]["candidateIndexCount"] = counts["candidateIndexCount"]
mysql_evidence = json.loads(
    (ROOT / "specs" / "001-project-delivery-platform" / "evidence" / "migration" / "ddl-mysql84-execution-evidence.json").read_text(encoding="utf-8")
)
facts["isolatedMysqlExecution"].update(
    {
        "status": mysql_evidence["status"],
        "mysqlVersion": mysql_evidence["mysqlVersion"],
        "tableCount": mysql_evidence["tableCount"],
        "columnCount": mysql_evidence["columnCount"],
        "constraintCount": mysql_evidence["constraintCount"],
    }
)
for ref in (
    "docs/decisions/0026-cutover-flow-business-baseline-correction.md",
    "docs/decisions/0027-cutover-physical-model-correction.md",
):
    if ref not in p3e09["evidenceRefs"]:
        p3e09["evidenceRefs"].append(ref)
register_path.write_text(json.dumps(gate_register, ensure_ascii=False, indent=4) + "\n", encoding="utf-8")
