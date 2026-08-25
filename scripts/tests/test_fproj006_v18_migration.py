import hashlib
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V86 = MIGRATIONS / "V86__fproj006_project_governance_foundation.sql"
V87 = MIGRATIONS / "V87__fproj006_project_governance_seed.sql"
PREVIOUS_MIGRATIONS_SHA256 = (
    "a0c5aee57dfb759f0b8f97e824b63a839260eedf3c454d5e0db8380f32b47c88"
)


class FProj006V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v86 = V86.read_text(encoding="utf-8")
        cls.v87 = V87.read_text(encoding="utf-8")
        cls.snapshot_table = cls.v86.split(
            "CREATE TABLE IF NOT EXISTS `proj_project_stage_snapshot` (", 1
        )[1].split(") ENGINE=InnoDB", 1)[0]

    def test_v86_and_v87_are_forward_migrations(self) -> None:
        self.assertTrue(V86.is_file())
        self.assertTrue(V87.is_file())
        self.assertEqual(86, int(V86.name[1:3]))
        self.assertEqual(87, int(V87.name[1:3]))

    def test_v1_through_v85_remain_unchanged(self) -> None:
        digest = hashlib.sha256()
        previous = sorted(
            (
                path
                for path in MIGRATIONS.glob("V*__*.sql")
                if int(re.match(r"V(\d+)", path.name).group(1)) <= 85
            ),
            key=lambda path: int(re.match(r"V(\d+)", path.name).group(1)),
        )
        self.assertEqual(85, len(previous))
        for path in previous:
            digest.update(path.name.encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())
            digest.update(b"\0")
        self.assertEqual(PREVIOUS_MIGRATIONS_SHA256, digest.hexdigest())

    def test_shared_snapshot_key_is_preserved(self) -> None:
        self.assertRegex(
            self.snapshot_table,
            re.compile(
                r"UNIQUE KEY `uk_proj_stage_snapshot_project_stage_no`\s*"
                r"\(`tenant_id`, `project_id`, `stage_code`, `snapshot_no`\)"
            ),
        )
        self.assertIn(
            "UNIQUE KEY `uk_proj_stage_snapshot_operation` "
            "(`tenant_id`, `operation_id`)",
            self.snapshot_table,
        )

    def test_pm10_additive_fields_are_physically_nullable(self) -> None:
        fields = (
            "operation_type",
            "before_stage",
            "after_stage",
            "before_lifecycle_status",
            "after_lifecycle_status",
            "before_assignment_status",
            "after_assignment_status",
            "reason_code",
            "reason_detail",
            "reassignment_requirement",
            "business_basis",
            "legacy_items_json",
            "guard_snapshot_json",
            "tree_version",
            "provider_facts_json",
            "related_snapshot_id",
            "operation_id",
            "operator_user_id",
            "operated_at",
        )
        for field in fields:
            with self.subTest(field=field):
                definition = re.search(
                    rf"^\s*`{field}`\s+[^,]+,?$",
                    self.snapshot_table,
                    re.MULTILINE,
                )
                self.assertIsNotNone(definition)
                self.assertIn(" NULL", definition.group(0))
                self.assertNotIn("NOT NULL", definition.group(0))

    def test_platform_audit_contract_is_reused(self) -> None:
        platform_migrations = "\n".join(
            path.read_text(encoding="utf-8")
            for path in MIGRATIONS.glob("V*__*.sql")
        )
        self.assertIn("CREATE TABLE IF NOT EXISTS `plt_operation_audit`", platform_migrations)
        self.assertNotIn("plt_audit_record", self.v86 + self.v87)

    def test_no_parallel_governance_fact_tables_are_created(self) -> None:
        migration = (self.v86 + self.v87).lower()
        for forbidden in (
            "proj_project_governance_action",
            "proj_project_governance_history",
            "proj_project_governance_work_order",
            "proj_project_governance_impact",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, migration)

    def test_seed_uses_stable_permissions_without_role_grants(self) -> None:
        for permission in (
            "pms:project:governance:query",
            "pms:project:rollback",
            "pms:project:close",
            "pms:project:reopen",
        ):
            with self.subTest(permission=permission):
                self.assertIn(permission, self.v87)
        self.assertIn("pms_project_governance_reason", self.v87)
        self.assertNotRegex(self.v87, r"(?i)INSERT\s+(?:IGNORE\s+)?INTO\s+`system_role_menu`")
        self.assertIn("WHERE `id` IN (19158, 19159, 19160, 19161, 19162)", self.v87)


if __name__ == "__main__":
    unittest.main()
