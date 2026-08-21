from __future__ import annotations

import importlib.util
import tempfile
import textwrap
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "validate_f_proj_001_core_cutover.py"
SPEC = importlib.util.spec_from_file_location("validate_f_proj_001_core_cutover", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class CoreCutoverValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp_dir.name)
        self.migration = self.repo / "sql/migrations/V63__f_proj_001_core_project_write_model.sql"
        self.migration.parent.mkdir(parents=True)
        self.main_java = self.repo / "pms-module-project/src/main/java/example"
        self.main_java.mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write(self, relative_path: str, content: str) -> None:
        path = self.repo / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(content), encoding="utf-8")

    def write_valid_fixture(self) -> None:
        statements = []
        for table in MODULE.REQUIRED_CORE_TABLES:
            if table == "proj_project_template_task_definition":
                statements.append(
                    "ALTER TABLE `proj_project_template_task_definition` "
                    "ADD COLUMN `task_definition_key` VARCHAR(128) NOT NULL;"
                )
            else:
                statements.append(f"CREATE TABLE `{table}` (`id` BIGINT NOT NULL);")
        self.migration.write_text("\n".join(statements), encoding="utf-8")
        self.write(
            "pms-module-project/src/main/java/example/ProjectMasterController.java",
            """
            package example;
            @RestController
            @RequestMapping("/pms/projects")
            class ProjectMasterController {
                @PostMapping
                Object createProject() { return null; }
            }
            """,
        )
        self.write(
            "pms-module-project/src/main/java/example/ProjectManualCreationServiceImpl.java",
            """
            package example;
            @Service
            class ProjectManualCreationServiceImpl {
                Object createProject() { return null; }
            }
            """,
        )

    def test_accepts_six_formal_tables_and_single_active_create_path(self) -> None:
        self.write_valid_fixture()
        self.assertEqual([], MODULE.validate_repository(self.repo))

    def test_rejects_missing_formal_table(self) -> None:
        self.write_valid_fixture()
        self.migration.write_text(
            self.migration.read_text(encoding="utf-8").replace(
                "CREATE TABLE `cut_cutover_checklist_item_result`",
                "CREATE TABLE `removed_cutover_checklist_item_result`",
            ),
            encoding="utf-8",
        )
        errors = MODULE.validate_repository(self.repo)
        self.assertTrue(any("cut_cutover_checklist_item_result" in error for error in errors), errors)

    def test_rejects_new_write_to_legacy_project_table(self) -> None:
        self.write_valid_fixture()
        self.migration.write_text(
            self.migration.read_text(encoding="utf-8")
            + "\nUPDATE `pms_project` SET `name` = 'forbidden';\n",
            encoding="utf-8",
        )
        errors = MODULE.validate_repository(self.repo)
        self.assertTrue(any("legacy pms_project write" in error for error in errors), errors)

    def test_rejects_orm_write_to_legacy_project_table(self) -> None:
        self.write_valid_fixture()
        self.write(
            "pms-module-project/src/main/java/example/LegacyProjectDO.java",
            """
            package example;
            @TableName("pms_project")
            class LegacyProjectDO {}
            """,
        )
        self.write(
            "pms-module-project/src/main/java/example/LegacyProjectMapper.java",
            """
            package example;
            interface LegacyProjectMapper extends BaseMapperX<LegacyProjectDO> {}
            """,
        )
        self.write(
            "pms-module-project/src/main/java/example/LegacyWriteConsumer.java",
            """
            package example;
            class LegacyWriteConsumer {
                LegacyProjectMapper mapper;
                void save(LegacyProjectDO value) { mapper.insert(value); }
            }
            """,
        )
        errors = MODULE.validate_repository(self.repo)
        self.assertTrue(any("ORM write to legacy pms_project" in error for error in errors), errors)

    def test_rejects_second_active_create_controller(self) -> None:
        self.write_valid_fixture()
        self.write(
            "pms-module-project/src/main/java/example/AnotherProjectController.java",
            """
            package example;
            @RestController
            @RequestMapping("/pms/projects-alias")
            class AnotherProjectController {
                @PostMapping
                Object createProject() { return null; }
            }
            """,
        )
        errors = MODULE.validate_repository(self.repo)
        self.assertTrue(any("active create controller" in error for error in errors), errors)

    def test_rejects_active_reference_to_deprecated_type(self) -> None:
        self.write_valid_fixture()
        self.write(
            "pms-module-project/src/main/java/example/LegacyProjectCreator.java",
            """
            package example;
            @Deprecated(forRemoval = false, since = "F-PROJ-001")
            class LegacyProjectCreator {}
            """,
        )
        self.write(
            "pms-module-project/src/main/java/example/ActiveConsumer.java",
            """
            package example;
            class ActiveConsumer { LegacyProjectCreator creator; }
            """,
        )
        errors = MODULE.validate_repository(self.repo)
        self.assertTrue(any("references deprecated type LegacyProjectCreator" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
