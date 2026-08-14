from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_phase3_evidence_submission.py"
SPEC = importlib.util.spec_from_file_location("validate_phase3_evidence_submission", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class Phase3EvidenceSubmissionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "submission.json"
        self.payload = {"schemaVersion": 1, "id": "P3-E08", "status": "DRAFT", "decisionOwner": None, "reviewOwner": None, "confirmedFacts": {}, "evidenceRefs": [], "verificationResult": None}
        self.write()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self) -> None:
        self.path.write_text(json.dumps(self.payload), encoding="utf-8")

    def test_draft_is_allowed_without_invented_facts(self) -> None:
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_submitted_requires_all_facts_and_owner(self) -> None:
        self.payload["status"] = "EVIDENCE_SUBMITTED"
        self.write()
        self.assertTrue(any("missing confirmed facts" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_packet_with_accepted_direction_passes(self) -> None:
        self.payload.update({"id": "P3-E01", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E01"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED"})
        self.write()
        self.assertEqual([], VALIDATOR.validate(self.path))

    def test_submitted_packet_rejects_direction_drift(self) -> None:
        self.payload.update({"id": "P3-E07", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E07"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED"})
        self.write()
        self.assertTrue(any("ADR-0004 direction" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_e03_rejects_recovery_objective_drift(self) -> None:
        self.payload.update({"id": "P3-E03", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E03"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED", "approvedRpo": "PT1H", "approvedRto": "PT8H"})
        self.write()
        self.assertTrue(any("ADR-0005" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_e03_rejects_backup_or_topology_drift(self) -> None:
        self.payload.update({"id": "P3-E03", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E03"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED", **VALIDATOR.RECOVERY_OBJECTIVES, "retention": {**VALIDATOR.BACKUP_RETENTION_POLICY, "dailyRetention": "P7D"}, "recoveryTopology": VALIDATOR.RECOVERY_TOPOLOGY})
        self.write()
        self.assertTrue(any("ADR-0012" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_e05_rejects_permanent_audit_policy_drift(self) -> None:
        self.payload.update({"id": "P3-E05", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E05"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED", "retentionPolicy": {"policyCode": "P3Y"}})
        self.write()
        self.assertTrue(any("ADR-0006" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_e05_rejects_trace_sampling_drift(self) -> None:
        self.payload.update({"id": "P3-E05", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E05"]}
        self.payload["confirmedFacts"].update({
            "directionDecision": "A", "directionStatus": "ACCEPTED",
            "retentionPolicy": VALIDATOR.PERMANENT_AUDIT_POLICY,
            "samplingPolicy": {**VALIDATOR.TRACE_SAMPLING_POLICY, "standardSuccessSampleRate": 0.01},
        })
        self.write()
        self.assertTrue(any("ADR-0011" in item for item in VALIDATOR.validate(self.path)))

    def test_submitted_e05_rejects_export_authorization_drift(self) -> None:
        self.payload.update({"id": "P3-E05", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "REQUIREMENT_OWNER", "evidenceRefs": ["controlled-report"]})
        self.payload["confirmedFacts"] = {key: "evidence-value" for key in VALIDATOR.REQUIRED_FACTS["P3-E05"]}
        self.payload["confirmedFacts"].update({"directionDecision": "A", "directionStatus": "ACCEPTED", "retentionPolicy": VALIDATOR.PERMANENT_AUDIT_POLICY, "samplingPolicy": VALIDATOR.TRACE_SAMPLING_POLICY, "exportAuthorizationPolicy": {**VALIDATOR.EXPORT_AUTHORIZATION_POLICY, "approvalRequired": True}})
        self.write()
        self.assertTrue(any("ADR-0014" in item for item in VALIDATOR.validate(self.path)))

    def test_verified_e08_rejects_failed_type_check(self) -> None:
        self.payload.update({"status": "VERIFIED", "decisionOwner": "FE", "reviewOwner": "QA", "verificationResult": "PASS", "evidenceRefs": ["report"]})
        self.payload["confirmedFacts"] = {key: "x" for key in VALIDATOR.REQUIRED_FACTS["P3-E08"]}
        self.payload["confirmedFacts"].update({"exitCode": 1, "errorCount": 182})
        self.write()
        self.assertTrue(any("exitCode=0" in item for item in VALIDATOR.validate(self.path)))

    def test_e09_rejects_migration_approval_hash(self) -> None:
        self.payload.update({"schemaVersion": 2, "id": "P3-E09", "status": "EVIDENCE_SUBMITTED", "decisionOwner": "DATA", "evidenceRefs": ["report"]})
        self.payload["confirmedFacts"] = {key: "A" for key in VALIDATOR.REQUIRED_FACTS["P3-E09"]}
        self.payload["confirmedFacts"]["approvedDdlSha256"] = "A"
        self.write()
        self.assertTrue(any("must remain empty" in item for item in VALIDATOR.validate(self.path)))

    def test_secret_value_field_is_rejected(self) -> None:
        self.payload["confirmedFacts"] = {"password": "unsafe"}
        self.write()
        self.assertTrue(any("must not be embedded" in item for item in VALIDATOR.validate(self.path)))


if __name__ == "__main__":
    unittest.main()
