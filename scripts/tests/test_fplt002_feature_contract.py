from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-PLT-002-physical-contract.json"
REUSE_AUDIT = REPOSITORY_ROOT / "specs/features/F-PLT-002-legacy-form-reuse-audit.md"
SECURITY_DESIGN = REPOSITORY_ROOT / "docs/design/14-security-design.md"
TEST_DESIGN = REPOSITORY_ROOT / "docs/design/20-test-design.md"


class Fplt002FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))
        cls.reuse_audit = REUSE_AUDIT.read_text(encoding="utf-8")
        cls.security_design = SECURITY_DESIGN.read_text(encoding="utf-8")
        cls.test_design = TEST_DESIGN.read_text(encoding="utf-8")

    def test_forward_amendment_preserves_prior_go_without_authorizing_implementation(self) -> None:
        self.assertEqual("BASELINE_READY_FORWARD_AMENDMENT", self.contract["status"])
        self.assertEqual(
            "GO_NPDMS_FPLT002_FEATURE_READY_20260828_01_R1",
            self.contract["priorFeatureReadyDecision"],
        )
        self.assertEqual(
            "GO_REMEDIATION_COMMIT_4D04DBD63BBD01683416563BECE31DA6CD53F849",
            self.contract["featureReadyDecision"],
        )
        self.assertIn("本次修订门禁：`PENDING`", self.feature_spec)
        self.assertIn("共享动态表单模板与实例基础能力功能规格", self.feature_spec)
        self.assertIn("不重开已完成的手工动态表单闭环", self.feature_spec)

    def test_platform_owns_only_the_shared_form_foundation(self) -> None:
        owner = self.contract["owner"]
        self.assertEqual("PLATFORM", owner["context"])
        self.assertEqual("pms-module-platform", owner["module"])
        self.assertIn("consuming Contexts own", owner["businessBoundary"])
        self.assertEqual(
            "DynamicFormBusinessInstanceApi",
            self.contract["interfaces"]["publicModuleApi"]["interface"],
        )
        self.assertIn("WorkBinding自动匹配", self.feature_spec)
        self.assertIn("实例提交/审批/完成状态机", self.feature_spec)

    def test_real_fsol_caller_has_one_narrow_transactional_api_and_owner_policy(self) -> None:
        interfaces = self.contract["interfaces"]
        api = interfaces["publicModuleApi"]
        self.assertEqual("pms-module-platform-api", api["module"])
        self.assertEqual(
            {
                "inspectRevisionForUsage",
                "lockAndRevalidateRevisionForUsage",
                "createBusinessInstance",
                "inspectInstance",
                "patchInstanceValues",
                "cloneBusinessInstance",
                "lockAndRevalidateInstance",
            },
            set(api["methods"]),
        )
        self.assertIn("never use REQUIRES_NEW", api["transaction"])
        self.assertIn("never create a second idempotency record", api["transaction"])
        revision_use = api["methods"]["inspectRevisionForUsage"]
        self.assertIn("action=REVISION_BINDING_PUBLISH|REVISION_FROZEN_USE", revision_use["input"])
        self.assertIn("ignores later template availability", revision_use["phaseRule"])
        policy = interfaces["businessObjectPolicyProvider"]
        self.assertEqual("SOL/REQUIREMENT_ANALYSIS", policy["firstCaller"])
        self.assertIn("fails closed", policy["failurePolicy"])
        self.assertIn("NO_CONSUMER_PROVIDER_CALLBACK_AFTER_THE_FIRST_PLT_LOCK", self.contract["lockOrder"])
        self.assertIn("FileReferenceAttached", " ".join(self.contract["acceptance"]["businessComposition"]))
        outbox = self.contract["platformFacts"]["outboxEvents"]
        self.assertEqual(1, len(outbox))
        self.assertEqual("FileReferenceAttached", outbox[0]["eventType"])
        self.assertIn("PlatformTransactionalOutboxWriter", outbox[0]["writer"])
        self.assertIn("replay adds zero", outbox[0]["cardinality"])

    def test_business_actions_and_transaction_propagation_are_closed(self) -> None:
        self.assertEqual(
            "GO_NPDMS_FSOL003_FILE_LIFECYCLE_ACTION_MAPPING_20260828_01",
            self.contract["fileLifecycleActionBoundaryDecision"],
        )
        interfaces = self.contract["interfaces"]
        expected_actions = {
            "REVISION_BINDING_PUBLISH",
            "REVISION_FROZEN_USE",
            "CREATE",
            "READ",
            "PATCH",
            "COMPLETE",
            "CLONE_SOURCE",
            "CLONE_TARGET",
            "FILE_READ",
            "FILE_WRITE",
        }
        self.assertEqual(expected_actions, set(interfaces["businessActionCodes"]))
        mapping = interfaces["businessObjectPolicyProvider"]["apiActionMapping"]
        self.assertEqual("CREATE", mapping["createBusinessInstance"])
        self.assertIn("CLONE_SOURCE", mapping["cloneBusinessInstance"])
        self.assertEqual("FILE_WRITE", mapping["fileProviderUploadReferenceReplaceDetach"])
        lifecycle_mapping = mapping["businessFileProviderArchiveInvalidate"]
        self.assertEqual("FILE_WRITE", lifecycle_mapping["businessAction"])
        self.assertEqual("BUSINESS_INSTANCE_ONLY", lifecycle_mapping["scope"])
        self.assertIn("F-PLT-001 file-management command", lifecycle_mapping["entry"])
        self.assertIn("independent pms:file:archive", lifecycle_mapping["entry"])
        self.assertEqual("DENY", lifecycle_mapping["manualInstance"])
        self.assertEqual("NONE", lifecycle_mapping["uiProjection"])
        self.assertNotIn("ARCHIVE", self.contract["pmsFileArtifactField"]["manualInstanceActions"])
        self.assertNotIn("INVALIDATE", self.contract["pmsFileArtifactField"]["manualInstanceActions"])
        self.assertIn("ARCHIVE", self.contract["pmsFileArtifactField"]["businessInstanceActions"])
        self.assertIn("INVALIDATE", self.contract["pmsFileArtifactField"]["businessInstanceActions"])
        self.assertIn("pms:file:archive", self.contract["pmsFileArtifactField"]["businessOwnerPolicy"])
        self.assertIn("independently requires", self.contract["pmsFileArtifactField"]["businessOwnerPolicy"])
        self.assertIn("manual instances", self.contract["pmsFileArtifactField"]["businessOwnerPolicy"])
        self.assertIn("same action", mapping["lockAndRevalidateInstance"])
        transaction = " ".join(interfaces["publicModuleApi"]["transaction"])
        self.assertIn("propagation MANDATORY", transaction)
        self.assertIn("reject calls without an existing caller transaction", transaction)
        self.assertNotIn("REQUIRED or MANDATORY", transaction)
        create_input = interfaces["publicModuleApi"]["methods"]["createBusinessInstance"]["input"]
        self.assertIn("preallocatedInstanceId", create_input)
        self.assertIn("exact caller-preallocated instance id", " ".join(self.contract["tables"]["plt_dynamic_form_instance"]["rules"]))

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

    def test_server_validation_does_not_execute_client_code(self) -> None:
        validation = self.contract["serverValidation"]
        self.assertIn("required", validation["authoritativeDeclarativeRules"])
        self.assertIn("enumerated option", validation["authoritativeDeclarativeRules"])
        self.assertIn("functions", validation["clientOnlyNonAuthoritativeRules"])
        self.assertIn("cannot be claimed as a server completion guard", validation["failure"])
        inspect_output = self.contract["interfaces"]["publicModuleApi"]["methods"]["inspectInstance"]["output"]
        self.assertIn("declarativeValidationResult", inspect_output)

    def test_legacy_is_audited_then_copied_without_mutation_or_dual_write(self) -> None:
        reuse = self.contract["reuse"]
        self.assertEqual("COMPLETE", reuse["auditStatus"])
        self.assertEqual(
            "LEGACY_AND_FRAMEWORK_IMPLEMENTATION_AUDIT_COMPLETE",
            self.contract["approvedApproach"]["sequence"][0],
        )
        self.assertEqual(
            "specs/features/F-PLT-002-legacy-form-reuse-audit.md",
            reuse["auditEvidence"],
        )
        self.assertTrue(REUSE_AUDIT.is_file())
        self.assertEqual(
            "3adea6121000b5bb55b176d352b5afa94143b7dd",
            reuse["auditedImplementation"]["productCodeCommit"],
        )

        groups = {group["group"]: group for group in reuse["sourceGroups"]}
        self.assertEqual(
            {"BPM_FORM_CREATE", "LEGACY_PMS_FORM", "LEGACY_REQUIREMENT_ANALYSIS"},
            set(groups),
        )
        decisions = set(reuse["decisionVocabulary"])
        self.assertEqual({"DIRECT_REUSE", "COPY_THEN_ENHANCE", "DO_NOT_REUSE"}, decisions)
        for group in groups.values():
            self.assertEqual("COMPLETE", group["status"])
            self.assertTrue(group["mappings"])
            for mapping in group["mappings"]:
                self.assertEqual(
                    {"source", "item", "decision", "target", "rationale", "legacyUnchangedVerification"},
                    set(mapping),
                )
                self.assertIn(mapping["decision"], decisions)
                for key in ("source", "item", "target", "rationale", "legacyUnchangedVerification"):
                    self.assertTrue(mapping[key].strip())

        bpm_items = " ".join(mapping["item"] for mapping in groups["BPM_FORM_CREATE"]["mappings"])
        for required in (
            "fc-designer",
            "useFormCreateDesigner",
            "encode/decode",
            "global FormCreate",
            "designerConfig and save interaction",
            "copy and restore interactions",
        ):
            self.assertIn(required, bpm_items)

        legacy_items = " ".join(mapping["item"] for mapping in groups["LEGACY_PMS_FORM"]["mappings"])
        for required in (
            "productType",
            "conf and fields",
            "version",
            "template snapshot",
            "value save and refresh",
            "template list, metadata edit and action feedback",
            "detail/preview",
            "template selection, instance list/edit/detail and refresh interactions",
            "raw formData textarea and pre preview",
            "submit/approve/reject/delete",
        ):
            self.assertIn(required, legacy_items)

        requirement_items = " ".join(
            mapping["item"] for mapping in groups["LEGACY_REQUIREMENT_ANALYSIS"]["mappings"]
        )
        for required in ("11 labels", "Editor v-model", "manual template selection", "project entry", "legacy CRUD"):
            self.assertIn(required, requirement_items)

        for audit_id in ("BPM-01", "BPM-09", "PMS-01", "PMS-12", "REQ-01", "REQ-04A", "REQ-06"):
            self.assertIn(audit_id, self.reuse_audit)
        self.assertIn("设计前审计已经完成", self.feature_spec)
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
