import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE_ROOT = ROOT / "pms-module-service/src/main"
PLAN = ROOT / "docs/superpowers/plans/2026-08-30-f-ins-001-inspection-rule-version-and-field-configuration-foundation.md"
TASK = ROOT / "tasks/features/F-INS-001.md"
TEST_ASSETS = (
    "scripts/tests/test_fins001_plan_and_scope.py",
    "scripts/tests/test_fins001_legacy_preservation.py",
    "scripts/tests/test_fins001_owner_and_query_boundary.py",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/integration/asset/AssetProductTypeContractTest.java",
    "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleSecurityReviewPermissionGuard.java",
    "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleContentDigestService.java",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleSecurityReviewPermissionGuardTest.java",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleContentDigestServiceTest.java",
    "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRevisionRules.java",
    "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRegexValidator.java",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRevisionRulesTest.java",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleRegexValidatorTest.java",
    "pms-module-service/src/main/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleSecretScanner.java",
    "pms-module-service/src/test/java/cn/iocoder/yudao/module/pms/service/domain/inspectionrule/InspectionRuleSecretScannerTest.java",
)
QUERY_PARAMETER_EXCEPTIONS = {}


class FIns001OwnerAndQueryBoundaryTest(unittest.TestCase):

    def test_new_runtime_keeps_owner_and_query_boundaries(self):
        sources = []
        for folder in SERVICE_ROOT.rglob("inspectionrule"):
            sources.extend(path.read_text(encoding="utf-8-sig") for path in folder.rglob("*.*"))
        combined = "\n".join(sources)
        for forbidden in ("ast_", "proj_", "cus_", "cut_", ".last("):
            self.assertNotIn(forbidden, combined)
        xml_sources = []
        for folder in SERVICE_ROOT.rglob("inspectionrule"):
            xml_sources.extend(path.read_text(encoding="utf-8-sig") for path in folder.rglob("*.xml"))
        self.assertNotIn("${", "\n".join(xml_sources))
        self.assertNotIn("@Select(", combined)
        self.assertNotIn("@Update(", combined)
        self.assertNotIn("@Insert(", combined)
        self.assertNotIn("@Delete(", combined)

    def test_mapper_queries_use_one_scenario_query_object(self):
        mapper_root = SERVICE_ROOT / "java/cn/iocoder/yudao/module/pms/service/dal/mysql/inspectionrule"
        matched_exceptions = set()
        mapper_paths = list(mapper_root.rglob("*Mapper.java")) if mapper_root.exists() else []
        for path in mapper_paths:
            mapper_source = path.read_text(encoding="utf-8-sig")
            self.assertNotRegex(mapper_source, re.compile(r"\bMap\s*<"))
            self.assertNotRegex(mapper_source, re.compile(r"\b\w+(?:Req|Resp)?VO\b"))
            declaration_source = mapper_source
            declarations = re.findall(
                r"^[ \t]*(?!return\b)[\w<>, ?]+[ \t]+((?:select|get|find|list|page|count|exists)\w*)\s*\(([^)]*)\)\s*;",
                declaration_source,
                re.MULTILINE)
            relative_path = path.relative_to(ROOT).as_posix()
            for method_name, parameters in declarations:
                if parameters.strip() == "query":
                    self.assertRegex(mapper_source, rf"return\s+{method_name}\(query\);")
                    continue
                normalized = re.sub(r"@\w+(?:\([^)]*\))?\s*", "", parameters).strip()
                parameter_types = [] if not normalized else [
                    parameter.strip().rsplit(maxsplit=1)[0]
                    for parameter in normalized.split(",")
                ]
                signature = f"{relative_path}#{method_name}({','.join(parameter_types)})"
                expected_types = QUERY_PARAMETER_EXCEPTIONS.get(signature)
                if expected_types is not None:
                    self.assertEqual(expected_types, parameter_types)
                    matched_exceptions.add(signature)
                    continue
                if parameter_types:
                    self.assertEqual(1, len(parameter_types))
                    self.assertRegex(parameter_types[0], r"Query$")
        self.assertEqual(set(QUERY_PARAMETER_EXCEPTIONS), matched_exceptions)

    def test_service_only_consumes_asset_public_api(self):
        pom = (ROOT / "pms-module-service/pom.xml").read_text(encoding="utf-8-sig")
        self.assertIn("<artifactId>pms-module-asset-api</artifactId>", pom)
        self.assertNotIn("<artifactId>pms-module-asset</artifactId>", pom)

    def test_security_review_reuses_system_permission_api_without_system_internals(self):
        guard = (SERVICE_ROOT / "java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleSecurityReviewPermissionGuard.java").read_text(encoding="utf-8-sig")
        self.assertIn("PermissionApi", guard)
        self.assertIn("hasAnyPermissions", guard)
        self.assertNotIn("SecurityFrameworkService", guard)
        self.assertNotIn("system_", guard)
        self.assertFalse((SERVICE_ROOT / "java/cn/iocoder/yudao/module/pms/service/service/inspectionrule/security/InspectionRuleExplicitAuthorizationApi.java").exists())

    def test_test_assets_are_traced_by_plan_and_task(self):
        plan = PLAN.read_text(encoding="utf-8-sig")
        task = TASK.read_text(encoding="utf-8-sig")
        for asset in TEST_ASSETS:
            self.assertIn(asset, plan)
        self.assertIn("Requirement ID：`INS-03", task)
        self.assertIn("`INS-09", task)
        self.assertIn("Task 1", task)
        self.assertIn("Task 2", task)


if __name__ == "__main__":
    unittest.main()
