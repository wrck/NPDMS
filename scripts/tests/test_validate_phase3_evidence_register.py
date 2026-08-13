from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_phase3_evidence_register.py"
SPEC = importlib.util.spec_from_file_location("validate_phase3_evidence_register", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class Phase3EvidenceRegisterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "register.json"
        items = []
        for index in range(1, 10):
            identifier = f"P3-E{index:02d}"
            facts = {}
            refs = []
            if identifier == "P3-E08":
                facts = {"result": "FAIL", "exitCode": 1}
                refs = ["failure.md"]
            if identifier == "P3-E09":
                facts = {"currentDdlSha256": "CURRENT", "legacyCatalogDdlSha256": "OLD", "driftDecision": "DEFER"}
                refs = ["drift.md"]
            expected_direction = VALIDATOR.DIRECTION_DECISIONS.get(identifier)
            decision_owner = None
            if expected_direction:
                facts.update({"directionDecision": expected_direction, "directionStatus": "ACCEPTED", "chosenDirection": "test direction"})
                refs.append(VALIDATOR.DECISION_REF)
                decision_owner = "REQUIREMENT_OWNER"
            if identifier in VALIDATOR.LOCAL_REPOSITORY_ASSESSMENTS:
                facts["localRepositoryAssessment"] = VALIDATOR.LOCAL_REPOSITORY_ASSESSMENTS[identifier]
                refs.append("docs/engineering/gates/phase-3/runtime-fact-inventory.md")
            items.append({"id": identifier, "status": "OPEN", "decisionOwner": decision_owner, "reviewOwner": None, "confirmedFacts": facts, "evidenceRefs": refs, "blocks": ["GATE"]})
        self.payload = {"schemaVersion": 1, "phase": "SDS_PHASE_3", "baseline": "PRD_V1.6", "decisionBaseline": VALIDATOR.DECISION_REF, "overallStatus": "NOT_READY_FOR_SDS_BASELINE", "items": items}
        self.write()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self) -> None:
        self.path.write_text(json.dumps(self.payload), encoding="utf-8")

    def test_open_register_is_structurally_valid(self) -> None:
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_open_register_fails_release_readiness(self) -> None:
        errors = VALIDATOR.validate(self.path, require_ready=True)
        self.assertTrue(any("not ready" in error for error in errors))

    def test_verified_requires_owner_and_evidence(self) -> None:
        self.payload["items"][0]["status"] = "VERIFIED"
        self.write()
        self.assertTrue(any("VERIFIED requires" in error for error in VALIDATOR.validate(self.path)))

    def test_missing_item_is_detected(self) -> None:
        self.payload["items"].pop()
        self.write()
        self.assertTrue(any("coverage mismatch" in error for error in VALIDATOR.validate(self.path)))

    def test_wrong_direction_decision_is_detected(self) -> None:
        self.payload["items"][0]["confirmedFacts"]["directionDecision"] = "B"
        self.write()
        self.assertTrue(any("direction decision" in error for error in VALIDATOR.validate(self.path)))

    def test_local_development_evidence_cannot_be_promoted_to_production(self) -> None:
        self.payload["items"][1]["confirmedFacts"]["localRepositoryAssessment"] = "PRODUCTION_HA_VERIFIED"
        self.write()
        self.assertTrue(any("local repository evidence assessment" in error for error in VALIDATOR.validate(self.path)))


if __name__ == "__main__":
    unittest.main()
