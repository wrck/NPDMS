from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "generate_target_field_catalog.py"
SPEC = importlib.util.spec_from_file_location("generate_target_field_catalog", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class GenerateTargetFieldCatalogTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = {
            "tables": [{"source": "pms_crm_execution_order", "target": "com_crm_execution_order", "owner": "COM"}],
            "fields": [{"sourceTable": "pms_crm_execution_order", "sourceColumn": "submitted_time", "targetTable": "com_crm_execution_order", "targetColumn": "submit_time"}],
        }

    def test_source_coordinates_are_not_renamed(self) -> None:
        row = {"sourceTable": "pm_project_property_from_sms", "sourceColumn": "submitTime", "targets": ["pms_crm_execution_order.submitted_time"]}
        updated = MODULE.rewrite_target_references(row, self.contract)
        self.assertEqual("pm_project_property_from_sms", updated["sourceTable"])
        self.assertEqual("submitTime", updated["sourceColumn"])
        self.assertEqual(["com_crm_execution_order.submit_time"], updated["targets"])

    def test_catalog_metadata_survives_rename(self) -> None:
        item = {"tableName": "pms_crm_execution_order", "columnName": "submitted_time", "dataElementRefs": ["项目.提交时间"]}
        renamed = MODULE.remap_catalog_item(item, self.contract)
        self.assertEqual(["项目.提交时间"], renamed["dataElementRefs"])
        self.assertEqual("com_crm_execution_order", renamed["tableName"])
        self.assertEqual("submit_time", renamed["columnName"])

    def test_target_binding_is_renamed_without_touching_evidence(self) -> None:
        row = {"evidenceRefs": ["系统支撑!A1"], "targetBindings": [{"tableName": "pms_crm_execution_order", "columnName": "submitted_time", "jsonPath": None}]}
        updated = MODULE.rewrite_target_references(row, self.contract)
        self.assertEqual(["系统支撑!A1"], updated["evidenceRefs"])
        self.assertEqual("com_crm_execution_order", updated["targetBindings"][0]["tableName"])
        self.assertEqual("submit_time", updated["targetBindings"][0]["columnName"])

    def test_new_adr_field_uses_explicit_metadata_without_legacy_source(self) -> None:
        ddl = type("Table", (), {
            "columns": {
                "code_root_id": {
                    "dataType": "BIGINT", "nullable": False, "defaultValue": None,
                    "generated": False, "description": "namespace root",
                }
            }
        })()
        rows = MODULE.build_catalog(
            {"com_crm_execution_order": ddl}, [], self.contract,
            {("com_crm_execution_order", "code_root_id"): {
                "domain": "项目管理", "fieldClass": "RELATION", "dataElementRefs": [],
            }},
        )
        self.assertEqual("code_root_id", rows[0]["columnName"])
        self.assertEqual("RELATION", rows[0]["fieldClass"])
        self.assertNotIn("dataElementRefs", rows[0])
        self.assertNotIn("basisRefs", rows[0])

    def test_binding_range_resolves_only_exact_source_field_coordinates(self) -> None:
        binding = {
            "sourceField": "pm_source.customerCode|customerName",
            "evidenceRef": "data-elements://schema-records.jsonl#项目管理!A10:A12",
        }
        records = [
            {"sheet": "项目管理", "row": 10, "cell": "A10", "tableName": "pm_source", "fieldName": "customerCode"},
            {"sheet": "项目管理", "row": 11, "cell": "A11", "tableName": "pm_source", "fieldName": "unrelated"},
            {"sheet": "项目管理", "row": 12, "cell": "A12", "tableName": "pm_source", "fieldName": "customerName"},
        ]
        self.assertEqual(
            ["项目管理!A10", "项目管理!A12"],
            MODULE.exact_binding_coordinates(binding, records),
        )

    def test_v3_target_is_preserved_as_source_evidence(self) -> None:
        contract = {
            "tables": [{"source": "pms_technical_advisory", "target": "kno_technical_advisory", "owner": "KNO"}],
            "fields": [],
            "implementationScope": {"excludedTargets": ["kno_technical_advisory"]},
        }
        row = {
            "sourceTable": "legacy_notice",
            "sourceColumn": "title",
            "targets": ["pms_technical_advisory.advisory_title"],
            "targetBindings": [{"tableName": "pms_technical_advisory", "columnName": "advisory_title", "jsonPath": None}],
        }
        updated = MODULE.rewrite_target_references(row, contract)
        self.assertEqual("SOURCE_ONLY", updated["disposition"])
        self.assertEqual(["plt_migration_source_record.source_payload"], updated["targets"])
        self.assertEqual("V3_TARGET_EXCLUDED", updated["decisionStatus"])

    def test_user_excluded_maintenance_table_has_no_target_write(self) -> None:
        row = {
            "sourceTable": "pm_project_maintenance", "sourceColumn": "id",
            "targets": ["com_crm_execution_order.id"],
            "targetBindings": [{"tableName": "com_crm_execution_order", "columnName": "id"}],
            "rawPreservedBy": "plt_migration_source_record.source_payload",
        }
        updated = MODULE.rewrite_target_references(row, self.contract)
        self.assertEqual("USER_CONFIRMED_EXCLUDED", updated["decisionStatus"])
        self.assertEqual("EXCLUDED", updated["disposition"])
        self.assertEqual([], updated["targets"])
        self.assertEqual([], updated["targetBindings"])
        self.assertNotIn("rawPreservedBy", updated)


if __name__ == "__main__":
    unittest.main()
