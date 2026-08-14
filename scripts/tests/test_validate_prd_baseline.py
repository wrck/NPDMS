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
        source = root / "docs" / "baseline" / "prd-v1.7.md"
        report = root / "docs" / "reports" / "2026-08-10-PRD与13领域FR差异审查.md"
        text = source.read_text(encoding="utf-8-sig")
        candidate_text = mutate(text)

        with tempfile.TemporaryDirectory() as temporary:
            candidate = Path(temporary) / "prd.md"
            candidate.write_text(candidate_text, encoding="utf-8")
            return {
                check.name: check
                for check in MODULE.validate(candidate, report, "V1.7", "正式基线")
            }

    def test_appendix_a2_statistics_must_match_formal_requirements(self) -> None:
        root = MODULE_PATH.parents[1]
        source = root / "docs" / "baseline" / "prd-v1.7.md"
        report = root / "docs" / "reports" / "2026-08-10-PRD与13领域FR差异审查.md"
        text = source.read_text(encoding="utf-8-sig")
        candidate_text = text.replace("| V2主版本需求 | 48条 |", "| V2主版本需求 | 49条 |", 1)
        self.assertNotEqual(text, candidate_text)

        with tempfile.TemporaryDirectory() as temporary:
            candidate = Path(temporary) / "prd.md"
            candidate.write_text(candidate_text, encoding="utf-8")
            checks = MODULE.validate(candidate, report, "V1.7", "正式基线")

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

    def test_confirmed_cutover_flow_contract_passes(self) -> None:
        text = """## 第十章 割接管理模块功能需求
P1首页任务接入，P6割接跟踪与闭环。
| 需求编号 | CUT-01 |
| 目标版本 | V1 |
一线工程师提交问卷和人工等级，用服经理在P5审批中复核。
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
一线工程师提交问卷和人工等级，用服经理在P5审批中复核。
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


if __name__ == "__main__":
    unittest.main()
