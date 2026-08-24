import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V1 = MIGRATIONS / "V1__yudao_platform.sql"
V57 = MIGRATIONS / "V57__proj_project_manual_creation.sql"
V82 = MIGRATIONS / "V82__fproj004_project_category_deduplicate.sql"
V83 = MIGRATIONS / "V83__fproj005_service_manager_assignment.sql"


class FProj005V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v1 = V1.read_text(encoding="utf-8")
        cls.v57 = V57.read_text(encoding="utf-8")
        cls.v83 = V83.read_text(encoding="utf-8")
        cls.legacy_member_table = cls.v57.split(
            "CREATE TABLE IF NOT EXISTS `proj_project_member_assignment` (", 1
        )[1].split(") ENGINE=InnoDB", 1)[0]

    def test_v83_is_the_next_forward_migration(self) -> None:
        self.assertTrue(V82.is_file())
        self.assertTrue(V83.is_file())
        self.assertEqual(83, int(V83.name[1:3]))

    def test_v83_adds_approved_member_assignment_fields(self) -> None:
        for field in ("department_id", "assignment_type", "site_id", "change_reason"):
            with self.subTest(field=field):
                self.assertIn(f"column_name = '{field}'", self.v83)
                self.assertIn(f"ADD COLUMN `{field}`", self.v83)
        self.assertIn("idx_proj_member_current_responsibility", self.v83)

    def test_v83_adds_persistent_notification_delivery_key(self) -> None:
        self.assertIn("ADD COLUMN `delivery_key` VARCHAR(128) NULL", self.v83)
        self.assertIn("uk_system_notify_message_delivery", self.v83)
        self.assertRegex(
            self.v83,
            re.compile(
                r"UNIQUE KEY `uk_system_notify_message_delivery` "
                r"\(`tenant_id`,`user_type`,`delivery_key`\)"
            ),
        )

    def test_v83_does_not_create_parallel_history_or_retry_tables(self) -> None:
        self.assertNotRegex(self.v83, r"(?i)CREATE\s+TABLE")
        for forbidden in (
            "proj_project_member_assignment_history",
            "system_notify_message_history",
            "notification_retry",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.v83.lower())

    def test_legacy_table_definitions_remain_compatible(self) -> None:
        for field in ("department_id", "assignment_type", "site_id", "change_reason"):
            with self.subTest(field=field):
                self.assertNotIn(f"`{field}`", self.legacy_member_table)
        self.assertNotIn("`delivery_key`", self.v1)


if __name__ == "__main__":
    unittest.main()
