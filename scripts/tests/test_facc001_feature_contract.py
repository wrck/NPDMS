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
    source = contract.get("physicalDelta", {}).get("tables", {}).get("acc_project_deliverable_source_version", {})
    if source.get("currentMarker") != "case when relation_status='CURRENT' then 1 else null end":
        errors.append("source-current")
    events = contract.get("events", {}).get("AcceptanceReportVersionChanged", {})
    if events.get("changeTypes") != ["EFFECTIVE", "REPLACED", "REVOKED"]:
        errors.append("event-types")
    if not any("attachments[{sequence" in fact for fact in events.get("facts", [])):
        errors.append("attachments")
    if contract.get("transactionBoundary", {}).get("archiveFailureRollsBackReport") is not False:
        errors.append("compensation")
    if contract.get("moduleApis", {}).get("AcceptanceScopeBindingApi", {}).get("reportTrigger") != "FORBIDDEN":
        errors.append("binding")
    if "V17_UNCHANGED_EXACT_FORWARD_BINDING_ONLY" not in contract.get("acceptance", []):
        errors.append("legacy")
    provisioning = contract.get("activityProvisioning", {})
    if provisioning.get("existingProjectNoMatchingTask") != "UNCHANGED_NOT_INFERRED" or \
            provisioning.get("partialDuplicateOrAmbiguous") != "FAIL_BATCH":
        errors.append("provisioning")
    for marker in ("Feature Ready：`NOT_READY", "ACC-03@V1=FULL", "ACC-04@V1=PARTIAL", "Q-FCOM-002"):
        if marker not in spec:
            errors.append(f"spec:{marker}")
    for marker in ("REUSE-01", "REUSE-11", "DO_NOT_REUSE_NEW_ONLY", "T-INITIAL-ACCEPT", "D-FINAL-REPORT"):
        if marker not in audit and marker not in json.dumps(contract, ensure_ascii=False):
            errors.append(f"audit:{marker}")
    return errors


class Facc001FeatureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.spec = SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        cls.audit = AUDIT.read_text(encoding="utf-8")

    def test_candidate_is_complete_but_not_preapproved(self) -> None:
        self.assertEqual([], contract_errors(self.contract, self.spec, self.audit))
        self.assertEqual("CANDIDATE_NOT_READY", self.contract["status"])

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

    def test_q_fcom002_only_blocks_out_of_scope_path(self) -> None:
        question = self.contract["openQuestions"]["Q-FCOM-002"]
        self.assertEqual("BLOCKED_BY_SPEC", question["status"])
        self.assertEqual("OUT_OF_SCOPE_ONLY", question["featurePathImpact"])


if __name__ == "__main__":
    unittest.main()
