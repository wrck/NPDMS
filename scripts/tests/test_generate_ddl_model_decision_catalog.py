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
        self.assertEqual(60, rendered.count("|T-"))
        self.assertEqual(60, rendered.count("|O-"))
        self.assertEqual(60, rendered.count("|PK-"))
        self.assertEqual(48, rendered.count("|FK-"))
        self.assertEqual(122, rendered.count("|IX-"))
        self.assertEqual(123, rendered.count("|UK-"))
        self.assertEqual(94, rendered.count("|CK-"))
        self.assertIn("1,240", rendered)
        self.assertIn("来源幂等键（15项）", rendered)
        self.assertIn("租户行引用键（60项）", rendered)
        self.assertIn("业务身份键（18项）", rendered)
        self.assertIn("版本与永久序号（8项）", rendered)
        self.assertIn("关系事实粒度（17项）", rendered)
        self.assertNotIn("uk_order_primary_execution", rendered)
        self.assertNotIn("com_delivery_scope.chk_scope_active", rendered)
        self.assertNotIn("com_crm_execution_order.chk_crm_execution_af", rendered)
        self.assertNotIn("plt_migration_issue.chk_migration_issue_resolution", rendered)
        self.assertIn("com_delivery_scope_detail", rendered)
        self.assertIn("ast_device_project_assignment.current_device_id", rendered)
        self.assertIn("ast_device_shipment_event.rma_marked", rendered)
        self.assertIn("ast_device_component_relation.current_slot_code", rendered)
        self.assertIn("MODEL_BASELINE_READY", rendered)
        self.assertNotIn("当前哈希Q07待确认", rendered)
        self.assertNotIn("当前哈希Q08待确认", rendered)

    def test_all_checks_are_semantically_classified(self) -> None:
        rendered = GENERATOR.render(ROOT)
        self.assertNotIn("UNCLASSIFIED", rendered)


if __name__ == "__main__":
    unittest.main()
