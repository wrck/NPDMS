import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FEATURE_SPEC = ROOT / "specs/features/F-COM-001-contract-order-association-and-delivery-scope-allocation.md"
PHYSICAL_CONTRACT = ROOT / "specs/features/F-COM-001-physical-contract.json"
MIGRATION_CONTRACT = ROOT / "docs/traceability/domain-entity-migration-contract.json"
DATABASE_DESIGN = ROOT / "docs/design/09-database-design.md"
MODULE_MAPPING = ROOT / "specs/001-project-delivery-platform/appendices/module-boundary-and-naming.md"
REUSE_AUDIT = ROOT / "specs/features/F-COM-001-legacy-reuse-audit.md"
FEATURE_INDEX = ROOT / "specs/features/README.md"


class Fcom001FeatureContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.feature_spec = FEATURE_SPEC.read_text(encoding="utf-8")
        cls.contract = json.loads(PHYSICAL_CONTRACT.read_text(encoding="utf-8"))
        cls.migration_contract = json.loads(MIGRATION_CONTRACT.read_text(encoding="utf-8"))
        cls.database_design = DATABASE_DESIGN.read_text(encoding="utf-8")
        cls.module_mapping = MODULE_MAPPING.read_text(encoding="utf-8")
        cls.reuse_audit = REUSE_AUDIT.read_text(encoding="utf-8")
        cls.feature_index = FEATURE_INDEX.read_text(encoding="utf-8")

    def test_candidate_remains_blocked_only_by_the_unresolved_contract_scope_fact(self) -> None:
        self.assertEqual("CANDIDATE_NOT_READY", self.contract["status"])
        self.assertEqual("BLOCKED_BY_SPEC_Q_FCOM_001", self.contract["featureReadyDecision"])
        self.assertEqual("BLOCKED_BY_SPEC", self.contract["openQuestions"]["Q-FCOM-001"]["status"])
        self.assertIn("不得由Technical Plan", self.feature_spec)
        self.assertIn("CANDIDATE", self.feature_index)
        self.assertIn("NOT_READY", self.feature_index)

    def test_office_snapshot_replaces_ast_location_without_inference(self) -> None:
        self.assertNotIn("AssetLocationApi", self.contract["moduleApis"])
        self.assertNotIn("siteId", self.feature_spec)
        self.assertNotIn("siteLocationId", self.feature_spec)
        self.assertNotIn("UNRESOLVED", self.feature_spec)
        office = self.contract["officeSnapshot"]
        self.assertEqual("PROJ", office["owner"])
        self.assertEqual(
            ["projectId", "officeDepartmentId", "officeDepartmentCode", "officeDepartmentName", "officeDepartmentVersion"],
            office["frozenFields"],
        )
        self.assertIn("NO_AST_ADDRESS_NAME_OR_ORDER_INFERENCE", office["inferencePolicy"])
        self.assertIn("办事处发生时快照", self.reuse_audit)

    def test_feature_forward_delta_matches_the_approved_sds_gate(self) -> None:
        self.assertEqual(
            "P3_E09_FEATURE_FORWARD_DELTAS_008_009_REVIEWED_GO",
            self.contract["phaseGateImpact"],
        )
        tables = self.contract["physicalDelta"]["tables"]
        self.assertEqual("decimal(18,6) NOT NULL", tables["com_delivery_scope"]["fields"]["allocated_qty"])
        self.assertEqual("tinyint unsigned NOT NULL", tables["com_sales_order_line"]["fields"]["unit_scale"])
        self.assertIn("implementation_location", tables["com_delivery_scope_detail"]["removedFields"])
        self.assertNotIn("acceptance_id", tables["acc_acceptance_scope_binding"]["fields"])
        self.assertEqual("bigint NOT NULL", tables["acc_acceptance_scope_binding"]["fields"]["project_stage_snapshot_id"])

    def test_owner_apis_and_atomic_binding_paths_are_explicit(self) -> None:
        carriers = self.contract["implementationCarriers"]
        self.assertEqual(["pms-module-commerce-api", "pms-module-commerce"], carriers["COM"])
        self.assertEqual(["pms-module-project-api", "pms-module-project"], carriers["PROJ"])
        self.assertEqual(["pms-module-project-api", "pms-module-project"], carriers["ACC"])
        self.assertIn("语义Owner仍分别为PROJ/ACC", self.feature_spec)
        self.assertIn("`pms-module-project` | 项目承接、项目团队、项目组合、项目层级、任务WBS、里程碑、风险、问题、验收与闭环", self.module_mapping)
        apis = self.contract["moduleApis"]
        for api in (
            "ProjectOfficeFactApi",
            "ProjectAcceptanceStageFactApi",
            "AcceptanceScopeGuardApi",
            "DeliveryScopeAcceptanceLockApi",
            "AcceptanceScopeBindingApi",
        ):
            with self.subTest(api=api):
                self.assertIn(api, apis)
                self.assertEqual("REAL_PROVIDER_REQUIRED", apis[api]["provider"])
        self.assertEqual(
            ["PROJ_PROJECT", "COM_ORDER_LINE_IF_APPLICABLE", "COM_DELIVERY_SCOPE_BY_STABLE_ID", "ACC_SCOPE_BINDING"],
            self.contract["transactionBoundary"]["lockOrder"],
        )
        self.assertIn("PROJECT_STAGE_ENTRY", self.contract["acceptanceBinding"]["triggers"])
        self.assertIn("SCOPE_VERSION_EFFECTIVE", self.contract["acceptanceBinding"]["triggers"])
        self.assertFalse(self.contract["acceptanceBinding"]["reportTriggersBinding"])

    def test_v70_required_targets_and_deterministic_detail_sequence_are_frozen(self) -> None:
        mappings = self.contract["v70Conversion"]["requiredTargetMappings"]
        expected = {
            "com_sales_order_line.status",
            "com_delivery_scope.project_code",
            "com_delivery_scope.order_source_system",
            "com_delivery_scope.order_company_code",
            "com_delivery_scope.order_type",
            "com_delivery_scope.order_no",
            "com_delivery_scope.line_no",
            "com_delivery_scope.allocation_source",
            "com_delivery_scope.status",
            "com_delivery_scope_detail.detail_sequence",
        }
        self.assertEqual(expected, set(mappings))
        self.assertIn("ROW_NUMBER", mappings["com_delivery_scope_detail.detail_sequence"])
        self.assertEqual("FAIL_BATCH", self.contract["v70Conversion"]["missingOrConflict"])
        managed = {
            record["object"]: next(
                source
                for source in record["sources"]
                if source.get("gate") == "F-COM-001" and "requiredTargetMappings" in source
            )["requiredTargetMappings"]
            for record in self.migration_contract["records"]
            if record["object"] in {"OrderLine", "DeliveryScope", "DeliveryScopeDetail"}
        }
        flattened = {
            target: rule
            for object_mappings in managed.values()
            for target, rule in object_mappings.items()
        }
        self.assertEqual(flattened, mappings)

    def test_q_fcom002_blocks_only_close_or_unlock(self) -> None:
        question = self.contract["openQuestions"]["Q-FCOM-002"]
        self.assertEqual("BLOCKED_BY_SPEC", question["status"])
        self.assertEqual("EXIT_ROLLBACK_BINDING_CLOSE_OR_UNLOCK_ONLY", question["blockingScope"])
        self.assertTrue(question["confirmedEntryAndNewVersionPathsRemainImplementable"])

    def test_every_feature_forward_field_is_backed_by_the_approved_p3_e09_delta(self) -> None:
        grouped = {
            ("com_sales_order_line", "order_qty"): "order_qty/open_qty/delivered_qty",
            ("com_sales_order_line", "open_qty"): "order_qty/open_qty/delivered_qty",
            ("com_sales_order_line", "delivered_qty"): "order_qty/open_qty/delivered_qty",
            ("acc_acceptance_scope_binding", "creator"): "creator/updater",
            ("acc_acceptance_scope_binding", "updater"): "creator/updater",
            ("acc_acceptance_scope_binding", "create_time"): "create_time/update_time",
            ("acc_acceptance_scope_binding", "update_time"): "create_time/update_time",
        }
        for table, table_contract in self.contract["physicalDelta"]["tables"].items():
            self.assertIn(f"`{table}`", self.database_design)
            for field, definition in table_contract["fields"].items():
                with self.subTest(table=table, field=field):
                    evidence_field = grouped.get((table, field), field)
                    self.assertIn(evidence_field, self.database_design)
                    self.assertIn(definition, self.database_design)
        binding_row = next(
            line
            for line in self.database_design.splitlines()
            if line.startswith("| `acc_acceptance_scope_binding`")
        )
        self.assertNotIn("acceptance_id", self.contract["physicalDelta"]["tables"]["acc_acceptance_scope_binding"]["fields"])
        self.assertIn("不含`acceptance_id`", binding_row)
        self.assertIn("project_stage_snapshot_id bigint NOT NULL", binding_row)


if __name__ == "__main__":
    unittest.main()
