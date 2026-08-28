from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "validate_prd_baseline.py"
sys.path.insert(0, str(MODULE_PATH.parent))
SPEC = importlib.util.spec_from_file_location("validate_prd_baseline", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class ValidatePrdBaselineTest(unittest.TestCase):
    def validate_candidate(self, mutate) -> dict[str, object]:
        root = MODULE_PATH.parents[1]
        source = root / "docs" / "baseline" / "prd-v1.8.md"
        report = root / "docs" / "reports" / "2026-08-19-PRD-V1.8基线变更报告.md"
        text = source.read_text(encoding="utf-8-sig")
        candidate_text = mutate(text)

        with tempfile.TemporaryDirectory() as temporary:
            candidate = Path(temporary) / "prd.md"
            candidate.write_text(candidate_text, encoding="utf-8")
            return {
                check.name: check
                for check in MODULE.validate(candidate, report, "V1.8", "正式基线")
            }

    def test_appendix_a2_statistics_must_match_formal_requirements(self) -> None:
        root = MODULE_PATH.parents[1]
        source = root / "docs" / "baseline" / "prd-v1.8.md"
        report = root / "docs" / "reports" / "2026-08-19-PRD-V1.8基线变更报告.md"
        text = source.read_text(encoding="utf-8-sig")
        candidate_text = text.replace("| V2主版本需求 | 47条 |", "| V2主版本需求 | 48条 |", 1)
        self.assertNotEqual(text, candidate_text)

        with tempfile.TemporaryDirectory() as temporary:
            candidate = Path(temporary) / "prd.md"
            candidate.write_text(candidate_text, encoding="utf-8")
            checks = MODULE.validate(candidate, report, "V1.8", "正式基线")

        statistics = {check.name: check for check in checks}
        self.assertIn("附录A.2正式需求统计一致", statistics)
        self.assertFalse(statistics["附录A.2正式需求统计一致"].passed)

    def test_requirement_blocks_support_mixed_heading_levels(self) -> None:
        text = """#### 4.2.1 PM-01 项目创建
| 需求编号 | PM-01 |
| 目标版本 | V1 |
**核心业务规则：**项目规则

### 10.2 割接任务管理（CUT-01）
| 需求编号 | CUT-01 |
| 目标版本 | V1任务闭环；V2增强 |
**核心业务规则：**割接规则

### 11.2 巡检任务管理（INS-01）
| 需求编号 | INS-01 |
| 目标版本 | V2 |
**核心业务规则：**巡检规则

# 附录
"""
        blocks = dict(MODULE.requirement_blocks(text))

        self.assertEqual({"PM-01", "CUT-01", "INS-01"}, set(blocks))
        self.assertIn("| 目标版本 | V1任务闭环；V2增强 |", blocks["CUT-01"])
        self.assertNotIn("巡检规则", blocks["CUT-01"])
        self.assertNotIn("# 附录", blocks["INS-01"])

    def test_appendix_c_must_not_restore_work_order_core_object(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "| 4 | 满意度任务与问卷 | 项目成员执行的满意度收集、评分、签字和不达标整改重收 | ACC-02 |",
                "| 4 | 工单 | 工时、实施及保障服务的工作记录 | WO-01～06 |",
                1,
            )
        )

        self.assertIn("附录C不含工单核心对象", checks)
        self.assertFalse(checks["附录C不含工单核心对象"].passed)

    def test_appendix_c_must_include_satisfaction_core_object_with_acc_02(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "| 4 | 满意度任务与问卷 | 项目成员执行的满意度收集、评分、签字和不达标整改重收 | ACC-02 |",
                "| 4 | 客户反馈记录 | 项目成员记录客户反馈 | SUB-03 |",
                1,
            )
        )

        self.assertIn("附录C满意度核心对象", checks)
        self.assertFalse(checks["附录C满意度核心对象"].passed)

    def test_requirement_id_pattern_accepts_domain_addendum_ids(self) -> None:
        text = """#### 13.5.15 AST-02 设备维保客观状态计算
| 需求编号 | AST-02 |
| 目标版本 | V1 |
"""

        self.assertEqual(["AST-02"], [identifier for identifier, _ in MODULE.requirement_blocks(text)])

    def test_v18_removed_requirements_do_not_return_to_formal_scope(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "| ACC-05 | 遗留问题转持续服务跟踪 | P1 | 从V1/V2移出至V3",
                "| COM-02 | 合同订单履约回写与对账 | P1 | 从V1/V2移出至V3",
                1,
            )
        )

        self.assertFalse(checks["V1.8退出需求边界"].passed)

    def test_v18_project_state_layers_are_required(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace("EXCEPTION_CLOSED", "REMOVED_EXCEPTION_STATE")
        )

        self.assertFalse(checks["V1.8项目状态分层"].passed)

    def test_confirmed_cutover_flow_contract_passes(self) -> None:
        text = """## 第十章 割接管理模块功能需求
P1首页任务接入，P6割接跟踪与闭环。
| 需求编号 | CUT-01 |
| 目标版本 | V1 |
一线工程师提交问卷和人工等级，服务经理在P5审批中复核。
无匹配规则时允许一线补充自定义项并标记配置缺口，不直接阻断割接主流程。
上传方案校验文件有效性、安全性、方案归属和人工确认，不强制解析或补齐在线模板字段。
任一项为“否”必须填写不合理原因并驳回。
专项提前时间按自然日计算，不新增平台通用时效。
审批通过后仍允许修改保障人员安排；角色或任务职责变化必须创建新方案版本。
提交即形成归档闭环事实并结束本次割接流程。
"""

        checks = MODULE.cutover_flow_contract(text)

        self.assertTrue(all(checks.values()), checks)

    def test_transplanted_work_order_and_execution_engine_fail_cutover_contract(self) -> None:
        text = """## 第十章 割接管理模块功能需求
P1首页任务接入，P6割接跟踪与闭环。
| 需求编号 | CUT-01 |
| 目标版本 | V1 |
| 需求编号 | CUT-11 |
| 目标版本 | V2 |
### 割接保障任务（CUT-11）
平台建立逐步骤执行状态机，全部步骤完成后进入稳定观察。
遗留项进入待办跟踪，全部遗留项闭环后方可归档。
"""

        checks = MODULE.cutover_flow_contract(text)

        self.assertFalse(checks["CUT-11退出当前范围"])
        self.assertFalse(checks["无步骤观察扩张"])
        self.assertFalse(checks["无遗留项归档阻断"])

    def test_explicit_execution_engine_prohibitions_are_allowed(self) -> None:
        text = """## 第十章 割接管理模块功能需求
P1首页任务接入，P6割接跟踪与闭环。
| 需求编号 | CUT-01 |
| 目标版本 | V1 |
一线工程师提交问卷和人工等级，服务经理在P5审批中复核。
无匹配规则时允许一线补充自定义项并标记配置缺口，不直接阻断割接主流程。
上传方案校验文件有效性、安全性、方案归属和人工确认，不强制解析或补齐在线模板字段。
任一项为“否”必须填写不合理原因并驳回。
专项提前时间按自然日计算，不新增平台通用时效。
审批通过后仍允许修改保障人员安排；角色或任务职责变化必须创建新方案版本。
提交即形成归档闭环事实并结束本次割接流程。
CUT-06不建立逐步骤执行状态机，也不生成稳定观察任务。
"""

        checks = MODULE.cutover_flow_contract(text)

        self.assertTrue(checks["无步骤观察扩张"], checks)

    def test_confirmed_project_workbench_contract_passes(self) -> None:
        root = MODULE_PATH.parents[1]
        text = (root / "docs" / "baseline" / "prd-v1.8.md").read_text(encoding="utf-8-sig")

        checks = MODULE.project_workbench_contract(text)

        self.assertTrue(all(checks.values()), checks)

    def test_project_workbench_requires_default_task_native_binding(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "每个ProjectTask必须且只能有一个当前有效`WorkBinding`",
                "ProjectTask可以没有WorkBinding",
                1,
            )
        )

        self.assertFalse(checks["工作台-WorkBinding统一必填"].passed)

    def test_project_workbench_keeps_task_native_generic_detail(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "`TASK_NATIVE`直接使用ProjectTask通用详情执行",
                "TASK_NATIVE不提供通用任务详情",
                1,
            )
        )

        self.assertFalse(checks["工作台-通用任务详情基础能力"].passed)

    def test_project_workbench_generic_detail_cannot_replace_bound_business(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "通用基础信息不得替代非`TASK_NATIVE`绑定的业务执行",
                "通用基础信息可以替代非TASK_NATIVE绑定的业务执行",
                1,
            )
        )

        self.assertFalse(checks["工作台-通用详情不替代绑定业务"].passed)

    def test_project_workbench_non_native_completion_cannot_bypass_business_fact(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "其他类型校验绑定业务事实、审批结果、表单提交版本或门禁快照。非`TASK_NATIVE`任务不得通过通用“完成任务”动作绕过目标业务事实",
                "其他类型校验绑定业务事实、审批结果、表单提交版本或门禁快照。非TASK_NATIVE任务可以直接使用通用完成动作",
                1,
            )
        )

        self.assertFalse(checks["工作台-任务完成按绑定类型判定"].passed)

    def test_project_workbench_must_keep_unlimited_task_hierarchy(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "不把业务任务限制为固定两层",
                "业务任务固定为两层",
                1,
            )
        )

        self.assertFalse(checks["工作台-StageTask导航不限制树深"].passed)

    def test_cutover_collection_must_remain_in_same_p3_workbench(self) -> None:
        checks = self.validate_candidate(
            lambda text: text.replace(
                "不新增采集阶段、独立通用任务或采集结果中转页面",
                "新增独立采集阶段和采集结果中转页面",
                1,
            )
        )

        self.assertFalse(checks["工作台-CUT03同一P3工作台"].passed)


if __name__ == "__main__":
    unittest.main()
