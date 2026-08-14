from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "p3e09_approval_policy.py"
SPEC = importlib.util.spec_from_file_location("p3e09_approval_policy", MODULE_PATH)
POLICY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(POLICY)


class P3E09ApprovalPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.register = {
            "currentDdlSha256": "DDL",
            "itemsSha256": "ITEMS",
            "items": [
                {"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT"},
                {"itemId": "TABLE:a", "decision": "AMEND_CURRENT"},
            ],
        }
        self.evidence = {
            "currentDdlSha256": "DDL",
            "itemsSha256": "ITEMS",
            "itemIdsSha256": POLICY.item_ids_sha256(self.register["items"]),
            "deferredItemCount": 0,
            "mysql84DdlSha256": "DDL",
            "independentReviewResult": "GO",
            "approvedDdlSha256": None,
        }

    def test_complete_model_evidence_allows_sds_without_migration_approval(self) -> None:
        self.assertEqual([], POLICY.validate_model_baseline(self.register, self.evidence))

    def test_model_baseline_rejects_drifted_artifact_hash(self) -> None:
        self.evidence["mysql84DdlSha256"] = "OTHER"
        self.assertTrue(any("MySQL 8.4" in error for error in POLICY.validate_model_baseline(self.register, self.evidence)))

    def test_model_baseline_rejects_deferred_items(self) -> None:
        self.register["items"][1]["decision"] = "DEFER"
        self.evidence["deferredItemCount"] = 1
        self.assertTrue(any("DEFER" in error for error in POLICY.validate_model_baseline(self.register, self.evidence)))

    def test_model_baseline_rejects_migration_approval_hash(self) -> None:
        self.evidence["approvedDdlSha256"] = "DDL"
        self.assertTrue(any("must remain empty" in error for error in POLICY.validate_model_baseline(self.register, self.evidence)))


if __name__ == "__main__":
    unittest.main()
