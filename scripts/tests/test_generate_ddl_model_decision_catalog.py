from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "generate_ddl_model_decision_catalog.py"
SPEC = importlib.util.spec_from_file_location("generate_ddl_model_decision_catalog", MODULE_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(GENERATOR)


class DdlModelDecisionCatalogTest(unittest.TestCase):
    def test_catalog_has_complete_concrete_scope(self) -> None:
        rendered = GENERATOR.render(ROOT)
        self.assertEqual(49, rendered.count("|T-"))
        self.assertEqual(49, rendered.count("|O-"))
        self.assertEqual(49, rendered.count("|PK-"))
        self.assertEqual(47, rendered.count("|FK-"))
        self.assertEqual(106, rendered.count("|IX-"))
        self.assertEqual(100, rendered.count("|UK-"))
        self.assertEqual(79, rendered.count("|CK-"))
        self.assertIn("1,048", rendered)
        self.assertIn("1055项MATCH只保留逐项追溯", rendered)
        self.assertIn("#### 业务身份键（15项）", rendered)
        self.assertIn("#### 来源幂等键（15项）", rendered)
        self.assertIn("#### 当前唯一记录（5项）", rendered)
        self.assertIn("#### 版本与永久序号（3项）", rendered)
        self.assertIn("#### 关系事实粒度（13项）", rendered)
        self.assertIn("#### 租户行引用键（49项）", rendered)
        self.assertIn("### 1.7 8个可空唯一键逐项判断", rendered)
        self.assertIn("### 1.9 25个精确匹配字段与排序规则", rendered)
        self.assertIn("com_delivery_scope.chk_scope_active", rendered)

    def test_all_checks_are_semantically_classified(self) -> None:
        rendered = GENERATOR.render(ROOT)
        self.assertNotIn("UNCLASSIFIED", rendered)


if __name__ == "__main__":
    unittest.main()
