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
    def write_review(self, **overrides: str) -> None:
        fields = {
            "status": "APPROVED",
            "conclusion": "GO",
            "ddlSha256": "DDL",
            "itemsSha256": self.register["itemsSha256"],
            "itemCount": "2",
            "deferCount": "0",
            "testResult": "PASS",
            **overrides,
        }
        content = "\n".join(f"> {name}: `{value}`" for name, value in fields.items()) + "\n"
        (self.root / self.review_ref).write_text(content, encoding="utf-8")

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.review_ref = "docs/engineering/gates/phase-3/independent-review.md"
        review_path = self.root / self.review_ref
        review_path.parent.mkdir(parents=True)
        review_path.write_text("", encoding="utf-8")
        self.register = {
            "currentDdlSha256": "DDL",
            "items": [
                {"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT"},
                {"itemId": "TABLE:a", "decision": "AMEND_CURRENT"},
            ],
        }
        self.register["itemsSha256"] = POLICY.canonical_items_sha256(self.register["items"])
        self.evidence = {
            "currentDdlSha256": "DDL",
            "targetCatalogDdlSha256": "DDL",
            "mappingDdlSha256": "DDL",
            "validationDdlSha256": "DDL",
            "manifestDdlSha256": "DDL",
            "itemsSha256": self.register["itemsSha256"],
            "itemIdsSha256": POLICY.item_ids_sha256(self.register["items"]),
            "deferredItemCount": 0,
            "mysql84DdlSha256": "DDL",
            "independentReviewResult": "GO",
            "independentReviewRef": self.review_ref,
            "decisionOwner": "requirement-owner",
            "reviewOwner": "independent-reviewer",
            "evidenceRefs": [self.review_ref],
            "isolatedMysqlExecution": {"status": "PASS"},
        }
        self.write_review()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_complete_model_evidence_allows_sds_without_migration_approval_field(self) -> None:
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

    def test_model_baseline_rejects_legacy_migration_approval_field(self) -> None:
        for value in (None, "", "DDL"):
            with self.subTest(value=value):
                self.evidence["approvedDdlSha256"] = value
                errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
                self.assertTrue(any("legacy migration approval field is not allowed" in error for error in errors))
                self.evidence.pop("approvedDdlSha256")

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

    def test_model_baseline_rejects_review_reference_resolving_outside_formal_directory(self) -> None:
        escaped_ref = "docs/decisions/../../AGENTS.md"
        (self.root / "AGENTS.md").write_text("独立复审结论：GO\n", encoding="utf-8")
        self.evidence["independentReviewRef"] = escaped_ref
        self.evidence["evidenceRefs"] = [escaped_ref]
        self.assertTrue(any("must resolve inside a formal gate or ADR" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_accepts_explicit_formal_go_conclusion(self) -> None:
        self.write_review()
        self.assertEqual([], POLICY.validate_model_baseline(self.register, self.evidence, root=self.root))

    def test_model_baseline_accepts_explicit_go_conclusion(self) -> None:
        self.write_review()
        self.assertEqual([], POLICY.validate_model_baseline(self.register, self.evidence, root=self.root))

    def test_model_baseline_rejects_formal_no_go_conclusion(self) -> None:
        self.write_review(conclusion="NO_GO")
        self.assertTrue(any("independent GO conclusion" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_ambiguous_go_text(self) -> None:
        (self.root / self.review_ref).write_text("独立复审已完成，后续事项为 GO。\n", encoding="utf-8")
        self.assertTrue(any("independent GO conclusion" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_pending_or_non_go_review_record(self) -> None:
        self.write_review(status="IN_REVIEW", conclusion="PENDING_FRESH_REVIEW")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("independent review status mismatch" in error for error in errors))
        self.assertTrue(any("independent review conclusion mismatch" in error for error in errors))

    def test_model_baseline_rejects_stale_model_facts(self) -> None:
        for field, value in {
            "ddlSha256": "OLD_DDL",
            "itemsSha256": "OLD_ITEMS",
            "itemCount": "1882",
            "deferCount": "1",
            "testResult": "FAIL",
        }.items():
            with self.subTest(field=field):
                self.write_review(**{field: value})
                errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
                self.assertTrue(any(f"independent review {field} mismatch" in error for error in errors))

    def test_model_baseline_rejects_go_record_with_contradictory_text(self) -> None:
        self.write_review()
        with (self.root / self.review_ref).open("a", encoding="utf-8") as review:
            review.write("本记录不是GO，仍待复审。\n")
        self.assertTrue(any("contradiction" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

    def test_model_baseline_rejects_duplicate_fixed_field_even_when_one_value_matches(self) -> None:
        self.write_review()
        with (self.root / self.review_ref).open("a", encoding="utf-8") as review:
            review.write("> ddlSha256: `OTHER`\n")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("must appear exactly once: ddlSha256" in error for error in errors))

    def test_model_baseline_rejects_non_pass_review_test_result(self) -> None:
        self.write_review(testResult="FAIL")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("testResult must be PASS" in error for error in errors))

    def test_model_baseline_rejects_non_pass_isolated_mysql_result(self) -> None:
        self.evidence["isolatedMysqlExecution"] = {"status": "FAIL"}
        self.write_review(testResult="FAIL")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("isolatedMysqlExecution.status must be PASS" in error for error in errors))
        self.assertTrue(any("testResult must be PASS" in error for error in errors))

if __name__ == "__main__":
    unittest.main()
