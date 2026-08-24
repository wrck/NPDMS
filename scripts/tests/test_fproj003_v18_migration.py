import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V77 = MIGRATIONS / "V77__fproj003_authorization_grant.sql"
V78 = MIGRATIONS / "V78__fproj003_authorization_seed_and_menu.sql"
V79 = MIGRATIONS / "V79__fproj003_authorization_demo_seed.sql"


class FProj003V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v77 = V77.read_text(encoding="utf-8")
        cls.v78 = V78.read_text(encoding="utf-8")
        cls.v79 = V79.read_text(encoding="utf-8")

    def test_forward_migrations_follow_v76_without_editing_compose_wiring(self) -> None:
        self.assertEqual([77, 78, 79], [int(path.name[1:3]) for path in (V77, V78, V79)])
        compose = (ROOT / "compose.yaml").read_text(encoding="utf-8")
        self.assertIn("migrate:", compose)
        self.assertIn("./sql/migrations:/flyway/sql:ro", compose)

    def test_v77_owns_platform_grant_table_and_current_unique_key(self) -> None:
        self.assertEqual(["plt_authorization_grant"], re.findall(r"CREATE TABLE `([^`]+)`", self.v77))
        self.assertIn("uk_plt_authorization_grant_current", self.v77)
        self.assertIn("ck_plt_authorization_grant_interval", self.v77)
        self.assertIn("'ACTIVE','REVOKED','EXPIRED'", self.v77)

    def test_v78_seeds_only_fixed_action_and_scope_values(self) -> None:
        for marker in (
            "pms_project_authorization_action", "PROJECT_VIEW", "PROJECT_MANAGE",
            "pms_project_authorization_scope", "CURRENT_PROJECT", "PROJECT_AND_DESCENDANTS",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.v78)
        self.assertEqual(2, len(re.findall(
            r"99200302000[12].*?'pms_project_authorization_action'", self.v78)))
        self.assertEqual(2, len(re.findall(
            r"99200302000[34].*?'pms_project_authorization_scope'", self.v78)))
        self.assertIn("992003010001", self.v78)
        self.assertIn("992003020004", self.v78)
        self.assertGreaterEqual(self.v78.count("ON DUPLICATE KEY UPDATE"), 3)

    def test_v78_converges_permissions_under_existing_project_detail(self) -> None:
        for permission in (
            "pms:project:authorization:query",
            "pms:project:authorization:manage",
            "pms:project:authorization:revoke",
        ):
            self.assertIn(permission, self.v78)
        self.assertEqual(3, self.v78.count(", 18071, '', '',"))
        self.assertNotRegex(self.v78, r"'[^']*authorization[^']*',\s*'[^']*',\s*2,")

    def test_v79_uses_existing_seed_users_projects_and_platform_codes(self) -> None:
        self.assertIn("992003", self.v79)
        self.assertIn("992002", self.v79)
        self.assertNotIn("INSERT INTO `system_users`", self.v79)
        self.assertNotIn("INSERT INTO `proj_project`", self.v79)
        for marker in ("'USER'", "'PROJ'", "'PROJECT'"):
            self.assertIn(marker, self.v79)

    def test_v79_covers_effective_history_disabled_and_absent_scenarios(self) -> None:
        for marker in (
            "EXACT-CURRENT", "ALL-DESCENDANTS", "NOT-YET-EFFECTIVE",
            "EXPIRED", "REVOKED", "INACTIVE-NOT-PARTICIPATING", "NO_MATCH",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.v79)
        self.assertIn("'2099-01-01 00:00:00'", self.v79)
        self.assertIsNotNone(re.search(r"'EXPIRED'.*?NULL, 'seed'", self.v79, re.DOTALL))
        self.assertIsNotNone(re.search(r"'REVOKED'.*?NULL, 'seed'", self.v79, re.DOTALL))
        self.assertIn("'seed', 'seed', b'1', 0", self.v79)

    def test_v79_is_replayable_without_expanding_no_match(self) -> None:
        self.assertIn("ON DUPLICATE KEY UPDATE", self.v79)
        self.assertEqual(6, len(re.findall(r"\(99200310000[1-6],", self.v79)))
        self.assertNotIn("992003100007", self.v79)
        self.assertIn("不写一条会反向形成有效权限", self.v79)


if __name__ == "__main__":
    unittest.main()
