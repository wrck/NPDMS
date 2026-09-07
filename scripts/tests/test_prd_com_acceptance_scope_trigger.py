from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_PRD = ROOT / "需求/PRD-项目实施交付管理平台.md"
BASELINE_PRD = ROOT / "docs/baseline/prd-v1.8.md"


def requirement_block(text: str, requirement_id: str) -> str:
    marker_index = text.index(f"| 需求编号 | {requirement_id} |")
    heading_index = text.rfind("\n#### ", 0, marker_index)
    next_heading = text.find("\n#### ", marker_index)
    return text[heading_index:next_heading if next_heading >= 0 else len(text)]


class PrdComAcceptanceScopeTriggerTest(unittest.TestCase):
    """COM-01/ACC-03: current PRD results and approved Feature-level contracts."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source_bytes = SOURCE_PRD.read_bytes()
        cls.baseline_bytes = BASELINE_PRD.read_bytes()
        cls.prd = cls.source_bytes.decode("utf-8")
        cls.com = (ROOT / "specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md").read_text(encoding="utf-8")
        cls.acc = (ROOT / "specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md").read_text(encoding="utf-8")
        cls.authorization = (ROOT / "docs/decisions/0038-commerce-contract-administrator-company-scope.md").read_text(encoding="utf-8")
        cls.report = (ROOT / "docs/decisions/0039-acceptance-report-version-and-deliverable-index.md").read_text(encoding="utf-8")
        cls.questions = (ROOT / "docs/decisions/open-questions.md").read_text(encoding="utf-8")

    def test_source_and_frozen_baseline_are_byte_identical(self) -> None:
        self.assertEqual(self.source_bytes, self.baseline_bytes)
        self.assertIn("CHG-PRD-2026-09-02-010", self.prd)

    def test_com_scope_binding_is_driven_by_project_acceptance_stage(self) -> None:
        com = requirement_block(self.prd, "COM-01")
        self.assertIn("全部当前有效范围的精确版本", com)
        self.assertIn("新生效范围版本必须同步绑定", com)
        self.assertIn("普通减量或释放不得绕过已绑定范围守卫", com)
        self.assertIn("PROJECT_STAGE_ENTRY/SCOPE_VERSION_EFFECTIVE", self.com)
        self.assertNotIn("submitAcceptance", com)

    def test_acc_owns_version_exact_binding_without_legacy_inference(self) -> None:
        self.assertIn("报告、活动和交付件状态不得触发、补建、关闭或反推范围绑定", self.report)
        self.assertIn("精确版本绑定原子提交", self.com)
        self.assertIn("当前版本", requirement_block(self.prd, "ACC-03"))
        self.assertIn("原子", self.acc)

    def test_project_stage_entry_precedes_acceptance_report_completeness(self) -> None:
        self.assertIn("项目进入验收阶段先完成范围绑定，不创建报告", self.report)
        self.assertIn("活动完成时才校验当前报告的验收时间、结论、验收人和附件", self.report)
        acc = requirement_block(self.prd, "ACC-03")
        self.assertIn("附件上传成功且字段完整后才形成有效版本", acc)
        self.assertIn("不生成当前有效版本", acc)

    def test_unapproved_exit_semantics_fail_closed(self) -> None:
        self.assertIn("Q-FCOM-002", self.com)
        self.assertIn("不写", self.com)
        self.assertIn("effective_to", self.com)
        self.assertIn("只阻断退出/回退", self.com)
        self.assertIn("Q-FCOM-002", self.acc)
        self.assertIn("不得触发、补建、关闭或反推范围绑定", self.report)

    def test_exit_semantics_are_registered_as_a_narrow_blocker(self) -> None:
        start = self.questions.index("### Q-FCOM-002")
        end = self.questions.find("\n### ", start + 1)
        question = self.questions[start:end if end >= 0 else len(self.questions)]
        self.assertIn("BLOCKED_BY_SPEC", question)
        self.assertIn("仅退出/回退", question)
        self.assertIn("阶段进入", question)
        self.assertIn("继续有效", question)

    def test_contract_admin_scope_uses_current_system_company_fact(self) -> None:
        self.assertIn("OrganizationScopeApi.getActiveScopes(subjectUserId)", self.com)
        self.assertIn("非空", self.com)
        self.assertIn("原值精确去重集合", self.com)
        self.assertIn("部门、主范围标记、scopeRole和项目关系均不得扩大或缩小该公司集合", self.com)
        self.assertIn("DeliveryScope也不得反推首次可见性", self.com)
        self.assertIn("不新增合同授权表", self.authorization)

    def test_contract_admin_scope_fails_closed_and_keeps_field_permission_separate(self) -> None:
        self.assertIn("列表为空，详情和写操作拒绝", self.com)
        self.assertIn("撤权或到期立即阻止后续查询和维护", self.com)
        self.assertIn("pms:commerce:contract:sensitive-read", self.com)
        self.assertIn("脱敏或不返回", self.com)
        self.assertIn("不删除既有关系、范围历史或审计证据", self.com)

    def test_q_fcom001_is_resolved_by_option_b_without_new_contract_grant(self) -> None:
        self.assertIn("Q-FCOM-001", self.com)
        self.assertIn("RESOLVED", self.com)
        self.assertIn("ACCEPTED", self.authorization)
        self.assertIn("方案B", self.authorization)
        self.assertIn("UserCompanyDepartmentScope", self.authorization)
        self.assertIn("不修改Yudao基础平台", self.authorization)


if __name__ == "__main__":
    unittest.main()
