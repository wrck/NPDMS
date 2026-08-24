import hashlib
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V70 = MIGRATIONS / "V70__commerce_delivery_scope_slice.sql"
V71 = MIGRATIONS / "V71__fproj002_split_tree_progress_carriers.sql"
V72 = MIGRATIONS / "V72__fproj002_v18_seed_and_menu.sql"
V73 = MIGRATIONS / "V73__fproj002_v18_visibility_seed.sql"
V74 = MIGRATIONS / "V74__fproj002_v18_organization_scope_seed.sql"
V75 = MIGRATIONS / "V75__fproj002_v18_parent_template_seed.sql"
V76 = MIGRATIONS / "V76__fproj002_project_code_sequence_repair.sql"

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
        cls.v72 = V72.read_text(encoding="utf-8")
        cls.v73 = V73.read_text(encoding="utf-8")
        cls.v74 = V74.read_text(encoding="utf-8")
        cls.v75 = V75.read_text(encoding="utf-8")
        cls.v76 = V76.read_text(encoding="utf-8")

    def test_forward_migrations_are_ordered_and_owner_prefixed(self) -> None:
        self.assertTrue(V70.is_file())
        self.assertTrue(V71.is_file())
        self.assertTrue(V72.is_file())
        self.assertTrue(V73.is_file())
        self.assertTrue(V74.is_file())
        self.assertTrue(V75.is_file())
        self.assertTrue(V76.is_file())
        self.assertLess(int(V70.name[1:3]), int(V71.name[1:3]))
        self.assertLess(int(V71.name[1:3]), int(V72.name[1:3]))
        self.assertLess(int(V72.name[1:3]), int(V73.name[1:3]))
        self.assertLess(int(V73.name[1:3]), int(V74.name[1:3]))
        self.assertLess(int(V74.name[1:3]), int(V75.name[1:3]))
        self.assertLess(int(V75.name[1:3]), int(V76.name[1:3]))
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

    def test_v72_seed_is_namespaced_idempotent_and_authority_safe(self) -> None:
        self.assertIn("FPROJ002-V18", self.v72)
        self.assertIn("992002", self.v72)
        self.assertGreaterEqual(self.v72.count("'seed'"), 20)
        self.assertIn("ON DUPLICATE KEY UPDATE", self.v72)
        self.assertIn("WHERE NOT EXISTS", self.v72)
        self.assertIn("PENDING_AUTHORITY", self.v72)
        self.assertIn("CONFIRMED", self.v72)
        self.assertNotRegex(self.v72, r"(?:ERP|CRM)[-_]?(?:CODE|VALUE)[-_]?\d+")

    def test_v72_covers_scope_tree_progress_and_closure_scenarios(self) -> None:
        for marker in (
            "EXACT_MATCH", "PARTIAL_MATCH_PRIORITY_YIELD", "FALLBACK",
            "NO-MATCH", "INACTIVE_NOT_PARTICIPATING", "depth` < 30",
            "SYSTEM_EQUAL", "MANUAL", "PENDING", "READY", "FACT_MISSING",
            "NORMAL_CLOSED", "ACTIVE",
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, self.v72)
        self.assertIn("ROW_NUMBER() OVER", self.v72)
        self.assertIn("proj_project_tree_path", self.v72)

    def test_v72_converges_menu_to_project_detail(self) -> None:
        self.assertIn("WHERE `id` IN (18012, 18024, 18025)", self.v72)
        self.assertIn("18071", self.v72)
        for permission in (
            "pms:project:create", "pms:project:update",
            "pms:project:progress-policy:update", "pms:project:progress-policy:submit",
        ):
            self.assertIn(permission, self.v72)

    def test_v73_adds_full_and_limited_visibility_without_editing_v72(self) -> None:
        self.assertIn("PROJECT_MANAGER", self.v73)
        self.assertIn("'MEMBER'", self.v73)
        self.assertIn("992002000000", self.v73)
        self.assertIn("992002000030", self.v73)
        self.assertGreaterEqual(self.v73.count("'seed'"), 4)

    def test_v74_reuses_platform_authority_for_split_acceptance(self) -> None:
        self.assertIn("DPTECH-DEMO", self.v74)
        self.assertIn("OFFICE-HZ-DEMO", self.v74)
        self.assertIn("proj_project_company_department_relation", self.v74)
        self.assertIn("system_user_company_department_scope", self.v74)
        self.assertNotIn("INSERT INTO `system_company`", self.v74)
        self.assertNotIn("INSERT INTO `system_dept`", self.v74)

    def test_v75_binds_parent_to_existing_published_template(self) -> None:
        self.assertIn("911016", self.v75)
        self.assertIn("910008", self.v75)
        self.assertIn("PUBLISHED", self.v75)
        self.assertNotIn("INSERT INTO `proj_project_template", self.v75)

    def test_v76_repairs_child_code_sequence_from_existing_project_facts(self) -> None:
        self.assertIn("proj_project_code_sequence", self.v76)
        self.assertIn("CONCAT('ROOT:', project_group.code_root_id)", self.v76)
        self.assertIn("MAX(`project_sequence`) + 1", self.v76)
        self.assertIn("GROUP BY `tenant_id`, `code_root_id`", self.v76)
        self.assertIn("GREATEST(`proj_project_code_sequence`.`next_value`, VALUES(`next_value`))", self.v76)

    def test_v70_non_auto_primary_keys_use_assigned_ids(self) -> None:
        base = ROOT / "pms-module-commerce" / "src" / "main" / "java" / "cn" / "iocoder" / "yudao" / "module" / "pms" / "commerce" / "dal" / "dataobject"
        carriers = (
            "scope/OrderLineDO.java",
            "scope/DeliveryScopeDO.java",
            "scope/DeliveryScopeDetailDO.java",
            "outbox/CommerceOutboxEventDO.java",
        )
        for relative in carriers:
            source = (base / relative).read_text(encoding="utf-8")
            with self.subTest(carrier=relative):
                self.assertIn("@TableId(type = IdType.ASSIGN_ID)", source)

    def test_single_tenant_controllers_fall_back_to_seed_tenant(self) -> None:
        controllers = (
            "projectprogress/ProjectProgressController.java",
            "projectclosureguard/ProjectClosureGuardController.java",
            "projectclosure/ProjectClosureController.java",
            "projectsplit/ProjectSplitRequestController.java",
        )
        base = ROOT / "pms-module-project" / "src" / "main" / "java" / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project" / "controller" / "admin"
        for relative in controllers:
            source = (base / relative).read_text(encoding="utf-8")
            with self.subTest(controller=relative):
                self.assertIn("TenantContextHolder.getTenantId()", source)
                self.assertIn("tenantId != null ? tenantId : 0L", source)
                self.assertNotIn("TenantContextHolder.getRequiredTenantId()", source)


if __name__ == "__main__":
    unittest.main()
