from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def requirement_block(text: str, requirement_id: str) -> str:
    marker_index = text.index(f"| 需求编号 | {requirement_id} |")
    heading_index = text.rfind("\n#### ", 0, marker_index)
    next_heading = text.find("\n#### ", marker_index)
    return text[heading_index:next_heading if next_heading >= 0 else len(text)]


class PrdSatisfactionQuestionnaireConfigurationTest(unittest.TestCase):
    """ACC-02: business outcomes stay in PRD; schema and scoring are approved refinements."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source_bytes = (ROOT / "需求/PRD-项目实施交付管理平台.md").read_bytes()
        cls.baseline_bytes = (ROOT / "docs/baseline/prd-v1.8.md").read_bytes()
        cls.prd = cls.source_bytes.decode("utf-8")
        cls.spec = (ROOT / "specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md").read_text(encoding="utf-8")
        cls.adr = (ROOT / "docs/decisions/0041-satisfaction-questionnaire-result-and-deliverable-source.md").read_text(encoding="utf-8")
        cls.domain = (ROOT / "specs/001-project-delivery-platform/domains/ACC-验收与项目闭环需求规格.md").read_text(encoding="utf-8")

    def test_source_snapshot_and_acc_projection_share_the_approved_rule(self) -> None:
        self.assertEqual(self.source_bytes, self.baseline_bytes)
        acc = requirement_block(self.prd, "ACC-02")
        self.assertIn("问卷实例冻结模板、题目、分值、达标阈值", acc)
        for content in (acc, self.domain):
            for value in ("阈值", "签字", "整改", "不可覆盖"):
                self.assertIn(value, content)
        self.assertIn("ACCEPTED", self.adr)
        self.assertIn("不得以Flyway受管种子替代正式模板配置正向路径", self.spec)

    def test_template_configuration_is_frozen_and_client_cannot_supply_score(self) -> None:
        acc = requirement_block(self.prd, "ACC-02")
        self.assertIn("模板后续发布不追溯修改既有实例", acc)
        self.assertIn("任何人不得人工修改客户评分或覆盖历史答案", acc)
        self.assertIn("schemaVersion=1", self.adr)
        self.assertIn("只在最终总分", self.adr)
        self.assertIn("客户端不得提交score、passed、threshold、weight、strategy或option score", self.spec)
        self.assertIn("不得执行脚本、表达式或客户端算法标识", self.adr)

    def test_invalid_configuration_and_answer_boundaries_are_explicit(self) -> None:
        self.assertIn("字段封闭、编码唯一、类型参数完整且非空", self.adr)
        self.assertIn("未知/重复题目或选项、类型/数量/长度不符在Response前拒绝", self.spec)
        self.assertIn("结构合法但缺必答项保存Response并形成未通过Result", self.spec)
        self.assertIn("必答缺失或签字无效时无论分数均", self.adr)
        acc = requirement_block(self.prd, "ACC-02")
        self.assertIn("重复提交按问卷实例与请求标识幂等处理", acc)
        self.assertIn("新任务、新实例和新判定", acc)
        self.assertIn("旧版本保持不可变", acc)


if __name__ == "__main__":
    unittest.main()
