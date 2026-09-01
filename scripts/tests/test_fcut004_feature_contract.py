import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
API = ROOT / "specs/features/F-CUT-004-api-contract.json"
PHYSICAL = ROOT / "specs/features/F-CUT-004-physical-contract.json"
APPROVAL = ROOT / "specs/features/F-CUT-005-approval-owner-contract.json"
TASK_PHYSICAL = ROOT / "specs/features/F-CUT-002-physical-contract.json"
TRACE = ROOT / "docs/traceability/domain-entity-migration-contract.json"
PHASE2 = ROOT / "docs/traceability/phase2-contract-map.md"


class FCut004FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.api = json.loads(API.read_text(encoding="utf-8"))
        cls.physical = json.loads(PHYSICAL.read_text(encoding="utf-8"))
        cls.approval = json.loads(APPROVAL.read_text(encoding="utf-8"))
        cls.task_physical = json.loads(TASK_PHYSICAL.read_text(encoding="utf-8"))
        cls.trace = json.loads(TRACE.read_text(encoding="utf-8"))
        cls.phase2 = PHASE2.read_text(encoding="utf-8")

    def test_p4_submit_and_p5_start_are_atomic_without_approval_ownership_drift(self):
        self.assertEqual("F-CUT-005", self.approval["ownerFeatureId"])
        start = self.approval["operations"]["start"]
        self.assertIn("MANDATORY", start["transaction"])
        self.assertIn("No production Provider, Fake, fallback", self.approval["productionBoundary"])
        self.assertEqual(
            {"PENDING", "PAUSED_SOURCE_INVALIDATED", "APPROVED", "REJECTED"},
            set(self.approval["enums"]["ApprovalStatus"]),
        )
        self.assertNotIn("TERMINATED", json.dumps(self.approval))

    def test_task_physical_contract_reaches_p5_and_p6_with_single_transition_owners(self):
        self.assertIn("P5", self.task_physical["enums"]["stageCode"])
        self.assertIn("P6", self.task_physical["enums"]["stageCode"])
        self.assertIn("APPROVING", self.task_physical["enums"]["taskStatus"])
        self.assertIn("CLOSURE_IN_PROGRESS", self.task_physical["enums"]["taskStatus"])
        owners = self.task_physical["downstreamStageForwardContract"]["writeOwners"]
        self.assertEqual("F-CUT-004 submit command", owners["P4_TO_P5"])
        self.assertEqual("F-CUT-004 source invalidation command", owners["P5_TO_P4_SOURCE_INVALIDATED"])
        self.assertEqual("F-CUT-005 final reject command", owners["P5_TO_P4"])
        self.assertEqual("F-CUT-005 all-nodes-approved command", owners["P5_TO_P6"])

    def test_plan_revision_lifecycle_does_not_impersonate_approval(self):
        self.assertEqual(
            ["DRAFT", "SUBMITTED", "INVALIDATED"],
            self.physical["lifecycles"]["planRevision"],
        )
        self.assertIn("F-CUT-005", self.physical["lifecycles"]["approvalProjectionOwner"])

    def test_only_sds_plan_tables_are_cut_business_tables(self):
        names = {table["name"] for table in self.physical["tables"]}
        self.assertEqual(
            {"cut_plan_revision", "cut_step", "cut_cutover_support_arrangement"},
            names,
        )
        self.assertIn("no fourth CUT business table", self.physical["additionalFacts"]["supportArrangementAudit"])

    def test_grade_d_never_requires_a_checklist(self):
        grade_d = self.physical["tables"][0]["conditionalContracts"]["NEW_PLATFORM_D"]
        self.assertIn("checklist_id/version are null", grade_d)
        self.assertIn("configuration", grade_d)
        self.assertIn("OPERATION/ROLLBACK", grade_d)
        self.assertIn("A/B/C/D", self.physical["tables"][0]["conditionalContracts"]["FULL_FILE_UPLOAD"])
        create_mode = self.api["operations"]["createDraft"]["request"]["body"]["fields"]["editMode"]
        self.assertIn("D allows FULL_FILE_UPLOAD or ONLINE_TEMPLATE_SIMPLE_D", create_mode)

    def test_steps_and_support_arrangements_have_one_authoritative_storage(self):
        root_json = self.physical["jsonContracts"]["content_snapshot"]
        self.assertIn("steps and supportArrangements are never stored", root_json["exclusions"])
        step = next(table for table in self.physical["tables"] if table["name"] == "cut_step")
        support = next(
            table for table in self.physical["tables"]
            if table["name"] == "cut_cutover_support_arrangement"
        )
        self.assertIn("only business truth", step["authority"])
        self.assertIn("only business truth", support["authority"])
        self.assertIn("atomically replaces", self.physical["draftAndReadConsistency"]["save"])
        self.assertIn("overlays", self.physical["draftAndReadConsistency"]["approvedContactChange"])

    def test_approved_contact_patch_uses_plan_version_as_only_public_cas(self):
        patch = self.api["operations"]["patchApprovedContacts"]
        self.assertIn("sole public version Owner", patch["concurrency"])
        self.assertIn("CAS cut_plan_revision.version", patch["concurrency"])
        self.assertNotIn("arrangementVersion", json.dumps(patch["request"]))
        physical = self.physical["draftAndReadConsistency"]["approvedContactChange"]
        self.assertIn("If-Match+1", physical)
        self.assertIn("any failure rolls back root, child and audit", physical)

    def test_all_seven_rest_operations_have_exact_requests_and_error_contracts(self):
        operations = self.api["operations"]
        self.assertEqual(
            {"detail", "createDraft", "saveDraft", "downloadDraft", "submit", "patchApprovedContacts", "revise"},
            set(operations),
        )
        for operation in operations.values():
            self.assertIn("headers", operation)
            self.assertIn("request", operation)
            self.assertIn("success", operation)
            self.assertIsInstance(operation["errors"], dict)
        self.assertEqual(
            ["sequenceNo", "plannedAt", "content"],
            self.api["commonTypes"]["PlanScheduleRow"]["exactKeys"],
        )

    def test_legacy_approval_fields_never_become_cut05_facts(self):
        migration = self.physical["legacyMigration"]
        self.assertEqual(
            {"status", "approved_by", "approved_time", "approval_opinion", "baseline_version"},
            set(migration["legacyApprovalFields"]),
        )
        self.assertIn("never mapped", migration["legacyApprovalDisposition"])
        plan = next(item for item in self.trace["records"] if item["object"] == "CutoverPlan")
        self.assertEqual("FEATURE_MAPPING_DEFINED", plan["sources"][0]["mappingStatus"])

    def test_legacy_forward_has_exact_identity_step_mapping_and_batch_protocol(self):
        migration = self.physical["legacyMigration"]
        self.assertEqual("pms_cut_plan", migration["source"])
        self.assertEqual("LEGACY_FORWARD", migration["rootMappings"]["origin_code"])
        legacy_snapshot = self.api["commonTypes"]["LegacyPlanSourceSnapshot"]
        self.assertTrue({"code", "name", "level", "remark"}.issubset(legacy_snapshot["exactKeys"]))
        self.assertEqual("trimmed non-blank -> cut_step PRE_OPERATION/1", migration["fieldMappings"]["pre_check"])
        self.assertEqual("trimmed non-blank -> cut_step OPERATION/1", migration["fieldMappings"]["procedure"])
        self.assertIn("STAGED_READY", migration["platformBatchProtocol"]["intake"])
        self.assertIn("atomically", migration["platformBatchProtocol"]["success"])

    def test_support_arrangements_are_new_only_and_cut04_has_no_approved_event(self):
        support = next(item for item in self.trace["records"] if item["object"] == "CutoverSupportArrangement")
        self.assertEqual("NONE_NEW", support["sources"][0]["sourceType"])
        cut04 = self.phase2.split("### CUT-04", 1)[1].split("### CUT-05", 1)[0]
        self.assertIn("CutoverApproved仅由CUT-05发布", cut04)
        self.assertNotIn("- 事件：CutoverApproved\n", cut04)


if __name__ == "__main__":
    unittest.main()
