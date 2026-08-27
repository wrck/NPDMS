from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
API_FILE = ROOT / "yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/device/index.ts"
VIEW_ROOT = ROOT / "yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/device"
INDEX_FILE = VIEW_ROOT / "index.vue"


class Fast001FrontendContractTest(unittest.TestCase):

    def read(self, path: Path) -> str:
        self.assertTrue(path.exists(), f"missing file: {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def test_device_client_uses_stable_paths_and_concurrency_headers(self):
        content = self.read(API_FILE)
        self.assertIn("const baseUrl = '/pms/asset/devices'", content)
        self.assertRegex(content, r"headers:\s*\{[^}]*'If-Match'[^}]*'Idempotency-Key'[^}]*\}")
        self.assertIn("/actions/assign-project", content)
        self.assertIn("/actions/assign-customer", content)
        self.assertIn("/configuration-logs/${logId}/download-url", content)
        self.assertNotRegex(content, r"fileUrl|presignedUrl|persistentUrl")

    def test_workbench_keeps_list_thin_and_loads_detail_slices_on_demand(self):
        content = self.read(INDEX_FILE)
        self.assertIn("getDevicePage", content)
        self.assertIn("openDetail", content)
        self.assertNotIn("getAssignmentHistory(row.deviceId", content)
        self.assertNotIn("getCustomerRelationships(row.deviceId", content)
        self.assertNotIn("getAssemblyTree(row.deviceId", content)
        self.assertNotIn("getWarrantyRecords(row.deviceId", content)
        self.assertNotIn("getConfigurationLogs(row.deviceId", content)
        self.assertIn("DeviceAssignmentHistoryDrawer", content)
        self.assertIn("DeviceCustomerRelationshipDrawer", content)
        self.assertIn("DeviceAssemblyTreeDrawer", content)

    def test_workbench_exposes_fixed_six_tabs_and_conp_fields(self):
        content = "\n".join(
            self.read(path)
            for path in [INDEX_FILE, *sorted((VIEW_ROOT / "components").glob("*.vue"))]
        )
        for label in ["出厂信息", "官网信息", "在网版本", "技术公告", "维保信息", "配置Log"]:
            self.assertIn(label, content)
        for field in ["conpVersion", "conpType", "conpSeries", "conpMark"]:
            self.assertIn(field, content)
        self.assertIn("NOT_AVAILABLE", content)
        self.assertIn("待核对", content)

    def test_unimplemented_owner_slices_have_no_local_substitutes(self):
        content = "\n".join(
            self.read(path)
            for path in [API_FILE, INDEX_FILE, *sorted((VIEW_ROOT / "components").glob("*.vue"))]
        )
        forbidden = [
            "/pms/mes",
            "/pms/kno/technical-notices",
            "/pms/cutover/target-version",
            "mockSyncCompleted",
            "LOCAL_FALLBACK",
        ]
        for value in forbidden:
            self.assertNotIn(value, content)
        self.assertIsNone(re.search(r"from ['\"]@/api/pms/(cutover|engineering|integration)", content))


if __name__ == "__main__":
    unittest.main()
