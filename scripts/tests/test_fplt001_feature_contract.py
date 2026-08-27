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
        storage = self.contract["interfaces"]["infraFileStorageReceiptApi"]
        self.assertEqual(
            "APPROVED_NPDMS_FPLT001_INFRA_EXCEPTION_20260826_01_R1",
            storage["approvalStatus"],
        )
        self.assertIn("URL_AS_FILE_IDENTITY", storage["forbidden"])
        self.assertIn("PLT_DIRECT_INFRA_MAPPER", storage["forbidden"])
        self.assertIn("retain all existing FileApi and FileClient methods", storage["compatibility"])
        self.assertIn("search all configs", storage["methods"]["store"]["lookup"])
        self.assertIn("50MB+1 bounded read", storage["uploadBoundary"])

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

    def test_revalidation_freezes_file_and_scope_versions(self) -> None:
        api = self.contract["interfaces"]["fileArtifactApi"]
        self.assertIn("referenceKey", api["inspectInput"])
        self.assertIn("referenceKey", api["lockAndRevalidateInput"])
        self.assertIn("referenceKey", api["response"])
        self.assertIn("expectedFileFactVersion.artifactVersion", api["lockAndRevalidateInput"])
        self.assertIn("expectedFileFactVersion.referenceVersion", api["lockAndRevalidateInput"])
        self.assertIn("expectedFileFactVersion.availabilityVersion", api["lockAndRevalidateInput"])
        self.assertIn("expectedScopeVersion", api["lockAndRevalidateInput"])
        self.assertEqual(
            [
                "BUSINESS_PROVIDER_LOCK_AND_REVALIDATE_EXPECTED_SCOPE_VERSION",
                "PLT_FILE_ARTIFACT_FOR_UPDATE",
                "PLT_EXACT_FILE_VERSION_FOR_UPDATE",
                "PLT_EXACT_FILE_REFERENCE_FOR_UPDATE",
            ],
            api["lockOrder"],
        )
        self.assertIn("VERSION_CONFLICT", api["conflictPolicy"])
        self.assertIn("select exactly one stable reference slot", api["referenceSelection"])
        self.assertIn("empty referenceKey", api["unknownPolicy"])

    def test_locked_file_events_use_transactional_outbox(self) -> None:
        facts = self.contract["platformFacts"]
        self.assertEqual(
            {
                "FileVersionCommitted",
                "FileReferenceAttached",
                "FileReferenceDetached",
                "FileArchived",
            },
            set(facts["outboxEvents"]),
        )
        self.assertIn("same PLT transaction", facts["outboxPolicy"])
        self.assertIn("stable eventId", facts["outboxPolicy"])

    def test_optional_security_scan_is_truthful_and_fail_closed_when_enabled(self) -> None:
        version = self.contract["tables"]["plt_file_version"]
        self.assertEqual({"PASSED", "SKIPPED"}, set(version["scanStatusCodes"]))
        session = self.contract["tables"]["plt_file_upload_session"]
        self.assertIn("PASSED when security scanning is enabled", session["successPolicy"])
        self.assertIn("otherwise SKIPPED", session["successPolicy"])

        provider = self.contract["interfaces"]["securityScanProvider"]
        self.assertEqual({"PASSED", "REJECTED", "ERROR"}, set(provider["resultCodes"]))
        self.assertIn("invokes no provider", provider["disabledPolicy"])
        self.assertIn("null provider code/version", provider["disabledPolicy"])
        self.assertIn("never degrades to SKIPPED", provider["failurePolicy"])
        self.assertIn("CHG-PRD-2026-08-27-004", self.feature_spec)

    def test_feature_ready_go_is_recorded(self) -> None:
        self.assertEqual("BASELINE", self.contract["status"])
        self.assertIn("NPDMS-FPLT001-INFRA-EXCEPTION-20260826-01-R1", self.feature_spec)
        self.assertEqual(
            "GO_NPDMS_FPLT001_FEATURE_READY_20260826_01_R2",
            self.contract["featureReadyDecision"],
        )
        self.assertIn("READY / GO NPDMS-FPLT001-FEATURE-READY-20260826-01-R2", self.feature_spec)


if __name__ == "__main__":
    unittest.main()
