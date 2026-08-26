from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-SOL-002-site-survey-assignment-and-readiness.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-SOL-002-physical-contract.json"


class Fsol002FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))

    def test_six_sol_tables_close_the_pre02_truth(self) -> None:
        self.assertEqual(
            {
                "sol_preparation",
                "sol_preparation_item",
                "sol_dynamic_form_instance",
                "sol_preparation_source_reference",
                "sol_preparation_item_waiver",
                "sol_preparation_readiness_snapshot",
            },
            set(self.contract["tables"]),
        )
        self.assertEqual("SOL", self.contract["owner"]["context"])

    def test_readiness_revalidation_freezes_the_same_current_fact(self) -> None:
        api = self.contract["publicReadinessApi"]
        self.assertIn("lockAndRevalidate", " ".join(api["contracts"]))
        for field in (
            "expectedBusinessVersion",
            "expectedPreparationVersion",
            "expectedReadinessVersion",
            "expectedSnapshotId",
            "expectedProjectScopeVersion",
        ):
            self.assertIn(field, api["revalidationQuery"])
        self.assertIn("synchronous lock-and-revalidate", api["readyPolicy"])

    def test_scope_actions_and_events_stay_within_locked_sds(self) -> None:
        permissions = self.contract["permissions"]
        actions = {entry["projectScopeAction"] for entry in permissions.values() if isinstance(entry, dict)}
        self.assertEqual({"PROJECT_VIEW", "PROJECT_MANAGE"}, actions)
        self.assertEqual([], self.contract["platformFacts"]["outboxEvents"])
        self.assertIn("no cross-context event", self.contract["platformFacts"]["reason"])

    def test_oa_and_legacy_are_not_promoted_to_owned_truth(self) -> None:
        self.assertIn("OA-required items are blocked", self.contract["dependencies"]["oaSource"])
        self.assertIn("no current truth", self.contract["legacy"]["policy"])
        self.assertIn("pms_eng_site_survey", self.feature_spec)

    def test_feature_ready_remains_pending_until_independent_review(self) -> None:
        self.assertEqual("DRAFT", self.contract["status"])
        self.assertEqual("PENDING_REVIEW", self.contract["featureReadyDecision"])
        self.assertIn("NPDMS-FSOL002-BOUNDARY-20260827-01", self.feature_spec)


if __name__ == "__main__":
    unittest.main()
