from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_repository_baseline_rules import validate_repository_rules


class RepositoryBaselineRulesTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp_dir.name)
        (self.repo / "tasks").mkdir()
        for relative in (
            "docs/baseline/prd-v1.8.md",
            "docs/engineering/00-engineering-chain.md",
            "docs/README.md",
            "specs/features/README.md",
        ):
            path = self.repo / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("fixture\n", encoding="utf-8")
        (self.repo / "tasks/features").mkdir()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_valid_agents(self) -> None:
        (self.repo / "AGENTS.md").write_text(
            """# NPDMS工程约束

本仓库是业务、设计、实现、测试与验收证据的唯一事实源，不再维护外部规格快照。
优先级：PRD > Engineering Constitution > SDS > Feature Spec > Technical Plan > Task > Code > Test / Runtime Evidence。
修改前读取PRD、工程链、SDS、`specs/features/`中的Feature Spec和`tasks/features/`中的Task。
业务规则不明确时标记BLOCKED_BY_SPEC。
基础平台使用JDK 25。前端和后端在宿主机运行。UI闭环必须由真实浏览器完成。
""",
            encoding="utf-8",
        )

    def _write_superseded_tasks(self) -> None:
        content = """# 历史计划

> **状态：SUPERSEDED**
>
> 本文件仅用于历史追溯，不再生成或驱动新开发任务。
> 当前任务从 `specs/features/` 中的Feature Spec重新生成，并在`tasks/features/`维护状态。

历史内容。
"""
        (self.repo / "tasks/plan.md").write_text(content, encoding="utf-8")
        (self.repo / "tasks/todo.md").write_text(content, encoding="utf-8")

    def test_repository_uses_same_repository_sources(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()

        errors = validate_repository_rules(self.repo)

        self.assertEqual([], errors)

    def test_required_formal_source_must_exist(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        (self.repo / "specs/features/README.md").unlink()

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("specs/features/README.md" in error for error in errors))

    def test_retired_external_snapshot_paths_are_rejected(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        (self.repo / "docs/specification-baseline").mkdir()
        (self.repo / ".spec-repo-f-ast-001").mkdir()
        script = self.repo / "scripts/sync_specification_baseline.py"
        script.parent.mkdir()
        script.write_text("# retired\n", encoding="utf-8")

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("docs/specification-baseline" in error for error in errors))
        self.assertTrue(any(".spec-repo-f-ast-001" in error for error in errors))
        self.assertTrue(any("sync_specification_baseline.py" in error for error in errors))

    def test_agents_preserves_jdk25_host_runtime_and_browser_acceptance(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        agents = self.repo / "AGENTS.md"
        agents.write_text(
            agents.read_text(encoding="utf-8")
            .replace("JDK 25", "JDK")
            .replace("宿主机", "容器")
            .replace("真实浏览器", "静态页面"),
            encoding="utf-8",
        )

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("JDK 25" in error for error in errors))
        self.assertTrue(any("宿主机" in error for error in errors))
        self.assertTrue(any("真实浏览器" in error for error in errors))

    def test_legacy_plan_is_marked_superseded(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        (self.repo / "tasks/plan.md").write_text("# 当前实施计划\n\n继续执行。\n", encoding="utf-8")

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("tasks/plan.md" in error for error in errors))

    def test_legacy_todo_is_marked_superseded(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        (self.repo / "tasks/todo.md").write_text("# 当前任务\n\n继续执行。\n", encoding="utf-8")

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("tasks/todo.md" in error for error in errors))

    def test_old_tasks_cannot_claim_current_feature_ready(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()
        todo = self.repo / "tasks/todo.md"
        todo.write_text(
            todo.read_text(encoding="utf-8").replace("状态：SUPERSEDED", "状态：FEATURE_READY"),
            encoding="utf-8",
        )

        errors = validate_repository_rules(self.repo)

        self.assertTrue(any("SUPERSEDED" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
