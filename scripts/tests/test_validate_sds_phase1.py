from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate_sds_phase1.py"
SPEC = importlib.util.spec_from_file_location("validate_sds_phase1", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
REPOSITORY_ROOT = MODULE_PATH.parents[1]


class ValidateSdsPhase1Test(unittest.TestCase):

    def build_fixture(self, root: Path) -> None:
        for relative in MODULE.REQUIRED_FILES:
            source = REPOSITORY_ROOT / relative
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)

    def validate_mutation(self, relative: str, old: str, new: str) -> list[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.build_fixture(root)
            target = root / relative
            content = target.read_text(encoding="utf-8-sig")
            self.assertIn(old, content)
            target.write_text(content.replace(old, new, 1), encoding="utf-8")
            return MODULE.validate(root)

    def test_current_phase1_review_candidate_passes_machine_gate(self) -> None:
        self.assertEqual([], MODULE.validate(REPOSITORY_ROOT))

    def test_owner_map_must_cover_each_formal_requirement_once(self) -> None:
        errors = self.validate_mutation(
            "docs/design/phase-1-domain-ownership.md",
            "PROJ-12、INT-01",
            "PROJ-12、PM-01",
        )
        self.assertTrue(any("Owner mapping" in error for error in errors), errors)

    def test_removed_or_deferred_requirements_cannot_return_to_owner_scope(self) -> None:
        errors = self.validate_mutation(
            "docs/design/phase-1-domain-ownership.md",
            "| COM | COM-01 |",
            "| COM | COM-01、COM-02 |",
        )
        self.assertTrue(any("removed/deferred" in error for error in errors), errors)

    def test_project_state_layers_are_required(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "`assignment_status`独立维护",
            "指派状态合并到项目状态",
        )
        self.assertTrue(any("project state layers" in error for error in errors), errors)

    def test_task_native_binding_is_required(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "未指定其他业务绑定时使用TASK_NATIVE",
            "允许任务不配置WorkBinding",
        )
        self.assertTrue(any("TASK_NATIVE" in error for error in errors), errors)

    def test_non_native_completion_cannot_bypass_business_facts(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02b-aggregate-boundary-decisions.md",
            "不以通用完成命令绕过目标业务事实",
            "允许使用通用完成命令",
        )
        self.assertTrue(any("completion guard" in error for error in errors), errors)

    def test_cut03_must_remain_inside_p3_boundary(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02-domain-model.md",
            "界面合并不产生新的业务阶段或聚合Owner",
            "界面合并产生独立采集阶段和聚合Owner",
        )
        self.assertTrue(any("CUT-03" in error for error in errors), errors)

    def test_machine_gate_cannot_claim_ready_before_fresh_review(self) -> None:
        errors = self.validate_mutation(
            "docs/engineering/gates/phase-1/gate-status.md",
            "PENDING_FRESH_REVIEW",
            "APPROVED / READY_FOR_PHASE_2",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
