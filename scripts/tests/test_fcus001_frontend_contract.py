import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NEW_API = (
    ROOT / "yudao-ui" / "yudao-ui-admin-vue3" / "src" / "api" / "pms"
    / "customer" / "index.ts"
)
LEGACY_API = (
    ROOT / "yudao-ui" / "yudao-ui-admin-vue3" / "src" / "api" / "pms"
    / "project" / "customer" / "index.ts"
)
MIGRATION = ROOT / "sql" / "migrations" / "V89__fcus001_customer_menu_and_permissions.sql"
LEGACY_VIEW = (
    ROOT / "yudao-ui" / "yudao-ui-admin-vue3" / "src" / "views" / "pms"
    / "project" / "customer" / "index.vue"
)


class CustomerFrontendContractTest(unittest.TestCase):

    def test_new_customer_client_uses_owner_routes_and_concurrency_headers(self):
        text = NEW_API.read_text(encoding="utf-8")

        self.assertIn("const baseUrl = '/pms/customers'", text)
        self.assertIn("url: baseUrl, params", text)
        self.assertIn("url: `${baseUrl}/${id}`", text)
        self.assertIn("url: `${baseUrl}/${id}/actions/${action}`", text)
        self.assertIn("lifecycleAction(id, 'disable'", text)
        self.assertIn("lifecycleAction(id, 'delete'", text)
        self.assertIn("lifecycleAction(id, 'restore'", text)
        self.assertIn("'Idempotency-Key': idempotencyKey", text)
        self.assertIn("'If-Match': String(expectedVersion)", text)

    def test_legacy_customer_client_is_read_only(self):
        text = LEGACY_API.read_text(encoding="utf-8")

        self.assertIn("export const getCustomerPage", text)
        self.assertIn("export const getCustomer", text)
        self.assertNotIn("export const createCustomer", text)
        self.assertNotIn("export const updateCustomer", text)
        self.assertNotIn("export const deleteCustomer", text)
        self.assertNotIn("export const createContact", text)
        self.assertNotIn("export const updateContact", text)
        self.assertNotIn("export const deleteContact", text)

    def test_legacy_customer_view_exposes_history_read_only_state(self):
        text = LEGACY_VIEW.read_text(encoding="utf-8")

        self.assertIn("客户历史（只读）", text)
        self.assertIn("数据截止时间", text)
        self.assertIn("/customer-asset/customers", text)
        self.assertNotIn("新增客户", text)
        self.assertNotIn(">编辑<", text)
        self.assertNotIn(">删除<", text)
        self.assertNotIn("CustomerApi.createCustomer", text)
        self.assertNotIn("CustomerApi.updateCustomer", text)
        self.assertNotIn("CustomerApi.deleteCustomer", text)

    def test_menu_migration_separates_new_permissions_from_legacy_history(self):
        text = MIGRATION.read_text(encoding="utf-8")

        self.assertIn("客户工作台", text)
        self.assertIn("pms/customer/index", text)
        self.assertIn("客户历史（只读）", text)
        self.assertIn("pms/project/customer/index", text)
        for permission in (
            "pms:customer:query",
            "pms:customer:create",
            "pms:customer:update",
            "pms:customer:disable",
            "pms:customer:delete",
            "pms:customer:restore",
            "pms:customer:sensitive-read",
            "pms:customer:export",
        ):
            self.assertIn(permission, text)
        self.assertIn("WHERE `id` IN (18002, 18003, 18004)", text)


if __name__ == "__main__":
    unittest.main()
