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
        (self.repo / "docs/specification-baseline").mkdir(parents=True)
        (self.repo / "docs/specification-baseline/manifest.json").write_text(
            '{"schemaVersion":1,"source":{"repositoryId":"project-delivery-platform-spec",'
            '"commit":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"files":[]}',
            encoding="utf-8",
        )
        (self.repo / "tasks").mkdir()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_valid_agents(self) -> None:
        (self.repo / "AGENTS.md").write_text(
            """# NPDMS工程约束

规格仓库是业务与设计唯一事实源；本地规格快照是锁定实现输入。
当前输入由 `docs/specification-baseline/manifest.json` 的 `source.commit` 决定。
禁止在NPDMS直接修改受管快照。修改前读取PRD、工程链、SDS、Feature Spec和Task。
基础平台使用JDK 25。前端和后端在宿主机运行。UI闭环必须由真实浏览器完成。
""",
            encoding="utf-8",
        )

    def _write_superseded_tasks(self) -> None:
        content = """# 历史计划

> **状态：SUPERSEDED**
>
> 本文件仅用于历史追溯，不再生成或驱动新开发任务。
> 当前任务从 `docs/specification-baseline/manifest.json` 锁定的Feature Spec重新生成。

历史内容。
"""
        (self.repo / "tasks/plan.md").write_text(content, encoding="utf-8")
        (self.repo / "tasks/todo.md").write_text(content, encoding="utf-8")

    def test_agents_uses_manifest_as_locked_input(self) -> None:
        self._write_valid_agents()
        self._write_superseded_tasks()

        errors = validate_repository_rules(self.repo)

        self.assertEqual([], errors)

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
