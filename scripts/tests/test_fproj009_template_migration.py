"""PM-03: F-PROJ-009 configuration-only forward migration structure.

Read-only local tests, not database/Flyway or runtime-graph acceptance. Candidate
migration discovery uses the feature suffix and remains valid after renumbering.
"""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TABLES = (
    "proj_delivery_definition_revision",
    "proj_delivery_definition_reference",
    "proj_stage_transition_definition",
)
# SDS09: existing identity + template composition; no runtime table additions.
ADDITIONS = {
    "proj_project_template": [("version", "INT NOT NULL DEFAULT 0")],
    "proj_project_template_revision": [("definition_snapshot", "JSON NULL")],
    "proj_project_template_stage_definition": [
        ("definition_revision_id", "BIGINT NULL"),
        ("start_node", "BIT(1) NULL"),
        ("terminal_node", "BIT(1) NULL"),
        ("work_binding_revision_id", "BIGINT NULL"),
        ("permission_policy_revision_id", "BIGINT NULL"),
        ("completion_rule_revision_id", "BIGINT NULL"),
    ],
    "proj_project_template_task_definition": [
        ("definition_revision_id", "BIGINT NULL"),
        ("work_binding_revision_id", "BIGINT NULL"),
        ("permission_policy_revision_id", "BIGINT NULL"),
        ("completion_rule_revision_id", "BIGINT NULL"),
    ],
    "proj_project_template_milestone_definition": [("definition_revision_id", "BIGINT NULL")],
    "proj_project_template_deliverable_definition": [("definition_revision_id", "BIGINT NULL")],
    "proj_project_template_gate_definition": [("definition_revision_id", "BIGINT NULL")],
}
CREATE = re.compile(
    r"CREATE\s+TABLE\s+`(\w+)`\s*\((.*?)\)\s*"
    r"ENGINE=InnoDB\s+DEFAULT\s+CHARSET=utf8mb4\s+COLLATE=utf8mb4_bin\s*;",
    re.IGNORECASE | re.DOTALL,
)
ALTER = re.compile(r"ALTER\s+TABLE\s+`(\w+)`\s+(.*?);", re.IGNORECASE | re.DOTALL)


def executable_sql(sql: str) -> str:
    if re.search(r"/\*[!+]", sql):
        raise AssertionError("executable comments are not allowed in this migration")
    return re.sub(r"--[^\n]*|/\*.*?\*/", "", sql, flags=re.DOTALL)


def compact(sql: str) -> str:
    return re.sub(r"\s+", "", sql).replace("`", "")


def assert_configuration_structure(case: unittest.TestCase, sql: str, contracts: dict) -> None:
    code = executable_sql(sql)
    creates = list(CREATE.finditer(code))
    case.assertEqual([match[1] for match in creates], list(TABLES))
    for match in creates:
        contract = contracts[match[1]]
        expected = [f"`{name}` {definition}" for name, definition in contract["columns"]]
        expected.extend(contract["constraints"])
        case.assertEqual(compact(match[2]), compact(",".join(expected)), match[1])
    alters = list(ALTER.finditer(code))
    case.assertEqual([match[1] for match in alters], list(ADDITIONS))
    for match in alters:
        expected = [f"ADD COLUMN `{name}` {definition}"
                    for name, definition in ADDITIONS[match[1]]]
        case.assertEqual(compact(match[2]), compact(",".join(expected)), match[1])
    # Whitelist all executable statements: detects data rewrites, extra tables,
    # hidden seeds, root replacements and even ALTERs that modify old columns.
    remaining = ALTER.sub("", CREATE.sub("", code))
    case.assertFalse(remaining.strip(), f"unexpected executable SQL: {remaining}")
    case.assertLess(creates[-1].end(), alters[0].start())


