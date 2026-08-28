import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROJECT_MAIN = ROOT / "pms-module-project" / "src" / "main" / "java"
ASSET_MAIN = ROOT / "pms-module-asset" / "src" / "main" / "java"
CUSTOMER_API = "cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi"


class CustomerOwnerBoundaryTest(unittest.TestCase):

    def test_project_runtime_customer_reads_use_public_api(self):
        allowed = {
            PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
            / "controller" / "admin" / "customer" / "CustomerController.java",
            PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
            / "service" / "customer" / "CustomerService.java",
            PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
            / "service" / "customer" / "CustomerServiceImpl.java",
            PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
            / "dal" / "mysql" / "customer" / "CustomerMapper.java",
        }
        forbidden = (
            "dal.dataobject.customer.CustomerDO",
            "dal.mysql.customer.CustomerMapper",
            "service.customer.CustomerService",
        )
        violations = []
        for path in PROJECT_MAIN.rglob("*.java"):
            if path in allowed:
                continue
            text = path.read_text(encoding="utf-8")
            if any(value in text for value in forbidden):
                violations.append(str(path.relative_to(ROOT)))
        self.assertEqual([], violations)

    def test_project_and_asset_customer_relationship_writes_use_public_api(self):
        project_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in PROJECT_MAIN.rglob("*.java")
            if not {"service", "customer"}.issubset(path.relative_to(PROJECT_MAIN).parts)
        )
        asset_sources = "\n".join(
            path.read_text(encoding="utf-8")
            for path in ASSET_MAIN.rglob("*.java")
        )
        self.assertIn(CUSTOMER_API, project_sources)
        self.assertIn(CUSTOMER_API, asset_sources)
        self.assertIn("CustomerLifecycleStatus.ENABLED", project_sources)
        self.assertIn("CustomerLifecycleStatus.ENABLED", asset_sources)
        project_creation = (PROJECT_MAIN / "cn" / "iocoder" / "yudao" / "module" / "pms" / "project"
                            / "service" / "projectmanual" / "ProjectManualCreationServiceImpl.java").read_text(
            encoding="utf-8")
        self.assertIn(CUSTOMER_API, project_creation)
        self.assertIn("CustomerLifecycleStatus.ENABLED", project_creation)


if __name__ == "__main__":
    unittest.main()
