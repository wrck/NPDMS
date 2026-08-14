from __future__ import annotations

import copy
import json
import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_implementation_baseline_inventory import (
    load_inventory,
    validate_inventory,
)


class ImplementationBaselineInventoryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repository = Path(__file__).resolve().parents[2]
        cls.inventory_path = cls.repository / "tasks/implementation-baseline-inventory.json"
        cls.inventory = load_inventory(cls.inventory_path)

    def _items(self) -> dict[str, dict]:
        return {item["objectKey"]: item for item in self.inventory["items"]}

    def test_every_inventory_item_has_classification_requirement_and_code_path(self) -> None:
        errors = validate_inventory(self.repository, self.inventory)

        self.assertEqual([], errors)

    def test_cut_execution_and_observation_are_excluded_current(self) -> None:
        items = self._items()

        self.assertEqual("EXCLUDED_CURRENT", items["CutExecution"]["classification"])
        self.assertEqual("EXCLUDED_CURRENT", items["CutObservation"]["classification"])
        self.assertIn("CUT-01", items["CutExecution"]["requirementRefs"])

    def test_srv_report_is_valid_v2_postponed(self) -> None:
        item = self._items()["SrvReport"]

        self.assertEqual("VALID_V2_POSTPONED", item["classification"])
        self.assertEqual(["INS-05"], item["requirementRefs"])

    def test_srv_maintenance_is_semantic_rework(self) -> None:
        item = self._items()["SrvMaintenance"]

        self.assertEqual("SEMANTIC_REWORK", item["classification"])
        self.assertIn("EQP-02", item["requirementRefs"])

    def test_maintenance_transition_is_semantic_rework(self) -> None:
        item = self._items()["MaintenanceTransition"]

        self.assertEqual("SEMANTIC_REWORK", item["classification"])
        self.assertIn("ACC-06", item["requirementRefs"])

    def test_mes_work_order_is_not_removed_by_pms_keyword_rule(self) -> None:
        item = self._items()["MesProductionWorkOrder"]

        self.assertEqual("PLATFORM_UPSTREAM_UNCHANGED", item["classification"])
        self.assertEqual("KEEP_PLATFORM_CAPABILITY", item["requiredAction"])

    def test_feature_ready_is_blocked_while_reconciliation_items_exist(self) -> None:
        changed = copy.deepcopy(self.inventory)
        changed["status"] = "FEATURE_READY"

        errors = validate_inventory(self.repository, changed)

        self.assertTrue(any("FEATURE_READY" in error for error in errors))

    def test_inventory_file_is_utf8_json_with_terminal_newline(self) -> None:
        content = self.inventory_path.read_text(encoding="utf-8")

        self.assertTrue(content.endswith("\n"))
        self.assertEqual(self.inventory, json.loads(content))


if __name__ == "__main__":
    unittest.main()
