import json
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "specs/features/F-ACC-002-satisfaction-questionnaire-result-and-deliverable-sync.md"
CONTRACT = ROOT / "specs/features/F-ACC-002-physical-contract.json"
AUDIT = ROOT / "specs/features/F-ACC-002-legacy-reuse-audit.md"
PLAN = ROOT / "docs/superpowers/plans/2026-08-30-f-acc-002-satisfaction-questionnaire-result-deliverable-sync.md"


def contract_errors(contract: dict, spec: str, audit: str) -> list[str]:
    errors = []
    if contract.get("requirements") != {
        "ACC-02@V1": "FULL", "ACC-04@V1": "PARTIAL_SATISFACTION_SOURCE_ONLY"
    }:
        errors.append("coverage")
    if contract.get("status") != "BASELINE_READY" or \
            contract.get("featureReadyDecision") != \
            "GO_145e4a61ea936d0679f2ec41a7d412975572e5a3":
        errors.append("feature-ready-state")
    questionnaire = contract.get("questionnaireConfiguration", {})
    if questionnaire.get("questionTypes") != ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "RATING", "TEXT"] or \
            questionnaire.get("scoringStrategies") != ["SUM_V1", "WEIGHTED_AVERAGE_V1"] or \
            questionnaire.get("multipleChoiceBounds") != "1<=minSelections<=maxSelections<=optionsCount" or \
            questionnaire.get("multipleChoiceMaxReachableScore") != \
            "MAX_AVERAGE_OVER_ALL_LEGAL_DISTINCT_SELECTION_SETS" or \
            questionnaire.get("rounding") != "EXACT_DECIMAL_INTERMEDIATE_FINAL_ONCE_THEN_COMPARE_THRESHOLD":
        errors.append("configurable-questionnaire")
    identity = contract.get("identityRules", {})
    if identity.get("remediationTrigger") != "ACC/SatisfactionRemediationFact" or \
            identity.get("taskRevision") != "FIRST_1_NEXT_PRIOR_PLUS_1" or \
            identity.get("sourceMeaning") != "ORIGINAL_BUSINESS_TRIGGER_IMMUTABLE_ACROSS_COLLECTION_CHAIN":
        errors.append("remediation-identity")
    projection = contract.get("deliverableProjection", {})
    if projection.get("rootLookup") != "tenant_id+project_id+deliverable_code=D-SAT-REPORT" or \
            projection.get("rootTaskCode") != "T-SAT-SURVEY" or \
            projection.get("missingDuplicateMismatch") != "PENDING_COMPENSATION_FAIL_CLOSED":
        errors.append("deliverable-root")
    if not projection.get("recordedBeforeCurrent", "").startswith(
            "REVALIDATE_SatisfactionResultFactApi_BY_RESULT_ID_AND_VERSION") or \
            projection.get("lateRecordedAfterInvalidatedOrNewer") != \
            "KEEP_NON_CURRENT_HISTORY_ACTIVE_FILES_AND_ARCHIVE_ELIGIBILITY" or \
            projection.get("invalidatedCurrentClear") != \
            "ONLY_IF_ROOT_POINTS_TO_EXACT_RESULT_AND_VERSION":
        errors.append("projection-monotonicity")
    initializer = contract.get("moduleApis", {}).get("SatisfactionTaskInitializationApi", {})
    if initializer.get("transactionPropagation") != "MANDATORY" or \
            "triggerObjectType" not in initializer.get("inputs", []):
        errors.append("initializer")
    trigger = contract.get("triggerSources", {}).get("v1PositivePath", {})
    if trigger.get("factType") != "AcceptanceActivityCompletionFact" or \
            trigger.get("applicableTiming") != "AFTER_INITIAL_ACCEPTANCE" or \
            trigger.get("defaultAssignee") != "CURRENT_PROJECT_MANAGER_IF_UNASSIGNED":
        errors.append("positive-trigger")
    paths = {(item.get("method"), item.get("path")) for item in contract.get("restApis", [])}
    if ("POST", "/api/v1/pms/satisfaction-questionnaire-templates/{id}/revisions/{revisionId}/actions/publish") not in paths:
        errors.append("template-publish")
    if ("POST", "/api/v1/pms/satisfaction-results/{id}/actions/invalidate") not in paths:
        errors.append("result-invalidation")
    project_scope = contract.get("moduleApis", {}).get("ProjectScopeApi", {})
    if project_scope.get("provider") != "ProjectScopeApiImpl" or \
            project_scope.get("scopeVersionSource") != "treeVersion" or \
            "lockAndRevalidate" not in project_scope.get("methods", []) or \
            "PROJECT_EDIT" not in project_scope.get("actions", []):
        errors.append("project-scope")
    export_api = contract.get("moduleApis", {}).get("ExportTaskApi", {})
    export_provider = contract.get("moduleApis", {}).get("ExportBusinessDataProvider", {})
    if export_api.get("methods") != ["request", "getFact", "retry"] or \
            "FAILED_RETRYABLE_TO_REQUESTED" not in export_api.get("retry", "") or \
            export_provider.get("owner") != "PLT" or \
            export_provider.get("businessFactOwner") != "ACC" or \
            export_provider.get("providerKey") != "ACC/SATISFACTION_RESULT" or \
            export_provider.get("revalidationPoints") != ["REQUEST", "GENERATION", "RETRY", "DOWNLOAD"]:
        errors.append("platform-export-owner")
    file_api = contract.get("moduleApis", {}).get("FileArtifactApi", {})
    if file_api.get("additiveMethods") != ["initializeBusinessGrantUpload", "completeBusinessGrantUpload",
                                           "lockAndRevalidateBusinessGrantFiles", "createGeneratedBusinessFile"] or \
            file_api.get("forbidden") != "FAKE_LOGIN_OR_BYPASS_FILE_POLICY":
        errors.append("external-file")
    grant_upload = file_api.get("businessGrantUpload", {})
    reservation = grant_upload.get("responseReservation", {})
    if reservation.get("persistence") != "PlatformCommandExecutionApi" or \
            reservation.get("replay") != "SAME_RESPONSE_ID" or \
            reservation.get("finalSubmission") != "USE_RESERVED_RESPONSE_ID_NO_NEW_ID" or \
            grant_upload.get("serverSlot", {}).get("clientGenerated") != "FORBIDDEN" or \
            grant_upload.get("policyQueries") != ["BusinessGrantUploadInitializePolicyQuery",
                                                  "BusinessGrantUploadCompletePolicyQuery",
                                                  "BusinessGrantFileRevalidationQuery"] or \
            grant_upload.get("providerDefaults") != "FAIL_CLOSED" or \
            grant_upload.get("grantIssuerUserId") != \
            "POSITIVE_LONG_FROM_CURRENT_GRANT_CREATOR_NOT_UPDATER_OR_CLIENT" or \
            grant_upload.get("finalRevalidation") != \
            "CANONICAL_PLT_FACT_MATCHES_ARTIFACT_VERSION_REFERENCE_GRANT_RESPONSE_POLICY_SCOPE_AND_SERVER_SLOT" or \
            grant_upload.get("clientFileFact") != "HANDLE_ONLY_NEVER_DIRECTLY_PERSISTED" or \
            "lockAndRevalidateBusinessGrantFiles" not in grant_upload.get("providerMethods", []) or \
            grant_upload.get("audit", {}).get("actor") != \
            "grantIssuerUserId_INTERNAL_RESPONSIBILITY_ONLY" or \
            "SECURITY_CONTEXT" not in grant_upload.get("audit", {}).get("forbidden", ""):
        errors.append("business-grant-owner-binding")
    generated = file_api.get("generatedBusinessFile", {})
    if generated.get("transactionPropagation") != "MANDATORY" or \
            generated.get("actor") != "RESULT_CURRENT_ASSIGNEE_SERVER_FROZEN" or \
            generated.get("permission") != "pms:file:upload" or \
            generated.get("policyQuery") != "GeneratedBusinessFilePolicyRevalidationQuery" or \
            generated.get("providerDispatch") != "ACC/SATISFACTION_RESULT_UNIQUE" or \
            generated.get("inputs", [])[4:8] != ["collectionTaskId", "questionnaireId", "responseId", "expectedTaskVersion"] or \
            "PROJECT_SCOPE_EDIT_TREE_VERSION_MATCH" not in generated.get("ownerRevalidation", []) or \
            generated.get("storageCompensation") != \
            "REUSE_FileUploadSession_AND_FileUploadCompensationService":
        errors.append("generated-result-document")
    result = contract.get("physicalDelta", {}).get("tables", {}).get("acc_satisfaction_result", {})
    if result.get("currentMarker") != \
            "case when result_status='EFFECTIVE' and passed=1 and effective_to is null then 1 else null end":
        errors.append("result-current")
    task = contract.get("physicalDelta", {}).get("tables", {}).get("acc_satisfaction_collection_task", {})
    if "tenant_id+collection_key+task_revision_no" not in task.get("uniqueKeys", []) or \
            "trigger_object_type" not in task.get("fields", []):
        errors.append("task-revision")
    remediation = contract.get("physicalDelta", {}).get("tables", {}).get("acc_satisfaction_remediation_fact", {})
    if remediation.get("immutable") is not True:
        errors.append("remediation-fact")
    if contract.get("transactionBoundary", {}).get("archiveFailureRollsBackResult") is not False:
        errors.append("compensation")
    transaction = contract.get("transactionBoundary", {})
    if transaction.get("generatedFileFailure") != \
            "RESPONSE_KEPT_TASK_PENDING_DECISION_ZERO_RESULT_RESULT_FILE_SUCCESS_IDEMPOTENCY_OUTBOX" or \
            "NO_SECOND_DOCUMENT" not in transaction.get("storageRollback", ""):
        errors.append("generated-file-rollback")
    if transaction.get("responseReservation") != \
            "SAME_GRANT_REQUEST_ID_REPLAYS_SERVER_RESPONSE_ID_DIFFERENT_DIGEST_CONFLICT" or \
            transaction.get("finalResponseIdentity") != \
            "PLATFORM_RESERVATION_ID_PLUS_PLT_CANONICAL_GRANT_FILE_REVALIDATION_NO_NEW_ID":
        errors.append("business-grant-response-identity")
    event = contract.get("events", {}).get("SatisfactionResultVersionChanged", {})
    if event.get("changeTypes") != ["RECORDED", "INVALIDATED"] or \
            not any("files[{role,sequence,sourceSequence,artifactId" in fact and "sha256" in fact
                    for fact in event.get("facts", [])) or \
            "invalidatedByUserId" not in event.get("facts", []):
        errors.append("event")
    file_policies = contract.get("filePolicies", {})
    if "sha256" not in file_policies.get("storedFacts", []) or \
            "fileHash" in file_policies.get("storedFacts", []) or \
            file_policies.get("physicalFieldMapping", {}).get("sha256") != "file_hash":
        errors.append("public-file-fact")
    source_attachment = contract.get("physicalDelta", {}).get("tables", {}).get(
        "acc_project_deliverable_source_attachment", {})
    if source_attachment.get("publicToPhysical", {}).get("sha256") != "file_hash":
        errors.append("file-physical-mapping")
    export_task = contract.get("physicalDelta", {}).get("tables", {}).get("plt_export_task", {})
    export_audit = contract.get("physicalDelta", {}).get("tables", {}).get("plt_export_audit", {})
    if export_task.get("owner") != "PLT" or \
            "failure_retryable" not in export_task.get("fields", []) or \
            export_task.get("stateRules") != "ONLY_FAILED_RETRYABLE_TO_REQUESTED_AND_ONLY_SUCCEEDED_TO_EXPIRED" or \
            "RETRY_REQUESTED" not in export_audit.get("actions", []):
        errors.append("platform-export-physical")
    disposition = contract.get("legacyDisposition", {})
    if disposition.get("v17V18CompletionCertificateStack") != "DO_NOT_REUSE_PRESERVE_EXISTING" or \
            "satisfactionScore" not in disposition.get("completionCertificateForbiddenFacts", []):
        errors.append("completion-certificate")
    if contract.get("legacyDisposition", {}).get("currentForward") != "NEW_ONLY_EXPLICIT_COMMANDS":
        errors.append("legacy")
    for marker in ("文档状态：`BASELINE`", "Feature Ready：`READY`", "ACC-02@V1=FULL",
                   "PARTIAL_SATISFACTION_SOURCE_ONLY", "T-SAT-SURVEY", "SatisfactionRemediationFact",
                   "actions/invalidate", "createGeneratedBusinessFile", "ProjectScopeApi/Impl", "sha256", "pms_acc_completion_certificate",
                   "ExportTaskApi.request/getFact/retry", "FAILED + failure_retryable=true", "双向乱序", "AI-MIG-000",
                   "PlatformCommandExecutionApi", "lockAndRevalidateBusinessGrantFiles", "grantIssuerUserId"):
        if marker not in spec:
            errors.append(f"spec:{marker}")
    for marker in ("REUSE-01", "REUSE-17", "REUSE-18", "PRESERVE_RAW", "ProjectWorkBindingFactApi",
                   "ProjectScopeApiImpl", "D-SAT-REPORT", "PlatformOutboxDeliveryApi",
                   "pms_acc_completion_certificate", "satisfactionScore", "NO_RUNTIME_CARRIER / BUILD_APPROVED_ADR_0042"):
        if marker not in audit:
            errors.append(f"audit:{marker}")
    return errors


