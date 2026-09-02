import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql/migrations/V163__fcom001_acceptance_identity_authorization_fix.sql"
V162_MIGRATION = ROOT / "sql/migrations/V162__fcom001_stage_entry_acceptance_seed.sql"


class Fcom001V163MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.sql = MIGRATION.read_text(encoding="utf-8")

    def test_v162_remains_immutable_and_v163_is_forward_only(self) -> None:
        self.assertTrue(V162_MIGRATION.exists())
        self.assertNotIn("V162__", self.sql)
        self.assertIn("START TRANSACTION", self.sql)
        self.assertIn("COMMIT", self.sql)
        self.assertIn("ROLLBACK", self.sql)

    def test_managed_user_is_renamed_without_touching_password_or_owner_links(self) -> None:
        for token in (
                "992002800002", "'fcom001_acceptance'", "'fcom001acceptance'",
                "`creator` = 'fcom001_seed'", "`nickname` = 'FCOM001全权限验收'",
                "`dept_id` = 930851", "`updater` = 'fcom001_v163'"):
            with self.subTest(token=token):
                self.assertIn(token, self.sql)
        self.assertNotRegex(self.sql, r"UPDATE\s+`system_users`[\s\S]*?SET[\s\S]*?`password`\s*=")
        self.assertNotRegex(self.sql, r"(?:UPDATE|DELETE)\s+`(?:system_user_company_department_scope|proj_project_member_assignment)`")

    def test_target_username_conflict_is_tenant_scoped_and_failure_closed(self) -> None:
        self.assertRegex(
            self.sql,
            r"conflicting_new_username_count[\s\S]*?`tenant_id` = 0[\s\S]*?`id` <> 992002800002"
        )
        self.assertIn("SIGNAL SQLSTATE '45000'", self.sql)
        self.assertIn("managed acceptance identity is partial or conflicting", self.sql)

    def test_project_update_grant_uses_exact_active_menu_identity(self) -> None:
        for token in (
                "`id` = 18069", "`parent_id` = 18067",
                "`permission` = 'pms:project:update'", "`status` = 0",
                "(992002800001, 18069"):
            with self.subTest(token=token):
                self.assertIn(token, self.sql)
        self.assertNotIn("18051", self.sql)
        self.assertNotIn("198721", self.sql)
        self.assertNotRegex(self.sql, r"COUNT\(\*\).*permission.*pms:project:update")

    def test_only_complete_before_or_complete_after_states_are_accepted(self) -> None:
        self.assertIn("old_username_count = 1 AND new_username_count = 0", self.sql)
        self.assertIn("project_update_grant_count = 0 AND project_update_grant_row_count = 0", self.sql)
        self.assertIn("old_username_count = 0 AND new_username_count = 1", self.sql)
        self.assertIn("project_update_grant_count = 1 AND project_update_grant_row_count = 1", self.sql)


if __name__ == "__main__":
    unittest.main()
