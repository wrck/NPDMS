import json
import unittest
from pathlib import Path


ROOT = Path(__file__).parents[2]
PHYSICAL = ROOT / "specs/features/F-CUT-002-physical-contract.json"
TRACE = ROOT / "docs/traceability/domain-entity-migration-contract.json"


class FCut002MigrationContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.physical = json.loads(PHYSICAL.read_text(encoding="utf-8"))
        cls.trace = json.loads(TRACE.read_text(encoding="utf-8"))

    def test_legacy_statuses_are_read_only_and_never_new_workflow_states(self):
        policy = self.physical["migration"]["legacyStatusPolicy"]
        self.assertEqual({str(value) for value in range(9)}, set(policy) - {"commandPolicy"})
        self.assertEqual({"LEGACY_UNKNOWN_READ_ONLY"}, {policy[str(value)] for value in range(9)})
        self.assertIn("no allowedActions", policy["commandPolicy"])

    def test_forbidden_business_facts_are_not_in_field_mappings(self):
        mappings = self.physical["migration"]["fieldMappings"]
        targets = {mapping["target"] for mapping in mappings}
        forbidden_targets = {
            "manual_grade", "owner_user_id", "customer_id",
            "implementation_readiness_snapshot_id", "project_scope_version",
            "current_assessment_id"
        }
        self.assertTrue(targets.isdisjoint(forbidden_targets))
        self.assertIn("risk_level never becomes manual_grade", self.physical["migration"]["forbiddenMappings"])

    def test_legacy_projection_columns_and_constants_are_explicit(self):
        table = self.physical["tables"]["cut_task"]
        self.assertEqual("VARCHAR(32) NOT NULL", table["columns"]["task_origin"])
        self.assertEqual("TINYINT NULL", table["columns"]["legacy_status_value"])
        self.assertEqual("INT NULL", table["columns"]["legacy_source_version"])
        self.assertEqual("VARCHAR(64) NULL", table["columns"]["legacy_mapping_version"])
        mappings = {mapping["target"]: mapping for mapping in self.physical["migration"]["fieldMappings"]}
        self.assertEqual("constant LEGACY_FORWARD", mappings["task_origin"]["source"])
        self.assertEqual("constant F-CUT-002-PMS-CUT-TASK-V1", mappings["legacy_mapping_version"]["source"])
        self.assertEqual("constant 0", mappings["version"]["source"])

    def test_each_unmigratable_path_has_an_explicit_disposition(self):
        dispositions = self.physical["migration"]["dispositions"]
        self.assertEqual(
            {
                "MIGRATED_LEGACY_READ_ONLY", "RETAIN_LEGACY_DELETED",
                "SOURCE_DATA_INVALID", "OWNER_FACT_UNAVAILABLE_OR_MISMATCH",
                "TARGET_IDENTITY_CONFLICT"
            },
            set(dispositions)
        )
        self.assertIn("LEGACY_FORWARD cut_task identity projection", dispositions["MIGRATED_LEGACY_READ_ONLY"])
        self.assertIn("create no target row", dispositions["RETAIN_LEGACY_DELETED"])
        self.assertIn("create no target row", dispositions["SOURCE_DATA_INVALID"])
        self.assertIn("target insert", dispositions["OWNER_FACT_UNAVAILABLE_OR_MISMATCH"])
        self.assertIn("target row", dispositions["TARGET_IDENTITY_CONFLICT"])

    def test_generated_trace_uses_feature_mapping_contract(self):
        record = next(item for item in self.trace["records"] if item["object"] == "CutoverTask")
        source = record["sources"][0]
        self.assertEqual("FEATURE_MAPPING_DEFINED", source["mappingStatus"])
        self.assertEqual("F-CUT-002_MIGRATION_CONTRACT_REVIEW", source["gate"])
        self.assertEqual("ALL_0_TO_8_TO_LEGACY_UNKNOWN_READ_ONLY", source["statusMapping"]["policy"])
        self.assertEqual(7, len(source["targetFieldBindings"]))


if __name__ == "__main__":
    unittest.main()
