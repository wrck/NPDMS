from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).parents[1] / "p3e09_approval_policy.py"
SPEC = importlib.util.spec_from_file_location("p3e09_approval_policy", MODULE_PATH)
POLICY = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(POLICY)


class P3E09ApprovalPolicyTest(unittest.TestCase):
    CANDIDATE_COMMIT = "a" * 40
    REVIEW_DATE = "2026-08-14"
    REVIEW_RANGE = "b" * 40 + ".." + CANDIDATE_COMMIT

    def write_review(self, **overrides: str) -> None:
        fields = {
            "status": "APPROVED",
            "conclusion": "GO",
            "candidateCommit": self.CANDIDATE_COMMIT,
            "ddlSha256": "DDL",
            "itemsSha256": self.register["itemsSha256"],
            "itemCount": "2",
            "deferCount": "0",
            "testResult": "PASS",
            "reviewDate": self.REVIEW_DATE,
            "reviewRange": self.REVIEW_RANGE,
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
            "candidateCommit": self.CANDIDATE_COMMIT,
            "approvedDdlSha256": None,
            "decisionOwner": "requirement-owner",
            "reviewOwner": "independent-reviewer",
            "evidenceRefs": [self.review_ref],
            "isolatedMysqlExecution": {"status": "PASS"},
            "reviewDate": self.REVIEW_DATE,
            "reviewRange": self.REVIEW_RANGE,
        }
        self.write_review()
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.range_check = patch.object(POLICY, "review_range_errors", return_value=[])
        self.range_check.start()

    def tearDown(self) -> None:
        self.candidate_check.stop()
        self.range_check.stop()
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

    def test_model_baseline_rejects_missing_migration_approval_hash_fact(self) -> None:
        self.evidence.pop("approvedDdlSha256")
        self.assertTrue(any("must be explicitly present" in error for error in POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)))

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

    def test_model_baseline_rejects_stale_candidate_or_model_facts(self) -> None:
        for field, value in {
            "candidateCommit": "b" * 40,
            "ddlSha256": "OLD_DDL",
            "itemsSha256": "OLD_ITEMS",
            "itemCount": "1882",
            "deferCount": "1",
            "testResult": "FAIL",
            "reviewDate": "2026-08-15",
            "reviewRange": "c" * 40 + ".." + self.CANDIDATE_COMMIT,
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
            review.write("> candidateCommit: `" + "b" * 40 + "`\n")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("must appear exactly once: candidateCommit" in error for error in errors))

    def test_model_baseline_rejects_non_pass_review_test_result(self) -> None:
        self.write_review(testResult="FAIL")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("testResult must be PASS" in error for error in errors))

    def test_model_baseline_rejects_invalid_review_date(self) -> None:
        self.write_review(reviewDate="2026/08/14")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("reviewDate must be ISO" in error for error in errors))

    def test_model_baseline_rejects_mismatched_review_range(self) -> None:
        self.write_review(reviewRange="c" * 40 + ".." + self.CANDIDATE_COMMIT)
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("independent review reviewRange mismatch" in error for error in errors))

    def test_model_baseline_rejects_non_pass_isolated_mysql_result(self) -> None:
        self.evidence["isolatedMysqlExecution"] = {"status": "FAIL"}
        self.write_review(testResult="FAIL")
        errors = POLICY.validate_model_baseline(self.register, self.evidence, root=self.root)
        self.assertTrue(any("isolatedMysqlExecution.status must be PASS" in error for error in errors))
        self.assertTrue(any("testResult must be PASS" in error for error in errors))

    def test_candidate_commit_rejects_nonexistent_commit(self) -> None:
        self.candidate_check.stop()
        result = type("Completed", (), {"returncode": 1, "stdout": b""})()
        with patch.object(POLICY.subprocess, "run", return_value=result):
            errors = POLICY.candidate_commit_errors(
                self.root, self.CANDIDATE_COMMIT, "DDL", "ITEMS",
            )
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.assertTrue(any("does not resolve" in error for error in errors))

    def test_candidate_commit_requires_full_hex_sha(self) -> None:
        self.candidate_check.stop()
        errors = POLICY.candidate_commit_errors(self.root, "cfd60d6", "DDL", "ITEMS")
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.assertTrue(any("full 40-character" in error for error in errors))

    def test_candidate_commit_rejects_non_ancestor_commit(self) -> None:
        self.candidate_check.stop()
        results = iter((
            type("Completed", (), {"returncode": 0, "stdout": b""})(),
            type("Completed", (), {"returncode": 1, "stdout": b""})(),
        ))
        with patch.object(POLICY.subprocess, "run", side_effect=lambda *_args, **_kwargs: next(results)):
            errors = POLICY.candidate_commit_errors(
                self.root, self.CANDIDATE_COMMIT, "DDL", "ITEMS",
            )
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.assertTrue(any("not reachable" in error for error in errors))

    def test_candidate_commit_rejects_mismatched_model_artifacts(self) -> None:
        self.candidate_check.stop()
        candidate_items = [{"itemId": "COLUMN:a:id", "decision": "DEFER"}]
        candidate_register = json.dumps({
            "currentDdlSha256": "DDL", "itemsSha256": "ITEMS", "items": candidate_items,
        }).encode("utf-8")
        results = iter((
            type("Completed", (), {"returncode": 0, "stdout": b""})(),
            type("Completed", (), {"returncode": 0, "stdout": b""})(),
            type("Completed", (), {"returncode": 0, "stdout": b"OTHER_DDL"})(),
            type("Completed", (), {"returncode": 0, "stdout": b"CURRENT_DDL"})(),
            type("Completed", (), {"returncode": 0, "stdout": candidate_register})(),
        ))
        with patch.object(POLICY.subprocess, "run", side_effect=lambda *_args, **_kwargs: next(results)):
            errors = POLICY.candidate_commit_errors(
                self.root,
                self.CANDIDATE_COMMIT,
                POLICY.sha256_bytes(b"DDL"),
                "ITEMS",
            )
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.assertTrue(any("current DDL artifact differs" in error for error in errors))
        self.assertTrue(any("does not match canonical items" in error for error in errors))

    def test_candidate_commit_rejects_changed_items_with_stale_declared_hash(self) -> None:
        self.candidate_check.stop()
        candidate_items = [{"itemId": "COLUMN:a:id", "decision": "DEFER"}]
        candidate_register = json.dumps({
            "currentDdlSha256": "DDL", "itemsSha256": "ITEMS", "items": candidate_items,
        }).encode("utf-8")
        results = iter((
            type("Completed", (), {"returncode": 0, "stdout": b""})(),
            type("Completed", (), {"returncode": 0, "stdout": b""})(),
            type("Completed", (), {"returncode": 0, "stdout": b"DDL"})(),
            type("Completed", (), {"returncode": 0, "stdout": b"DDL"})(),
            type("Completed", (), {"returncode": 0, "stdout": candidate_register})(),
        ))
        with patch.object(POLICY.subprocess, "run", side_effect=lambda *_args, **_kwargs: next(results)):
            errors = POLICY.candidate_commit_errors(self.root, self.CANDIDATE_COMMIT, "DDL", "ITEMS")
        self.candidate_check = patch.object(POLICY, "candidate_commit_errors", return_value=[])
        self.candidate_check.start()
        self.assertTrue(any("does not match canonical items" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
