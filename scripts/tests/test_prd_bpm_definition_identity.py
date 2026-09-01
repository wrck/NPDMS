from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_PRD = ROOT / "需求/PRD-项目实施交付管理平台.md"
BASELINE_PRD = ROOT / "docs/baseline/prd-v1.8.md"
PROJ_DOMAIN = ROOT / "specs/001-project-delivery-platform/domains/PROJ-项目治理需求规格.md"


def requirement_block(text: str, requirement_id: str) -> str:
    marker = f"| 需求编号 | {requirement_id} |"
    marker_index = text.index(marker)
    heading_index = text.rfind("\n#### ", 0, marker_index)
    next_heading = text.find("\n#### ", marker_index)
    return text[heading_index: next_heading if next_heading >= 0 else len(text)]


class PrdBpmDefinitionIdentityTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.source_bytes = SOURCE_PRD.read_bytes()
        cls.baseline_bytes = BASELINE_PRD.read_bytes()
        cls.prd = cls.source_bytes.decode("utf-8")
        cls.proj = PROJ_DOMAIN.read_text(encoding="utf-8")

    def test_pm03_uses_bpm_definition_identity_without_pms_version_model(self) -> None:
        self.assertEqual(self.source_bytes, self.baseline_bytes)
        pm03 = requirement_block(self.prd, "PM-03")
        for text in (pm03, self.proj):
            self.assertIn("默认按key选择最新生效流程定义", text)
            self.assertIn("历史`processDefinitionId`", text)
            self.assertIn("完整`taskDefinitionKey`", text)
            self.assertIn("不另建PMS流程版本", text)

    def test_project_type_variation_remains_in_template_and_gate_model(self) -> None:
        pm03 = requirement_block(self.prd, "PM-03")
        self.assertIn("不同项目类型的阶段、任务、里程碑、交付件、门禁规则可差异化", pm03)
        self.assertIn("BPM流程只承载其中需要审批的子流程，不替代项目状态机和门禁", pm03)
        self.assertIn("既有项目模板、项目和门禁记录中的流程版本列仅保留历史值", pm03)


if __name__ == "__main__":
    unittest.main()
