import hashlib
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V70 = MIGRATIONS / "V70__commerce_delivery_scope_slice.sql"
V71 = MIGRATIONS / "V71__fproj002_split_tree_progress_carriers.sql"

PROJ_TABLES = {
    "proj_project_split_request",
    "proj_project_split_item",
    "proj_project_split_scope",
    "proj_project_tree_version",
    "proj_project_tree_path",
    "proj_project_tree_change",
    "proj_project_progress_fact",
    "proj_project_progress_policy_revision",
    "proj_project_progress_policy_item",
    "proj_project_progress_snapshot",
    "proj_project_progress_snapshot_detail",
}

LEGACY_HASHES = {
    "V60__fpm02_project_tree_progress.sql": "971376935EB041187DB0F0FCB32BCEEDA1CBFC879006C9270B3046889F4CB7C1",
    "V61__fpm02_project_tree_demo_seed.sql": "0248F8C47FED054012B2AC8188BEE12D82A3C15DEF995C595D4BF5DE7180CC2C",
    "V62__fpm02_project_tree_fullchain_template.sql": "51E32E3DD5D70F577217C1B63E7FE76E4245683BAC5F3744AB73DC81A10A05E3",
}


class FProj002V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v70 = V70.read_text(encoding="utf-8")
        cls.v71 = V71.read_text(encoding="utf-8")

    def test_forward_migrations_are_ordered_and_owner_prefixed(self) -> None:
        self.assertTrue(V70.is_file())
        self.assertTrue(V71.is_file())
        self.assertLess(int(V70.name[1:3]), int(V71.name[1:3]))
        self.assertEqual(PROJ_TABLES, set(re.findall(r"CREATE TABLE `([^`]+)`", self.v71)))
        self.assertTrue(all(name.startswith("proj_") for name in PROJ_TABLES))
        self.assertTrue(all(name.startswith("com_") for name in re.findall(r"CREATE TABLE `([^`]+)`", self.v70)))

    def test_each_v71_table_has_tenant_audit_delete_and_version_columns(self) -> None:
        chunks = re.findall(r"CREATE TABLE `([^`]+)` \((.*?)\) ENGINE=", self.v71, re.DOTALL)
        self.assertEqual(len(PROJ_TABLES), len(chunks))
        for table, ddl in chunks:
            with self.subTest(table=table):
                for column in ("tenant_id", "creator", "create_time", "updater", "update_time", "deleted", "version"):
                    self.assertIn(f"`{column}`", ddl)

    def test_status_checks_and_required_uniques_are_present(self) -> None:
        for status in ("DRAFT", "APPLIED", "BUILDING", "ACTIVE", "FAILED", "APPROVING",
                       "REJECTED", "SUPERSEDED", "READY", "PENDING"):
            self.assertIn(f"'{status}'", self.v71)
        for key in ("uk_proj_split_item_client", "uk_proj_split_scope_dimension", "uk_proj_tree_version",
                    "uk_proj_tree_path", "uk_proj_tree_change_batch", "uk_proj_progress_fact",
                    "uk_proj_progress_policy_revision", "uk_proj_progress_policy_item",
                    "uk_proj_progress_snapshot", "uk_proj_progress_snapshot_detail"):
            self.assertIn(f"`{key}`", self.v71)
        self.assertIn("CHECK (`allocated_qty` > 0)", self.v71)
        self.assertIn("`project_id`,`policy_revision_id`,`tree_version`,`source_watermark`", self.v71)

    def test_v17_migrations_remain_unchanged(self) -> None:
        for name, expected in LEGACY_HASHES.items():
            actual = hashlib.sha256((MIGRATIONS / name).read_bytes()).hexdigest().upper()
            self.assertEqual(expected, actual, name)


if __name__ == "__main__":
    unittest.main()
