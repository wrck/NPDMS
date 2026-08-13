from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


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


if __name__ == "__main__":
    unittest.main()
