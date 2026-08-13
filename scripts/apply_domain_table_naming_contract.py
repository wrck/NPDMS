#!/usr/bin/env python3
"""Apply ADR-0019 naming to the Phase 2 domain object-table contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


OBJECT_MAP = Path("docs/traceability/domain-object-table-map.json")
GENERATOR = Path("scripts/generate_domain_entity_migration_contract.py")
PHASE2_GENERATOR = Path("scripts/generate_phase2_contract_map.py")
DESIGN_FILES = [
    Path("docs/design/08-data-model.md"),
    Path("docs/design/08a-domain-entity-migration-alignment.md"),
    Path("docs/design/09-database-design.md"),
]


SPECIAL = {
    "pms_ins_task": "srv_inspection_task",
    "pms_ins_task_rule_snapshot": "srv_inspection_task_rule_snapshot",
    "pms_ins_rule": "srv_inspection_rule",
    "pms_ins_rule_revision": "srv_inspection_rule_revision",
    "pms_ins_report_revision": "srv_inspection_report_revision",
    "pms_ins_service_issue": "srv_service_issue",
    "pms_ins_remediation": "srv_service_issue_remediation",
    "pms_wo_work_order": "srv_work_order",
    "pms_wo_handling_record": "srv_work_order_handling_record",
    "pms_wo_time_claim": "srv_time_claim",
    "pms_wo_time_adjustment": "srv_time_adjustment",
    "pms_cus_customer": "cus_customer",
    "pms_cus_contact": "cus_customer_contact",
    "pms_cus_project_contact_relation": "cus_project_customer_contact_relation",
    "pms_cus_relationship_snapshot": "cus_customer_relationship_snapshot",
    "pms_equipment": "ast_device",
    "pms_equipment_version": "ast_device_version",
    "pms_equipment_config_log": "ast_device_config_log",
    "pms_int_sync_batch": "ast_asset_sync_batch",
    "pms_int_sync_item": "ast_asset_sync_item",
    "pms_dac_device_credential": "plt_device_credential",
    "pms_dac_credential_grant": "plt_credential_grant",
    "pms_dac_collection_task": "plt_collection_task",
    "pms_dac_dispatch_attempt": "plt_dispatch_attempt",
    "pms_dac_callback_record": "plt_callback_record",
    "pms_dac_collection_result_ref": "plt_collection_result_reference",
}

SPECIAL_BY_OWNER = {
    ("pms_project_stage_snapshot", "PROJ"): "proj_project_stage_snapshot",
    ("pms_project_stage_snapshot", "IMP"): "imp_implementation_readiness_snapshot",
}

# Domain codes may replace the domain name itself, but must not remove a
# non-domain qualifier from the business object name.  These entries repair
# earlier names that lost such qualifiers (for example, "collection" from
# ConfigurationCollectionResult).
FULL_TARGETS = {
    "imp_configuration_result": "imp_configuration_collection_result",
    "imp_parse_attempt": "imp_configuration_collection_parse_attempt",
    "imp_debugging_result": "imp_joint_debugging_result",
    "imp_debugging_item": "imp_joint_debugging_item",
    "sol_plan_revision": "sol_construction_plan_revision",
    "sol_plan_item": "sol_construction_plan_item",
    "sol_plan_change": "sol_construction_plan_change",
    "sol_form_schema": "sol_dynamic_form_schema",
    "sol_form_schema_revision": "sol_dynamic_form_schema_revision",
    "sol_form_instance": "sol_dynamic_form_instance",
    "proj_project_phase_group": "proj_multi_phase_project_group",
    "proj_project_phase_member": "proj_multi_phase_project_member",
}

PREFIX_OWNERS = {
    "acc": "ACC", "ana": "ANA", "ast": "AST", "com": "COM",
    "cus": "CUS", "cut": "CUT", "imp": "IMP", "kno": "KNO",
    "plt": "PLT", "res": "RES", "sol": "SOL", "srv": "SRV",
}

PHASE2_SPECIAL = {
    "pms_project_tree_change": "proj_project_tree_change",
    "pms_int_reconciliation": "plt_integration_reconciliation",
    "pms_dac_result_consumption": "plt_collection_result_consumption",
}


def normalize_target(table: str, owner: str, exact: dict[str, str]) -> str:
    if table in FULL_TARGETS:
        return FULL_TARGETS[table]
    if table in set(exact.values()) or table.startswith(owner.lower() + "_"):
        return table
    if (table, owner) in SPECIAL_BY_OWNER:
        return SPECIAL_BY_OWNER[(table, owner)]
    if table in exact:
        return exact[table]
    if table in SPECIAL:
        return SPECIAL[table]
    if not table.startswith("pms_"):
        raise ValueError(f"unrecognized target table prefix: {table}")
    remainder = table[4:]
    owner_prefix = owner.lower() + "_"
    if remainder.startswith(owner_prefix):
        return remainder
    if owner == "PROJ" and (remainder.startswith("project_") or remainder.startswith("task_")):
        return "proj_" + remainder
    raise ValueError(f"target table needs an explicit full-name rule: {table} owner={owner}")


def normalize_phase2_target(table: str, exact: dict[str, str]) -> str:
    """Resolve a Phase 2 target table without changing migration source names."""
    if table in PHASE2_SPECIAL:
        return PHASE2_SPECIAL[table]
    if table in exact:
        return exact[table]
    if table in SPECIAL:
        return SPECIAL[table]
    if table == "pms_project_stage_snapshot":
        return "proj_project_stage_snapshot"
    if table.startswith("pms_project_") or table.startswith("pms_task_"):
        return "proj_" + table[4:]
    match = re.fullmatch(r"pms_([a-z]+)_(.+)", table)
    if match and match.group(1) in PREFIX_OWNERS:
        return normalize_target(table, PREFIX_OWNERS[match.group(1)], exact)
    if table.startswith("pms_ins_"):
        return "srv_inspection_" + table.removeprefix("pms_ins_")
    if table.startswith("pms_wo_"):
        return "srv_" + table.removeprefix("pms_wo_")
    if table.startswith("pms_dac_"):
        return "plt_" + table.removeprefix("pms_dac_")
    if table.startswith("pms_int_sync_"):
        return "ast_asset_sync_" + table.removeprefix("pms_int_sync_")
    raise ValueError(f"Phase 2 table needs an explicit naming rule: {table}")


def render_outputs(root: Path) -> dict[Path, str]:
    contract = json.loads((root / "docs/traceability/database-naming-contract.json").read_text(encoding="utf-8"))
    exact = {item["source"]: item["target"] for item in contract["tables"]}
    map_path = root / OBJECT_MAP
    payload = json.loads(map_path.read_text(encoding="utf-8"))
    replacements: dict[str, str] = {}
    for item in payload["objects"].values():
        owner = item["owner"]
        targets = []
        for table in item["targetTables"]:
            target = normalize_target(table, owner, exact)
            replacements[table] = target
            targets.append(target)
        item["targetTables"] = targets
    outputs = {map_path: json.dumps(payload, ensure_ascii=False, indent=2) + "\n"}

    generator_path = root / GENERATOR
    generator = generator_path.read_text(encoding="utf-8")
    marker = generator.index("\nMODEL_ENTITY_CONTRACTS")
    head, tail = generator[:marker], generator[marker:]
    for source, target in sorted(replacements.items(), key=lambda item: len(item[0]), reverse=True):
        head = head.replace(f'"{source}"', f'"{target}"')
    head = head.replace(
        '"ProjectStageSnapshot": ("imp_implementation_readiness_snapshot",)',
        '"ProjectStageSnapshot": ("proj_project_stage_snapshot",)',
    )
    outputs[generator_path] = head + tail

    phase2_path = root / PHASE2_GENERATOR
    phase2 = phase2_path.read_text(encoding="utf-8")
    phase2_tables = sorted(set(re.findall(r"\bpms_[a-z0-9_]+\b", phase2)))
    for source in sorted(phase2_tables, key=len, reverse=True):
        phase2 = phase2.replace(source, normalize_phase2_target(source, exact))
    for source, target in sorted(FULL_TARGETS.items(), key=lambda item: len(item[0]), reverse=True):
        phase2 = phase2.replace(source, target)
    outputs[phase2_path] = phase2

    for relative in DESIGN_FILES:
        path = root / relative
        text = path.read_text(encoding="utf-8")
        for source, target in sorted(replacements.items(), key=lambda item: len(item[0]), reverse=True):
            text = text.replace(f"`{source}`", f"`{target}`")
        if relative.name == "09-database-design.md":
            for source in sorted(phase2_tables, key=len, reverse=True):
                text = text.replace(f"`{source}`", f"`{normalize_phase2_target(source, exact)}`")
            for source, target in sorted(FULL_TARGETS.items(), key=lambda item: len(item[0]), reverse=True):
                text = text.replace(f"`{source}`", f"`{target}`")
        outputs[path] = text
    return outputs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    outputs = render_outputs(root)
    drift = [path for path, content in outputs.items() if path.read_text(encoding="utf-8") != content]
    if args.check:
        if drift:
            for path in drift:
                print(f"[FAIL] domain table naming drift: {path.relative_to(root).as_posix()}")
            return 1
        print(f"[PASS] domain object-table naming contract; files={len(outputs)}")
        return 0
    for path, content in outputs.items():
        path.write_text(content, encoding="utf-8", newline="\n")
    print(f"[PASS] applied domain object-table naming contract; files={len(outputs)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
