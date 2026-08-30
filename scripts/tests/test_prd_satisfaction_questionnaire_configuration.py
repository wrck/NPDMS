from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_PRD = ROOT / "需求/PRD-项目实施交付管理平台.md"
BASELINE_PRD = ROOT / "docs/baseline/prd-v1.8.md"
AMENDMENT = ROOT / "docs/baseline/prd-v1.8-amendment-010-configurable-questionnaire-foundation.md"
ACC_DOMAIN = ROOT / "specs/001-project-delivery-platform/domains/ACC-验收与项目闭环需求规格.md"
OPEN_QUESTIONS = ROOT / "docs/decisions/open-questions.md"


def requirement_block(text: str, requirement_id: str) -> str:
    marker = f"| 需求编号 | {requirement_id} |"
    marker_index = text.index(marker)
    heading_index = text.rfind("\n#### ", 0, marker_index)
    next_heading = text.find("\n#### ", marker_index)
    return text[heading_index: next_heading if next_heading >= 0 else len(text)]


class PrdSatisfactionQuestionnaireConfigurationTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.source_bytes = SOURCE_PRD.read_bytes()
        cls.baseline_bytes = BASELINE_PRD.read_bytes()
        cls.prd = cls.source_bytes.decode("utf-8")
        cls.amendment = AMENDMENT.read_text(encoding="utf-8")
        cls.acc_domain = ACC_DOMAIN.read_text(encoding="utf-8")
        cls.open_questions = OPEN_QUESTIONS.read_text(encoding="utf-8")

    def test_source_snapshot_and_acc_projection_share_the_candidate_rule(self) -> None:
        self.assertEqual(self.source_bytes, self.baseline_bytes)
        self.assertIn("CHG-PRD-2026-08-30-010", self.prd)
        acc = requirement_block(self.prd, "ACC-02")
        for text in (acc, self.amendment, self.acc_domain):
            self.assertIn("答案Schema", text)
            self.assertIn("计分策略", text)
        self.assertIn("平台不预置", acc)
        self.assertIn("不在方案阶段预设", self.amendment)
        self.assertIn("平台不预置", self.acc_domain)

    def test_template_configuration_is_frozen_and_client_cannot_supply_score(self) -> None:
        acc = requirement_block(self.prd, "ACC-02")
        self.assertIn("问卷实例冻结发布模板修订", acc)
        self.assertIn("相同模板修订与相同答案必须得到相同评分和达标结果", acc)
        self.assertIn("客户端不得提交总分、通过结果、阈值或计分规则", acc)
        self.assertIn("V1不执行任意脚本或表达式", acc)

    def test_invalid_configuration_and_answer_boundaries_are_explicit(self) -> None:
        acc = requirement_block(self.prd, "ACC-02")
        self.assertIn("无法确定性计分的配置", acc)
        self.assertIn("答卷格式错误、未知题目/选项或重复作答在Response写入前拒绝", acc)
        self.assertIn("结构合法但缺少必答项时保存不可变Response并形成未通过Result", acc)
        start = self.open_questions.index("### Q-FACC-001")
        question = self.open_questions[start:]
        self.assertIn("Status: BLOCKED_BY_SPEC", question)
        self.assertIn("修订010独立审批GO前仍保持", question)


if __name__ == "__main__":
    unittest.main()
