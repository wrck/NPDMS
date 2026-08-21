#!/usr/bin/env python3
"""Synchronize P3-E09 Requirement Owner decision facts into the Phase 3 register."""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path


REGISTER = Path("docs/engineering/gates/phase-3/phase3-evidence-register.json")
GENERATOR = Path("scripts/generate_phase3_evidence_packets.py")
SYNC_FACTS = {
    "currentDdlSha256", "modelDecisionStatus", "deferredItemCount",
    "v17DeltaStatus", "v18DeltaStatus", "decisionRegisterItemCount",
    "requirementOwnerConfirmation", "q07Decision", "q08Decision",
    "isolatedMysqlExecution", "targetCatalogDdlSha256", "mappingDdlSha256",
    "validationDdlSha256", "manifestDdlSha256", "itemsSha256", "itemIdsSha256",
    "mysql84DdlSha256", "independentReviewResult", "independentReviewRef",
    "releaseApplicability", "executionWindowPolicy",
}


def load_generator(root: Path):
    spec = importlib.util.spec_from_file_location("phase3_packet_generator_for_sync", root / GENERATOR)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.path.insert(0, str(root / "scripts"))
    spec.loader.exec_module(module)
    return module


def sync(payload: dict[str, object], generated: dict[str, object]) -> dict[str, object]:
    item = next((row for row in payload.get("items", []) if row.get("id") == "P3-E09"), None)
    if item is None:
        raise ValueError("Phase 3 register has no P3-E09 item")
    facts = item["confirmedFacts"]
    generated_facts = generated["confirmedFacts"]
    facts.pop("approvedDdlSha256", None)
    for key in ("candidateCommit", "reviewDate", "reviewRange"):
        facts.pop(key, None)
    for key in SYNC_FACTS:
        facts[key] = generated_facts[key]
    facts["driftDecision"] = "ACCEPT_CURRENT"
    ready = facts["modelDecisionStatus"] == "MODEL_BASELINE_READY"
    item["status"] = "VERIFIED" if ready else "OPEN"
    item["decisionOwner"] = "REQUIREMENT_OWNER"
    item["reviewOwner"] = "INDEPENDENT_REVIEWER" if ready else None
    item["blocks"] = generated["blocks"]
    for reference in generated["evidenceRefs"]:
        if reference not in item["evidenceRefs"]:
            item["evidenceRefs"].append(reference)
    payload.pop("overallStatus", None)
    payload["schemaVersion"] = 2
    payload["modelEvidenceStatus"] = "MODEL_BASELINE_READY" if ready else "MODEL_BASELINE_NOT_READY"
    return payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    path = root / REGISTER
    payload = json.loads(path.read_text(encoding="utf-8"))
    generated = load_generator(root).build_packets()["P3-E09"]
    expected = sync(json.loads(json.dumps(payload)), generated)
    expected_text = json.dumps(expected, ensure_ascii=False, indent=4) + "\n"
    if args.check:
        if path.read_text(encoding="utf-8") != expected_text:
            print("[FAIL] Phase 3 P3-E09 requirement confirmation drift")
            return 1
        print("[PASS] Phase 3 P3-E09 model baseline synchronized; AI-MIG-000 remains conditionally scoped to historical migration/data cutover releases")
        return 0
    path.write_text(expected_text, encoding="utf-8", newline="\n")
    print(f"[WRITE] {REGISTER.as_posix()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
