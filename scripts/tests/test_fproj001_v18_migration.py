import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql" / "migrations" / "V63__fproj001_v18_atomic_project_creation.sql"


class Fproj001V18MigrationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.assertTrue(MIGRATION.is_file(), f"missing forward migration: {MIGRATION.name}")
        self.sql = MIGRATION.read_text(encoding="utf-8").lower()

    def test_adds_independent_project_status_dimensions(self) -> None:
        self.assertIn("alter table `proj_project`", self.sql)
        self.assertIn("`lifecycle_status`", self.sql)
        self.assertIn("`current_stage`", self.sql)
        self.assertIn("`assignment_status`", self.sql)
        self.assertNotIn("alter table `proj_project` drop", self.sql)

    def test_adds_template_and_instance_execution_contracts(self) -> None:
        for token in (
            "`work_binding_type_code`",
            "`permission_policy_ref`",
            "`completion_rule_type_code`",
            "`definition_version`",
            "create table if not exists `proj_project_task_execution_contract`",
            "`contract_version`",
            "`current_marker`",
            "uk_project_task_execution_contract_current",
        ):
            self.assertIn(token, self.sql)

    def test_adds_common_platform_facts(self) -> None:
        for table in (
            "plt_idempotency_record",
            "plt_operation_audit",
            "plt_outbox_event",
        ):
            self.assertIn(f"create table if not exists `{table}`", self.sql)

    def test_adds_acc_owned_deliverable_table(self) -> None:
        self.assertIn("create table if not exists `acc_project_deliverable`", self.sql)
        self.assertIn("insert into `acc_project_deliverable`", self.sql)

    def test_backfill_is_explicit_task_native_only(self) -> None:
        self.assertIn("'task_native'", self.sql)
        self.assertIn("where `status` in ('s0', 's1', 's2', 's3', 's4', 's5', 's6')", self.sql)
        self.assertNotIn("task_name", self.sql)
        self.assertNotIn("menu_path", self.sql)
        self.assertNotIn("delete from", self.sql)


if __name__ == "__main__":
    unittest.main()
