#!/usr/bin/env python3
"""Apply the explicit Requirement Owner P3-E09 confirmation to the core contract."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
PACKET = Path("specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.json")
DECISION_REF = "docs/decisions/0028-p3-e09-current-hash-requirement-owner-confirmation.md"
EXPECTED_GROUPS = {"Q07", "Q08", "V1.7", "Q09", "Q10", "Q11", "Q12", "Q13", "Q14"}


def ids_sha256(item_ids: list[str]) -> str:
    canonical = json.dumps(sorted(item_ids), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest().upper()


def apply_confirmation(contract: dict[str, object], packet: dict[str, object]) -> dict[str, object]:
    ddl_sha = packet.get("currentDdlSha256")
    if packet.get("deferredItemCount") != 692 or packet.get("coveredDeferredItemCount") != 692:
        raise ValueError("P3-E09 confirmation packet must cover all 692 deferred items")
    if contract.get("q07TechnicalConstraintPolicy", {}).get("ddlSha256") != ddl_sha:
        raise ValueError("P3-E09 confirmation packet is not bound to the current contract DDL")

    groups = packet.get("groups", [])
    by_code = {group.get("code"): group for group in groups if isinstance(group, dict)}
    if set(by_code) != EXPECTED_GROUPS:
        raise ValueError("P3-E09 confirmation requires the exact nine decision groups")
    for code, group in by_code.items():
        if group.get("recommendedDecision") != "A":
            raise ValueError(f"P3-E09 group {code} is not the approved recommendation A")
        item_ids = [item.get("itemId") for item in group.get("items", []) if isinstance(item, dict)]
        if len(item_ids) != group.get("itemCount") or len(item_ids) != len(set(item_ids)):
            raise ValueError(f"P3-E09 group {code} item coverage is invalid")

    q07 = contract["q07TechnicalConstraintPolicy"]
    q07["status"] = "ACCEPTED"
    q07["decision"] = q07.pop("proposedDecision", q07.get("decision"))
    if q07["decision"] != "ACCEPT_CURRENT_FOR_SDS":
        raise ValueError("P3-E09 Q07 decision is not the approved recommendation")
    q07["decisionEvidenceRef"] = DECISION_REF
    q08 = contract["q08OrdinaryIndexPolicy"]
    q08["status"] = "ACCEPTED"
    q08["decision"] = q08.pop("proposedDecision", q08.get("decision"))
    if q08["decision"] != "ACCEPT_AS_CANDIDATE_BASELINE":
        raise ValueError("P3-E09 Q08 decision is not the approved recommendation")
    q08["decisionEvidenceRef"] = DECISION_REF

    v17_ids = sorted(item["itemId"] for item in by_code["V1.7"]["items"])
    v17 = contract["v17Delta"]
    v17["status"] = "ACCEPTED"
    v17["ddlSha256"] = ddl_sha
    v17["acceptedDdlItems"] = v17_ids
    v17["itemEvidenceRefs"] = {identifier: DECISION_REF for identifier in v17_ids}
    v17["requirementOwnerDecisionRef"] = "ADR-0028"

    union_ids = sorted({item["itemId"] for group in groups for item in group["items"]})
    contract["p3e09RequirementOwnerConfirmation"] = {
        "decisionRef": "ADR-0028",
        "status": "ACCEPTED",
        "confirmedAt": "2026-08-14",
        "decision": "ALL_RECOMMENDED_A",
        "ddlSha256": ddl_sha,
        "packetRef": PACKET.as_posix(),
        "decisionEvidenceRef": DECISION_REF,
        "deferredItemCountAtConfirmation": packet["deferredItemCount"],
        "coveredDeferredItemCount": packet["coveredDeferredItemCount"],
        "reconfirmedExistingDecisionItemCount": packet["reconfirmedExistingDecisionItemCount"],
        "confirmedUniqueItemCount": len(union_ids),
        "confirmedItemIdsSha256": ids_sha256(union_ids),
        "groups": {
            code: {
                "decision": "A",
                "itemCount": group["itemCount"],
                "itemIdsSha256": ids_sha256([item["itemId"] for item in group["items"]]),
            }
            for code, group in sorted(by_code.items())
        },
        "reviewStatus": "REVIEW_PENDING",
        "approvedDdlSha256": None,
    }
    return contract


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    contract_path = root / CONTRACT
    packet_path = root / PACKET
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    packet = json.loads(packet_path.read_text(encoding="utf-8"))
    expected = apply_confirmation(json.loads(json.dumps(contract)), packet)
    expected_text = json.dumps(expected, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if contract_path.read_text(encoding="utf-8") != expected_text:
            print("[FAIL] P3-E09 requirement confirmation contract drift")
            return 1
        print("[PASS] P3-E09 requirement confirmation is bound to the current DDL")
        return 0
    contract_path.write_text(expected_text, encoding="utf-8", newline="\n")
    print(f"[WRITE] {CONTRACT.as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
