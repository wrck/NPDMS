from __future__ import annotations

import sys
import subprocess
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import validate_delivery_units as delivery_units

from validate_delivery_units import (
    DeliveryUnit,
    parse_delivery_unit,
    path_matches,
    render_index,
    validate_changed_paths,
    validate_delivery_units,
)


FIELDS = {
    "DU状态": "CLAIMED",
    "DU类型": "TASK",
    "Feature协调": "F-SOL-003=TASK_COORDINATED",
    "Task范围": "Task 1",
    "Owner": "worker-1",
    "分支": "codex/f-sol-003",
    "Worktree": "M:/worktrees/f-sol-003",
    "认领基线": "1" * 40,
    "认领提交": "2" * 40,
    "修改边界": "pms-module-engineering/src/**",
    "串行资源": "NONE",
    "旧功能范围": "LegacyRequirementAnalysisFixedSections",
    "验证": "python test.py",
    "集成记录": "NONE",
}


class DeliveryUnitValidatorTest(unittest.TestCase):
    @staticmethod
    def git(repository: Path, *args: str) -> str:
        return subprocess.run(
            ["git", *args], cwd=repository, check=True, capture_output=True,
            text=True, encoding="utf-8",
        ).stdout.strip()

    def initialize_repository(self, repository: Path) -> None:
        self.git(repository, "init", "-b", "master")
        self.git(repository, "config", "user.name", "test")
        self.git(repository, "config", "user.email", "test@example.invalid")

    def unit(self, name: str, **changes: str) -> DeliveryUnit:
        fields = {**FIELDS, **changes}
        return DeliveryUnit(Path(f"tasks/delivery-units/{name}.md"), name, fields)

    def test_parse_multi_feature_record(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "DU-20260901-001.md"
            lines = ["# DU"] + [f"> {key}：`{value}`" for key, value in FIELDS.items()]
            lines[3] = "> Feature协调：`F-CUT-002=FEATURE_EXCLUSIVE;F-CUT-003=TASK_COORDINATED`"
            path.write_text("\n".join(lines), encoding="utf-8")

            unit = parse_delivery_unit(path)

            self.assertEqual(
                {"F-CUT-002": "FEATURE_EXCLUSIVE", "F-CUT-003": "TASK_COORDINATED"},
                unit.feature_modes,
            )

    def test_feature_exclusive_conflicts_with_second_active_claim(self) -> None:
        first = self.unit("DU-1", **{"Feature协调": "F-SOL-003=FEATURE_EXCLUSIVE"})
        second = self.unit(
            "DU-2",
            **{
                "Feature协调": "F-SOL-003=TASK_COORDINATED",
                "分支": "codex/f-sol-003-2",
                "修改边界": "scripts/tests/**",
            },
        )

        errors = validate_delivery_units(Path.cwd(), [first, second], check_git=False)

        self.assertTrue(any("FEATURE_EXCLUSIVE conflicts" in error for error in errors), errors)

    def test_overlapping_active_boundaries_fail(self) -> None:
        first = self.unit("DU-1")
        second = self.unit(
            "DU-2",
            **{
                "Feature协调": "F-SOL-002=TASK_COORDINATED",
                "分支": "codex/f-sol-002",
                "修改边界": "pms-module-engineering/src/main/**",
            },
        )

        errors = validate_delivery_units(Path.cwd(), [first, second], check_git=False)

        self.assertTrue(any("write boundary conflict" in error for error in errors), errors)

    def test_unclaimed_branch_changes_fail(self) -> None:
        errors = validate_changed_paths(
            [], branch="codex/unclaimed", changed_paths=["pms-module-project/A.java"], legacy_cutovers=[]
        )

        self.assertEqual(["branch codex/unclaimed has changes but no active Delivery Unit claim"], errors)

    def test_deprecated_path_requires_explicit_scope(self) -> None:
        unit = self.unit("DU-1", **{"旧功能范围": "NONE"})
        cutovers = [
            {
                "legacyKey": "LegacyRequirementAnalysisFixedSections",
                "legacyPaths": ["pms-module-engineering/src/**"],
            }
        ]

        errors = validate_changed_paths(
            [unit],
            branch="codex/f-sol-003",
            changed_paths=["pms-module-engineering/src/A.java"],
            legacy_cutovers=cutovers,
        )

        self.assertTrue(any("requires explicit legacy scope" in error for error in errors), errors)

    def test_rendered_index_is_a_projection(self) -> None:
        rendered = render_index([self.unit("DU-1")])

        self.assertIn("只投影认领、写边界和集成状态", rendered)
        self.assertIn("[DU-1](DU-1.md)", rendered)

    def test_path_matching_supports_recursive_boundaries(self) -> None:
        self.assertTrue(path_matches("pms-module-project/src/**", "pms-module-project/src/A.java"))
        self.assertFalse(path_matches("pms-module-project/src/**", "pms-module-project/pom.xml"))

    def test_cli_rejects_untracked_files_on_unclaimed_branch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self.initialize_repository(repository)
            (repository / "tasks/delivery-units").mkdir(parents=True)
            inventory = repository / "tasks/implementation-baseline-inventory.json"
            inventory.write_text('{"legacyCutovers": []}\n', encoding="utf-8")
            self.git(repository, "add", "tasks/implementation-baseline-inventory.json")
            self.git(repository, "commit", "-m", "baseline")
            self.git(repository, "switch", "-c", "codex/unclaimed")
            (repository / "NewService.java").write_text("class NewService {}\n", encoding="utf-8")

            output = StringIO()
            with patch.object(sys, "argv", ["validate_delivery_units.py", "--repository",
                                            str(repository), "--base-ref", "master"]), redirect_stdout(output):
                result = delivery_units.main()

            self.assertEqual(1, result)
            self.assertIn("no active Delivery Unit claim", output.getvalue())

    def test_changed_paths_include_staged_untracked_and_both_rename_paths(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)
            self.initialize_repository(repository)
            for folder in ("allowed", "outside", "ignored"):
                (repository / folder).mkdir()
            (repository / ".gitignore").write_text("ignored/\n", encoding="utf-8")
            (repository / "allowed/中文.txt").write_text("before\n", encoding="utf-8")
            (repository / "outside/original.txt").write_text("rename me\n", encoding="utf-8")
            self.git(repository, "add", ".gitignore", "allowed/中文.txt", "outside/original.txt")
            self.git(repository, "commit", "-m", "baseline")
            (repository / "allowed/中文.txt").write_text("after\n", encoding="utf-8")
            (repository / "allowed/staged.txt").write_text("staged\n", encoding="utf-8")
            self.git(repository, "add", "allowed/staged.txt")
            self.git(repository, "mv", "outside/original.txt", "allowed/renamed.txt")
            (repository / "allowed/untracked.txt").write_text("new\n", encoding="utf-8")
            (repository / "outside/新文件.md").write_text("new outside\n", encoding="utf-8")
            (repository / "ignored/cache.tmp").write_text("ignored\n", encoding="utf-8")

            changed = delivery_units._changed_paths(repository, "master")

            self.assertEqual(sorted([
                "allowed/中文.txt", "allowed/staged.txt", "allowed/renamed.txt",
                "allowed/untracked.txt", "outside/original.txt", "outside/新文件.md",
            ]), changed)
            errors = validate_changed_paths(
                [self.unit("DU-1", **{"修改边界": "allowed/**"})],
                branch="codex/f-sol-003", changed_paths=changed, legacy_cutovers=[],
            )
            self.assertEqual([
                "changed path is outside claimed boundaries: outside/original.txt",
                "changed path is outside claimed boundaries: outside/新文件.md",
            ], errors)

    def test_self_claim_requires_branch_to_contain_activation_commit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository = Path(directory)

            def git(*args: str) -> str:
                result = subprocess.run(
                    ["git", *args],
                    cwd=repository,
                    check=True,
                    capture_output=True,
                    text=True,
                    encoding="utf-8",
                )
                return result.stdout.strip()

            git("init", "-b", "master")
            git("config", "user.name", "test")
            git("config", "user.email", "test@example.invalid")
            (repository / "tasks/features").mkdir(parents=True)
            (repository / "tasks/features/F-SOL-003.md").write_text("# task\n", encoding="utf-8")
            unit_path = repository / "tasks/delivery-units/DU-TEST.md"
            unit_path.parent.mkdir(parents=True)

            planned = {**FIELDS, "DU状态": "PLANNED", "认领提交": "SELF"}
            unit_path.write_text(
                "# DU\n" + "\n".join(f"> {key}：`{value}`" for key, value in planned.items()),
                encoding="utf-8",
            )
            git("add", ".")
            git("commit", "-m", "plan claim")
            planned_commit = git("rev-parse", "HEAD")
            git("branch", "codex/f-sol-003", planned_commit)

            claimed = {**planned, "DU状态": "CLAIMED"}
            unit_path.write_text(
                "# DU\n" + "\n".join(f"> {key}：`{value}`" for key, value in claimed.items()),
                encoding="utf-8",
            )
            errors = validate_delivery_units(repository, [parse_delivery_unit(unit_path)])
            self.assertTrue(any("committed active claim" in error for error in errors), errors)
            git("add", str(unit_path.relative_to(repository)))
            git("commit", "-m", "activate claim")
            claim_commit = git("rev-parse", "HEAD")
            unit = parse_delivery_unit(unit_path)

            errors = validate_delivery_units(repository, [unit])
            self.assertTrue(any(claim_commit in error for error in errors), errors)

            git("branch", "-f", "codex/f-sol-003", claim_commit)
            self.assertEqual([], validate_delivery_units(repository, [unit]))

            released = {**claimed, "DU状态": "RELEASED"}
            unit_path.write_text(
                "# DU\n" + "\n".join(f"> {key}：`{value}`" for key, value in released.items()),
                encoding="utf-8",
            )
            git("add", str(unit_path.relative_to(repository)))
            git("commit", "-m", "release claim")
            unit_path.write_text(
                "# DU\n" + "\n".join(f"> {key}：`{value}`" for key, value in claimed.items()),
                encoding="utf-8",
            )
            errors = validate_delivery_units(repository, [parse_delivery_unit(unit_path)])
            self.assertTrue(any("committed active claim" in error for error in errors), errors)
            git("add", str(unit_path.relative_to(repository)))
            git("commit", "-m", "reactivate claim")
            new_claim = git("rev-parse", "HEAD")
            self.assertNotEqual(claim_commit, new_claim)
            unit = parse_delivery_unit(unit_path)
            errors = validate_delivery_units(repository, [unit])
            self.assertTrue(any(new_claim in error for error in errors), errors)
            git("branch", "-f", "codex/f-sol-003", new_claim)
            self.assertEqual([], validate_delivery_units(repository, [unit]))


if __name__ == "__main__":
    unittest.main()
