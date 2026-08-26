import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEW_ROOT = (
    ROOT / "yudao-ui" / "yudao-ui-admin-vue3" / "src" / "views" / "pms"
    / "customer"
)


class CustomerWorkbenchContractTest(unittest.TestCase):

    def test_workbench_exposes_customer_owner_sections_and_lifecycle_actions(self):
        text = (VIEW_ROOT / "index.vue").read_text(encoding="utf-8")

        for value in (
            "CustomerFormDrawer",
            "CustomerSourcePanel",
            "CustomerLocationPanel",
            "CustomerRelationSummaryPanel",
            "CustomerHistoryPanel",
            "departmentCode",
            "marketCode",
            "systemCode",
            "expendCode",
            "industryCode",
            "disableCustomer",
            "deleteCustomer",
            "restoreCustomer",
        ):
            self.assertIn(value, text)

    def test_customer_form_separates_crm_and_platform_fields(self):
        text = (VIEW_ROOT / "components" / "CustomerFormDrawer.vue").read_text(encoding="utf-8")

        self.assertIn("CRM 权威字段", text)
        self.assertIn("平台维护字段", text)
        self.assertIn("changedFields", text)
        self.assertIn("departmentCode", text)
        self.assertIn("industryCode", text)

    def test_detail_panels_surface_provider_availability_and_server_trimmed_contacts(self):
        source = (VIEW_ROOT / "components" / "CustomerSourcePanel.vue").read_text(encoding="utf-8")
        locations = (VIEW_ROOT / "components" / "CustomerLocationPanel.vue").read_text(encoding="utf-8")
        relations = (VIEW_ROOT / "components" / "CustomerRelationSummaryPanel.vue").read_text(encoding="utf-8")
        history = (VIEW_ROOT / "components" / "CustomerHistoryPanel.vue").read_text(encoding="utf-8")

        self.assertIn("contactPhone", source)
        self.assertIn("contactEmail", source)
        self.assertIn("locationId", locations)
        self.assertIn("available", relations)
        self.assertIn("dataAsOf", relations)
        self.assertIn("fieldOwner", history)


if __name__ == "__main__":
    unittest.main()
