import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql" / "migrations" / "V87__fcus001_customer_master.sql"


class CustomerMigrationContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.sql = MIGRATION.read_text(encoding="utf-8").lower()

    def test_creates_customer_owned_tables(self):
        for table in (
            "cus_customer_master",
            "cus_customer_external_mapping",
            "cus_customer_field_history",
            "cus_customer_location_reference",
        ):
            self.assertIn(f"create table `{table}`", self.sql)

    def test_customer_master_has_tenant_unique_code_and_version_fields(self):
        self.assertIn("unique key `uk_cus_customer_master_tenant_code` (`tenant_id`, `code`)", self.sql)
        for column in ("`source_type`", "`sync_status`", "`data_as_of`", "`version`", "`deleted`"):
            self.assertIn(column, self.sql)

    def test_current_crm_mapping_is_unique(self):
        self.assertIn("`current_marker`", self.sql)
        self.assertIn("`tenant_id`, `source_system`, `source_key`, `current_marker`", self.sql)

    def test_migrates_legacy_customer_with_original_id(self):
        self.assertIn("insert into `cus_customer_master`", self.sql)
        self.assertIn("select", self.sql)
        self.assertIn("legacy.`id`", self.sql)
        self.assertIn("from `pms_customer` legacy", self.sql)
        self.assertIn("not exists", self.sql)


if __name__ == "__main__":
    unittest.main()
