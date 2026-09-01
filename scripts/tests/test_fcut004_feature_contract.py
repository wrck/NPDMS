import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
API = ROOT / "specs/features/F-CUT-004-api-contract.json"
PHYSICAL = ROOT / "specs/features/F-CUT-004-physical-contract.json"
TRACE = ROOT / "docs/traceability/domain-entity-migration-contract.json"
PHASE2 = ROOT / "docs/traceability/phase2-contract-map.md"


class FCut004FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.api = json.loads(API.read_text(encoding="utf-8"))
        cls.physical = json.loads(PHYSICAL.read_text(encoding="utf-8"))
        cls.trace = json.loads(TRACE.read_text(encoding="utf-8"))
        cls.phase2 = PHASE2.read_text(encoding="utf-8")

    def test_p4_submit_and_p5_start_are_atomic_without_approval_ownership_drift(self):
        start = self.api["approvalStartPort"]
        self.assertEqual("F-CUT-005", start["owner"])
        self.assertIn("atomic", start["transaction"])
        self.assertIn("no Fake/fallback", start["production"])
        self.assertEqual(
            {"PENDING", "APPROVED", "REJECTED", "TERMINATED"},
            set(self.api["approvalResultPort"]["statuses"]),
        )

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
        grade_d = self.physical["tables"][0]["conditionalFields"]["D"]
        self.assertIn("checklist_id IS NULL", grade_d)
        self.assertIn("checklist_version IS NULL", grade_d)

    def test_legacy_approval_fields_never_become_cut05_facts(self):
        migration = self.physical["legacyMigration"]
        self.assertEqual(
            {"status", "approved_by", "approved_time", "approval_opinion", "baseline_version"},
            set(migration["legacyApprovalFields"]),
        )
        self.assertIn("never mapped", migration["legacyApprovalDisposition"])
        plan = next(item for item in self.trace["records"] if item["object"] == "CutoverPlan")
        self.assertEqual("FEATURE_MAPPING_DEFINED", plan["sources"][0]["mappingStatus"])

    def test_support_arrangements_are_new_only_and_cut04_has_no_approved_event(self):
        support = next(item for item in self.trace["records"] if item["object"] == "CutoverSupportArrangement")
        self.assertEqual("NONE_NEW", support["sources"][0]["sourceType"])
        cut04 = self.phase2.split("### CUT-04", 1)[1].split("### CUT-05", 1)[0]
        self.assertIn("CutoverApproved仅由CUT-05发布", cut04)
        self.assertNotIn("- 事件：CutoverApproved\n", cut04)


if __name__ == "__main__":
    unittest.main()
