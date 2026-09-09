"""PM-03 / PM-11: F-PLT-003 forward SQL structure, not MySQL/Flyway acceptance.

The filename is located by feature suffix so master may renumber the candidate.
Only local SQL and the authoritative machine contract are read; no DB is opened.
"""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TABLE = "plt_business_view_revision"
CONTRACT = ROOT / "docs/traceability/sds-revision-016-physical-contract.json"
CREATE = re.compile(
    r"CREATE\s+TABLE\s+`(\w+)`\s*\((.*?)\)\s*"
    r"ENGINE=InnoDB\s+DEFAULT\s+CHARSET=utf8mb4\s+COLLATE=utf8mb4_bin\s*;",
    re.IGNORECASE | re.DOTALL,
)


def executable_sql(sql: str) -> str:
    # Do not let a comment satisfy a required constraint or conceal executable SQL.
    if re.search(r"/\*[!+]", sql):
        raise AssertionError("executable comments are not allowed in this migration")
    return re.sub(r"--[^\n]*|/\*.*?\*/", "", sql, flags=re.DOTALL)


def compact(sql: str) -> str:
    return re.sub(r"\s+", "", sql).replace("`", "")


def assert_registry_contract(case: unittest.TestCase, sql: str, contract: dict) -> None:
    creates = list(CREATE.finditer(executable_sql(sql)))
    case.assertEqual([match[1] for match in creates], [TABLE])
    expected = [f"`{name}` {definition}" for name, definition in contract["columns"]]
    expected.extend(contract["constraints"])
    case.assertEqual(compact(creates[0][2]), compact(",".join(expected)))


def assert_safe_statements(case: unittest.TestCase, sql: str) -> None:
    statements = [part.strip() for part in executable_sql(sql).split(";") if part.strip()]
    case.assertEqual(len(statements), 2, "only registry creation and menu insertion allowed")
    case.assertIsNotNone(CREATE.fullmatch(statements[0] + ";"))
    case.assertRegex(statements[1], r"(?is)^INSERT\s+INTO\s+`system_menu`\s*\(")
    case.assertNotRegex(statements[1], r"(?i)\b(IGNORE|REPLACE|UPDATE|DELETE|SELECT)\b")
    case.assertNotRegex(executable_sql(sql), r"(?i)\b(system_role_menu|system_role)\b")


