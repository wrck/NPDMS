import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
FILES = {
    version: MIGRATIONS / name
    for version, name in {
        109: "V109__fast001_device_master_and_source_facts.sql",
        110: "V110__fast001_device_shipments_and_software_versions.sql",
        111: "V111__fast001_device_temporal_assignments.sql",
        112: "V112__fast001_device_relationship_location_warranty.sql",
        113: "V113__fast001_device_download_grant.sql",
        114: "V114__fast001_legacy_equipment_forward_migration.sql",
        115: "V115__fast001_device_ancestor_projection_operations.sql",
        116: "V116__fast001_device_ancestor_projection_event_watermark.sql",
        117: "V117__fast001_device_ancestor_projection_job.sql",
        118: "V118__fast001_device_menu_permissions_and_legacy_access.sql",
        119: "V119__fast001_device_acceptance_seed.sql",
        120: "V120__fast001_browser_acceptance_users.sql",
        121: "V121__fast001_browser_acceptance_login_names.sql",
        122: "V122__fast001_customer_summary_acceptance_seed.sql",
    }.items()
}
GENERATOR = ROOT / "scripts" / "generate_fast001_performance_data.py"
PLAN_VERIFIER = ROOT / "scripts" / "verify_fast001_query_plan.py"
BROWSER_ACCEPTANCE = ROOT / "scripts" / "tests" / "run_fast001_browser_acceptance.mjs"


