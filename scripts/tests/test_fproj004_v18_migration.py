import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V54 = MIGRATIONS / "V54__fpm03_template_demo_seed.sql"
V59 = MIGRATIONS / "V59__fpm01_manual_match_demo_seed.sql"
V80 = MIGRATIONS / "V80__fproj004_template_match_history.sql"
V81 = MIGRATIONS / "V81__fproj004_template_match_seed_and_permission.sql"
V82 = MIGRATIONS / "V82__fproj004_project_category_deduplicate.sql"
PHYSICAL_CONTRACT = ROOT / "specs" / "features" / "F-PROJ-004-physical-contract.json"


class FProj004V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v54 = V54.read_text(encoding="utf-8")
        cls.v59 = V59.read_text(encoding="utf-8")
        cls.v80 = V80.read_text(encoding="utf-8")
        cls.v81 = V81.read_text(encoding="utf-8")
        cls.v82 = V82.read_text(encoding="utf-8")
        cls.physical_contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))

    def test_forward_migrations_follow_v79(self) -> None:
        self.assertEqual([80, 81, 82], [int(path.name[1:3]) for path in (V80, V81, V82)])
        self.assertNotIn("ALTER TABLE `proj_project` ADD", self.v80 + self.v81 + self.v82)

    def test_v80_creates_only_the_approved_append_only_fact(self) -> None:
        self.assertEqual(
            ["proj_project_template_match_history"],
            re.findall(r"CREATE TABLE IF NOT EXISTS `([^`]+)`", self.v80),
        )
        for forbidden in (
            "proj_project_business_attribute_history",
            "classification_case",
            "template_impact",
            "outbox",
        ):
            self.assertNotIn(forbidden, self.v80.lower())

    def test_v80_contains_physical_contract_fields_and_keys(self) -> None:
        fields = self.physical_contract["objects"]["ProjectTemplateMatchHistory"]["physicalFields"]
        for field in fields:
            with self.subTest(field=field):
                self.assertIn(f"`{field}`", self.v80)
        self.assertIn("uk_proj_template_match_history_operation", self.v80)
        self.assertIn("uk_proj_template_match_history_idempotency", self.v80)
        self.assertIn("ck_proj_template_match_history_trigger_purpose", self.v80)
        self.assertIn("ck_proj_template_match_history_source", self.v80)
        self.assertIn("ck_proj_template_match_history_matched", self.v80)

    def test_v81_retires_tree_codes_without_rewriting_projects(self) -> None:
        self.assertIn("`value` IN ('MAIN', 'SUB')", self.v81)
        self.assertIn("'GENERAL'", self.v81)
        self.assertIn("'ENGINEERING'", self.v81)
        self.assertNotRegex(self.v81, r"UPDATE\s+`?proj_project`?")
        self.assertIn("pms:project:classify", self.v81)

    def test_existing_seeds_are_revalidated_for_all_match_scenarios(self) -> None:
        combined = self.v54 + self.v59
        for marker in (
            "唯一命中", "部分限定", "优先级让位", "无匹配", "多匹配", "停用不参与",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, combined)
        self.assertNotIn("INSERT INTO `proj_project_template`", self.v81)

    def test_v82_disables_duplicate_categories_without_rewriting_projects(self) -> None:
        self.assertEqual(2, self.v82.count("UPDATE `system_dict_data`"))
        self.assertIn("`id` <> 992004020001", self.v82)
        self.assertIn("`id` <> 992004020002", self.v82)
        self.assertNotRegex(self.v82, r"UPDATE\s+`?proj_project`?")


if __name__ == "__main__":
    unittest.main()
