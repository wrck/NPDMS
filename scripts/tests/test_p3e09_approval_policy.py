from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "p3e09_approval_policy.py"
SPEC = importlib.util.spec_from_file_location("p3e09_approval_policy", MODULE_PATH)
POLICY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(POLICY)


class P3E09ApprovalPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.submission_ref = "docs/engineering/gates/phase-3/submissions/P3-E09/release-01.json"
        self.submission_path = self.root / self.submission_ref
        self.submission_path.parent.mkdir(parents=True)
        reviewer = "reviewer-01"
        self.register = {
            "currentDdlSha256": "DDL",
            "items": [
                {"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT", "reviewOwner": reviewer},
                {"itemId": "TABLE:a", "decision": "AMEND_CURRENT", "reviewOwner": reviewer},
            ],
        }
        self.register["itemsSha256"] = "ITEMS"
        signoffs = {}
        for role, signer in {
            "DATA_ARCHITECTURE_OWNER": "data-01",
            "BUSINESS_OWNER": "business-01",
            "MIGRATION_OWNER": "migration-01",
            "INDEPENDENT_REVIEWER": reviewer,
        }.items():
            evidence_ref = f"docs/engineering/gates/phase-3/submissions/P3-E09/attestations/{role}.txt"
            evidence = self.root / evidence_ref
            evidence.parent.mkdir(parents=True, exist_ok=True)
            evidence.write_text(f"signed by {signer}", encoding="utf-8")
            signoffs[role] = {
                "status": "APPROVED",
                "signerId": signer,
                "signedAt": "2026-08-14T12:00:00+08:00",
                "attestationMethod": "APPROVAL_SYSTEM_RECORD",
                "evidenceRef": evidence_ref,
                "evidenceSha256": POLICY.sha256_bytes(evidence.read_bytes()),
            }
        self.payload = {
            "schemaVersion": 2,
            "id": "P3-E09",
            "status": "VERIFIED",
            "reviewOwner": reviewer,
            "capturedAt": "2026-08-14T12:00:00+08:00",
            "verificationResult": "PASS",
            "confirmedFacts": {
                field: "DDL" for field in (
                    "approvedDdlSha256", "currentDdlSha256", "targetCatalogDdlSha256",
                    "mappingDdlSha256", "validationDdlSha256", "manifestDdlSha256",
                )
            },
            "approvalBinding": {
                "currentDdlSha256": "DDL",
                "itemsSha256": "ITEMS",
                "itemCount": 2,
                "itemIdsSha256": POLICY.item_ids_sha256(self.register["items"]),
            },
            "signoffs": signoffs,
        }
        self.write()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write(self) -> None:
        self.submission_path.write_text(json.dumps(self.payload), encoding="utf-8")

    def test_exact_four_role_hashed_submission_is_accepted(self) -> None:
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertEqual([], errors)

    def test_requirement_adr_cannot_replace_independent_review_attestation(self) -> None:
        adr = self.root / "docs/decisions/0028.md"
        adr.parent.mkdir(parents=True)
        adr.write_text("requirement decision", encoding="utf-8")
        signoff = self.payload["signoffs"]["INDEPENDENT_REVIEWER"]
        signoff["evidenceRef"] = "docs/decisions/0028.md"
        signoff["evidenceSha256"] = POLICY.sha256_bytes(adr.read_bytes())
        self.write()
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertTrue(any("independent existing file" in error for error in errors))

    def test_reviewer_cannot_equal_a_decision_owner(self) -> None:
        self.payload["signoffs"]["INDEPENDENT_REVIEWER"]["signerId"] = "data-01"
        self.payload["reviewOwner"] = "data-01"
        self.write()
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertTrue(any("distinct" in error for error in errors))

    def test_each_role_requires_a_distinct_attestation_file(self) -> None:
        shared = self.payload["signoffs"]["DATA_ARCHITECTURE_OWNER"]
        business = self.payload["signoffs"]["BUSINESS_OWNER"]
        business["evidenceRef"] = shared["evidenceRef"]
        business["evidenceSha256"] = shared["evidenceSha256"]
        self.write()
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertTrue(any("distinct attestation" in error for error in errors))

    def test_each_role_requires_independently_signed_content(self) -> None:
        data = self.payload["signoffs"]["DATA_ARCHITECTURE_OWNER"]
        business = self.payload["signoffs"]["BUSINESS_OWNER"]
        business_path = self.root / business["evidenceRef"]
        data_path = self.root / data["evidenceRef"]
        business_path.write_bytes(data_path.read_bytes())
        business["evidenceSha256"] = data["evidenceSha256"]
        self.write()
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertTrue(any("independently signed" in error for error in errors))

    def test_verified_submission_requires_captured_at_and_matching_ddl_facts(self) -> None:
        self.payload["capturedAt"] = None
        self.payload["confirmedFacts"]["mappingDdlSha256"] = "OTHER"
        self.write()
        errors, _ = POLICY.validate_approval_submission(self.root, self.submission_ref, self.register)
        self.assertTrue(any("capturedAt" in error for error in errors))
        self.assertTrue(any("every DDL artifact" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
