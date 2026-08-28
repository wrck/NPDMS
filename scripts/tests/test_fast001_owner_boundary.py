import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSET_ROOT = ROOT / "pms-module-asset"
PROJECT_ROOT = ROOT / "pms-module-project"
DEVICE_PACKAGES = (
    "device",
    "shipment",
    "version",
    "assignment",
    "assembly",
    "warranty",
    "configurationlog",
)
FORBIDDEN_JAVA_FRAGMENTS = (
    ".service.",
    ".dal.",
    ".repository.",
)
FORBIDDEN_MODULE_PACKAGES = (
    "module.pms.project",
    "module.pms.customer",
    "module.pms.cutover",
    "module.pms.implementation",
    "module.pms.knowledge",
)
FORBIDDEN_TABLE_PREFIXES = (
    "proj_",
    "cus_",
    "cut_",
    "imp_",
    "kno_",
)


class Fast001OwnerBoundaryTest(unittest.TestCase):

    def test_asset_device_code_uses_other_modules_only_through_public_api(self):
        violations = []
        for path in self._device_java_files():
            text = path.read_text(encoding="utf-8")
            for module_package in FORBIDDEN_MODULE_PACKAGES:
                if module_package not in text:
                    continue
                imports = [line.strip() for line in text.splitlines() if line.strip().startswith("import ")]
                for imported in imports:
                    if module_package in imported and any(fragment in imported for fragment in FORBIDDEN_JAVA_FRAGMENTS):
                        violations.append(f"{path.relative_to(ROOT)}: {imported}")
        self.assertEqual([], violations)

    def test_asset_device_mappers_do_not_read_other_owner_tables(self):
        violations = []
        mapper_root = ASSET_ROOT / "src" / "main" / "resources" / "mapper"
        for package_name in DEVICE_PACKAGES:
            package_root = mapper_root / package_name
            if not package_root.exists():
                continue
            for path in package_root.rglob("*.xml"):
                text = path.read_text(encoding="utf-8").lower()
                for prefix in FORBIDDEN_TABLE_PREFIXES:
                    if prefix in text:
                        violations.append(f"{path.relative_to(ROOT)}: {prefix}")
        self.assertEqual([], violations)

    def test_customer_device_summary_reads_ast_owner_tables(self):
        implementation = ASSET_ROOT / "src" / "main" / "java" / "cn" / "iocoder" / "yudao" / "module" / "pms" / "asset" / "api" / "customer" / "AssetCustomerDeviceSummaryApiImpl.java"
        mapper = ASSET_ROOT / "src" / "main" / "resources" / "mapper" / "device" / "DeviceQueryMapper.xml"
        implementation_text = implementation.read_text(encoding="utf-8")
        mapper_text = mapper.read_text(encoding="utf-8").lower()
        self.assertIn("DeviceMapper", implementation_text)
        self.assertNotIn("EquipmentMapper", implementation_text)
        self.assertIn("selectcustomersummarylist", mapper_text)
        self.assertIn("selectcustomersummarycount", mapper_text)
        self.assertIn("ast_device", mapper_text)
        self.assertIn("ast_device_customer_relationship", mapper_text)
        self.assertNotIn("pms_equipment", mapper_text)

    def test_project_device_guard_is_exposed_from_project_api(self):
        api = PROJECT_ROOT / "pms-module-project-api" / "src" / "main" / "java" / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project" / "api" / "reference" / "ProjectDeviceAssignmentGuardApi.java"
        self.assertTrue(api.exists())

    def _device_java_files(self):
        main_root = ASSET_ROOT / "src" / "main" / "java"
        for path in main_root.rglob("*.java"):
            if any(package_name in path.parts for package_name in DEVICE_PACKAGES):
                yield path


if __name__ == "__main__":
    unittest.main()
