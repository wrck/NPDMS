import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql" / "migrations" / "V88__fcus001_customer_classification_scope.sql"


class CustomerScopeMigrationContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION.read_text(encoding="utf-8").lower()

    def test_uses_department_code_without_parallel_office_fields(self):
        self.assertIn("`department_code`", self.sql)
        self.assertIn("`department_name`", self.sql)
        self.assertNotIn("`department_id`", self.sql)
        self.assertNotIn("`office_code`", self.sql)
        self.assertNotIn("`office_department_code`", self.sql)

    def test_adds_customer_master_contact_fields(self):
        self.assertIn("`contact_phone`", self.sql)
        self.assertIn("`contact_email`", self.sql)

    def test_creates_market_relation_without_relation_foreign_key(self):
        self.assertIn("create table `cus_market_relation`", self.sql)
        for column in (
            "`market_code`",
            "`system_code`",
            "`expend_code`",
            "`industry_code`",
        ):
            self.assertIn(column, self.sql)
        self.assertNotIn("market_relation_id", self.sql)

    def test_scope_slice_supports_all_five_multiselect_dimensions(self):
        self.assertIn("create table `cus_customer_scope_slice`", self.sql)
        for column in (
            "`department_codes`",
            "`market_codes`",
            "`system_codes`",
            "`expend_codes`",
            "`industry_codes`",
        ):
            self.assertIn(column, self.sql)
        for column in (
            "`department_mode`",
            "`market_mode`",
            "`system_mode`",
            "`expend_mode`",
            "`industry_mode`",
        ):
            self.assertIn(column, self.sql)


if __name__ == "__main__":
    unittest.main()
