import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = ROOT / "pms-module-asset"
QUERY_MAPPER = ASSET_ROOT / "src" / "main" / "resources" / "mapper" / "device" / "DeviceQueryMapper.xml"
DEVICE_PACKAGES = (
    "device",
    "shipment",
    "version",
    "assignment",
    "assembly",
    "warranty",
    "configurationlog",
)


class Fast001QueryContractTest(unittest.TestCase):

    def test_device_list_uses_explicit_lightweight_projection(self):
        self.assertTrue(QUERY_MAPPER.exists())
        text = QUERY_MAPPER.read_text(encoding="utf-8")
        normalized = re.sub(r"\s+", " ", text).lower()
        self.assertIn("selectvisibledevicelist", normalized)
        self.assertIn("selectvisibledevicecount", normalized)
        self.assertNotIn("select *", normalized)
        self.assertNotIn("join ast_device_shipment", normalized)
        self.assertNotIn("${", text)
        self.assertNotIn("product_desc", normalized)
        self.assertNotIn("location_snapshot", normalized)

    def test_device_packages_do_not_use_sql_annotations_or_last(self):
        violations = []
        main_root = ASSET_ROOT / "src" / "main" / "java"
        forbidden = ("@Select", "@Update", "@Delete", "@Insert", ".last(")
        for path in main_root.rglob("*.java"):
            if not any(package_name in path.parts for package_name in DEVICE_PACKAGES):
                continue
            text = path.read_text(encoding="utf-8")
            for value in forbidden:
                if value in text:
                    violations.append(f"{path.relative_to(ROOT)}: {value}")
        self.assertEqual([], violations)


if __name__ == "__main__":
    unittest.main()
