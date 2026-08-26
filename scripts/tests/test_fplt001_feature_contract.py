from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-PLT-001-unified-file-identity-and-version-management.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-PLT-001-physical-contract.json"


class Fplt001FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))

    def test_platform_owns_business_truth_and_infra_is_technical_only(self) -> None:
        owner = self.contract["owner"]
        self.assertEqual("PLATFORM", owner["context"])
        storage = self.contract["interfaces"]["infraFileApiExceptionCandidate"]
        self.assertIn("URL_AS_FILE_IDENTITY", storage["forbidden"])
        self.assertIn("PLT_DIRECT_INFRA_MAPPER", storage["forbidden"])
        self.assertIn("retain all existing FileApi methods", storage["compatibility"])

    def test_artifact_version_and_reference_are_separate_facts(self) -> None:
        tables = self.contract["tables"]
        self.assertIn("plt_file_artifact", tables)
        self.assertIn("plt_file_version", tables)
        self.assertIn("plt_file_reference", tables)
        self.assertEqual(
            ["tenant_id", "artifact_id", "version_no"],
            tables["plt_file_version"]["stableKey"],
        )
        self.assertNotIn("is_current", tables["plt_file_version"]["requiredFields"])
        self.assertIn("artifact_id", tables["plt_file_reference"]["requiredFields"])
        self.assertIn("file_version_no", tables["plt_file_reference"]["requiredFields"])

    def test_business_provider_actions_are_closed_and_fail_closed(self) -> None:
        provider = self.contract["interfaces"]["businessObjectPolicyProvider"]
        self.assertEqual(
            {
                "UPLOAD", "REFERENCE", "READ", "DOWNLOAD", "PREVIEW",
                "REPLACE", "DETACH", "ARCHIVE", "INVALIDATE",
            },
            set(provider["actionCodes"]),
        )
        self.assertIn("zero, multiple", provider["selectionPolicy"])
        self.assertIn("never writes or returns PRE-01 approval status", provider["solFirstConsumer"])

    def test_storage_api_exception_remains_explicitly_unapproved(self) -> None:
        candidate = self.contract["interfaces"]["infraFileApiExceptionCandidate"]
        self.assertEqual("PENDING_FEATURE_READY_DECISION", candidate["approvalStatus"])
        self.assertEqual("IN_REVIEW", self.contract["status"])
        self.assertIn("PENDING_FEATURE_READY_DECISION", self.feature_spec)
        self.assertEqual(
            "PENDING_INDEPENDENT_REVIEW_WITH_INFRA_FILE_API_EXCEPTION_DECISION",
            self.contract["featureReadyDecision"],
        )


if __name__ == "__main__":
    unittest.main()
