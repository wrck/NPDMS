import json
import unittest
from copy import deepcopy
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "specs/features/F-ACC-001-acceptance-report-version-and-deliverable-sync.md"
CONTRACT = ROOT / "specs/features/F-ACC-001-physical-contract.json"
AUDIT = ROOT / "specs/features/F-ACC-001-legacy-reuse-audit.md"


def contract_errors(contract: dict, spec: str, audit: str) -> list[str]:
    errors = []
    if contract.get("requirements") != {"ACC-03@V1": "FULL", "ACC-04@V1": "PARTIAL_REPORT_SOURCES_ONLY"}:
        errors.append("coverage")
    report = contract.get("physicalDelta", {}).get("tables", {}).get("acc_acceptance_report_version", {})
    if report.get("currentMarker") != "case when report_status='EFFECTIVE' and effective_to is null then 1 else null end":
        errors.append("report-current")
    if "publisher_user_id" not in report.get("fields", []) or \
            report.get("publisherRule") != "DRAFT_NULL_EFFECTIVE_AND_HISTORY_IMMUTABLE_SERVER_AUTHENTICATED_USER":
        errors.append("publisher")
    source = contract.get("physicalDelta", {}).get("tables", {}).get("acc_project_deliverable_source_version", {})
    if source.get("currentMarker") != "case when relation_status='CURRENT' then 1 else null end":
        errors.append("source-current")
    events = contract.get("events", {}).get("AcceptanceReportVersionChanged", {})
    if events.get("changeTypes") != ["EFFECTIVE", "REPLACED", "REVOKED"]:
        errors.append("event-types")
    if not any("attachments[{sequence,fileArtifactId,fileVersionNo,referenceKey,artifactVersion,referenceVersion,availabilityVersion,scopeVersion,fileHash}]" == fact
               for fact in events.get("facts", [])):
        errors.append("attachments")
    if "publisherActorUserId" not in events.get("facts", []):
        errors.append("event-publisher")
    if contract.get("transactionBoundary", {}).get("archiveFailureRollsBackReport") is not False:
        errors.append("compensation")
    if contract.get("moduleApis", {}).get("AcceptanceScopeBindingApi", {}).get("reportTrigger") != "FORBIDDEN":
        errors.append("binding")
    if "V17_UNCHANGED_EXACT_FORWARD_BINDING_ONLY" not in contract.get("acceptance", []):
        errors.append("legacy")
    module_apis = contract.get("moduleApis", {})
    initializer = module_apis.get("AcceptanceActivityInitializationApi", {})
    if initializer.get("transactionPropagation") != "MANDATORY" or \
            initializer.get("newProjectOrder") != "TASKS_NON_ACC_CONTRACTS_MILESTONES_DELIVERABLE_ROOT_ACTIVITY_ACC_CONTRACT":
        errors.append("initializer")
    file_api = module_apis.get("FileArtifactApi", {})
    if file_api.get("attachExistingVersionsTarget") != \
            "ACC/ACCEPTANCE_REPORT_VERSION/*/ACCEPTANCE_REPORT_ATTACHMENT_ADDITIVE_ONLY" or \
            file_api.get("archiveModel") != "ACTIVE_ATTACHMENT_REFERENCE_PLUS_SEPARATE_ARCHIVED_REFERENCE" or \
            file_api.get("archivePermission") != "pms:file:archive" or \
            file_api.get("tenantIsolation") != "REQUIRED" or \
            "actorUserId" not in file_api.get("archiveInputs", []) or \
            file_api.get("archiveActorSource") != "ACC_REPORT_VERSION_PUBLISHER_USER_ID":
        errors.append("file-api")
    transaction = contract.get("transactionBoundary", {})
    if transaction.get("outboxWriteApi") != "PlatformCommandExecutionApi" or \
            transaction.get("outboxDeliveryApi") != "PlatformOutboxDeliveryApi" or \
            transaction.get("closureRecheckDelivery") != "NOT_CLAIMED_BY_THIS_FEATURE":
        errors.append("outbox-delivery")
    if transaction.get("accCompletionPermissionCheck") != \
            "AFTER_ACC_EXECUTION_CONTRACT_RESOLUTION_BEFORE_PROVIDER_OR_STATE_WRITE_REQUIRE_BOTH":
        errors.append("completion-permissions")
    if module_apis.get("FileBusinessObjectPolicyProvider", {}).get("scopeVersionSource") != \
            "ProjectScopeApi.treeVersion":
        errors.append("file-policy")
    required_public_fields = {"file_artifact_id", "file_version_no", "reference_key", "artifact_version",
                              "reference_version", "availability_version", "scope_version", "file_hash"}
    for table_name in ("acc_acceptance_report_attachment", "acc_project_deliverable_source_attachment"):
        attachment = contract.get("physicalDelta", {}).get("tables", {}).get(table_name, {})
        if not required_public_fields.issubset(set(attachment.get("fields", []))) or \
                "file_version_id" in attachment.get("fields", []):
            errors.append(f"public-file-facts:{table_name}")
    provisioning = contract.get("activityProvisioning", {})
    if provisioning.get("existingProjectNoMatchingTask") != "UNCHANGED_NOT_INFERRED" or \
            provisioning.get("partialDuplicateOrAmbiguous") != "FAIL_BATCH":
        errors.append("provisioning")
    if provisioning.get("bothTerminal") != "UNCHANGED_PRESERVE_HISTORY" or \
            provisioning.get("mixedTerminalNonTerminal") != "FAIL_BATCH":
        errors.append("terminal-partition")
    for marker in ("Feature Ready：`READY / GO`", "Q-GOV-20260901-001`已由master修订011关闭",
                   "ACC-03@V1=FULL", "ACC-04@V1=PARTIAL", "Q-FCOM-002",
                   "ADR-0040", "ACCEPTANCE_REPORT_ARCHIVE", "AcceptanceActivityInitializationApi"):
        if marker not in spec:
            errors.append(f"spec:{marker}")
    for marker in ("REUSE-01", "REUSE-14", "DO_NOT_REUSE_NEW_ONLY", "T-INITIAL-ACCEPT", "D-FINAL-REPORT",
                   "ExistingFileReferenceTarget", "FileQueryService"):
        if marker not in audit and marker not in json.dumps(contract, ensure_ascii=False):
            errors.append(f"audit:{marker}")
    return errors


