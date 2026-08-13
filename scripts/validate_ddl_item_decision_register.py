#!/usr/bin/env python3
"""Validate AI-MIG-000 item-by-item DDL decisions against current read-only facts."""

from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import sys
from pathlib import Path


ALLOWED_DECISIONS = {"ACCEPT_CURRENT", "RESTORE_APPROVED_BASELINE", "AMEND_CURRENT", "DEFER"}


def nonempty(value: object) -> bool:
    return value is not None and value != "" and value != [] and value != {}


def canonical_sha(items: list[dict[str, object]]) -> str:
    data = json.dumps(items, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(data).hexdigest().upper()


def generated_decision_errors(
    actual_by_id: dict[str, dict[str, object]],
    expected_by_id: dict[str, dict[str, object]],
) -> list[str]:
    errors: list[str] = []
    for identifier in sorted(set(actual_by_id) & set(expected_by_id)):
        actual, generated = actual_by_id[identifier], expected_by_id[identifier]
        for field in ("decision", "decisionOwner"):
            if actual.get(field) != generated.get(field):
                errors.append(f"{identifier} generated decision mismatch: {field}")
        expected_refs = set(generated.get("evidenceRefs", []))
        actual_refs = set(actual.get("evidenceRefs", []))
        if not expected_refs.issubset(actual_refs):
            errors.append(f"{identifier} generated decision mismatch: evidenceRefs")
    return errors


def evidence_reference_errors(root: Path, items: list[dict[str, object]]) -> list[str]:
    errors: list[str] = []
    for item in items:
        if item.get("decision") == "DEFER":
            continue
        for reference in item.get("evidenceRefs", []):
            if not isinstance(reference, str) or not reference:
                errors.append(f"{item.get('itemId')} invalid evidence reference")
                continue
            target = reference.split("#", 1)[0]
            if not (root / target).is_file():
                errors.append(f"{item.get('itemId')} evidence reference does not exist: {target}")
    return errors


def final_approval_errors(root: Path, register: dict[str, object], approved_count: int) -> list[str]:
    approval = register.get("approval", {})
    if not isinstance(approval, dict) or not nonempty(approval.get("approvedDdlSha256")):
        return []
    errors: list[str] = []
    if approval.get("approvedDdlSha256") != register.get("currentDdlSha256"):
        errors.append("approvedDdlSha256 must equal currentDdlSha256")
    if approval.get("itemsSha256") != register.get("itemsSha256"):
        errors.append("approval must bind the current itemsSha256")
    approval_refs = approval.get("evidenceRefs", [])
    errors.extend(evidence_reference_errors(
        root, [{"itemId": "APPROVAL", "decision": "ACCEPT_CURRENT", "evidenceRefs": approval_refs}]
    ))
    items = register.get("items", [])
    if approved_count != len(items) or not nonempty(approval.get("decisionOwner")) or not nonempty(approval.get("reviewOwner")) or not nonempty(approval_refs) or not nonempty(approval.get("signedAt")):
        errors.append("approvedDdlSha256 requires all items reviewed and final approval evidence")
    return errors


def load_generator(script_dir: Path):
    path = script_dir / "generate_ddl_drift_review.py"
    spec = importlib.util.spec_from_file_location("ddl_drift_generator_for_validation", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def resolve_baseline(generator, root: Path, report: dict[str, object]):
    inputs = report["inputs"]
    if inputs["baselineSource"] == "GIT_DDL":
        return generator.parse_ddl(generator.git_blob(root, inputs["baselineCommit"], inputs["ddlPath"]))
    catalog = root / inputs["baselineCatalogPath"]
    _commit, tables = generator.locate_catalog_baseline(root, catalog, inputs["baselineDdlSha256"])
    naming_contract = json.loads((root / "docs/traceability/database-naming-contract.json").read_text(encoding="utf-8"))
    return generator.normalize_baseline_names(tables, naming_contract)


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    migration = root / "specs" / "001-project-delivery-platform" / "evidence" / "migration"
    register_path = migration / "ddl-item-decision-register.json"
    report_path = migration / "ddl-drift-review.json"
    try:
        register = json.loads(register_path.read_text(encoding="utf-8"))
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read DDL decision evidence: {exc}"]
    if register.get("schemaVersion") != 1 or register.get("purpose") != "AI-MIG-000_ITEM_BY_ITEM_DECISION_REGISTER":
        errors.append("DDL item decision register schema/purpose mismatch")
    if register.get("baselineDdlSha256") != report.get("inputs", {}).get("baselineDdlSha256") or register.get("currentDdlSha256") != report.get("inputs", {}).get("currentDdlSha256"):
        errors.append("DDL item decision register hash binding mismatch")
    items = register.get("items")
    if not isinstance(items, list):
        return errors + ["DDL item decision register items must be a list"]
    identifiers = [item.get("itemId") for item in items if isinstance(item, dict)]
    if len(identifiers) != len(set(identifiers)):
        errors.append("duplicate DDL decision itemId")
    if register.get("itemsSha256") != canonical_sha(items):
        errors.append("DDL decision itemsSha256 mismatch")

    generator = load_generator(root / "scripts")
    ddl_path = root / report["inputs"]["ddlPath"]
    current_tables = generator.parse_ddl(ddl_path.read_bytes())
    baseline_tables = resolve_baseline(generator, root, report)
    expected = generator.ddl_item_decision_register(
        register["baselineDdlSha256"], register["currentDdlSha256"], baseline_tables, current_tables,
        constraints_comparable=report["summary"]["constraintsComparable"],
        options_comparable=report["summary"]["tableOptionsComparable"],
    )
    expected_by_id = {item["itemId"]: item for item in expected["items"]}
    actual_by_id = {item.get("itemId"): item for item in items if isinstance(item, dict)}
    if set(actual_by_id) != set(expected_by_id):
        errors.append("DDL decision item coverage differs from generated facts")
    immutable_fields = ("itemType", "table", "name", "comparisonStatus", "baselineValue", "currentValue")
    for identifier in sorted(set(actual_by_id) & set(expected_by_id)):
        actual, generated = actual_by_id[identifier], expected_by_id[identifier]
        for field in immutable_fields:
            if actual.get(field) != generated.get(field):
                errors.append(f"{identifier} immutable fact mismatch: {field}")
        decision = actual.get("decision")
        if decision not in ALLOWED_DECISIONS:
            errors.append(f"{identifier} invalid decision: {decision}")
        if decision != "DEFER" and (not nonempty(actual.get("decisionOwner")) or not nonempty(actual.get("evidenceRefs"))):
            errors.append(f"{identifier} non-DEFER decision requires decisionOwner and evidenceRefs")

    naming_contract = json.loads((root / "docs/traceability/database-naming-contract.json").read_text(encoding="utf-8"))
    project_code_contract = json.loads((root / "docs/traceability/project-code-contract.json").read_text(encoding="utf-8"))
    market_relation_contract = json.loads((root / "docs/traceability/market-relation-contract.json").read_text(encoding="utf-8"))
    core_contract = json.loads((root / "docs/traceability/core-migration-schema-contract.json").read_text(encoding="utf-8"))
    decided_expected = generator.apply_unchanged_baseline_column_decisions(copy.deepcopy(expected))
    decided_expected = generator.apply_accepted_naming_decisions(decided_expected, naming_contract)
    decided_expected = generator.apply_accepted_project_code_decisions(decided_expected, project_code_contract)
    decided_expected = generator.apply_accepted_market_relation_decisions(decided_expected, market_relation_contract)
    decided_expected = generator.apply_accepted_core_schema_decisions(decided_expected, core_contract)
    decided_expected = generator.apply_accepted_q03_decisions(decided_expected, core_contract)
    q07_status = core_contract.get("q07TechnicalConstraintPolicy", {}).get("status")
    q08_status = core_contract.get("q08OrdinaryIndexPolicy", {}).get("status")
    if q07_status == "ACCEPTED" and q08_status == "ACCEPTED":
        decided_expected = generator.apply_accepted_q07_q08_decisions(decided_expected, core_contract)
    if core_contract.get("v17Delta", {}).get("status") == "ACCEPTED":
        decided_expected = generator.apply_accepted_v17_delta_decisions(decided_expected, core_contract)
    expected_decided_by_id = {item["itemId"]: item for item in decided_expected["items"]}
    errors.extend(generated_decision_errors(actual_by_id, expected_decided_by_id))
    errors.extend(evidence_reference_errors(root, items))
    for decision_key in (
        "namingDecision",
        "unchangedBaselineColumnDecision",
        "projectCodeDecision",
        "marketRelationDecision",
        "coreMigrationSchemaDecision",
        "q03Decision",
    ):
        if register.get(decision_key) != decided_expected.get(decision_key):
            errors.append(f"DDL decision register {decision_key} metadata mismatch")
    conditional_metadata = []
    if q07_status == "ACCEPTED" and q08_status == "ACCEPTED":
        conditional_metadata.extend(["q07Decision", "q08Decision"])
    if core_contract.get("v17Delta", {}).get("status") == "ACCEPTED":
        conditional_metadata.append("v17DeltaDecision")
    for decision_key in conditional_metadata:
        if register.get(decision_key) != decided_expected.get(decision_key):
            errors.append(f"DDL decision register {decision_key} metadata mismatch")

    counts: dict[str, int] = {}
    statuses: dict[str, int] = {}
    approved_count = 0
    for item in items:
        counts[item["itemType"]] = counts.get(item["itemType"], 0) + 1
        statuses[item["comparisonStatus"]] = statuses.get(item["comparisonStatus"], 0) + 1
        if item.get("decision") != "DEFER" and nonempty(item.get("reviewOwner")):
            approved_count += 1
    expected_summary = {"itemCount": len(items), "byType": counts, "byComparisonStatus": statuses, "approvedCount": approved_count}
    if register.get("summary") != expected_summary:
        errors.append("DDL item decision summary mismatch")
    errors.extend(final_approval_errors(root, register, approved_count))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] AI-MIG-000 DDL item decision register")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
