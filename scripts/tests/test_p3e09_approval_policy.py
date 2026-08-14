from __future__ import annotations

import importlib.util
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
        self.review_ref = "docs/engineering/gates/phase-3/independent-review.md"
        review_path = self.root / self.review_ref
        review_path.parent.mkdir(parents=True)
        review_path.write_text("独立复审结论：GO", encoding="utf-8")
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
            "targetCatalogDdlSha256": "DDL",
            "mappingDdlSha256": "DDL",
            "validationDdlSha256": "DDL",
            "manifestDdlSha256": "DDL",
            "itemsSha256": "ITEMS",
            "itemIdsSha256": POLICY.item_ids_sha256(self.register["items"]),
            "deferredItemCount": 0,
            "mysql84DdlSha256": "DDL",
            "independentReviewResult": "GO",
            "independentReviewRef": self.review_ref,
            "approvedDdlSha256": None,
            "decisionOwner": "requirement-owner",
            "reviewOwner": "independent-reviewer",
            "evidenceRefs": [self.review_ref],
        }

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_complete_model_evidence_allows_sds_without_migration_approval(self) -> None:
        self.assertEqual([], POLICY.validate_model_baseline(self.register, self.evidence, root=self.root))

    def test_model_baseline_rejects_each_formal_artifact_hash_drift(self) -> None:
        for field in POLICY.DDL_ARTIFACT_HASH_FIELDS - {"currentDdlSha256"}:
            with self.subTest(field=field):
                self.evidence[field] = "OTHER"
                errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
                self.assertTrue(any(field in error for error in errors))
                self.evidence[field] = "DDL"

    def test_model_baseline_rejects_deferred_items(self) -> None:
        self.register["items"][1]["decision"] = "DEFER"
        self.evidence["deferredItemCount"] = 1
        self.assertTrue(any("DEFER" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_migration_approval_hash(self) -> None:
        self.evidence["approvedDdlSha256"] = "DDL"
        self.assertTrue(any("must remain empty" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_self_review(self) -> None:
        self.evidence["reviewOwner"] = self.evidence["decisionOwner"]
        self.assertTrue(any("must differ" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_non_go_review(self) -> None:
        self.evidence["independentReviewResult"] = "NO_GO"
        self.assertTrue(any("independentReviewResult=GO" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_missing_review_reference(self) -> None:
        self.evidence.pop("independentReviewRef")
        self.assertTrue(any("independent review reference" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_invalid_review_reference(self) -> None:
        self.evidence["independentReviewRef"] = "docs/reference/review.md"
        self.assertTrue(any("formal gate or ADR" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))


if __name__ == "__main__":
    unittest.main()
