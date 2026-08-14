from __future__ import annotations

import importlib.util
import json
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "apply_p3e09_requirement_confirmation.py"
SPEC = importlib.util.spec_from_file_location("apply_p3e09_requirement_confirmation", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class P3E09RequirementConfirmationTest(unittest.TestCase):
    def load_inputs(self):
        contract = json.loads((ROOT / MODULE.CONTRACT).read_text(encoding="utf-8"))
        packet = json.loads((ROOT / MODULE.PACKET).read_text(encoding="utf-8"))
        return contract, packet

    def test_applies_all_nine_groups_without_reviewer_approval(self) -> None:
        contract, packet = self.load_inputs()
        result = MODULE.apply_confirmation(contract, packet)
        confirmation = result["p3e09RequirementOwnerConfirmation"]
        self.assertEqual("ACCEPTED", confirmation["status"])
        self.assertEqual(MODULE.EXPECTED_GROUPS, set(confirmation["groups"]))
        self.assertEqual(695, confirmation["confirmedUniqueItemCount"])
        self.assertEqual("REVIEW_PENDING", confirmation["reviewStatus"])
        self.assertIsNone(confirmation["approvedDdlSha256"])
        self.assertEqual("ACCEPTED", result["q07TechnicalConstraintPolicy"]["status"])
        self.assertEqual("ACCEPTED", result["q08OrdinaryIndexPolicy"]["status"])
        self.assertEqual("ACCEPTED", result["v17Delta"]["status"])
        self.assertEqual(257, len(result["v17Delta"]["acceptedDdlItems"]))

    def test_rejects_missing_group_and_wrong_hash(self) -> None:
        contract, packet = self.load_inputs()
        packet["groups"] = packet["groups"][:-1]
        with self.assertRaisesRegex(ValueError, "exact nine"):
            MODULE.apply_confirmation(contract, packet)

        contract, packet = self.load_inputs()
        packet["currentDdlSha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "current contract DDL"):
            MODULE.apply_confirmation(contract, packet)


if __name__ == "__main__":
    unittest.main()