class Fast001MigrationContractTest(unittest.TestCase):

    def test_fast001_migration_versions_are_unique_and_seed_follows_current_maximum(self):
        versions = {}
        for path in MIGRATIONS.glob("V*__*.sql"):
            match = re.match(r"V(\d+)__", path.name)
            if match:
                versions.setdefault(int(match.group(1)), []).append(path.name)
        duplicates = {version: names for version, names in versions.items() if len(names) > 1}
        self.assertEqual({}, duplicates)
        self.assertEqual(122, max(version for version in versions if version <= 122))
        self.assertEqual(FILES[122].name, versions[122][0])

    def test_v109_and_v110_create_master_and_version_facts(self):
        sql = self._sql(109) + self._sql(110)
        for table in (
            "ast_device", "ast_device_factory_info", "ast_device_shipment",
            "ast_device_factory_version", "ast_product_official_info",
            "ast_product_official_version", "ast_device_network_version",
            "ast_device_network_version_event",
        ):
            self.assertIn(f"create table `{table}`", sql)
        self.assertIn("unique key `uk_ast_device_tenant_sn` (`tenant_id`, `sn`)", sql)
        self.assertNotIn("`tenant_id`, `sn`, `deleted`", sql)
        for column in (
            "shipment_time", "package_no", "contract_no", "shipment_record_id",
            "project_id", "customer_id", "site_id", "warranty_start_date",
            "conp_version", "conp_type", "conp_series", "conp_mark",
        ):
            self.assertIn(f"`{column}`", sql)
        self.assertNotIn("conboot_version", sql)

    def test_v111_to_v113_create_temporal_and_download_facts(self):
        sql = self._sql(111) + self._sql(112) + self._sql(113)
        for table in (
            "ast_device_project_relationship", "ast_device_project_ancestor",
            "ast_device_customer_relationship", "ast_device_assignment_reconciliation",
            "ast_device_assembly", "ast_device_relationship", "ast_device_location",
            "ast_device_warranty", "ast_device_warranty_record", "ast_device_download_grant",
        ):
            self.assertIn(f"create table `{table}`", sql)
        self.assertNotIn("ast_device_current_assignment", sql)
        self.assertNotIn("ast_device_assignment_history", sql)
        self.assertIn("parent_device_sn", sql)
        self.assertIn("child_device_sn", sql)
        self.assertNotIn("maintenance_", sql)
        for column in ("token_digest", "user_id", "device_sn", "configuration_log_id", "expires_at", "consumed_at"):
            self.assertIn(f"`{column}`", sql)
        self.assertNotIn("`token` varchar", sql)

    def test_v115_and_v116_separate_event_and_operation_watermarks(self):
        sql = self._sql(115) + self._sql(116)
        self.assertIn("create table `ast_device_ancestor_projection_operation`", sql)
        self.assertIn("add column `event_id` varchar(128)", sql)
        self.assertIn("set `event_id` = `operation_id`", sql)
        self.assertIn("modify column `event_id` varchar(128) not null", sql)
        self.assertIn("(`tenant_id`, `event_id`)", sql)
        for column in ("operation_id", "tree_version", "assignment_version"):
            self.assertIn(f"`{column}`", sql)

    def test_v117_registers_device_ancestor_projection_job(self):
        sql = self._sql(117)
        self.assertIn("insert into infra_job", sql)
        self.assertIn("deviceassignedprojectionjob", sql)
        self.assertIn("'0/30 * * * * ?'", sql)
        self.assertIn("where not exists", sql)

    def test_constraint_names_are_unique_across_fast001_schema_migrations(self):
        sql = "\n".join(self._sql(version) for version in range(109, 118))
        names = re.findall(r"constraint `([^`]+)`", sql)
        duplicates = sorted({name for name in names if names.count(name) > 1})
        self.assertEqual([], duplicates)

    def test_v114_migrates_only_explicit_legacy_fields(self):
        sql = self._sql(114)
        self.assertIn("insert into `ast_device`", sql)
        self.assertIn("legacy.`id`", sql)
        self.assertIn("legacy.`serial_number`", sql)
        self.assertIn("from `pms_equipment` legacy", sql)
        self.assertIn("not exists", sql)
        self.assertIn("legacy_pms", sql)
        self.assertNotIn("trigger", sql)
        self.assertNotIn("conp_version", sql)
        self.assertNotIn("shipment_time", sql)

    def test_v119_contains_controlled_idempotent_acceptance_seed(self):
        sql = self._sql(119)
        self.assertIn("fast001_seed", sql)
        self.assertIn("fast001_test_mes", sql)
        self.assertIn("fast001_test_itr", sql)
        self.assertIn("fast001_test_kno", sql)
        self.assertIn("on duplicate key update", sql)
        self.assertRegex(sql, r"9700000000000000\d+")
        for token in (
            "fast001_cross_tenant_sn", "fast001_shipment_current", "fast001_shipment_late",
            "fast001_shipment_disabled", "fast001_assignment_mismatch", "fast001_location_resolved",
            "fast001_location_unresolved", "fast001_warranty", "fast001_conp_exact",
            "fast001_conp_range", "fast001_conp_unknown", "fast001_assembly_level_1",
            "fast001_assembly_level_2", "fresh", "stale", "failed", "pending_mapping",
            "not_available",
        ):
            self.assertIn(token, sql)
        self.assertIn("`deleted` = b'0'", sql)
        self.assertNotIn("source_system`, `source_key`) values ('mes'", sql)
        self.assertNotIn("source_system`, `source_key`) values ('itr'", sql)
        self.assertNotIn("source_system`, `source_key`) values ('kno'", sql)

    def test_v120_contains_fixed_browser_roles_and_users(self):
        sql = self._sql(120)
        for token in (
            "fast001_browser_readonly", "fast001_browser_operator", "fast001_browser_denied",
            "fast001_readonly", "fast001_operator", "fast001_denied",
            "pms:device:query", "pms:device:assign", "pms:device-configuration-log:download",
            "198770", "198771", "198772", "198774", "19001", "19006",
        ):
            self.assertIn(token, sql)
        self.assertIn("on duplicate key update", sql)
        self.assertIn("where not exists", sql)
        self.assertNotIn("pms:equipment:create", sql)
        self.assertNotIn("pms:equipment:update", sql)
        self.assertNotIn("pms:equipment:delete", sql)
        self.assertNotIn("pms:equipment:status-change", sql)

    def test_v121_replaces_invalid_login_names_with_alphanumeric_names(self):
        sql = self._sql(121)
        for token in ("fast001readonly", "fast001operator", "fast001denied", "fast001_seed"):
            self.assertIn(token, sql)
        self.assertNotIn("'fast001_readonly'", sql)
        self.assertNotIn("'fast001_operator'", sql)
        self.assertNotIn("'fast001_denied'", sql)

    def test_browser_acceptance_script_contract(self):
        text = self._script(BROWSER_ACCEPTANCE)
        for token in (
            "fast001_browser_operator", "fast001_browser_readonly", "fast001_browser_denied",
            "fast001readonly", "fast001operator", "fast001denied",
            "mainid: '970000000000000001'", "configurationlogid: '970000000000071001'",
            "fast001_sn_main", "fast001_sn_child_1", "fast001_sn_not_available",
            "出厂信息", "官网信息", "在网版本", "技术公告", "维保信息", "配置log",
            "stale", "failed", "not_available", "tenant-id", "if-match", "idempotency-key",
            "320", "768", "1024", "1440", "emulation.setdevicemetricsoverride",
            "http://127.0.0.1:18083", "http://127.0.0.1:58082/admin-api",
            "/pms/equipment", "consoleerrors", "failedapiresponses",
        ):
            self.assertIn(token, text)

    def test_browser_acceptance_requires_explicit_password_without_creating_evidence(self):
        env = os.environ.copy()
        env.pop("FAST001_BROWSER_PASSWORD", None)
        with tempfile.TemporaryDirectory() as output_dir:
            result = subprocess.run(
                [
                    "node", str(BROWSER_ACCEPTANCE),
                    "http://127.0.0.1:1", "http://127.0.0.1:1",
                    "http://127.0.0.1:1/admin-api", output_dir,
                ],
                cwd=ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                check=False,
                env=env,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn("FAST001_BROWSER_PASSWORD", result.stderr)
            self.assertEqual([], list(Path(output_dir).iterdir()))

    def test_browser_acceptance_source_and_output_evidence_do_not_expose_password(self):
        text = BROWSER_ACCEPTANCE.read_text(encoding="utf-8")
        password = "fast001-sensitive-password"
        self.assertNotIn("admin123", text)
        self.assertNotRegex(text, r"FAST001_BROWSER_PASSWORD\s*\|\|")
        with tempfile.TemporaryDirectory() as output_dir:
            env = os.environ.copy()
            env["FAST001_BROWSER_PASSWORD"] = password
            result = subprocess.run(
                [
                    "node", str(BROWSER_ACCEPTANCE),
                    "http://127.0.0.1:1", "http://127.0.0.1:1",
                    "http://127.0.0.1:1/admin-api", output_dir,
                ],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
                env=env,
            )
            evidence = result.stdout + result.stderr
            for path in Path(output_dir).rglob("*"):
                if path.is_file():
                    evidence += path.read_text(encoding="utf-8", errors="ignore")
            self.assertNotIn(password, evidence)

    def test_performance_generator_contract(self):
        text = self._script(GENERATOR)
        for token in (
            "--devices", "2000000", "--shipments", "4000000", "--tenant-id",
            "--batch-size", "10000", "--cleanup", "fast001_perf_",
        ):
            self.assertIn(token, text)
        self.assertIn("insert into `ast_device`", text)
        self.assertIn("insert into `ast_device_shipment`", text)
        self.assertIn("delete from `ast_device_shipment`", text)
        self.assertIn("delete from `ast_device`", text)
        self.assertEqual(0, self._help(GENERATOR).returncode)

    def test_query_plan_verifier_contract(self):
        text = self._script(PLAN_VERIFIER)
        for token in (
            "explain format=json", "uk_ast_device_tenant_sn", "idx_ast_device_project",
            "idx_ast_device_customer", "ast_device_shipment", "rows_examined_per_scan",
            "json.dumps", "--max-rows-examined",
        ):
            self.assertIn(token, text)
        self.assertEqual(0, self._help(PLAN_VERIFIER).returncode)

    def _sql(self, version):
        path = FILES[version]
        self.assertTrue(path.exists(), f"缺少迁移文件：{path.name}")
        return path.read_text(encoding="utf-8").lower()

    def _script(self, path):
        self.assertTrue(path.exists(), f"缺少工具：{path.name}")
        return path.read_text(encoding="utf-8").lower()

    def _help(self, path):
        return subprocess.run(
            [sys.executable, str(path), "--help"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
