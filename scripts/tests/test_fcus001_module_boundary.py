import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class CustomerModuleBoundaryTest(unittest.TestCase):

    def test_customer_modules_are_registered(self):
        root_pom = (ROOT / "pom.xml").read_text(encoding="utf-8")
        server_pom = (ROOT / "yudao-server" / "pom.xml").read_text(encoding="utf-8")

        self.assertIn("pms-module-customer/pms-module-customer-api", root_pom)
        self.assertIn("<module>pms-module-customer</module>", root_pom)
        self.assertIn("<artifactId>pms-module-customer</artifactId>", server_pom)
        self.assertTrue((ROOT / "pms-module-customer" / "pom.xml").exists())
        self.assertTrue((ROOT / "pms-module-customer" / "pms-module-customer-api" / "pom.xml").exists())

    def test_project_and_asset_depend_only_on_customer_api(self):
        for module in ("pms-module-project", "pms-module-asset"):
            pom = (ROOT / module / "pom.xml").read_text(encoding="utf-8")
            self.assertIn("<artifactId>pms-module-customer-api</artifactId>", pom)
            self.assertNotIn("<artifactId>pms-module-customer</artifactId>", pom)

    def test_customer_api_does_not_depend_on_implementation(self):
        api_pom = ROOT / "pms-module-customer" / "pms-module-customer-api" / "pom.xml"
        text = api_pom.read_text(encoding="utf-8")
        self.assertNotIn("<artifactId>pms-module-customer</artifactId>", text)


if __name__ == "__main__":
    unittest.main()
