import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql/migrations/V126__fcom001_stage_entry_acceptance_seed.sql"


class Fcom001V126MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.sql = MIGRATION.read_text(encoding="utf-8")

    def test_seed_has_independent_project_tree_and_stage_transition_preconditions(self) -> None:
        for token in (
                "992002900001", "F-COM001-STAGE-ENTRY-001",
                "F-COM001-STAGE-ENTRY-TREE-V1", "`node_count`,",
                "`path_count`, `activated_at`",
                "992002900001, 992002900001, 0", "'S4', '上线阶段', 40, 'DONE'",
                "'S5', '验收阶段', 50, 'PENDING'", "'PROJECT_MANAGER'",
                "F-COM001-STAGE-ENTRY-LINE-001", "F-COM001-STAGE-ENTRY-SCOPE-001"):
            with self.subTest(token=token):
                self.assertIn(token, self.sql)
        self.assertNotIn("992002000000, 992002900001", self.sql)

    def test_seed_is_atomic_and_rejects_partial_or_conflicting_identity(self) -> None:
        self.assertIn("DECLARE EXIT HANDLER FOR SQLEXCEPTION", self.sql)
        self.assertIn("START TRANSACTION", self.sql)
        self.assertIn("IF existing_identity_count = 0 THEN", self.sql)
        self.assertIn("managed stage-entry seed is incomplete or conflicting", self.sql)
        self.assertIn("ROLLBACK", self.sql)
        self.assertIn("COMMIT", self.sql)
        self.assertNotIn("ON DUPLICATE KEY UPDATE", self.sql)

    def test_seed_starts_without_stage_entry_snapshot_binding_or_report(self) -> None:
        for table in ("proj_project_stage_snapshot", "acc_acceptance_scope_binding", "pms_acc_acceptance"):
            self.assertRegex(self.sql, rf"SELECT COUNT\(\*\) FROM `{table}`")
        self.assertNotRegex(self.sql, r"INSERT\s+INTO\s+`(?:proj_project_stage_snapshot|acc_acceptance_scope_binding|pms_acc_acceptance)`")

    def test_seed_uses_dedicated_order_line_scope_and_detail(self) -> None:
        self.assertIn("992002900007", self.sql)
        self.assertIn("992002900008", self.sql)
        self.assertIn("992002900009", self.sql)
        self.assertIn("'CONFIRMED'", self.sql)
        self.assertIn("'F-COM001-PRODUCT-STAGE-ENTRY'", self.sql)
        self.assertIn("`allocation_version`", self.sql)
        self.assertNotIn("992002300005", self.sql)
        self.assertNotIn("992002300006", self.sql)


if __name__ == "__main__":
    unittest.main()
