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

    def test_cut_execution_and_observation_runtime_is_retired(self) -> None:
        items = self._items()

        expected = "RUNTIME_RETIRED_DATA_PENDING_EVIDENCE"
        self.assertEqual(expected, items["CutExecution"]["classification"])
        self.assertEqual(expected, items["CutObservation"]["classification"])
        self.assertIn("CUT-01", items["CutExecution"]["requirementRefs"])

    def test_retired_cutover_runtime_has_no_api_view_or_task_action_bypass(self) -> None:
        forbidden_paths = (
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/execution",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/observation",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/execution",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/dataobject/observation",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/execution",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/dal/mysql/observation",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/execution",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/observation",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/CutExecutionStatusRules.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/CutObservationStatusRules.java",
            "yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cut-execution",
            "yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cut-observation",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cut-execution",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cut-observation",
        )
        for raw_path in forbidden_paths:
            path = self.repository / raw_path
            has_runtime_content = path.is_file() or (
                path.is_dir() and any(child.is_file() for child in path.rglob("*"))
            )
            self.assertFalse(has_runtime_content, raw_path)

        shared_runtime_files = (
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/enums/ErrorCodeConstants.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/enums/CutStatusEnum.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/controller/admin/task/CutTaskController.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutTaskService.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/service/task/CutTaskServiceImpl.java",
            "pms-module-cutover/src/main/java/cn/iocoder/yudao/module/pms/cutover/domain/CutTaskStatusRules.java",
            "yudao-ui/yudao-ui-admin-vue3/src/api/pms/cutover/cut-task/index.ts",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/cutover/cut-task/index.vue",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-detail/index.vue",
        )
        forbidden_tokens = (
            "CutExecution",
            "CutObservation",
            "cut-execution",
            "cut-observation",
            "CUT_EXECUTION_",
            "CUT_OBSERVATION_",
            "startExecution",
            "completeExecution",
            "startObservation",
            "completeObservation",
            "rollbackCutTask",
            "terminateCutTask",
            'Action.ROLLBACK',
            'Action.TERMINATE',
            'void rollback(',
            'void terminate(',
            '/start-execution',
            '/complete-execution',
            '/start-observation',
            '/complete-observation',
            '/rollback',
            '/terminate',
        )
        for raw_path in shared_runtime_files:
            content = (self.repository / raw_path).read_text(encoding="utf-8")
            for token in forbidden_tokens:
                self.assertNotIn(token, content, f"{raw_path}: {token}")

    def test_retirement_migration_revokes_menu_permissions_without_touching_business_tables(self) -> None:
        migration = self.repository / "sql/migrations/V50__retire_excluded_cutover_runtime_surfaces.sql"
        self.assertTrue(migration.is_file())
        content = migration.read_text(encoding="utf-8")
        for menu_id in (19022, 19023, 19073, 19074, 19075, 19076, 19077, 19078):
            self.assertIn(str(menu_id), content)
        self.assertIn("UPDATE `system_role_menu`", content)
        self.assertIn("UPDATE `system_menu`", content)
        self.assertNotIn("DROP TABLE", content.upper())
        self.assertNotIn("pms_cut_execution", content)
        self.assertNotIn("pms_cut_observation", content)

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
