from __future__ import annotations

import json
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = REPOSITORY_ROOT / "specs/features/F-SOL-003-requirement-analysis-versioning.md"
PHYSICAL_CONTRACT = REPOSITORY_ROOT / "specs/features/F-SOL-003-physical-contract.json"
PLATFORM_CONTRACT = REPOSITORY_ROOT / "specs/features/F-PLT-002-physical-contract.json"
FEATURE_INDEX = REPOSITORY_ROOT / "specs/features/README.md"
LEGACY_JAVA = [
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/domain/requirement/RequirementAnalysisCatalog.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/dataobject/preparation/RequirementAnalysisSectionDO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/dal/mysql/preparation/RequirementAnalysisSectionMapper.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisCommandService.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisQueryService.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisFilePolicyProvider.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisAttachmentReqVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisAttachmentRespVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisCompareRespVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisCompletionBlockerRespVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisSectionPatchReqVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisSectionRespVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisVersionRespVO.java",
    REPOSITORY_ROOT / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/controller/admin/preparation/vo/RequirementAnalysisWorkspaceRespVO.java",
]
LEGACY_FRONTEND = [
    REPOSITORY_ROOT / "yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/requirement-analysis/index.ts",
    REPOSITORY_ROOT / "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/RequirementAnalysisSectionCard.vue",
    REPOSITORY_ROOT / "yudao-ui/yudao-ui-admin-vue3/src/views/pms/project/project-master-detail/components/requirementAnalysisInteraction.ts",
]


