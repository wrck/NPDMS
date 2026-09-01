from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_PRD = ROOT / "需求/PRD-项目实施交付管理平台.md"
BASELINE_PRD = ROOT / "docs/baseline/prd-v1.8.md"
AMENDMENT = ROOT / "docs/baseline/prd-v1.8-amendment-009-acceptance-scope-stage-trigger.md"


def requirement_block(text: str, requirement_id: str) -> str:
    marker = f"| 需求编号 | {requirement_id} |"
    marker_index = text.index(marker)
    heading_index = text.rfind("\n#### ", 0, marker_index)
    next_heading = text.find("\n#### ", marker_index)
    return text[heading_index: next_heading if next_heading >= 0 else len(text)]


class PrdComAcceptanceScopeTriggerTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.source_bytes = SOURCE_PRD.read_bytes()
        cls.baseline_bytes = BASELINE_PRD.read_bytes()
        cls.prd = cls.source_bytes.decode("utf-8")
        cls.amendment = AMENDMENT.read_text(encoding="utf-8")

    def test_source_and_frozen_baseline_are_byte_identical(self) -> None:
        self.assertEqual(self.source_bytes, self.baseline_bytes)
        self.assertIn("CHG-PRD-2026-08-29-009", self.prd)

    def test_com_scope_binding_is_driven_by_project_acceptance_stage(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        self.assertIn("项目进入其设定的验收阶段时", com)
        self.assertIn("全部当前有效DeliveryScope分配版本同步进入验收范围", com)
        self.assertIn("新当前分配版本在生效时同步进入验收范围", com)
        self.assertIn("阶段进入或范围版本生效不得标记成功", com)
        self.assertNotIn("submitAcceptance", com)

    def test_acc_owns_version_exact_binding_without_legacy_inference(self) -> None:
        acc = requirement_block(self.prd, "ACC-03")
        self.assertIn("ACC为该项目全部当前有效DeliveryScope分配版本追加范围绑定", acc)
        self.assertIn("绑定保存精确分配版本", acc)
        self.assertIn("不得从既有项目级验收状态或报告状态反推历史事实", acc)
        self.assertIn("不留下部分绑定", acc)

    def test_project_stage_entry_precedes_acceptance_report_completeness(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        acc = requirement_block(self.prd, "ACC-03")
        for block in (com, acc, self.amendment):
            self.assertIn("项目阶段进入", block)
            self.assertIn("不要求创建或补齐初验/终验", block)
        self.assertIn("报告尚未形成不得阻断已满足其他门禁的阶段进入", acc)
        self.assertIn("验收时间、结论、验收人和附件完备", acc)
        self.assertIn("对应验收活动不得标记完成", acc)

    def test_unapproved_exit_semantics_fail_closed(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        acc = requirement_block(self.prd, "ACC-03")
        for block in (com, acc, self.amendment):
            self.assertIn("Q-FCOM-002", block)
            self.assertIn("不得自动解锁或关闭", block)

    def test_contract_admin_scope_uses_current_system_company_fact(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        for text in (com, self.amendment):
            self.assertIn("SYSTEM", text)
            self.assertIn("当前有效", text)
            self.assertIn("公司编码", text)
            self.assertIn("精确匹配", text)
            self.assertIn("授权事实", text)
        self.assertIn("部门信息只作为该授权事实的上下文", com)
        self.assertNotIn("新增显式合同授权", com)

    def test_contract_admin_scope_fails_closed_and_keeps_field_permission_separate(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        self.assertIn("列表返回空，详情和写操作拒绝", com)
        self.assertIn("撤销或到期后立即禁止后续查询和关系维护", com)
        self.assertIn("独立字段权限", com)
        self.assertIn("脱敏或不返回", com)

if __name__ == "__main__":
    unittest.main()
