from __future__ import annotations

import copy
import json
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_implementation_baseline_inventory import (
    find_retired_cutover_runtime_surfaces,
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

    def _write_runtime_fixture(self, repository: Path, raw_path: str, content: str) -> None:
        path = repository / raw_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_retired_cutover_guard_scans_arbitrary_backend_runtime_paths(self) -> None:
        fixtures = {
            "class-execution": ("class CutExecutionShadow {}", "type"),
            "class-observation": ("class CutObservationShadow {}", "type"),
            "route-execution": ('@RequestMapping("/pms/cut-execution")', "route"),
            "route-observation": ('@RequestMapping("/pms/cut-observation")', "route"),
            "permission-execution": ("pms:cut-execution:create", "permission"),
            "permission-observation": ("pms:cut-observation:update", "permission"),
            "comment-cannot-hide-type": ("// legacy CutExecution must not return", "type"),
        }
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for index, (name, (content, expected_label)) in enumerate(fixtures.items()):
                with self.subTest(name=name):
                    path = (
                        "pms-module-cutover/src/main/java/example/hidden/"
                        f"Arbitrary{index}.java"
                    )
                    self._write_runtime_fixture(repository, path, content)
                    errors = find_retired_cutover_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_cutover_guard_rejects_all_six_cut_task_bypass_actions(self) -> None:
        actions = {
            "/start-execution": "startExecution",
            "/complete-execution": "completeExecution",
            "/start-observation": "startObservation",
            "/complete-observation": "completeObservation",
            "/rollback": "rollback",
            "/terminate": "terminate",
        }
        for index, (route, method) in enumerate(actions.items()):
            with self.subTest(route=route):
                with tempfile.TemporaryDirectory() as directory:
                    repository = Path(directory)
                    route_path = (
                        "pms-module-cutover/src/main/java/example/hidden/"
                        f"ArbitraryTaskRoute{index}.java"
                    )
                    route_content = (
                        'class CutTaskShadowController {\n'
                        '  static final String BASE = "/pms/cut-task";\n'
                        f'  @PutMapping("{route}") void handle() {{}}\n'
                        '}\n'
                    )
                    method_path = (
                        "pms-module-cutover/src/main/java/example/hidden/"
                        f"ArbitraryTaskMethod{index}.java"
                    )
                    method_content = f"class CutTaskShadowService {{ void {method}() {{}} }}"
                    self._write_runtime_fixture(repository, route_path, route_content)
                    self._write_runtime_fixture(repository, method_path, method_content)
                    errors = find_retired_cutover_runtime_surfaces(repository)
                    self.assertTrue(
                        any(route_path in error and "bypass" in error for error in errors),
                        errors,
                    )
                    self.assertTrue(
                        any(method_path in error and "bypass" in error for error in errors),
                        errors,
                    )

    def test_retired_cutover_guard_scans_frontend_runtime_source(self) -> None:
        fixtures = {
            "api-route": ("request.get({ url: '/pms/cut-execution/page' })", "route"),
            "view-permission": ("v-hasPermi=['pms:cut-observation:update']", "permission"),
            "project-action": ("const action = terminateCutTask", "bypass"),
            "comment-cannot-hide-action": ("// do not restore terminateCutTask", "bypass"),
        }
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for index, (name, (content, expected_label)) in enumerate(fixtures.items()):
                with self.subTest(name=name):
                    path = (
                        "yudao-ui/yudao-ui-admin-vue3/src/feature/hidden/"
                        f"arbitrary-{index}.ts"
                    )
                    self._write_runtime_fixture(repository, path, content)
                    errors = find_retired_cutover_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_cutover_guard_excludes_non_runtime_evidence(self) -> None:
        excluded_paths = (
            "sql/migrations/V999__historical.sql",
            "scripts/tests/fixtures/historical.py",
            "docs/evidence/cutover-history.md",
            "tasks/implementation-baseline-inventory.json",
            "docs/baseline/prd-v1.7.md",
        )
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path in excluded_paths:
                self._write_runtime_fixture(
                    repository,
                    path,
                    "CutExecution /pms/cut-observation pms:cut-execution:create terminateCutTask",
                )
            self.assertEqual([], find_retired_cutover_runtime_surfaces(repository))

    def test_retired_cutover_guard_allows_cut_plan_terminate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write_runtime_fixture(
                repository,
                "pms-module-cutover/src/main/java/example/CutPlanController.java",
                'class CutPlanController { @PutMapping("/terminate") void terminate() {} }',
            )
            self.assertEqual([], find_retired_cutover_runtime_surfaces(repository))

    def test_inventory_validation_invokes_runtime_guard_independently(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self._write_runtime_fixture(
                repository,
                "pms-module-cutover/src/main/java/example/NewSurface.java",
                "class CutObservationReplacement {}",
            )
            inventory = {
                "schemaVersion": 1,
                "status": "BASELINE_SYNCED_IMPLEMENTATION_RECONCILIATION_REQUIRED",
                "items": [],
                "unexpected": True,
            }

            errors = validate_inventory(repository, inventory)

            self.assertTrue(any("NewSurface.java" in error for error in errors), errors)

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
