import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROJECT_MAIN = ROOT / "pms-module-project" / "src" / "main" / "java"
ASSET_MAIN = ROOT / "pms-module-asset" / "src" / "main" / "java"


class LegacyCustomerWriteBoundaryTest(unittest.TestCase):

    def test_legacy_customer_service_exposes_only_reads(self):
        service = (PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
                   / "service" / "customer" / "CustomerService.java").read_text(encoding="utf-8")
        implementation = (PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
                          / "service" / "customer" / "CustomerServiceImpl.java").read_text(encoding="utf-8")
        for method in ("createCustomer(", "updateCustomer(", "deleteCustomer("):
            self.assertNotIn(method, service)
            self.assertNotIn(method, implementation)

    def test_legacy_customer_controller_does_not_delegate_writes(self):
        controller = (PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
                      / "controller" / "admin" / "customer" / "CustomerController.java").read_text(encoding="utf-8")
        self.assertIn("CUSTOMER_LEGACY_ROUTE_READ_ONLY", controller)
        for call in ("customerService.createCustomer", "customerService.updateCustomer", "customerService.deleteCustomer"):
            self.assertNotIn(call, controller)

    def test_cust_and_ast_customer_routes_cannot_add_writes(self):
        route_pattern = re.compile(r'@RequestMapping\("/pms/(cust|ast)"\)')
        write_pattern = re.compile(r'@(PostMapping|PutMapping|DeleteMapping|PatchMapping)')
        for source_root in (PROJECT_MAIN, ASSET_MAIN):
            for path in source_root.rglob("*.java"):
                text = path.read_text(encoding="utf-8")
                if route_pattern.search(text):
                    self.assertIsNone(write_pattern.search(text), str(path))


if __name__ == "__main__":
    unittest.main()
