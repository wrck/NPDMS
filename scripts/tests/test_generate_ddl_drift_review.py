from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "generate_ddl_drift_review.py"
SPEC = importlib.util.spec_from_file_location("generate_ddl_drift_review", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class DdlDriftReviewTest(unittest.TestCase):
    def test_parser_handles_generated_column_and_constraints(self) -> None:
        ddl = b"""CREATE TABLE sample (
          id BIGINT NOT NULL COMMENT 'id',
          active_id BIGINT GENERATED ALWAYS AS (CASE WHEN id > 0 THEN id ELSE NULL END) STORED COMMENT 'active',
          PRIMARY KEY (id),
          CONSTRAINT chk_sample CHECK (id >= 0)
        ) ENGINE = InnoDB COMMENT = 'sample';"""
        table = MODULE.parse_ddl(ddl)["sample"]
        self.assertEqual({"id", "active_id"}, set(table.columns))
        self.assertEqual(2, len(table.constraints))
        self.assertEqual(
            "CASE WHEN id > 0 THEN id ELSE NULL END",
            table.columns["active_id"]["generatedExpression"],
        )

    def test_missing_historical_generated_expression_is_not_reported_as_match(self) -> None:
        before = {
            "dataType": "BIGINT", "nullable": True, "defaultValue": None,
            "generated": True, "generatedExpression": None, "description": "active",
        }
        after = dict(before, generatedExpression="CASE WHEN status = 'ACTIVE' THEN id ELSE NULL END")
        self.assertEqual("UNVERIFIED_BASELINE_MISSING", MODULE.column_comparison_status(before, after))

    def test_compare_reports_column_constraint_and_options(self) -> None:
        old = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB COMMENT='old';")
        new = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NULL, name VARCHAR(20), PRIMARY KEY (id), KEY idx_name (name)) ENGINE = InnoDB COMMENT='new';")
        result = MODULE.compare(old, new)
        self.assertEqual(2, len(result["columnDiff"]))
        self.assertEqual(1, len(result["constraintDiff"]))
        self.assertEqual(1, len(result["tableOptionDiff"]))

    def test_all_differences_remain_defer(self) -> None:
        old = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL) ENGINE = InnoDB;")
        new = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL, code VARCHAR(10)) ENGINE = InnoDB;")
        result = MODULE.compare(old, new)
        self.assertTrue(all(item["decision"] == "DEFER" for values in result.values() for item in values))

    def test_current_constraint_inventory_is_hash_bound_and_unapproved(self) -> None:
        tables = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;")
        inventory = MODULE.current_constraint_inventory("DDL_HASH", tables)
        self.assertEqual("DDL_HASH", inventory["currentDdlSha256"])
        self.assertEqual(1, inventory["constraintCount"])
        self.assertEqual("DEFER", inventory["records"][0]["decision"])
        self.assertIsNone(inventory["approval"]["approvedDdlSha256"])

    def test_parse_ddl_includes_alter_table_foreign_key(self) -> None:
        tables = MODULE.parse_ddl(b"""
CREATE TABLE parent (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
CREATE TABLE child (id BIGINT NOT NULL, parent_id BIGINT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
ALTER TABLE child ADD CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent (id);
""")
        self.assertIn(
            "CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent (id)",
            tables["child"].constraints,
        )

    def test_item_decision_register_is_complete_and_unapproved(self) -> None:
        baseline = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL) ENGINE = InnoDB;")
        current = MODULE.parse_ddl(b"CREATE TABLE t (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;")
        register = MODULE.ddl_item_decision_register(
            "BASELINE", "CURRENT", baseline, current,
            constraints_comparable=False, options_comparable=False,
        )
        self.assertEqual({"TABLE": 1, "COLUMN": 1, "CONSTRAINT": 1, "TABLE_OPTION": 1}, register["summary"]["byType"])
        self.assertTrue(all(item["decision"] == "DEFER" for item in register["items"]))
        constraint = next(item for item in register["items"] if item["itemType"] == "CONSTRAINT")
        self.assertEqual("UNVERIFIED_BASELINE_MISSING", constraint["comparisonStatus"])
        self.assertIsNone(register["approval"]["approvedDdlSha256"])

    def test_accepted_naming_decisions_do_not_fabricate_review_approval(self) -> None:
        baseline = MODULE.parse_ddl(b"CREATE TABLE old_table (old_name BIGINT NOT NULL) ENGINE = InnoDB;")
        current = MODULE.parse_ddl(b"CREATE TABLE new_table (new_name BIGINT NOT NULL) ENGINE = InnoDB;")
        register = MODULE.ddl_item_decision_register(
            "BASELINE", "CURRENT", MODULE.normalize_baseline_names(baseline, {
                "tables": [{"source": "old_table", "target": "new_table"}],
                "fields": [{"sourceTable": "old_table", "sourceColumn": "old_name", "targetTable": "new_table", "targetColumn": "new_name"}],
            }), current, constraints_comparable=False, options_comparable=False,
        )
        decided = MODULE.apply_accepted_naming_decisions(register, {
            "tables": [{"source": "old_table", "target": "new_table"}],
            "fields": [{"sourceTable": "old_table", "sourceColumn": "old_name", "targetTable": "new_table", "targetColumn": "new_name"}],
        })
        table = next(item for item in decided["items"] if item["itemType"] == "TABLE")
        column = next(item for item in decided["items"] if item["itemType"] == "COLUMN")
        self.assertEqual("AMEND_CURRENT", table["decision"])
        self.assertEqual("AMEND_CURRENT", column["decision"])
        self.assertIsNone(table["reviewOwner"])
        self.assertEqual(0, decided["summary"]["approvedCount"])
        self.assertIsNone(decided["approval"]["approvedDdlSha256"])

    def test_accepted_project_code_decisions_are_exact_and_unapproved(self) -> None:
        baseline = MODULE.parse_ddl(b"CREATE TABLE proj_project (id BIGINT NOT NULL) ENGINE = InnoDB;")
        current = MODULE.parse_ddl(
            b"CREATE TABLE proj_project (id BIGINT NOT NULL, code_root_id BIGINT NOT NULL) ENGINE = InnoDB;"
        )
        register = MODULE.ddl_item_decision_register(
            "BASELINE", "CURRENT", baseline, current,
            constraints_comparable=False, options_comparable=False,
        )
        decided = MODULE.apply_accepted_project_code_decisions(register, {
            "acceptedDdlItems": ["COLUMN:proj_project:code_root_id"],
        })
        item = next(
            item for item in decided["items"]
            if item["itemId"] == "COLUMN:proj_project:code_root_id"
        )
        self.assertEqual("AMEND_CURRENT", item["decision"])
        self.assertEqual("REQUIREMENT_OWNER", item["decisionOwner"])
        self.assertIsNone(item["reviewOwner"])
        self.assertEqual(0, decided["summary"]["approvedCount"])

    def test_project_code_decision_rejects_missing_ddl_item(self) -> None:
        register = MODULE.ddl_item_decision_register(
            "BASELINE", "CURRENT",
            MODULE.parse_ddl(b"CREATE TABLE proj_project (id BIGINT NOT NULL) ENGINE = InnoDB;"),
            MODULE.parse_ddl(b"CREATE TABLE proj_project (id BIGINT NOT NULL) ENGINE = InnoDB;"),
            constraints_comparable=False, options_comparable=False,
        )
        with self.assertRaisesRegex(ValueError, "missing DDL items"):
            MODULE.apply_accepted_project_code_decisions(register, {
                "acceptedDdlItems": ["COLUMN:proj_project:code_root_id"],
            })

    def test_market_relation_decision_covers_directory_and_object_snapshots(self) -> None:
        current = MODULE.parse_ddl(b"""
        CREATE TABLE cus_market_relation (id BIGINT NOT NULL, market_code VARCHAR(64), PRIMARY KEY (id)) ENGINE = InnoDB;
        CREATE TABLE cus_customer (id BIGINT NOT NULL, market_code VARCHAR(64), KEY idx_customer_market_relation (market_code)) ENGINE = InnoDB;
        CREATE TABLE proj_project (id BIGINT NOT NULL, market_code VARCHAR(64), KEY idx_project_market_relation (market_code)) ENGINE = InnoDB;
        """)
        register = MODULE.ddl_item_decision_register(
            "BASELINE", "CURRENT", {}, current,
            constraints_comparable=False, options_comparable=False,
        )
        decided = MODULE.apply_accepted_market_relation_decisions(register, {
            "targetTable": "cus_market_relation",
            "businessFields": ["market_code"],
        })
        accepted = {item["itemId"] for item in decided["items"] if item["decision"] == "AMEND_CURRENT"}
        self.assertIn("TABLE:cus_market_relation", accepted)
        self.assertIn("COLUMN:cus_customer:market_code", accepted)
        self.assertIn("COLUMN:proj_project:market_code", accepted)
        self.assertIn("CONSTRAINT:cus_customer:idx_customer_market_relation", accepted)
        self.assertEqual("ADR-0021", decided["marketRelationDecision"]["decisionRef"])

    def test_core_schema_decision_marks_exact_current_items(self) -> None:
        current = MODULE.parse_ddl(b"""CREATE TABLE plt_external_key_mapping (
          id BIGINT NOT NULL,
          target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
          target_sequence INT UNSIGNED NOT NULL DEFAULT 0,
          UNIQUE KEY uk_external_key_source_target (target_role, target_sequence, id),
          CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)
        ) ENGINE = InnoDB;""")
        register = MODULE.ddl_item_decision_register(
            "OLD", "NEW", {}, current,
            constraints_comparable=False, options_comparable=False,
        )
        contract = {
            "acceptedDdlItems": [
                "COLUMN:plt_external_key_mapping:target_role",
                "COLUMN:plt_external_key_mapping:target_sequence",
                "CONSTRAINT:plt_external_key_mapping:uk_external_key_source_target",
                "CONSTRAINT:plt_external_key_mapping:chk_external_key_target_sequence",
            ],
            "v3DesignOnlyTables": ["a", "b", "c", "d"],
        }
        result = MODULE.apply_accepted_core_schema_decisions(register, contract)
        decided = {item["itemId"] for item in result["items"] if item["decision"] == "AMEND_CURRENT"}
        self.assertEqual(set(contract["acceptedDdlItems"]), decided)
        self.assertEqual(26, result["coreMigrationSchemaDecision"]["removedCrossDomainForeignKeyCount"])

    def test_q03_decision_marks_business_facts_but_not_q07_q08_items(self) -> None:
        decided_ids = {
            "COLUMN:ast_device_project_assignment:current_device_id",
            "CONSTRAINT:ast_device_project_assignment:uk_device_current_assignment",
            "COLUMN:cus_customer_contact:primary_customer_id",
            "CONSTRAINT:cus_customer_contact:uk_customer_primary_contact",
            "COLUMN:proj_project_company_department_relation:primary_project_id",
            "CONSTRAINT:proj_project_company_department_relation:uk_project_primary_company_department",
            "COLUMN:com_delivery_scope:current_order_line_id",
            "CONSTRAINT:com_delivery_scope:uk_scope_current",
            "TABLE:com_delivery_scope_detail",
            "COLUMN:com_delivery_scope_detail:delivery_scope_id",
            "COLUMN:com_delivery_scope_detail:detail_sequence",
            "COLUMN:com_delivery_scope_detail:product_code",
            "COLUMN:com_delivery_scope_detail:product_name",
            "COLUMN:com_delivery_scope_detail:device_type_code",
            "COLUMN:com_delivery_scope_detail:device_type_name",
            "COLUMN:com_delivery_scope_detail:allocated_qty",
            "COLUMN:com_delivery_scope_detail:implementation_location",
            "COLUMN:com_delivery_scope_detail:delivery_batch_no",
            "COLUMN:com_delivery_scope_detail:source_record_key",
            "COLUMN:com_delivery_scope_detail:remark",
            "CONSTRAINT:com_delivery_scope_detail:uk_delivery_scope_detail_sequence",
            "CONSTRAINT:com_delivery_scope_detail:fk_delivery_scope_detail_scope",
            "CONSTRAINT:com_delivery_scope_detail:chk_delivery_scope_detail_subject",
            "COLUMN:com_order_execution_relation:is_primary",
            "COLUMN:com_order_execution_relation:primary_order_id",
        }
        deferred_ids = {
            "CONSTRAINT:com_delivery_scope_detail:PRIMARY",
            "CONSTRAINT:com_delivery_scope_detail:uk_delivery_scope_detail_tenant_row",
            "CONSTRAINT:com_delivery_scope_detail:idx_delivery_scope_detail_product",
        }
        items = [
            {
                "itemId": item_id,
                "decision": "DEFER",
                "decisionOwner": None,
                "reviewOwner": None,
                "evidenceRefs": [],
            }
            for item_id in sorted(decided_ids | deferred_ids)
        ]
        register = {"items": items, "summary": {"approvedCount": 0}}
        contract = {
            "q03CurrentBusinessFacts": {
                "deviceProjectAssignment": "ONE_CURRENT_DIRECT_PROJECT_PER_DEVICE",
                "customerPrimaryContact": "ONE_CURRENT_PRIMARY_CONTACT_PER_CUSTOMER",
                "projectPrimaryCompanyDepartment": "ONE_CURRENT_PRIMARY_RELATION_PER_PROJECT_ROLE",
                "deliveryScope": "ONE_CURRENT_HEADER_PER_PROJECT_ORDER_LINE_WITH_DETAILS",
                "orderExecution": "MULTIPLE_PRIMARY_EXECUTIONS_ALLOWED",
            }
        }

        result = MODULE.apply_accepted_q03_decisions(register, contract)

        actual = {item["itemId"] for item in result["items"] if item["decision"] == "AMEND_CURRENT"}
        self.assertEqual(decided_ids, actual)
        self.assertEqual("ADR-0023-Q03", result["q03Decision"]["decisionRef"])
        self.assertEqual(
            ["P3-E09-Q07", "P3-E09-Q08"],
            result["q03Decision"]["deferredPhysicalDecisionRefs"],
        )


if __name__ == "__main__":
    unittest.main()
