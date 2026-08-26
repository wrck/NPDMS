from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-SOL-001-project-duration-baseline-and-change-approval.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-SOL-001-physical-contract.json"


class Fsol001FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))

    def test_bpm_workflow_is_the_only_approval_result_path(self) -> None:
        workflow = self.contract["interfaces"]["bpmWorkflow"]
        self.assertEqual({"projectId": "Long"}, workflow["standardVariables"])
        self.assertIn("BPM_APPROVAL", workflow["governanceIntegration"])
        self.assertNotIn("approve|reject|withdraw", self.contract["interfaces"]["actions"]["route"])
        self.assertIn("BpmProcessInstanceStatusEvent", self.feature_spec)

    def test_project_scope_uses_only_the_approved_actions(self) -> None:
        permissions = self.contract["permissions"]
        actions = {entry["projectScopeAction"] for entry in permissions.values() if isinstance(entry, dict)}
        self.assertEqual({"PROJECT_VIEW", "PROJECT_MANAGE"}, actions)
        fact = self.contract["interfaces"]["projectParticipantFact"]
        for field in ("lifecycleStatus", "currentStage", "projectVersion"):
            self.assertIn(field, fact["response"])
        self.assertIn("lockAndRevalidate", " ".join(fact["contract"]))

    def test_construction_plan_root_is_unique_without_deleted(self) -> None:
        root = self.contract["tables"]["sol_construction_plan"]
        self.assertEqual(["tenant_id", "project_id"], root["stableKey"])
        self.assertNotIn("deleted", root["stableKey"])
        self.assertIn("no delete or rebuild", root["deletionPolicy"])

    def test_feature_ready_decision_is_locked(self) -> None:
        self.assertEqual("BASELINE", self.contract["status"])
        self.assertEqual(
            "GO_NPDMS_FSOL001_FEATURE_READY_20260826_01_R1",
            self.contract["featureReadyDecision"],
        )
        self.assertIn("NPDMS-FSOL001-FEATURE-READY-20260826-01-R1", self.feature_spec)


if __name__ == "__main__":
    unittest.main()
