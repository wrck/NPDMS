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
            "RE_REVIEW_REQUIRED",
            "APPROVED / READY_FOR_PHASE_2",
        )
        self.assertTrue(any("fresh-context" in error for error in errors), errors)

    def test_version_scope_must_keep_v1_closure_and_v2_technical_notice(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02e-version-scope-matrix.md",
            "PM-10异常关闭和受控重开、CLO-02正常闭环",
            "只维护ACTIVE项目",
        )
        self.assertTrue(any("version scope" in error for error in errors), errors)

    def test_configuration_log_must_have_asset_owner(self) -> None:
        errors = self.validate_mutation(
            "docs/design/02c-data-ownership-matrix.md",
            "ConfigurationLog原始文件、不可变解析版本和设备关联",
            "配置Log关联",
        )
        self.assertTrue(any("ConfigurationLog Owner" in error for error in errors), errors)

    def test_eqp02_traceability_must_include_configuration_log(self) -> None:
        errors = self.validate_mutation(
            "docs/traceability/requirement-matrix.md",
            "ConfigurationLog / Device / DeviceArchive",
            "Device / DeviceArchive",
        )
        self.assertTrue(any("EQP-02" in error for error in errors), errors)

    def test_inspection_state_machine_must_keep_prd_states(self) -> None:
        errors = self.validate_mutation(
            "docs/design/05-state-machine.md",
            "待准备、待预检、巡检中、待报告、待标注、待办跟踪中、已闭环、已归档、已取消",
            "新建、准备、执行、报告、闭环",
        )
        self.assertTrue(any("Inspection state" in error for error in errors), errors)

    def test_service_handover_event_must_have_one_producer(self) -> None:
        errors = self.validate_mutation(
            "docs/design/04-module-design.md",
            "ServiceStatusChanged | 当前不创建持续服务跟踪对象",
            "ServiceStatusChanged、ServiceHandoverCreated | 当前不创建持续服务跟踪对象",
        )
        self.assertTrue(any("ServiceHandoverCreated" in error for error in errors), errors)

    def test_pm10_terminal_commands_need_operation_authorization(self) -> None:
        errors = self.validate_mutation(
            "docs/design/07-authorization-design.md",
            "工程管理部关闭岗 | 关闭、重开",
            "服务经理 | 关闭、重开",
        )
        self.assertTrue(any("PM-10 authorization" in error for error in errors), errors)

    def test_formal_architecture_cannot_claim_runtime_gate_release(self) -> None:
        errors = self.validate_mutation(
            "docs/design/03-system-architecture.md",
            "运行提交、证据批次、构建结果和放行结论只登记在对应工程门禁",
            "Q2实现工作包门禁已解除",
        )
        self.assertTrue(any("runtime evidence" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