class Facc001FeatureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        cls.audit = AUDIT.read_text(encoding="utf-8")

    def test_source_feature_ready_is_adopted_by_master_baseline(self) -> None:
        self.assertEqual([], contract_errors(self.contract, self.spec, self.audit))
        self.assertEqual("BASELINE_READY", self.contract["status"])
        self.assertIsNone(self.contract["masterBlocker"])
        self.assertEqual("GO_BDE0FEAC019BAF820634ECC6A0E88272672B601D",
                         self.contract["sourceFeatureReadyDecision"])
        self.assertEqual("GO_701BDF701539A0D65F3C67EB10AA0605DE58C4A7",
                         self.contract["sourceTechnicalPlanPrerequisiteAmendment"])

    def test_gate_rejects_draft_as_current(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["physicalDelta"]["tables"]["acc_acceptance_report_version"]["currentMarker"] = (
            "case when effective_to is null then 1 else null end")
        self.assertIn("report-current", contract_errors(mutated, self.spec, self.audit))

    def test_gate_rejects_missing_revoke_event_or_attachment_collection(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["events"]["AcceptanceReportVersionChanged"]["changeTypes"].remove("REVOKED")
        self.assertIn("event-types", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["events"]["AcceptanceReportVersionChanged"]["facts"][-1] = "fileArtifactId"
        self.assertIn("attachments", contract_errors(mutated, self.spec, self.audit))

    def test_gate_rejects_missing_archive_actor_or_outbox_delivery_boundary(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["archiveInputs"].remove("actorUserId")
        self.assertIn("file-api", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["events"]["AcceptanceReportVersionChanged"]["facts"].remove("publisherActorUserId")
        self.assertIn("event-publisher", contract_errors(mutated, self.spec, self.audit))
        mutated = deepcopy(self.contract)
        mutated["transactionBoundary"]["closureRecheckDelivery"] = "MARKED_DELIVERED"
        self.assertIn("outbox-delivery", contract_errors(mutated, self.spec, self.audit))

    def test_gate_rejects_internal_file_ids_or_single_reference_archive(self) -> None:
        mutated = deepcopy(self.contract)
        fields = mutated["physicalDelta"]["tables"]["acc_acceptance_report_attachment"]["fields"]
        if "file_version_no" in fields:
            fields[fields.index("file_version_no")] = "file_version_id"
        elif "file_version_id" not in fields:
            fields.append("file_version_id")
        self.assertTrue(any(error.startswith("public-file-facts")
                            for error in contract_errors(mutated, self.spec, self.audit)))
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["FileArtifactApi"]["archiveModel"] = "ARCHIVE_ATTACHMENT_REFERENCE_IN_PLACE"
        self.assertIn("file-api", contract_errors(mutated, self.spec, self.audit))

    def test_gate_rejects_report_triggered_scope_binding(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["moduleApis"]["AcceptanceScopeBindingApi"]["reportTrigger"] = "REPORT_EFFECTIVE"
        self.assertIn("binding", contract_errors(mutated, self.spec, self.audit))

    def test_physical_tables_match_approved_delta(self) -> None:
        self.assertEqual({
            "acc_acceptance", "acc_acceptance_report_version", "acc_acceptance_report_attachment",
            "acc_project_deliverable", "acc_project_deliverable_source_version",
            "acc_project_deliverable_source_attachment"
        }, set(self.contract["physicalDelta"]["tables"]))
        self.assertEqual("FORBIDDEN", self.contract["physicalDelta"]["crossContextForeignKeys"])

    def test_owner_completion_and_permissions_are_explicit(self) -> None:
        api = self.contract["moduleApis"]["AcceptanceActivityCompletionFactApi"]
        self.assertEqual("MANDATORY", api["transactionPropagation"])
        self.assertEqual(4, len(self.contract["permissions"]))
        self.assertIn("pms:project-task:execute", next(
            item for item in self.contract["restApis"] if item["path"].endswith("actions/complete"))["permissions"])

    def test_legacy_audit_forbids_v17_or_name_inference(self) -> None:
        self.assertIn("`DO_NOT_REUSE`", self.audit)
        self.assertIn("NEW_ONLY", self.audit)
        self.assertEqual("FAIL_BATCH", self.contract["legacyDisposition"]["missingAmbiguousOrPartial"])
        self.assertIn("NAME", self.contract["legacyDisposition"]["forbiddenInference"])

    def test_activity_provisioning_has_new_and_existing_project_paths(self) -> None:
        provisioning = self.contract["activityProvisioning"]
        self.assertIn("ACC_INITIALIZER", provisioning["newProject"])
        self.assertIn("APPEND_ACC_CURRENT_CONTRACT", provisioning["existingProjectAction"])
        self.assertEqual("PROJECT_TASK_CONTRACT_ACTIVITY_AND_DELIVERABLE_ALL_OR_NOTHING",
                         provisioning["atomicity"])

    def test_activity_provisioning_rejects_mixed_terminal_cutover(self) -> None:
        mutated = deepcopy(self.contract)
        mutated["activityProvisioning"]["mixedTerminalNonTerminal"] = "UNCHANGED"
        self.assertIn("terminal-partition", contract_errors(mutated, self.spec, self.audit))

    def test_q_fcom002_only_blocks_out_of_scope_path(self) -> None:
        question = self.contract["openQuestions"]["Q-FCOM-002"]
        self.assertEqual("BLOCKED_BY_SPEC", question["status"])
        self.assertEqual("OUT_OF_SCOPE_ONLY", question["featurePathImpact"])


if __name__ == "__main__":
    unittest.main()