class Fsol003DynamicFormAmendmentTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))
        cls.platform_contract = json.loads(PLATFORM_CONTRACT.read_text(encoding="utf-8"))
        cls.feature_index = FEATURE_INDEX.read_text(encoding="utf-8")

    def test_candidate_is_a_new_feature_ready_amendment_not_an_old_plan_reuse(self) -> None:
        self.assertEqual("BASELINE_READY", self.contract["status"])
        self.assertEqual(
            "GO_REMEDIATION_COMMIT_4D04DBD63BBD01683416563BECE31DA6CD53F849",
            self.contract["featureReadyDecision"],
        )
        self.assertEqual("FORWARD_AMENDMENT_AFTER_F_PLT_002", self.contract["contractType"])
        self.assertIn("2026-08-27 F-SOL-003 Technical Plan", self.contract["canceledInputs"])
        self.assertIn("旧Technical Plan和旧Implementation审查不能驱动实施", self.feature_spec)
        self.assertIn("全新的中文Technical Plan", self.feature_spec)
        self.assertIn("F-SOL-003", self.feature_index)
        feature_row = next(
            line for line in self.feature_index.splitlines() if line.startswith("| [F-SOL-003]")
        )
        self.assertIn("IMPLEMENTATION_COMPLETE", feature_row)

    def test_work_binding_freezes_one_revision_without_project_user_selection(self) -> None:
        binding = self.contract["workBinding"]
        self.assertEqual("project-template administrator", binding["selectionActor"])
        self.assertEqual("NONE", binding["projectUserSelection"])
        self.assertEqual(
            {
                "catalogCode",
                "catalogVersion",
                "extensionItems",
                "formConfJson",
                "formRulesJson",
            },
            set(binding["bindingConfig"]["forbidden"]),
        )
        self.assertIn("dynamicFormTemplateRevisionId", binding["bindingConfig"]["required"])
        self.assertIn("REVISION_BINDING_PUBLISH", binding["publishValidation"])
        self.assertIn("REVISION_FROZEN_USE", binding["runtimeFreeze"])
        self.assertIn("ignores later template disablement", binding["runtimeFreeze"])
        self.assertIn("项目用户无选模步骤", self.feature_spec)

    def test_sol_and_platform_have_one_truth_each(self) -> None:
        truth = self.contract["owner"]["truthBoundary"]
        self.assertIn("lifecycle", truth["SOL"])
        self.assertIn("instance schema, values", truth["PLATFORM"])
        composition = self.contract["composition"]
        self.assertEqual("PLT instance only", composition["formTruth"])
        self.assertIn("one PRE-04", composition["cardinality"])
        self.assertFalse(composition["crossContextForeignKey"])
        self.assertIn("SOL value snapshot", composition["forbiddenDuplicates"])
        self.assertEqual("CANCELED_CANDIDATE_NOT_CURRENT_TRUTH", self.contract["tables"]["sol_requirement_analysis_section"]["status"])
        self.assertFalse(self.contract["tables"]["sol_requirement_analysis_section"]["automaticDataMigration"])

    def test_platform_and_sol_contracts_agree_on_api_policy_and_lock_boundary(self) -> None:
        sol_api = self.contract["dynamicFormBusinessApi"]
        plt_api = self.platform_contract["interfaces"]["publicModuleApi"]
        self.assertEqual(sol_api["interface"], plt_api["interface"])
        self.assertEqual(set(sol_api["methods"]), set(plt_api["methods"]))
        self.assertIn("no REQUIRES_NEW", sol_api["transactionRules"])
        self.assertIn("no second idempotency record", sol_api["transactionRules"])
        self.assertEqual(
            "SOL/REQUIREMENT_ANALYSIS",
            self.contract["businessObjectPolicyProvider"]["providerKey"],
        )
        self.assertIn("no callback", self.contract["businessObjectPolicyProvider"]["lockBoundary"])
        self.assertIn(
            "NO_CONSUMER_PROVIDER_CALLBACK_AFTER_THE_FIRST_PLT_LOCK",
            self.platform_contract["lockOrder"],
        )

    def test_pre04_core_and_version_semantics_are_complete(self) -> None:
        compatibility = self.contract["pre04Compatibility"]
        self.assertEqual(11, len(compatibility["coreRichTextFields"]))
        self.assertTrue(compatibility["coreFileFieldRequired"])
        self.assertIn("exactly one", compatibility["coreFileFieldCardinality"])
        self.assertFalse(compatibility["attachmentValueRequired"])
        self.assertEqual(
            {"PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY"},
            set(compatibility["mandatoryFields"]),
        )
        axes = self.contract["stateAxes"]
        self.assertEqual({"DRAFT", "COMPLETED"}, set(axes["status"]))
        self.assertIn("one current draft and one current effective", " ".join(axes["invariants"]))
        self.assertIn("completed immutability", " ".join(self.contract["acceptance"]["realMySql"]))
        self.assertIn("declarativeValidationResult=VALID", self.contract["completion"]["platformValidation"])
        self.assertIn("not server completion facts", self.contract["completion"]["platformValidation"])

    def test_actions_mandatory_transactions_dual_versions_and_preallocated_ids_are_locked(self) -> None:
        self.assertEqual(
            "GO_NPDMS_FSOL003_FILE_LIFECYCLE_ACTION_MAPPING_20260828_01",
            self.contract["fileLifecycleActionBoundaryDecision"],
        )
        api = self.contract["dynamicFormBusinessApi"]
        self.assertEqual(
            set(self.platform_contract["interfaces"]["businessActionCodes"]),
            set(api["businessActionCodes"]),
        )
        self.assertIn("use propagation MANDATORY", " ".join(api["transactionRules"]))
        self.assertNotIn("MANDATORY or REQUIRED", " ".join(api["transactionRules"]))
        action_policy = self.contract["businessObjectPolicyProvider"]["actionPolicy"]
        self.assertEqual(set(api["businessActionCodes"]), set(action_policy))
        self.assertIn("current DRAFT", action_policy["COMPLETE"])
        self.assertIn("current effective COMPLETED", action_policy["CLONE_SOURCE"])
        self.assertIn("archive or invalidate", action_policy["FILE_WRITE"])
        self.assertIn("current project manager", action_policy["FILE_WRITE"])
        self.assertIn("PROJECT_MANAGE", action_policy["FILE_WRITE"])
        self.assertIn("pms:requirement-analysis:manage", action_policy["FILE_WRITE"])
        self.assertIn("current DRAFT with draft_marker=1", action_policy["FILE_WRITE"])
        self.assertIn("independently requires pms:file:archive", action_policy["FILE_WRITE"])
        self.assertIn("pms:file:archive", action_policy["FILE_WRITE"])
        lifecycle_mapping = self.contract["fileComposition"]["lifecycleActionMapping"]
        self.assertEqual("FILE_WRITE", lifecycle_mapping["ARCHIVE"])
        self.assertEqual("FILE_WRITE", lifecycle_mapping["INVALIDATE"])
        self.assertEqual(
            "PRE-04 page exposes no archive/invalidate button or allowedAction",
            lifecycle_mapping["uiProjection"],
        )
        self.assertEqual("DENY", lifecycle_mapping["manualDynamicFormInstance"])
        self.assertIn("pms:requirement-analysis:manage", lifecycle_mapping["functionalPermissions"])
        self.assertIn("pms:file:archive", lifecycle_mapping["functionalPermissions"])
        headers = self.contract["http"]["requiredHeaders"]
        self.assertEqual({"Idempotency-Key", "If-Match", "X-SOL-If-Match"}, set(headers["complete"]))
        self.assertIn("PLT dynamic-form instanceVersion", self.contract["http"]["versionHeaders"]["complete"])
        self.assertIn("SOL root", self.contract["http"]["versionHeaders"]["complete"])
        initial = self.contract["transactions"]["createInitial"]
        self.assertIn("caller preallocation", initial["idPolicy"])
        self.assertIn("never committed with null", initial["commitInvariant"])
        self.assertIn("no post-insert ID backfill", initial["idPolicy"])

    def test_clone_and_complete_are_atomic_and_revalidate_complete_facts(self) -> None:
        clone = self.contract["transactions"]["createNextDraft"]
        self.assertIn("target references", clone["atomic"])
        self.assertIn("outbox", clone["atomic"])
        complete = self.contract["transactions"]["complete"]
        self.assertEqual("no SOL callback after the first PLT lock", complete["rule"])
        self.assertIn(
            "N cloned references produce N events",
            " ".join(self.contract["acceptance"]["realMySql"]),
        )
        self.assertEqual("NONE", self.contract["fileComposition"]["solSnapshot"])
        self.assertIn("same slotKey", self.contract["fileComposition"]["unknownResponse"])

    def test_legacy_and_existing_candidate_use_are_explicit(self) -> None:
        reuse = self.contract["legacyAndCandidateReuse"]
        self.assertIn("sol_preparation draft/effective version axes", reuse["directReuse"])
        self.assertIn("SOL command/query/fact implementations", reuse["adjust"])
        self.assertIn("sol_requirement_analysis_section", reuse["notCurrentTruth"])
        self.assertIn("pms_eng_requirement backend/frontend/API/data/state/menu", reuse["legacyUnchanged"])
        self.assertIn("built-in super_admin access", reuse["legacyUnchanged"])

    def test_superseded_fixed_section_runtime_is_marked_deprecated(self) -> None:
        self.assertIn("统一标记为`DEPRECATED`", self.feature_spec)
        self.assertIn("RequirementAnalysisDynamicFormCommandService", self.feature_spec)
        self.assertIn("不得再承接新需求", self.feature_spec)
        for path in LEGACY_JAVA:
            self.assertIn("@Deprecated", path.read_text(encoding="utf-8"), path.as_posix())
        for path in LEGACY_FRONTEND:
            self.assertIn("@deprecated", path.read_text(encoding="utf-8"), path.as_posix())

        active_command = (
            REPOSITORY_ROOT
            / "pms-module-engineering/src/main/java/cn/iocoder/yudao/module/pms/engineering/service/requirement/RequirementAnalysisDynamicFormCommandService.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn("@Deprecated", active_command)


if __name__ == "__main__":
    unittest.main()
