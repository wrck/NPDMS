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
        self.assertEqual(63, rendered.count("|T-"))
        self.assertEqual(63, rendered.count("|O-"))
        self.assertEqual(63, rendered.count("|PK-"))
        self.assertEqual(48, rendered.count("|FK-"))
        self.assertEqual(126, rendered.count("|IX-"))
        self.assertEqual(130, rendered.count("|UK-"))
        self.assertEqual(94, rendered.count("|CK-"))
        self.assertIn("1,242", rendered)
        self.assertIn("项MATCH只保留逐项追溯", rendered)
        self.assertIn("#### 业务身份键（16项）", rendered)
        self.assertIn("#### 来源幂等键（18项）", rendered)
        self.assertIn("#### 当前唯一记录（5项）", rendered)
        self.assertIn("#### 版本与永久序号（10项）", rendered)
        self.assertIn("#### 关系事实粒度（18项）", rendered)
        self.assertIn("#### 租户行引用键（63项）", rendered)
        self.assertIn("### 1.7 8个可空唯一键逐项判断", rendered)
        self.assertNotIn("uk_order_primary_execution", rendered)
        self.assertNotIn("com_delivery_scope.chk_scope_active", rendered)
        self.assertNotIn("com_crm_execution_order.chk_crm_execution_af", rendered)
        self.assertNotIn("plt_migration_issue.chk_migration_issue_resolution", rendered)
        self.assertIn("状态耦合CHECK当前为0项", rendered)
        self.assertIn("com_delivery_scope_detail", rendered)
        self.assertIn("ast_device_project_assignment.current_device_id", rendered)
        self.assertIn("同一设备同一时点只有一个直接项目归属", rendered)
        self.assertIn("ast_device_shipment_event.rma_marked", rendered)
        self.assertIn("ast_device_component_relation.current_slot_code", rendered)

    def test_all_checks_are_semantically_classified(self) -> None:
        rendered = GENERATOR.render(ROOT)
        self.assertNotIn("UNCLASSIFIED", rendered)


if __name__ == "__main__":
    unittest.main()