class Fplt003BusinessViewMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        paths = list((ROOT / "sql/migrations").glob("V*__fplt003_business_view_registry.sql"))
        if len(paths) != 1:
            raise AssertionError(f"expected exactly one registry migration, found {paths}")
        cls.path = paths[0]
        cls.sql = cls.path.read_text(encoding="utf-8")
        cls.code = executable_sql(cls.sql)
        cls.contract = json.loads(CONTRACT.read_text(encoding="utf-8"))["tables"][TABLE]

    def test_exact_machine_contract_including_common_fields(self) -> None:
        assert_registry_contract(self, self.sql, self.contract)

    def test_owner_and_requirement_traceability(self) -> None:
        self.assertEqual(self.contract["owner"], "PLT")
        self.assertEqual(set(self.contract["requirementIds"]), {"PM-03", "PM-11"})
        for marker in ("Owner: PLT", "PM-03", "PM-11"):
            self.assertIn(marker, self.sql)
        self.assertIn("`owner_context` VARCHAR(32) NOT NULL", self.code)
        # Entity ownership is independent of view source and component location.
        for field in ("entity_type", "component_key", "component_version", "context_schema",
                      "supported_actions", "query_provider_key", "command_provider_key",
                      "permission_provider_key"):
            self.assertRegex(self.code, rf"`{field}`\s+[^\n]+NOT NULL")

    def test_identity_is_tenant_scoped_and_not_reusable_after_soft_delete(self) -> None:
        self.assertIn(compact(
            "UNIQUE KEY uk_bvr_identity (tenant_id, entity_type, view_key, revision_no)"
        ), compact(self.code))
        self.assertIn("COLLATE=utf8mb4_bin", self.code)
        self.assertNotIn("FOREIGN KEY", self.code)

    def test_view_source_form_combinations_are_checked(self) -> None:
        self.assertIn(compact(
            "CHECK ((view_source='PAGE' AND dynamic_form_revision_id IS NULL) OR "
            "(view_source='DYNAMIC_FORM' AND dynamic_form_revision_id IS NOT NULL "
            "AND dynamic_form_revision_id > 0))"
        ), compact(self.code))
        self.assertIn("`view_source` VARCHAR(16) NOT NULL", self.code)
        self.assertIn("`dynamic_form_revision_id` BIGINT NULL", self.code)

    def test_null_draft_and_disabled_publication_order(self) -> None:
        self.assertIn("`published_at` DATETIME(6) NULL", self.code)
        self.assertIn("`disabled_at` DATETIME(6) NULL", self.code)
        self.assertIn(compact(
            "CHECK (disabled_at IS NULL OR (published_at IS NOT NULL "
            "AND disabled_at >= published_at))"
        ), compact(self.code))

    def test_no_history_rewrite_provider_seeds_or_role_grants(self) -> None:
        assert_safe_statements(self, self.sql)
        self.assertNotRegex(self.code, r"(?i)\b(DROP|TRUNCATE|ALTER|REPLACE|DELETE)\b")

    def test_only_approved_page_and_three_action_menus(self) -> None:
        rows = re.findall(
            r"\((19980[0-3]),\s*'([^']+)',\s*'pms:business-view:([^']+)',"
            r"\s*(\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*'([^']*)',"
            r"\s*(NULL|'[^']*'),\s*(NULL|'[^']*'),\s*0,\s*b'1',\s*b'1',\s*b'1',"
            r"\s*'fplt003-seed',\s*NOW\(\),\s*'fplt003-seed',\s*NOW\(\),\s*b'0'\)",
            self.code,
        )
        self.assertEqual([(r[0], r[2], r[3], r[5]) for r in rows], [
            ("199800", "query", "2", "19271"),
            ("199801", "manage", "3", "199800"),
            ("199802", "publish", "3", "199800"),
            ("199803", "disable", "3", "199800"),
        ])
        self.assertEqual(rows[0][6], "business-view")
        self.assertEqual(rows[0][8], "'pms/platform/business-view/index'")
        self.assertEqual(rows[0][9], "'PmsBusinessView'")
        for row in rows[1:]:
            self.assertEqual(row[6:10], ("", "", "NULL", "NULL"))
        self.assertEqual(re.findall(r"'pms:business-view:([^']+)'", self.code),
                         ["query", "manage", "publish", "disable"])
        # No additional menu rows hidden behind unrelated permission strings.
        values = self.code.split("VALUES", 1)[1]
        remaining = values
        for match in re.finditer(r"\(19980[0-3],.*?NOW\(\),\s*b'0'\)", values, re.DOTALL):
            remaining = remaining.replace(match[0], "", 1)
        self.assertFalse(remaining.strip(" \r\n\t,;"))

    def test_contract_guard_rejects_weakened_identity_source_and_history_fields(self) -> None:
        mutations = [
            ("(`tenant_id`, `entity_type`, `view_key`, `revision_no`)",
             "(`entity_type`, `view_key`, `revision_no`)"),
            ("AND dynamic_form_revision_id IS NOT NULL", ""),
            ("AND dynamic_form_revision_id > 0", ""),
            ("view_source='PAGE' AND dynamic_form_revision_id IS NULL", "view_source='PAGE'"),
            ("published_at IS NOT NULL AND disabled_at >= published_at", "1=1"),
            ("`published_at` DATETIME(6) NULL", "`published_at` DATETIME(6) NOT NULL"),
            ("`owner_context` VARCHAR(32) NOT NULL,", ""),
            ("`version` INT NOT NULL DEFAULT 0", "`version` BIGINT NOT NULL DEFAULT 0"),
            ("`deleted` BIT(1) NOT NULL DEFAULT b'0',", "`deleted` BIT(1) NOT NULL DEFAULT b'0',\n`extra` INT,")
        ]
        for old, new in mutations:
            with self.subTest(mutation=old):
                self.assertIn(old, self.sql)
                with self.assertRaises(AssertionError):
                    assert_registry_contract(self, self.sql.replace(old, new), self.contract)

    def test_safety_guard_rejects_destructive_or_fabricated_data_statements(self) -> None:
        for statement in (
            "UPDATE plt_business_view_revision SET published_at=NOW();",
            "INSERT INTO plt_business_view_revision (id) VALUES (1);",
            "INSERT INTO system_role_menu (role_id, menu_id) VALUES (1, 199800);",
            "DROP TABLE plt_business_view_revision;",
            "TRUNCATE TABLE plt_business_view_revision;",
            "/*!80000 DELETE FROM system_menu */;",
        ):
            with self.subTest(statement=statement), self.assertRaises(AssertionError):
                assert_safe_statements(self, self.sql + "\n" + statement)


if __name__ == "__main__":
    unittest.main()
