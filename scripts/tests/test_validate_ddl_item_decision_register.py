from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock


MODULE_PATH = Path(__file__).parents[1] / "validate_ddl_item_decision_register.py"
SPEC = importlib.util.spec_from_file_location("validate_ddl_item_decision_register", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(VALIDATOR)


class DdlItemDecisionRegisterValidatorTest(unittest.TestCase):
    def test_non_defer_requires_owner_and_evidence(self) -> None:
        item = {"itemId": "TABLE:t", "itemType": "TABLE", "table": "t", "comparisonStatus": "MATCH", "baselineValue": True, "currentValue": True, "decision": "ACCEPT_CURRENT", "decisionOwner": None, "reviewOwner": None, "evidenceRefs": []}
        self.assertFalse(VALIDATOR.nonempty(item["decisionOwner"]))
        self.assertFalse(VALIDATOR.nonempty(item["evidenceRefs"]))

    def test_canonical_hash_changes_when_decision_changes(self) -> None:
        item = {"itemId": "TABLE:t", "decision": "DEFER"}
        before = VALIDATOR.canonical_sha([item])
        item["decision"] = "ACCEPT_CURRENT"
        self.assertNotEqual(before, VALIDATOR.canonical_sha([item]))

    def test_generated_decision_rejects_reverting_confirmed_item_to_defer(self) -> None:
        expected = {"COLUMN:a:id": {"decision": "AMEND_CURRENT", "decisionOwner": "REQUIREMENT_OWNER", "reviewOwner": None, "evidenceRefs": ["ADR"]}}
        actual = {"COLUMN:a:id": {**expected["COLUMN:a:id"], "decision": "DEFER"}}
        self.assertIn(
            "COLUMN:a:id generated decision mismatch: decision",
            VALIDATOR.generated_decision_errors(actual, expected),
        )

    def test_generated_decision_rejects_missing_v17_evidence(self) -> None:
        expected = {"TABLE:cut_cutover_closure": {"decision": "AMEND_CURRENT", "decisionOwner": "REQUIREMENT_OWNER", "reviewOwner": None, "evidenceRefs": ["ADR-0027"]}}
        actual = {"TABLE:cut_cutover_closure": {**expected["TABLE:cut_cutover_closure"], "evidenceRefs": []}}
        self.assertIn(
            "TABLE:cut_cutover_closure generated decision mismatch: evidenceRefs",
            VALIDATOR.generated_decision_errors(actual, expected),
        )

    def test_generated_decision_allows_independent_review_overlay(self) -> None:
        expected = {"COLUMN:a:id": {"decision": "ACCEPT_CURRENT", "decisionOwner": "DATA_ARCHITECTURE_OWNER", "reviewOwner": None, "evidenceRefs": ["fact"]}}
        actual = {"COLUMN:a:id": {"decision": "ACCEPT_CURRENT", "decisionOwner": "DATA_ARCHITECTURE_OWNER", "reviewOwner": "REVIEWER", "evidenceRefs": ["fact", "review"]}}
        self.assertEqual([], VALIDATOR.generated_decision_errors(actual, expected))

    def test_missing_evidence_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors = VALIDATOR.evidence_reference_errors(
                Path(directory),
                [{"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT", "evidenceRefs": ["missing.md#decision"]}],
            )
        self.assertEqual(1, len(errors))
        self.assertIn("does not exist", errors[0])

    def test_final_approval_binds_current_ddl_items_and_existing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            register = {
                "currentDdlSha256": "CURRENT",
                "itemsSha256": "ITEMS",
                "items": [{"itemId": "COLUMN:a:id"}],
                "approval": {
                    "approvedDdlSha256": "WRONG",
                    "itemsSha256": "WRONG_ITEMS",
                    "decisionOwner": "DATA",
                    "reviewOwner": "REVIEW",
                    "signedAt": "2026-08-14T00:00:00+08:00",
                    "evidenceRefs": ["missing-review.md"],
                },
            }
            errors = VALIDATOR.final_approval_errors(root, register, 1)
        self.assertTrue(any("equal currentDdlSha256" in error for error in errors))
        self.assertTrue(any("current itemsSha256" in error for error in errors))
        self.assertTrue(any("does not exist" in error for error in errors))

    def test_catalog_baseline_is_loaded_from_historical_hash(self) -> None:
        generator = Mock()
        historical_tables = {"pms_project": object()}
        normalized_tables = {"proj_project": object()}
        generator.locate_catalog_baseline.return_value = ("abc123", historical_tables)
        generator.normalize_baseline_names.return_value = normalized_tables
        report = {
            "inputs": {
                "baselineSource": "TARGET_FIELD_CATALOG",
                "baselineCatalogPath": "catalog.jsonl",
                "baselineDdlSha256": "OLD_HASH",
            }
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            contract = root / "docs" / "traceability" / "database-naming-contract.json"
            contract.parent.mkdir(parents=True)
            contract.write_text("{}", encoding="utf-8")
            result = VALIDATOR.resolve_baseline(generator, root, report)
        self.assertIs(result, normalized_tables)
        generator.locate_catalog_baseline.assert_called_once_with(root, root / "catalog.jsonl", "OLD_HASH")
        generator.normalize_baseline_names.assert_called_once_with(historical_tables, {})


if __name__ == "__main__":
    unittest.main()
