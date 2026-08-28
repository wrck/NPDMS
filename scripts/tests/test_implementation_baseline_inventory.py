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
    find_retired_maintenance_runtime_surfaces,
    find_retired_project_tree_write_runtime_surfaces,
    find_retired_project_write_runtime_surfaces,
    find_retired_template_runtime_surfaces,
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

    def test_customer_master_runtime_is_adapted_and_legacy_customer_is_read_only(self) -> None:
        items = self._items()

        self.assertEqual("ADAPTED", items["CustomerMasterCurrentRuntime"]["classification"])
        self.assertEqual("REUSED", items["LegacyCustomerHistoryReadOnly"]["classification"])
        self.assertIn("CUS-03", items["CustomerMasterCurrentRuntime"]["requirementRefs"])
        self.assertIn("pms-module-customer/", items["CustomerMasterCurrentRuntime"]["codePaths"])

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

    def test_srv_maintenance_and_maintenance_transition_runtime_is_retired(self) -> None:
        items = self._items()

        expected = "RUNTIME_RETIRED_DATA_PENDING_EVIDENCE"
        self.assertEqual(expected, items["SrvMaintenance"]["classification"])
        self.assertEqual(expected, items["MaintenanceTransition"]["classification"])
        self.assertIn("EQP-02", items["SrvMaintenance"]["requirementRefs"])
        self.assertIn("ACC-06", items["MaintenanceTransition"]["requirementRefs"])

    def test_retired_maintenance_guard_scans_arbitrary_runtime_paths(self) -> None:
        fixtures = {
            "type-srv": ("class SrvMaintenanceShadow {}", "maintenance type"),
            "type-transition": ("class MaintenanceTransitionShadow {}", "maintenance type"),
            "route-srv": ('@RequestMapping("/pms/srv-maintenance")', "maintenance route"),
            "route-transition": ("const path = '/pms/acceptance/maintenance-transition'", "maintenance route"),
            "permission-srv": ("pms:srv-maintenance:create", "maintenance permission"),
            "permission-transition": ("pms:acc-maintenance-transition:update", "maintenance permission"),
            "comment-cannot-hide-type": ("// legacy SrvMaintenance must not return", "maintenance type"),
        }
        backend_roots = (
            "pms-module-service/src/main/java/example/hidden",
            "pms-module-project/src/main/java/example/hidden",
        )
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for index, (name, (content, expected_label)) in enumerate(fixtures.items()):
                with self.subTest(name=name):
                    root = backend_roots[index % len(backend_roots)]
                    path = f"{root}/Arbitrary{index}.java"
                    self._write_runtime_fixture(repository, path, content)
                    errors = find_retired_maintenance_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_maintenance_guard_scans_frontend_runtime_source(self) -> None:
        fixtures = {
            "api-route": ("request.get({ url: '/pms/srv-maintenance/page' })", "maintenance route"),
            "view-import": ("import * as Api from '@/api/pms/project/maintenance-transition'", "maintenance route"),
            "view-permission": ("v-hasPermi=['pms:srv-maintenance:update']", "maintenance permission"),
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
                    errors = find_retired_maintenance_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_maintenance_guard_excludes_non_runtime_evidence(self) -> None:
        excluded_paths = (
            "sql/migrations/V999__historical.sql",
            "docs/design/09-database-design.md",
            "tasks/implementation-baseline-inventory.json",
        )
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path in excluded_paths:
                self._write_runtime_fixture(
                    repository,
                    path,
                    "SrvMaintenance /pms/srv-maintenance pms:acc-maintenance-transition:create",
                )
            self.assertEqual([], find_retired_maintenance_runtime_surfaces(repository))

    def test_retired_maintenance_runtime_has_no_api_view_or_controller_surface(self) -> None:
        forbidden_paths = (
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/controller/admin/srvmaintenance",
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/dataobject/srvmaintenance",
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/dal/mysql/srvmaintenance",
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/srvmaintenance",
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/maintenancetransition",
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/dataobject/maintenancetransition",
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/dal/mysql/maintenancetransition",
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/service/maintenancetransition",
            "yudao-ui/yudao-ui-admin-vue3/src/api/pms/service/srv-maintenance",
            "yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/maintenance-transition",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/service/srv-maintenance",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/maintenance-transition",
        )
        for raw_path in forbidden_paths:
            path = self.repository / raw_path
            has_runtime_content = path.is_file() or (
                path.is_dir() and any(child.is_file() for child in path.rglob("*"))
            )
            self.assertFalse(has_runtime_content, raw_path)

        shared_runtime_files = (
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/enums/ErrorCodeConstants.java",
            "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/package-info.java",
            "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/enums/ErrorCodeConstants.java",
            "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-detail/index.vue",
        )
        forbidden_tokens = (
            "SrvMaintenance",
            "srv-maintenance",
            "SRV_MAINTENANCE_",
            "MaintenanceTransition",
            "maintenance-transition",
            "ACC_MAINTENANCE_TRANSITION_",
        )
        for raw_path in shared_runtime_files:
            content = (self.repository / raw_path).read_text(encoding="utf-8")
            for token in forbidden_tokens:
                self.assertNotIn(token, content, f"{raw_path}: {token}")

    def test_maintenance_retirement_migration_revokes_menu_permissions_without_touching_business_tables(self) -> None:
        migration = self.repository / "sql/migrations/V51__retire_semantic_rework_maintenance_runtime_surfaces.sql"
        self.assertTrue(migration.is_file())
        content = migration.read_text(encoding="utf-8")
        for menu_id in (19028, 19091, 19092, 19093, 19099, 19125, 19126, 19127, 19128, 19129):
            self.assertIn(str(menu_id), content)
        self.assertIn("UPDATE `system_role_menu`", content)
        self.assertIn("UPDATE `system_menu`", content)
        self.assertNotIn("DROP TABLE", content.upper())
        self.assertNotIn("pms_srv_maintenance", content)
        self.assertNotIn("pms_acc_maintenance_transition", content)

    def test_retired_template_guard_scans_arbitrary_runtime_paths(self) -> None:
        fixtures = {
            "type-phase-template": ("class ProjectPhaseTemplateShadow {}", "template type"),
            "type-create-from-template": ("class ProjectCreateFromTemplateShadow {}", "template type"),
            "route-singular": ('@RequestMapping("/pms/project-template")', "template route"),
            "route-phase-template": ("const base = '/pms/project/project-phase-template'", "template route"),
            "route-instantiate": ('@PostMapping("/instantiate-from-template")', "template route"),
            "permission-phase-template": ("pms:phase-template:create", "template permission"),
            "table-project-template": ("String table = \"pms_project_template\";", "template table"),
            "table-phase-template": ("String table = \"pms_project_phase_template\";", "template table"),
        }
        backend_root = "pms-module-project/src/main/java/example/hidden"
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for index, (name, (content, expected_label)) in enumerate(fixtures.items()):
                with self.subTest(name=name):
                    path = f"{backend_root}/Arbitrary{index}.java"
                    self._write_runtime_fixture(repository, path, content)
                    errors = find_retired_template_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_template_guard_scans_frontend_runtime_source(self) -> None:
        fixtures = {
            "api-import": (
                "import * as Api from '@/api/pms/project/project-template'",
                "template route",
            ),
            "view-permission": (
                "v-hasPermi=['pms:phase-template:query']",
                "template permission",
            ),
            "comment-cannot-hide-type": (
                "// legacy ProjectPhaseTemplateMapper must not return",
                "template type",
            ),
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
                    errors = find_retired_template_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_template_guard_allows_rebuilt_plural_surface(self) -> None:
        rebuilt_sources = {
            "controller": (
                "pms-module-project/src/main/java/example/template/ProjectTemplateController.java",
                '@RequestMapping("/pms/project-templates") class ProjectTemplateController {}',
            ),
            "service": (
                "pms-module-project/src/main/java/example/template/ProjectTemplateServiceImpl.java",
                "class ProjectTemplateServiceImpl { String table = \"proj_project_template\"; }",
            ),
            "api": (
                "yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/project-templates/index.ts",
                "request.get({ url: '/pms/project-templates/page' })",
            ),
            "view": (
                "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-templates/index.vue",
                "v-hasPermi=['pms:project-template:publish']",
            ),
        }
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path, content in rebuilt_sources.values():
                self._write_runtime_fixture(repository, path, content)
            self.assertEqual([], find_retired_template_runtime_surfaces(repository))

    def test_retired_template_guard_excludes_non_runtime_evidence(self) -> None:
        excluded_paths = (
            "sql/migrations/V47__pms_project_template.sql",
            "sql/migrations/V999__future.sql",
            "docs/design/09-database-design.md",
            "tasks/implementation-baseline-inventory.json",
        )
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path in excluded_paths:
                self._write_runtime_fixture(
                    repository,
                    path,
                    "ProjectPhaseTemplate /pms/project-template pms:phase-template:create "
                    "pms_project_phase_template instantiate-from-template",
                )
            self.assertEqual([], find_retired_template_runtime_surfaces(repository))

    def test_retired_project_write_guard_scans_arbitrary_runtime_paths(self) -> None:
        fixtures = {
            "controller-composition": (
                "pms-module-project/src/main/java/example/hidden/ShadowProjectController.java",
                '@RequestMapping("/pms/project") class ShadowProjectController '
                '{ @PostMapping("/create") void createProject() {} }',
                "route composition",
            ),
            "api-composition": (
                "yudao-ui/yudao-ui-admin-vue3/src/feature/hidden/arbitrary-compose.ts",
                "const baseUrl = '/pms/project'; "
                "request.put({ url: `${baseUrl}/classify`, data })",
                "route composition",
            ),
            "full-route": (
                "yudao-ui/yudao-ui-admin-vue3/src/feature/hidden/arbitrary-full.ts",
                "request.post({ url: '/pms/project/create', data })",
                "retired project write route",
            ),
            "permission": (
                "pms-module-project/src/main/java/example/hidden/ShadowProjectPermission.java",
                "@PreAuthorize(\"@ss.hasPermission('pms:project:assign')\")",
                "permission",
            ),
            "comment-cannot-hide-route": (
                "yudao-ui/yudao-ui-admin-vue3/src/feature/hidden/arbitrary-comment.ts",
                "// legacy pms/project/assign-manager must not return",
                "retired project write route",
            ),
        }
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for name, (path, content, expected_label) in fixtures.items():
                with self.subTest(name=name):
                    self._write_runtime_fixture(repository, path, content)
                    errors = find_retired_project_write_runtime_surfaces(repository)
                    self.assertTrue(
                        any(path in error and expected_label in error for error in errors),
                        errors,
                    )

    def test_retired_project_write_guard_allows_plural_new_chain_surfaces(self) -> None:
        rebuilt_sources = {
            "controller": (
                "pms-module-project/src/main/java/cn/iocoder/yudao/module/pms/project/controller/admin/projects/ProjectController.java",
                '@RequestMapping("/pms/projects") class ProjectController { '
                '@PreAuthorize("@ss.hasPermission(\'pms:project:create\')") '
                "@PostMapping void createProject() {} }",
            ),
            "api": (
                "yudao-ui/yudao-ui-admin-vue3/src/api/pms/project/projects/index.ts",
                "const baseUrl = '/pms/projects'; request.post({ url: baseUrl, data }); "
                "request.put({ url: `${baseUrl}/${id}/actions/assign-manager`, data })",
            ),
            "view": (
                "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/projects/index.vue",
                "v-hasPermi=\"['pms:project:create', 'pms:project:assign']\"",
            ),
        }
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path, content in rebuilt_sources.values():
                self._write_runtime_fixture(repository, path, content)
            self.assertEqual([], find_retired_project_write_runtime_surfaces(repository))

    def test_retired_project_write_guard_excludes_non_runtime_evidence(self) -> None:
        excluded_paths = (
            "sql/migrations/V49__pms_menu.sql",
            "sql/migrations/V999__future.sql",
            "docs/baseline/prd-v1.7.md",
            "tasks/implementation-baseline-inventory.json",
        )
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            for path in excluded_paths:
                self._write_runtime_fixture(
                    repository,
                    path,
                    "'/pms/project' + '/create' pms/project/classify "
                    "pms:project:delete pms:project:assign",
                )
            self.assertEqual([], find_retired_project_write_runtime_surfaces(repository))

    def test_retired_project_write_runtime_has_no_legacy_write_surface(self) -> None:
        # Guard-first (F-PM01 T1): red while the legacy /pms/project write chain
        # still exists; turns green when T5 freezes the legacy write surfaces.
        self.assertEqual(
            [], find_retired_project_write_runtime_surfaces(self.repository)
        )

    def test_retired_project_tree_runtime_has_no_legacy_surface(self) -> None:
        # F-PROJ-002收敛到/pms/projects/{id}/tree后，旧树运行面不得回流。
        self.assertEqual(
            [], find_retired_project_tree_write_runtime_surfaces(self.repository)
        )

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
