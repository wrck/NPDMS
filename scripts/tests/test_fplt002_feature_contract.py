from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-PLT-002-physical-contract.json"
SECURITY_DESIGN = REPOSITORY_ROOT / "docs/design/14-security-design.md"
TEST_DESIGN = REPOSITORY_ROOT / "docs/design/20-test-design.md"


class Fplt002FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))
        cls.security_design = SECURITY_DESIGN.read_text(encoding="utf-8")
        cls.test_design = TEST_DESIGN.read_text(encoding="utf-8")

    def test_candidate_is_feature_ready_review_only(self) -> None:
        self.assertEqual("IN_REVIEW", self.contract["status"])
        self.assertEqual("PENDING_INDEPENDENT_REVIEW", self.contract["featureReadyDecision"])
        self.assertIn("IN_REVIEW / NOT_YET_READY", self.feature_spec)
        self.assertIn("本候选不是Technical Plan或Implementation授权", self.feature_spec)

    def test_platform_owns_only_the_shared_form_foundation(self) -> None:
        owner = self.contract["owner"]
        self.assertEqual("PLATFORM", owner["context"])
        self.assertEqual("pms-module-platform", owner["module"])
        self.assertIn("consuming Contexts own", owner["businessBoundary"])
        self.assertEqual("NONE_UNTIL_REAL_CROSS_MODULE_CALLER", self.contract["interfaces"]["publicModuleApi"])
        self.assertIn("WorkBinding自动匹配", self.feature_spec)
        self.assertIn("实例提交/审批/完成状态机", self.feature_spec)

    def test_template_revision_and_manual_instance_are_unambiguous(self) -> None:
        machines = self.contract["stateMachines"]
        self.assertEqual("DISABLED", machines["templateAvailability"]["initial"])
        self.assertEqual({"ENABLED", "DISABLED"}, set(machines["templateAvailability"]["states"]))
        self.assertEqual({"DRAFT", "PUBLISHED"}, set(machines["templateRevision"]["states"]))
        self.assertEqual(["PUBLISHED"], machines["templateRevision"]["terminal"])
        self.assertEqual([], machines["manualInstance"]["states"])

        tables = self.contract["tables"]
        self.assertEqual(
            {
                "plt_dynamic_form_template",
                "plt_dynamic_form_template_revision",
                "plt_dynamic_form_instance",
            },
            set(tables),
        )
        self.assertIn("PUBLISHED rows are immutable", " ".join(tables["plt_dynamic_form_template_revision"]["rules"]))
        self.assertIn("ordinary value PATCH uses version CAS", tables["plt_dynamic_form_instance"]["rules"])
        self.assertIn("current_published_revision_id always belongs to this template", tables["plt_dynamic_form_template"]["rules"])

    def test_first_version_retains_the_complete_formcreate_surface(self) -> None:
        form_create = self.contract["formCreateContract"]
        retained = " ".join(form_create["retainedSurfaces"])
        for required in ("all current FormCreate built-in controls", "API selector", "iframe", "events, functions and parseFunc"):
            self.assertIn(required, retained)

        not_applied = " ".join(form_create["notAppliedInVersion1"])
        for deferred in ("component allowlist", "iframe origin allowlist", "API URL or endpoint catalog allowlist", "script sandbox"):
            self.assertIn(deferred, not_applied)

        boundary = form_create["executionBoundary"]
        self.assertEqual("current authenticated user browser only", boundary["location"])
        self.assertIn("no server proxy", boundary["apiSelector"])
        self.assertIn("every target API still enforces", boundary["serverAuthorization"])
        self.assertIn("notAppliedInVersion1", self.contract["formCreateContract"])
        self.assertIn("nested layouts", form_create["fieldDiscovery"]["uniqueness"])

    def test_file_artifact_field_and_ordinary_uploads_remain_distinct(self) -> None:
        field = self.contract["pmsFileArtifactField"]
        self.assertEqual("PmsFileArtifact", field["componentName"])
        self.assertEqual("PLATFORM", field["fileOwnerKey"]["ownerContext"])
        self.assertEqual("DYNAMIC_FORM_INSTANCE", field["fileOwnerKey"]["objectType"])
        self.assertEqual("FORM_FIELD_ATTACHMENT/{fieldKey}", field["fileOwnerKey"]["purposeCode"])
        self.assertIn("never controlled FileArtifact evidence", field["ordinaryUploadPolicy"])
        self.assertIn("value_json stores no forged file vector", field["storagePolicy"])
        self.assertIn("inspectReferenceSets", field["readPolicy"])
        self.assertIn("never issue one query per field", field["readPolicy"])

    def test_legacy_is_audited_then_copied_without_mutation_or_dual_write(self) -> None:
        reuse = self.contract["reuse"]
        self.assertTrue(reuse["requiredAudit"])
        self.assertIn("copied to new PLATFORM-owned names", reuse["copyThenEnhance"])
        self.assertIn("dual write", reuse["forbidden"])
        self.assertIn("automatic legacy migration", reuse["forbidden"])
        self.assertIn("legacy requirement-analysis classes, APIs, routes, pages, data and behavior", reuse["unchangedLegacy"])

    def test_api_payloads_do_not_leave_implementation_owned_fields_ambiguous(self) -> None:
        payloads = self.contract["interfaces"]["payloads"]
        self.assertEqual(
            ["templateCode", "templateName", "categoryCode"],
            payloads["templateCreate"]["required"],
        )
        self.assertIn("formRulesJson", payloads["revisionPatch"]["required"])
        self.assertIn("templateRevisionId", payloads["instanceCreate"]["required"])
        self.assertIn("controlled file keys are rejected", payloads["instancePatch"]["semantics"])
        self.assertIn("enable -> manual select", self.contract["acceptance"]["wholeLoop"])

    def test_sds_keeps_the_intentional_client_code_boundary_and_whole_browser_loop(self) -> None:
        self.assertIn("高信任模板发布者", self.security_design)
        self.assertIn("PLT不执行服务端脚本、不代理URL", self.security_design)
        self.assertIn("不建立URL/域名白名单", self.security_design)
        self.assertIn("新建模板与草稿→完整FormCreate配置", self.test_design)
        for viewport in ("320", "768", "1024", "1440"):
            self.assertIn(viewport, self.test_design)


if __name__ == "__main__":
    unittest.main()
