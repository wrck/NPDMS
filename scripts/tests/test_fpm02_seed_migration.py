import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql" / "migrations" / "V61__fpm02_project_tree_demo_seed.sql"
CORRECTION = ROOT / "sql" / "migrations" / "V62__fpm02_project_tree_fullchain_template.sql"


class Fpm02SeedMigrationTest(unittest.TestCase):

    def setUp(self) -> None:
        self.sql = MIGRATION.read_text(encoding="utf-8")

    def test_seed_is_forward_only_idempotent_and_auditable(self) -> None:
        self.assertIn("INSERT INTO `proj_project`", self.sql)
        self.assertIn("WHERE NOT EXISTS", self.sql)
        self.assertIn("'seed'", self.sql)
        self.assertNotIn("UPDATE `proj_project`", self.sql)
        self.assertNotIn("DELETE FROM `proj_project`", self.sql)

    def test_seed_covers_tree_depth_and_business_level_reuse(self) -> None:
        self.assertIn(
            "NULL AS parent_id, 920001 AS root_id, '/' AS tree_path, 0 AS tree_depth",
            self.sql,
        )
        self.assertIn(
            "920004, 920001, '/920001/920002/920004/', 3",
            self.sql,
        )
        office_depths = re.findall(r"'LEVEL_OFFICE'\s*,\s*'办事处'", self.sql)
        self.assertGreaterEqual(len(office_depths), 2)

    def test_seed_covers_manual_and_default_equal_weights(self) -> None:
        self.assertRegex(self.sql, r"60\.00\s*,\s*'MANUAL'")
        self.assertRegex(self.sql, r"40\.00\s*,\s*'MANUAL'")
        self.assertRegex(self.sql, r"NULL\s*,\s*'DEFAULT_EQUAL'")

    def test_child_code_sequence_continues_after_seeded_children(self) -> None:
        self.assertIn("ROOT:920001", self.sql)
        self.assertRegex(self.sql, r"'ROOT:920001'\s*,\s*6")

    def test_forward_correction_binds_only_demo_tree_to_s0_s6_template(self) -> None:
        sql = CORRECTION.read_text(encoding="utf-8")
        self.assertIn("UPDATE `proj_project`", sql)
        self.assertIn("`lifecycle_template_id` = 910001", sql)
        self.assertIn("`lifecycle_template_revision_no` = 2", sql)
        self.assertIn("`creator` = 'seed'", sql)
        self.assertIn("`code_root_id` = 920001", sql)
        self.assertNotIn("UPDATE `proj_project_template_revision`", sql)


if __name__ == "__main__":
    unittest.main()
