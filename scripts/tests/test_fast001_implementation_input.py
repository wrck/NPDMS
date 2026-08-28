import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = ROOT / "specs" / "features" / "F-AST-001-device-serial-archive-and-temporal-assignment.md"
DESIGN = ROOT / "docs" / "superpowers" / "specs" / "2026-08-26-f-ast-001-device-master-forward-migration-design.md"


class Fast001ImplementationInputTest(unittest.TestCase):

    def test_feature_spec_is_read_directly_from_same_repository(self):
        self.assertTrue(FEATURE_SPEC.is_file())
        self.assertFalse((ROOT / "docs/specification-baseline/manifest.json").exists())

    def test_feature_spec_contains_frozen_implementation_rules(self):
        text = FEATURE_SPEC.read_text(encoding="utf-8")
        for value in (
            "ast_device_project_relationship",
            "ast_device_project_ancestor",
            "ast_device_customer_relationship",
            "conp_version/conp_type/conp_series/conp_mark",
            "约200万唯一设备和400万以上发货记录",
            "续保客观记录",
            "普通角色停止全部写操作，`super_admin`仅保留对旧设备模型的创建、更新、删除和状态变更能力",
        ):
            self.assertIn(value, text)
        self.assertIn(
            "不得使用`ast_device_current_assignment`或`ast_device_assignment_history`",
            text,
        )
        self.assertIn("不得代理或双写AST", text)

    def test_design_contains_seventeen_target_tables(self):
        text = DESIGN.read_text(encoding="utf-8")
        tables = (
            "ast_device",
            "ast_device_factory_info",
            "ast_device_shipment",
            "ast_device_factory_version",
            "ast_product_official_info",
            "ast_product_official_version",
            "ast_device_network_version",
            "ast_device_network_version_event",
            "ast_device_project_relationship",
            "ast_device_project_ancestor",
            "ast_device_customer_relationship",
            "ast_device_assignment_reconciliation",
            "ast_device_assembly",
            "ast_device_relationship",
            "ast_device_location",
            "ast_device_warranty",
            "ast_device_warranty_record",
        )
        for table in tables:
            self.assertIn(f"CREATE TABLE `{table}`", text)


if __name__ == "__main__":
    unittest.main()