class Facc002FeatureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        cls.audit = AUDIT.read_text(encoding="utf-8")
        cls.plan = PLAN.read_text(encoding="utf-8")

    def test_baseline_contract_matches_independent_go(self) -> None:
        self.assertEqual([], contract_errors(self.contract, self.spec, self.audit))

    def test_rejects_original_trigger_reuse_for_remediation(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["identityRules"]["remediationTrigger"] = "ORIGINAL_BUSINESS_TRIGGER"
        self.assertIn("remediation-identity", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_unreachable_multiple_choice_contract(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["questionnaireConfiguration"]["multipleChoiceMaxReachableScore"] = "MAX_SINGLE_OPTION_SCORE"
        self.assertIn("configurable-questionnaire", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_missing_task_revision_unique_key(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["physicalDelta"]["tables"]["acc_satisfaction_collection_task"]["uniqueKeys"].remove(
            "tenant_id+collection_key+task_revision_no")
        self.assertIn("task-revision", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_arbitrary_deliverable_root(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["deliverableProjection"]["rootLookup"] = "ANY_PROJECT_DELIVERABLE"
        self.assertIn("deliverable-root", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_late_recorded_restoring_invalidated_result(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["deliverableProjection"]["recordedBeforeCurrent"] = "SET_CURRENT_DIRECTLY"
        self.assertIn("projection-monotonicity", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_result_current_without_pass_guard(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["physicalDelta"]["tables"]["acc_satisfaction_result"]["currentMarker"] = (
            "case when effective_to is null then 1 else null end")
        self.assertIn("result-current", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_external_upload_auth_bypass(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["forbidden"] = "FAKE_LOGIN_ALLOWED"
        self.assertIn("external-file", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_unbound_response_or_client_file_fact(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["businessGrantUpload"]["responseReservation"]["replay"] = \
            "ALLOCATE_NEW_RESPONSE_ID"
        self.assertIn("business-grant-owner-binding", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["businessGrantUpload"]["clientFileFact"] = \
            "TRUST_AND_PERSIST"
        self.assertIn("business-grant-owner-binding", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_client_or_updater_as_grant_audit_actor(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["businessGrantUpload"]["grantIssuerUserId"] = \
            "CLIENT_ACTOR_OR_GRANT_UPDATER"
        self.assertIn("business-grant-owner-binding", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["businessGrantUpload"]["serverSlot"]["clientGenerated"] = \
            "ALLOWED"
        self.assertIn("business-grant-owner-binding", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_generated_document_without_owner_or_storage_compensation(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["generatedBusinessFile"]["actor"] = "JOB_USER"
        self.assertIn("generated-result-document", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["transactionBoundary"]["storageRollback"] = "IGNORE_ORPHAN_OBJECT"
        self.assertIn("generated-file-rollback", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_missing_real_trigger_or_template_publish_path(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["triggerSources"]["v1PositivePath"]["factType"] = "TODO_COMPLETED"
        self.assertIn("positive-trigger", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["restApis"] = [item for item in mutated["restApis"] if "actions/publish" not in item["path"]]
        self.assertIn("template-publish", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_archive_failure_rolling_back_result(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["transactionBoundary"]["archiveFailureRollsBackResult"] = True
        self.assertIn("compensation", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_legacy_current_truth(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["legacyDisposition"]["currentForward"] = "MIGRATE_CALLBACK_SCORE"
        self.assertIn("legacy", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_missing_result_invalidation_command(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["restApis"] = [item for item in mutated["restApis"]
                               if "actions/invalidate" not in item["path"]]
        self.assertIn("result-invalidation", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_abstract_or_local_project_scope(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["ProjectScopeApi"]["provider"] = "ACC_LOCAL_SCOPE"
        self.assertIn("project-scope", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_public_file_hash_alias_or_missing_physical_mapping(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["filePolicies"]["storedFacts"][-1] = "fileHash"
        self.assertIn("public-file-fact", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        del mutated["physicalDelta"]["tables"]["acc_project_deliverable_source_attachment"]["publicToPhysical"]
        self.assertIn("file-physical-mapping", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_completion_certificate_as_new_result_truth(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["legacyDisposition"]["v17V18CompletionCertificateStack"] = "DIRECT_REUSE"
        self.assertIn("completion-certificate", contract_errors(mutated, self.spec, self.audit))

    def test_rejects_missing_platform_export_owner_or_retry_watermark(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["ExportTaskApi"]["methods"].remove("retry")
        self.assertIn("platform-export-owner", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["physicalDelta"]["tables"]["plt_export_task"]["fields"].remove("failure_retryable")
        self.assertIn("platform-export-physical", contract_errors(mutated, self.spec, self.audit))

    def test_plan_uses_one_submission_channel_per_questionnaire(self) -> None:
        self.assertIn("revision1只走现场协助", self.plan)
        self.assertIn("revision2后只走匿名渠道", self.plan)
        self.assertNotIn("客户问卷第一次因未达标", self.plan)


if __name__ == "__main__":
    unittest.main()
