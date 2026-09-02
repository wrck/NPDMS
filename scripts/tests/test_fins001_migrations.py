import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V173 = MIGRATIONS / "V173__fins001_inspection_rule_revision.sql"
V174 = MIGRATIONS / "V174__fins001_inspection_rule_seed_and_menu.sql"
V175 = MIGRATIONS / "V175__fins001_legacy_rule_forward_migration.sql"


REQUIREMENT_IDS = ("INS-03", "INS-09")


class Fins001MigrationContractTest(unittest.TestCase):

    def test_v173_is_unique_current_migration(self):
        versions = {}
        for path in MIGRATIONS.glob("V*__*.sql"):
            match = re.match(r"V(\d+)__", path.name)
            if match:
                versions.setdefault(int(match.group(1)), []).append(path.name)
        duplicates = {version: names for version, names in versions.items() if len(names) > 1}
        self.assertEqual({}, duplicates)
        self.assertEqual(V173.name, versions[173][0])

    def test_v173_creates_only_five_inspection_rule_tables(self):
        sql = self._sql()
        for table in (
            "srv_inspection_rule",
            "srv_inspection_rule_revision",
            "srv_inspection_rule_command_revision",
            "srv_inspection_rule_product_type_revision",
            "srv_inspection_rule_security_review",
        ):
            self.assertIn(f"create table `{table}`", sql)
        self.assertEqual(5, sql.count("create table `srv_inspection_rule"))
        self.assertNotIn("srv_inspection_task_rule_snapshot", sql)
        self.assertNotIn("ast_product_type", sql)
        self.assertNotIn("pms_srv_rule", sql)

    def test_v173_contains_tenant_audit_and_optimistic_lock_fields(self):
        for table_sql in self._table_definitions(self._sql()):
            for column in (
                "`version`", "`creator`", "`create_time`", "`updater`",
                "`update_time`", "`deleted`", "`tenant_id`",
            ):
                self.assertIn(column, table_sql)

    def test_v173_places_permanent_name_identity_on_rule(self):
        sql = self._sql()
        rule = self._table(sql, "srv_inspection_rule")
        revision = self._table(sql, "srv_inspection_rule_revision")
        self.assertIn("`rule_name` varchar(128) not null", rule)
        self.assertIn("unique key `uk_srv_inspection_rule_tenant_name` (`tenant_id`, `rule_name`)", rule)
        self.assertIn("`rule_name_snapshot` varchar(128) not null", revision)
        self.assertNotIn("`rule_name` varchar", revision)

    def test_v173_contains_frozen_unique_and_foreign_key_contracts(self):
        sql = self._sql()
        for token in (
            "unique key `uk_srv_inspection_rule_tenant_detection` (`tenant_id`, `detection_id`)",
            "unique key `uk_srv_inspection_rule_tenant_id` (`tenant_id`, `id`)",
            "unique key `uk_srv_inspection_rule_tenant_id_name` (`tenant_id`, `id`, `rule_name`)",
            "unique key `uk_srv_inspection_revision_rule_no` (`tenant_id`, `rule_id`, `revision_no`)",
            "unique key `uk_srv_inspection_revision_current` (`tenant_id`, `rule_id`, `current_published_marker`)",
            "unique key `uk_srv_inspection_revision_tenant_id` (`tenant_id`, `id`)",
            "unique key `uk_srv_inspection_command_key` (`tenant_id`, `revision_id`, `stable_command_key`)",
            "unique key `uk_srv_inspection_command_order` (`tenant_id`, `revision_id`, `execution_order`)",
            "unique key `uk_srv_inspection_product_type` (`tenant_id`, `revision_id`, `product_type_code`)",
            "unique key `uk_srv_inspection_review_reference` (`tenant_id`, `review_reference`)",
            "foreign key (`tenant_id`, `rule_id`, `rule_name_snapshot`)",
            "references `srv_inspection_rule` (`tenant_id`, `id`, `rule_name`)",
            "foreign key (`tenant_id`, `revision_id`)",
        ):
            self.assertIn(token, sql)

    def test_v173_allows_incomplete_draft_revision_fields_and_checks_published_main_fields(self):
        sql = self._sql()
        revision = self._table(sql, "srv_inspection_rule_revision")
        self.assertIn("`rule_name_snapshot` varchar(128) not null", revision)
        for column in (
            "inspection_item", "description", "category_code",
            "category_name_snapshot", "severity_code", "severity_name_snapshot",
            "sort_order", "expected_result_regex", "threshold_data_type",
            "threshold_operator", "threshold_unit",
        ):
            self.assertRegex(revision, rf"`{column}` [^,]+ default null")
        self.assertIn("`threshold_value` decimal(20,6) default null", revision)
        self.assertIn("constraint `chk_srv_inspection_revision_publish_complete` check", revision)
        self.assertIn("`status_code` = 'draft'", revision)
        self.assertIn("`threshold_data_type` = 'number'", revision)

    def test_v173_allows_incomplete_draft_child_rows(self):
        sql = self._sql()
        command = self._table(sql, "srv_inspection_rule_command_revision")
        product = self._table(sql, "srv_inspection_rule_product_type_revision")
        for token in (
            "`stable_command_key` varchar(96) default null",
            "`command_content` text default null",
            "`execution_order` int unsigned default null",
            "`timeout_seconds` tinyint unsigned default null",
            "`continue_on_timeout` bit(1) default null",
        ):
            self.assertIn(token, command)
        for token in (
            "`product_type_code` varchar(64) default null",
            "`product_type_name_snapshot` varchar(128) default null",
        ):
            self.assertIn(token, product)

    def test_v173_contains_state_fact_and_security_review_checks(self):
        sql = self._sql()
        for token in (
            "when `status_code` = 'published' then 1 else null end",
            "check (`status_code` in ('draft', 'published', 'disabled'))",
            "check (`revision_no` > 0)",
            "check (`execution_order` is null or `execution_order` > 0)",
            "check (`timeout_seconds` is null or `timeout_seconds` between 1 and 30)",
            "check (`category_code` is null or `category_code` in ('basic', 'operating_status', 'log', 'business_status', 'redundancy', 'routing', 'security', 'forwarding_channel', 'load_balancing', 'traffic_cleaning'))",
            "check (`severity_code` is null or `severity_code` in ('general', 'severe', 'fatal'))",
            "check (`threshold_data_type` is null or `threshold_data_type` = 'number')",
            "check (`threshold_operator` is null or `threshold_operator` in ('>', '<', '≥', '≤', '=', '≠'))",
            "check (`conclusion_code` in ('passed', 'rejected'))",
            "check (char_length(`content_digest`) = 64 and `content_digest` regexp '^[0-9a-f]{64}$')",
            "constraint `chk_srv_inspection_revision_state_facts` check",
        ):
            self.assertIn(token, sql)

    def test_v173_has_no_destructive_or_cross_owner_writes(self):
        sql = self._sql()
        for token in (
            "drop table", "truncate table", "delete from", "insert into",
            "update `", "system_role_menu",
        ):
            self.assertNotIn(token, sql)

    def test_v174_contains_two_formal_dictionaries(self):
        sql = self._v149()
        self.assertIn("pms_inspection_rule_category", sql)
        self.assertIn("pms_inspection_rule_severity", sql)
        for value in (
            "basic", "operating_status", "log", "business_status", "redundancy",
            "routing", "security", "forwarding_channel", "load_balancing",
            "traffic_cleaning", "general", "severe", "fatal",
        ):
            self.assertIn(f"'{value}'", sql)
        self.assertEqual(13, sql.count("'f-ins-001'"))

    def test_v174_contains_independent_menu_and_six_permissions(self):
        sql = self._v149()
        for permission in (
            "pms:inspection-rule:query",
            "pms:inspection-rule:manage",
            "pms:inspection-rule:security-review",
            "pms:inspection-rule:publish",
            "pms:inspection-rule:disable",
            "pms:inspection-rule:select",
        ):
            self.assertIn(permission, sql)
        self.assertIn("'巡检规则版本'", sql)
        self.assertIn("19265", sql)
        self.assertNotIn("system_role_menu", sql)

    def test_v174_does_not_seed_rules_or_product_types(self):
        sql = self._v149()
        for token in (
            "insert into `srv_inspection_rule",
            "insert into `ast_",
            "insert into `pms_srv_rule`",
            "insert into `system_role`",
        ):
            self.assertNotIn(token, sql)

    def test_v174_fails_closed_on_seed_identity_conflicts(self):
        sql = self._v149()
        self.assertIn(
            "create procedure `fins001_assert_seed_identity`",
            sql,
        )
        self.assertIn("signal sqlstate '45000'", sql)
        for token in (
            "actual.`status` <> expected.`status`",
            "not (actual.`remark` <=> expected.`remark`)",
            "not (actual.`color_type` <=> expected.`color_type`)",
            "not (actual.`css_class` <=> expected.`css_class`)",
            "actual.`sort` <> expected.`sort`",
            "not (actual.`path` <=> expected.`path`)",
            "not (actual.`icon` <=> expected.`icon`)",
            "actual.`visible` <> expected.`visible`",
            "actual.`keep_alive` <> expected.`keep_alive`",
            "actual.`always_show` <> expected.`always_show`",
        ):
            self.assertIn(token, sql)
        menu_preflight = sql.index("f-ins-001 menu identity conflict")
        self.assertGreater(sql.index("insert into `system_dict_type`", menu_preflight), menu_preflight)
        self.assertGreater(sql.index("insert into `system_dict_data`", menu_preflight), menu_preflight)
        self.assertGreater(sql.index("insert into `system_menu`", menu_preflight), menu_preflight)
        self.assertIn(
            "drop procedure if exists `fins001_assert_seed_identity`",
            sql,
        )
        self.assertNotIn("insert ignore", sql)
        self.assertNotIn("on duplicate key update", sql)

    def test_v175_preserves_legacy_and_performs_zero_target_writes(self):
        sql = self._v150()
        self.assertIn("pms_srv_rule", sql)
        self.assertIn("eligible legacy rows = 0", sql)
        self.assertIn("target inserts = 0", sql)
        self.assertIn("legacy updates = 0", sql)
        for token in (
            "insert into `srv_inspection_rule",
            "update `pms_srv_rule`",
            "delete from `pms_srv_rule`",
            "alter table `pms_srv_rule`",
            "content like",
            "regexp",
        ):
            self.assertNotIn(token, sql)

    def test_v175_is_assertion_only_and_leaves_no_procedure(self):
        sql = self._v150()
        self.assertIn("create procedure `fins001_assert_zero_legacy_conversion`", sql)
        self.assertIn("call `fins001_assert_zero_legacy_conversion`", sql)
        self.assertIn("drop procedure if exists `fins001_assert_zero_legacy_conversion`", sql)
        self.assertNotIn("create table", sql)
        self.assertNotIn("alter table", sql)

    @staticmethod
    def _table_definitions(sql):
        return re.findall(
            r"create table `srv_inspection_rule[^`]*` \((.*?)\) engine=innodb",
            sql,
            flags=re.DOTALL,
        )

    @staticmethod
    def _table(sql, table):
        match = re.search(
            rf"create table `{table}` \((.*?)\) engine=innodb",
            sql,
            flags=re.DOTALL,
        )
        if match is None:
            raise AssertionError(f"missing table {table}")
        return match.group(1)

    @staticmethod
    def _sql():
        return V173.read_text(encoding="utf-8-sig").lower()

    @staticmethod
    def _v149():
        return V174.read_text(encoding="utf-8-sig").lower()

    @staticmethod
    def _v150():
        return V175.read_text(encoding="utf-8-sig").lower()


if __name__ == "__main__":
    unittest.main()
