import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC_REPO = ROOT / ".spec-repo-f-ast-001"
FEATURE_SPEC = SPEC_REPO / "specs" / "features" / "F-AST-001-device-serial-archive-and-temporal-assignment.md"
DESIGN = ROOT / "docs" / "superpowers" / "specs" / "2026-08-26-f-ast-001-device-master-forward-migration-design.md"
LOCKED_COMMIT = "92726b8c60c48a5c4923b6d5addeb5314a94bb97"


class Fast001ImplementationInputTest(unittest.TestCase):

    def test_feature_spec_repository_is_locked(self):
        head = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=SPEC_REPO,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        self.assertEqual(LOCKED_COMMIT, head)

    def test_feature_spec_contains_frozen_implementation_rules(self):
        text = FEATURE_SPEC.read_text(encoding="utf-8")
        for value in (
            "ast_device_project_relationship",
            "ast_device_project_ancestor",
            "ast_device_customer_relationship",
            "conp_version/conp_type/conp_series/conp_mark",
            "约200万唯一设备和400万以上发货记录",
            "续保客观记录",
            "旧`/pms/equipment`入口只保留历史列表和详情读取并停止全部写操作",
        ):
            self.assertIn(value, text)
        self.assertIn(
            "不得使用`ast_device_current_assignment`或`ast_device_assignment_history`",
            text,
        )

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
