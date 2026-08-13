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