class Fproj009TemplateMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        paths = list((ROOT / "sql/migrations").glob("V*__fproj009_template_configuration.sql"))
        if len(paths) != 1:
            raise AssertionError(f"expected exactly one template migration, found {paths}")
        cls.path = paths[0]
        cls.sql = cls.path.read_text(encoding="utf-8")
        cls.code = executable_sql(cls.sql)
        contract_path = ROOT / "docs/traceability/sds-revision-016-physical-contract.json"
        cls.contracts = json.loads(contract_path.read_text(encoding="utf-8"))["tables"]
        cls.bodies = {match[1]: match[2] for match in CREATE.finditer(cls.code)}

    def test_exact_machine_contract_and_sds_additions_only(self) -> None:
        assert_configuration_structure(self, self.sql, self.contracts)

    def test_owner_requirement_and_tenant_scoped_unique_keys(self) -> None:
        self.assertIn("Owner: PROJ", self.sql)
        self.assertIn("PM-03", self.sql)
        for table in TABLES:
            with self.subTest(table=table):
                self.assertEqual(self.contracts[table]["owner"], "PROJ")
                self.assertIn("PM-03", self.contracts[table]["requirementIds"])
                self.assertIn("`tenant_id` BIGINT NOT NULL", self.bodies[table])
                keys = re.findall(r"UNIQUE KEY `\w+`\s*\(([^)]+)\)", self.bodies[table])
                self.assertTrue(keys)
                for key in keys:
                    self.assertTrue(key.startswith("`tenant_id`,"), key)
                    self.assertNotIn("deleted", key)
        self.assertEqual(self.code.count("COLLATE=utf8mb4_bin"), 3)

    def test_definition_identity_and_null_draft_publication(self) -> None:
        body = self.bodies[TABLES[0]]
        self.assertIn(compact(
            "UNIQUE KEY uk_pdd_revision (tenant_id, definition_kind, definition_code, revision_no)"
        ), compact(body))
        self.assertIn("`published_at` DATETIME(6) NULL", body)
        self.assertIn("`disabled_at` DATETIME(6) NULL", body)
        self.assertIn(compact(
            "CHECK ((revision_state='DRAFT' AND published_at IS NULL) OR "
            "(revision_state='PUBLISHED' AND published_at IS NOT NULL))"
        ), compact(body))
        self.assertIn("CHECK (JSON_TYPE(payload) = 'OBJECT')", body)
        self.assertIn("schema_version > 0", body)

    def test_definition_reference_foreign_keys_include_both_tenants(self) -> None:
        body = self.bodies[TABLES[1]]
        foreign_keys = re.findall(
            r"FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES\s*`(\w+)`\s*\(([^)]+)\)", body
        )
        self.assertEqual([(compact(src), table, compact(dst)) for src, table, dst in foreign_keys], [
            ("tenant_id,owner_revision_id", TABLES[0], "tenant_id,id"),
            ("tenant_id,target_revision_id", TABLES[0], "tenant_id,id"),
        ])
        self.assertIn(compact("UNIQUE KEY uk_pdd_tenant_id (tenant_id, id)"),
                      compact(self.bodies[TABLES[0]]))
        self.assertIn("CHECK (owner_revision_id <> target_revision_id)", body)
        self.assertNotRegex(body, r"(?i)\b(CASCADE|SET NULL)\b")

    def test_default_branch_unique_generated_marker(self) -> None:
        body = self.bodies[TABLES[2]]
        self.assertIn(compact(
            "`default_marker` TINYINT GENERATED ALWAYS AS "
            "(CASE WHEN is_default=1 THEN 1 ELSE NULL END) STORED"
        ), compact(body))
        self.assertIn(compact(
            "UNIQUE KEY uk_std_default (tenant_id, template_revision_id, from_stage_code, default_marker)"
        ), compact(body))
        self.assertIn("CHECK (is_default IN (0,1) AND revision_no > 0)", body)
        self.assertIn("CHECK (from_stage_code <> to_stage_code)", body)
        self.assertIn("`condition_rule_revision_id` BIGINT NULL", body)

    def test_existing_identity_gets_only_technical_cas_version(self) -> None:
        alters = {match[1]: match[2] for match in ALTER.finditer(self.code)}
        self.assertEqual(compact(alters["proj_project_template"]),
                         compact("ADD COLUMN version INT NOT NULL DEFAULT 0"))

    def test_historical_details_remain_null_without_guessed_graph_or_references(self) -> None:
        alters = {match[1]: match[2] for match in ALTER.finditer(self.code)}
        for table, columns in ADDITIONS.items():
            if table == "proj_project_template":
                continue
            with self.subTest(table=table):
                self.assertNotRegex(alters[table], r"(?i)\b(DEFAULT|NOT NULL|MODIFY|CHANGE)\b")
                for name, definition in columns:
                    self.assertIn(f"ADD COLUMN `{name}` {definition}", alters[table])
        self.assertNotRegex(self.code, r"(?i)\b(sort_order|process_definition_version|LEGACY|GRAPH)\b")

    def test_no_runtime_tables_backfill_seed_or_history_overwrite(self) -> None:
        self.assertEqual(set(self.bodies), set(TABLES))
        self.assertNotRegex(self.code, r"(?i)\b(DROP|TRUNCATE|DELETE|REPLACE|INSERT|RENAME)\b")
        self.assertNotRegex(self.code, r"(?im)^\s*UPDATE\b")
        self.assertNotIn("proj_project_stage_transition", self.code)
        self.assertNotIn("proj_project_stage_execution_contract", self.code)
        self.assertNotIn("proj_project_task_execution_contract", self.code)
        self.assertIn("Q-FPROJ009-001", self.sql)

    def test_structure_guard_rejects_missing_tenant_fk_and_weakened_constraints(self) -> None:
        mutations = [
            ("(`tenant_id`, `definition_kind`, `definition_code`, `revision_no`)",
             "(`definition_kind`, `definition_code`, `revision_no`)"),
            ("FOREIGN KEY (`tenant_id`,`owner_revision_id`)", "FOREIGN KEY (`owner_revision_id`)"),
            ("FOREIGN KEY (`tenant_id`,`target_revision_id`)", "FOREIGN KEY (`target_revision_id`)"),
            ("REFERENCES `proj_delivery_definition_revision` (`tenant_id`,`id`)",
             "REFERENCES `proj_delivery_definition_revision` (`id`)"),
            ("THEN 1 ELSE NULL END", "THEN 1 ELSE 0 END"),
            ("`from_stage_code`, `default_marker`", "`default_marker`"),
            ("revision_state='DRAFT' AND published_at IS NULL", "revision_state='DRAFT'"),
            ("`published_at` DATETIME(6) NULL", "`published_at` DATETIME(6) NOT NULL"),
            ("ADD COLUMN `definition_snapshot` JSON NULL", "ADD COLUMN `definition_snapshot` JSON NOT NULL"),
            ("ADD COLUMN `start_node` BIT(1) NULL", "ADD COLUMN `start_node` BIT(1) NULL DEFAULT b'0'"),
            ("ADD COLUMN `definition_revision_id` BIGINT NULL", "ADD COLUMN `definition_revision_id` BIGINT UNSIGNED NULL"),
            ("ADD COLUMN `version` INT NOT NULL DEFAULT 0", "ADD COLUMN `version` INT NULL"),
        ]
        for old, new in mutations:
            with self.subTest(mutation=old):
                self.assertIn(old, self.sql)
                with self.assertRaises(AssertionError):
                    assert_configuration_structure(self, self.sql.replace(old, new), self.contracts)

    def test_structure_guard_rejects_history_rewrite_and_extra_runtime_carriers(self) -> None:
        for statement in (
            "UPDATE proj_project_template_revision SET definition_snapshot=JSON_OBJECT();",
            "UPDATE proj_project_template SET status='LEGACY';",
            "TRUNCATE TABLE proj_project_template_stage_definition;",
            "DROP TABLE proj_project_template_revision;",
            "ALTER TABLE proj_project_template_revision DROP COLUMN process_definition_version;",
            "INSERT INTO proj_delivery_definition_revision (id) VALUES (1);",
            "CREATE TABLE proj_project_stage_transition (id BIGINT);",
            "CREATE TABLE proj_project_stage_execution_contract (id BIGINT);",
            "/*!80000 UPDATE proj_project_template SET status='GRAPH' */;",
        ):
            with self.subTest(statement=statement), self.assertRaises(AssertionError):
                assert_configuration_structure(self, self.sql + "\n" + statement, self.contracts)


if __name__ == "__main__":
    unittest.main()
