from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
MIGRATION = ROOT / "sql/migrations/V118__fast001_device_menu_permissions_and_legacy_access.sql"
EQUIPMENT_CONTROLLER = ROOT / "pms-module-asset/src/main/java/cn/iocoder/yudao/module/pms/asset/controller/admin/equipment/EquipmentController.java"
EQUIPMENT_API = ROOT / "yudao-ui/yudao-ui-admin-vue3/src/api/pms/asset/equipment/index.ts"
EQUIPMENT_VIEW = ROOT / "yudao-ui/yudao-ui-admin-vue3/src/views/pms/asset/equipment/index.vue"
PERMISSION_SERVICE = ROOT / "yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/permission/PermissionServiceImpl.java"


class Fast001LegacyWritePermissionsTest(unittest.TestCase):

    def read(self, path: Path) -> str:
        self.assertTrue(path.is_file(), f"missing file: {path.relative_to(ROOT)}")
        return path.read_text(encoding="utf-8")

    def test_v118_adds_device_workbench_permissions(self):
        sql = self.read(MIGRATION).lower()
        for value in (
            "设备工作台",
            "pms:device:query",
            "pms:device:assign",
            "pms:device:conflict-handle",
            "pms:device-configuration-log:download",
            "pms/asset/device/index",
        ):
            self.assertIn(value.lower(), sql)
        self.assertIn("on duplicate key update", sql)

    def test_v118_revokes_only_non_super_admin_legacy_write_grants(self):
        sql = self.read(MIGRATION).lower()
        self.assertIn("update `system_role_menu`", sql)
        self.assertRegex(sql, r"menu_id`?\s+in\s*\(19002,\s*19003,\s*19004,\s*19005\)")
        self.assertIn("`system_role`", sql)
        self.assertIn("'super_admin'", sql)
        self.assertRegex(sql, r"code`?\s*<>\s*'super_admin'")
        self.assertNotRegex(sql, r"menu_id`?\s+in\s*\([^)]*19001")
        self.assertNotRegex(sql, r"menu_id`?\s+in\s*\([^)]*19006")
        self.assertNotIn("update `system_menu`", sql)
        self.assertNotIn("delete from `system_role_menu`", sql)
        self.assertNotIn("insert into `system_role_menu`", sql)

    def test_legacy_backend_and_frontend_write_surfaces_remain(self):
        controller = self.read(EQUIPMENT_CONTROLLER)
        api = self.read(EQUIPMENT_API)
        view = self.read(EQUIPMENT_VIEW)
        for value in (
            '@RequestMapping("/pms/equipment")',
            '@PostMapping("/create")',
            '@PutMapping("/update")',
            '@DeleteMapping("/delete")',
            '@PutMapping("/status-change")',
            "pms:equipment:create",
            "pms:equipment:update",
            "pms:equipment:delete",
            "pms:equipment:status-change",
            "pms:equipment:query",
            "pms:equipment-version:query",
        ):
            self.assertIn(value, controller)
        for value in (
            "createEquipment",
            "updateEquipment",
            "deleteEquipment",
            "changeEquipmentStatus",
            "getEquipmentPage",
            "getEquipmentVersionList",
        ):
            self.assertIn(value, api)
        for permission in (
            "pms:equipment:create",
            "pms:equipment:update",
            "pms:equipment:delete",
            "pms:equipment:status-change",
        ):
            self.assertIn(permission, view)

    def test_super_admin_keeps_platform_wide_permission_semantics(self):
        content = self.read(PERMISSION_SERVICE)
        self.assertIn("return roleService.hasAnySuperAdmin(convertSet(roles, RoleDO::getId));", content)
        self.assertIsNotNone(re.search(r"if \(hasAnyPermission\(roles, permission\)\).*?return true;", content, re.S))


if __name__ == "__main__":
    unittest.main()
