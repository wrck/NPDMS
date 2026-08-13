#!/usr/bin/env python3
"""Validate the machine-readable Phase 3 evidence register."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


EXPECTED_IDS = {f"P3-E{index:02d}" for index in range(1, 10)}
VALID_STATUS = {"OPEN", "EVIDENCE_SUBMITTED", "VERIFIED", "REJECTED", "NOT_APPLICABLE"}
BASELINE_REQUIRED = {f"P3-E{index:02d}" for index in range(1, 7)} | {"P3-E09"}
DIRECTION_DECISIONS = {
    "P3-E01": "A",
    "P3-E02": "A",
    "P3-E03": "A",
    "P3-E04": "A",
    "P3-E05": "A",
    "P3-E06": "A",
    "P3-E07": "B",
    "P3-E09": "A",
}
DECISION_REF = "docs/decisions/0004-phase3-production-assurance-directions.md"
LOCAL_REPOSITORY_ASSESSMENTS = {
    "P3-E01": "NO_PRODUCTION_EVIDENCE",
    "P3-E02": "DEVELOPMENT_SINGLE_NODE_ONLY",
    "P3-E03": "NO_RECOVERY_EXERCISE_EVIDENCE",
    "P3-E04": "NO_ENTERPRISE_KMS_EVIDENCE",
    "P3-E05": "CAPABILITY_ONLY_NO_PRODUCTION_BACKEND_EVIDENCE",
    "P3-E06": "LOCAL_HEALTH_SCRIPTS_ONLY",
}


def validate(path: Path, *, require_ready: bool = False) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read evidence register: {exc}"]

    if payload.get("schemaVersion") != 1:
        errors.append("unsupported evidence register schemaVersion")
    if payload.get("phase") != "SDS_PHASE_3" or payload.get("baseline") != "PRD_V1.6":
        errors.append("evidence register phase/baseline mismatch")

    items = payload.get("items")
    if not isinstance(items, list):
        return errors + ["evidence register items must be a list"]
    identifiers = [item.get("id") for item in items if isinstance(item, dict)]
    if len(identifiers) != len(set(identifiers)):
        errors.append("duplicate evidence item id")
    if set(identifiers) != EXPECTED_IDS:
        errors.append(f"evidence item coverage mismatch; missing={sorted(EXPECTED_IDS - set(identifiers))}, extra={sorted(set(identifiers) - EXPECTED_IDS)}")

    for item in items:
        if not isinstance(item, dict):
            errors.append("evidence item must be an object")
            continue
        identifier = item.get("id", "UNKNOWN")
        status = item.get("status")
        if status not in VALID_STATUS:
            errors.append(f"{identifier} invalid status: {status}")
        for field in ("decisionOwner", "reviewOwner", "confirmedFacts", "evidenceRefs", "blocks"):
            if field not in item:
                errors.append(f"{identifier} missing field: {field}")
        if not isinstance(item.get("confirmedFacts"), dict):
            errors.append(f"{identifier} confirmedFacts must be an object")
        if not isinstance(item.get("evidenceRefs"), list) or not isinstance(item.get("blocks"), list):
            errors.append(f"{identifier} evidenceRefs/blocks must be lists")
        if status == "VERIFIED":
            if not item.get("decisionOwner") or not item.get("reviewOwner"):
                errors.append(f"{identifier} VERIFIED requires decisionOwner and reviewOwner")
            if not item.get("confirmedFacts") or not item.get("evidenceRefs"):
                errors.append(f"{identifier} VERIFIED requires confirmed facts and evidence refs")
        if status == "NOT_APPLICABLE" and identifier != "P3-E07":
            errors.append(f"{identifier} cannot be NOT_APPLICABLE")

    by_id = {item["id"]: item for item in items if isinstance(item, dict) and "id" in item}
    if payload.get("decisionBaseline") != DECISION_REF:
        errors.append("Phase 3 direction decision baseline mismatch")
    for identifier, expected_decision in DIRECTION_DECISIONS.items():
        item = by_id.get(identifier, {})
        facts = item.get("confirmedFacts", {})
        if facts.get("directionDecision") != expected_decision or facts.get("directionStatus") != "ACCEPTED":
            errors.append(f"{identifier} direction decision must remain {expected_decision}/ACCEPTED")
        if item.get("decisionOwner") != "REQUIREMENT_OWNER" or DECISION_REF not in item.get("evidenceRefs", []):
            errors.append(f"{identifier} direction decision owner/reference mismatch")
    for identifier, assessment in LOCAL_REPOSITORY_ASSESSMENTS.items():
        item = by_id.get(identifier, {})
        if item.get("confirmedFacts", {}).get("localRepositoryAssessment") != assessment:
            errors.append(f"{identifier} local repository evidence assessment mismatch")
        if "docs/engineering/gates/phase-3/runtime-fact-inventory.md" not in item.get("evidenceRefs", []):
            errors.append(f"{identifier} local repository assessment reference missing")
    e08 = by_id.get("P3-E08", {})
    if e08.get("confirmedFacts", {}).get("result") != "FAIL" or e08.get("status") == "VERIFIED":
        errors.append("P3-E08 must retain the currently verified ts:check failure until closure evidence replaces it")
    e09 = by_id.get("P3-E09", {})
    facts = e09.get("confirmedFacts", {})
    if facts.get("currentDdlSha256") == facts.get("legacyCatalogDdlSha256") or facts.get("driftDecision") != "DEFER":
        errors.append("P3-E09 must retain the current DDL drift and DEFER decision until AI-MIG-000 is approved")

    ready = all(by_id.get(identifier, {}).get("status") == "VERIFIED" for identifier in BASELINE_REQUIRED)
    expected_overall = "READY_FOR_SDS_BASELINE" if ready else "NOT_READY_FOR_SDS_BASELINE"
    if payload.get("overallStatus") != expected_overall:
        errors.append(f"overallStatus must be {expected_overall}")
    if require_ready and not ready:
        errors.append(f"Phase 3 evidence not ready; unverified={sorted(identifier for identifier in BASELINE_REQUIRED if by_id.get(identifier, {}).get('status') != 'VERIFIED')}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--register",
        type=Path,
        default=Path("docs/engineering/gates/phase-3/phase3-evidence-register.json"),
    )
    parser.add_argument("--require-ready", action="store_true")
    args = parser.parse_args()
    errors = validate(args.register, require_ready=args.require_ready)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] Phase 3 evidence register structure and current gate state")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
