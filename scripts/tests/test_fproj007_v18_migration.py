import hashlib
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "sql" / "migrations"
V88 = MIGRATIONS / "V88__fproj007_project_task_runtime.sql"
V89 = MIGRATIONS / "V89__fproj007_project_task_seed.sql"
PREVIOUS_MIGRATIONS_SHA256 = (
    "7bbe77ab67df49a32019056caa817868b6f9d94fa3cecc465b98a1e739725fd3"
)


class FProj007V18MigrationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.v88 = V88.read_text(encoding="utf-8")
        cls.v89 = V89.read_text(encoding="utf-8")

    def test_v88_and_v89_are_forward_migrations(self) -> None:
        self.assertTrue(V88.is_file())
        self.assertTrue(V89.is_file())
        self.assertEqual(88, int(re.match(r"V(\d+)", V88.name).group(1)))
        self.assertEqual(89, int(re.match(r"V(\d+)", V89.name).group(1)))

    def test_v1_through_v87_remain_unchanged(self) -> None:
        digest = hashlib.sha256()
        previous = sorted(
            (
                path
                for path in MIGRATIONS.glob("V*__*.sql")
                if int(re.match(r"V(\d+)", path.name).group(1)) <= 87
            ),
            key=lambda path: int(re.match(r"V(\d+)", path.name).group(1)),
        )
        self.assertEqual(87, len(previous))
        for path in previous:
            digest.update(path.name.encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())
            digest.update(b"\0")
        self.assertEqual(PREVIOUS_MIGRATIONS_SHA256, digest.hexdigest())

    def test_v88_creates_exactly_six_contract_tables(self) -> None:
        created = set(
            re.findall(
                r"CREATE TABLE IF NOT EXISTS `([^`]+)`",
                self.v88,
                flags=re.IGNORECASE,
            )
        )
        self.assertEqual(
            {
                "proj_task_tree_path",
                "proj_task_dependency",
                "proj_project_task_assignment",
                "proj_project_task_completion_evaluation",
                "proj_task_state_machine_revision",
                "proj_task_state_transition",
            },
            created,
        )

    def test_project_and_task_runtime_fields_are_forward_additive(self) -> None:
        for field in ("task_tree_version", "task_progress_version"):
            with self.subTest(field=field):
                self.assertIn(f"ADD COLUMN `{field}`", self.v88)
        for field in (
            "parent_task_id",
            "root_task_id",
            "tree_depth",
            "business_level_code",
            "milestone_id",
            "plan_start_time",
            "plan_end_time",
            "actual_start_time",
            "actual_end_time",
            "progress",
            "state_machine_revision_id",
        ):
            with self.subTest(field=field):
                self.assertIn(f"ADD COLUMN `{field}`", self.v88)
        for forbidden in ("level_1_task_id", "level_2_task_id", "level_3_task_id", "max_tree_depth"):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, self.v88.lower())

    def test_tree_backfill_uses_stable_same_project_relationship_and_closure(self) -> None:
        self.assertIn("parent.`tenant_id` = child.`tenant_id`", self.v88)
        self.assertIn("parent.`project_id` = child.`project_id`", self.v88)
        self.assertIn("parent.`task_code` = child.`parent_task_code`", self.v88)
        self.assertIn("WITH RECURSIVE `task_tree`", self.v88)
        self.assertIn("WITH RECURSIVE `ancestor_paths`", self.v88)
        self.assertIn("`root_task_id` IS NULL OR `tree_depth` IS NULL", self.v88)
        self.assertIn("UNIQUE KEY `uk_proj_task_tree_path`", self.v88)

    def test_state_machine_is_four_linear_plus_four_cancel_transitions(self) -> None:
        expected = {
            ("PENDING_ASSIGN", "ASSIGN", "PENDING_START"),
            ("PENDING_START", "START", "IN_PROGRESS"),
            ("IN_PROGRESS", "SUBMIT", "PENDING_ACCEPT"),
            ("PENDING_ACCEPT", "COMPLETE", "DONE"),
            ("PENDING_ASSIGN", "CANCEL", "CLOSED"),
            ("PENDING_START", "CANCEL", "CLOSED"),
            ("IN_PROGRESS", "CANCEL", "CLOSED"),
            ("PENDING_ACCEPT", "CANCEL", "CLOSED"),
        }
        for source, action, target in expected:
            with self.subTest(source=source, action=action, target=target):
                self.assertRegex(
                    self.v89,
                    re.compile(
                        rf"SELECT\s+'{source}'(?:\s+AS\s+`from_status_code`)?\s*,\s*"
                        rf"'{action}'(?:\s+AS\s+`action_code`)?\s*,\s*"
                        rf"'{target}'(?:\s+AS\s+`to_status_code`)?"
                    ),
                )
        seeded_sources = re.findall(
            r"SELECT\s+'(PENDING_ASSIGN|PENDING_START|IN_PROGRESS|PENDING_ACCEPT)'"
            r"(?:\s+AS\s+`from_status_code`)?\s*,\s*"
            r"'(ASSIGN|START|SUBMIT|COMPLETE|CANCEL)'",
            self.v89,
        )
        self.assertEqual(8, len(seeded_sources))

    def test_existing_tasks_freeze_same_tenant_published_revision_without_null(self) -> None:
        self.assertIn("revision.`tenant_id` = task.`tenant_id`", self.v89)
        self.assertIn("task.`state_machine_revision_id` = revision.`id`", self.v89)
        self.assertIn("task.`state_machine_revision_id` IS NULL OR revision.`id` IS NULL", self.v89)
        self.assertIn("MODIFY COLUMN `state_machine_revision_id` BIGINT NOT NULL", self.v89)
        self.assertIn("FOREIGN KEY (`tenant_id`, `state_machine_revision_id`)", self.v89)

    def test_task_native_rule_correction_is_precisely_scoped(self) -> None:
        self.assertEqual(2, self.v88.count("'$.requiredStatus', 'DONE'"))
        self.assertEqual(2, self.v88.count("`work_binding_type_code` = 'TASK_NATIVE'"))
        self.assertEqual(2, self.v88.count("`completion_rule_type_code` = 'TASK_NATIVE_STATUS'"))
        self.assertEqual(2, self.v88.count("'$.requiredStatus')) = 'COMPLETED'"))
        self.assertNotIn("UPDATE `pms_project_task`", self.v88 + self.v89)
        self.assertNotRegex(self.v88 + self.v89, r"(?i)CREATE\s+TRIGGER")

    def test_platform_audit_and_legacy_tables_are_not_redefined(self) -> None:
        platform_migrations = "\n".join(
            path.read_text(encoding="utf-8")
            for path in MIGRATIONS.glob("V*__*.sql")
        )
        self.assertIn("CREATE TABLE IF NOT EXISTS `plt_operation_audit`", platform_migrations)
        self.assertNotIn("plt_audit_record", self.v88 + self.v89)
        self.assertNotRegex(self.v88 + self.v89, r"(?i)ALTER\s+TABLE\s+`pms_project_task`")
        self.assertNotRegex(self.v88 + self.v89, r"(?i)CREATE\s+TABLE.*`pms_project_task")

    def test_permissions_are_stable_and_delete_is_retired_without_role_grants(self) -> None:
        for permission in (
            "pms:project-task:query",
            "pms:project-task:create",
            "pms:project-task:update",
            "pms:project-task:move",
            "pms:project-task:assign",
            "pms:project-task:execute",
            "pms:project-task:complete",
            "pms:project-task-state:manage",
        ):
            with self.subTest(permission=permission):
                self.assertIn(permission, self.v89)
        self.assertIn("WHERE `id` = 18033", self.v89)
        self.assertIn("WHERE `menu_id` = 18033", self.v89)
        self.assertNotRegex(self.v89, r"(?i)INSERT\s+(?:IGNORE\s+)?INTO\s+`system_role_menu`")


if __name__ == "__main__":
    unittest.main()
