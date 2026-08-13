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
        self.assertNotIn("CONSTRAINT:cus_customer:idx_customer_market_relation", accepted)
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

    def test_core_schema_decision_covers_all_items_of_explicitly_removed_v3_tables(self) -> None:
        baseline = MODULE.parse_ddl(
            b"CREATE TABLE kno_technical_advisory (id BIGINT NOT NULL, advisory_code VARCHAR(64)) ENGINE = InnoDB;"
        )
        register = MODULE.ddl_item_decision_register(
            "OLD", "NEW", baseline, {}, constraints_comparable=False, options_comparable=False,
        )
        result = MODULE.apply_accepted_core_schema_decisions(register, {
            "acceptedDdlItems": [],
            "v3DesignOnlyTables": ["kno_technical_advisory"],
        })
        decided = [item for item in result["items"] if item["decision"] == "AMEND_CURRENT"]
        self.assertEqual(len(register["items"]), len(decided))
        self.assertTrue(all(item["decisionOwner"] == "REQUIREMENT_OWNER" for item in decided))
        self.assertTrue(all("0022-core-migration" in item["evidenceRefs"][0] for item in decided))

    def test_unchanged_baseline_columns_are_accepted_without_reviewer_signature(self) -> None:
        register = {
            "items": [
                {"itemId": "COLUMN:a:stable", "itemType": "COLUMN", "comparisonStatus": "MATCH", "decision": "DEFER", "decisionOwner": None, "reviewOwner": None, "evidenceRefs": []},
                {"itemId": "COLUMN:a:added", "itemType": "COLUMN", "comparisonStatus": "ADDED", "decision": "DEFER", "decisionOwner": None, "reviewOwner": None, "evidenceRefs": []},
            ],
            "summary": {"approvedCount": 0},
        }
        result = MODULE.apply_unchanged_baseline_column_decisions(register)
        self.assertEqual("ACCEPT_CURRENT", result["items"][0]["decision"])
        self.assertEqual("DATA_ARCHITECTURE_OWNER", result["items"][0]["decisionOwner"])
        self.assertIsNone(result["items"][0]["reviewOwner"])
        self.assertEqual("DEFER", result["items"][1]["decision"])
        self.assertEqual(1, result["unchangedBaselineColumnDecision"]["decidedItemCount"])

    def test_review_overlay_preserves_signature_but_not_conflicting_decision(self) -> None:
        register = {
            "currentDdlSha256": "HASH",
            "items": [{"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT", "decisionOwner": "DATA_ARCHITECTURE_OWNER", "reviewOwner": None, "evidenceRefs": ["fact"]}],
            "summary": {"approvedCount": 0},
            "approval": {"approvedDdlSha256": None},
        }
        previous = {
            "currentDdlSha256": "HASH",
            "items": [{"itemId": "COLUMN:a:id", "decision": "ACCEPT_CURRENT", "decisionOwner": "DATA_ARCHITECTURE_OWNER", "reviewOwner": "REVIEWER", "evidenceRefs": ["fact", "review"]}],
            "approval": {"approvedDdlSha256": None, "reviewOwner": "REVIEWER"},
        }
        result = MODULE.apply_review_overlay(register, previous)
        self.assertEqual("REVIEWER", result["items"][0]["reviewOwner"])
        self.assertIn("review", result["items"][0]["evidenceRefs"])
        self.assertEqual(1, result["summary"]["approvedCount"])
        conflicting = {**previous, "items": [{**previous["items"][0], "decision": "DEFER"}]}
        with self.assertRaisesRegex(ValueError, "conflicts"):
            MODULE.apply_review_overlay(register, conflicting)

    def test_v17_delta_decision_covers_every_item_and_preserves_review_gate(self) -> None:
        current = MODULE.parse_ddl(b"""
CREATE TABLE cut_cutover_closure (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  cutover_task_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id)
) ENGINE = InnoDB;
CREATE TABLE imp_configuration_collection_result (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB;
""")
        register = MODULE.ddl_item_decision_register(
            "OLD", "NEW", {}, current, constraints_comparable=False, options_comparable=False,
        )
        contract = {
            "v17Delta": {
                "status": "ACCEPTED",
                "ddlSha256": "NEW",
                "objectTargetTables": {
                    "CutoverClosure": ["cut_cutover_closure"],
                    "ConfigurationCollectionResult": ["imp_configuration_collection_result"],
                },
                "acceptedDdlItems": [item["itemId"] for item in register["items"]],
                "itemEvidenceRefs": {
                    item["itemId"]: (
                        "docs/decisions/0027-cutover-physical-model-correction.md"
                        if item["table"] == "cut_cutover_closure"
                        else "docs/decisions/0025-v1.7-p3-e09-ddl-delta.md"
                    )
                    for item in register["items"]
                },
            }
        }
        result = MODULE.apply_accepted_v17_delta_decisions(register, contract)
        self.assertTrue(all(item["decision"] == "AMEND_CURRENT" for item in result["items"]))
        cutover = [item for item in result["items"] if item["table"] == "cut_cutover_closure"]
        implementation = [item for item in result["items"] if item["table"] == "imp_configuration_collection_result"]
        self.assertTrue(all(any("0027-cutover" in ref for ref in item["evidenceRefs"]) for item in cutover))
        self.assertTrue(all(any("0025-v1.7" in ref for ref in item["evidenceRefs"]) for item in implementation))
        self.assertTrue(all(item["reviewOwner"] is None for item in result["items"]))
        self.assertEqual("REVIEW_PENDING", result["v17DeltaDecision"]["reviewStatus"])

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
            "COLUMN:com_delivery_scope_detail:id",
            "COLUMN:com_delivery_scope_detail:tenant_id",
            "COLUMN:com_delivery_scope_detail:creator",
            "COLUMN:com_delivery_scope_detail:create_time",
            "COLUMN:com_delivery_scope_detail:updater",
            "COLUMN:com_delivery_scope_detail:update_time",
            "COLUMN:com_delivery_scope_detail:deleted",
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
            result["q03Decision"]["relatedDecisionRefs"],
        )

    def test_q07_q08_decisions_separate_constraints_from_candidate_indexes(self) -> None:
        current_hash = "CURRENT"
        values = {
            "CONSTRAINT:a:PRIMARY": "PRIMARY KEY (id)",
            "CONSTRAINT:a:uk_a_tenant_row": "UNIQUE KEY uk_a_tenant_row (tenant_id, id)",
            "CONSTRAINT:a:fk_a_parent": "CONSTRAINT fk_a_parent FOREIGN KEY (parent_id) REFERENCES a (id)",
            "CONSTRAINT:a:chk_a_deleted": "CONSTRAINT chk_a_deleted CHECK (deleted IN (0, 1))",
            "CONSTRAINT:a:chk_contract_dates": "CONSTRAINT chk_contract_dates CHECK (end_at IS NULL OR end_at >= start_at)",
            "CONSTRAINT:a:chk_a_flag": "CONSTRAINT chk_a_flag CHECK (flag IN (0, 1))",
            "CONSTRAINT:a:chk_project_relation_self": "CONSTRAINT chk_project_relation_self CHECK (parent_id <> child_id)",
            "CONSTRAINT:a:chk_project_depth": "CONSTRAINT chk_project_depth CHECK (depth >= 0)",
            "CONSTRAINT:a:idx_a_query": "KEY idx_a_query (tenant_id, status, id)",
            "CONSTRAINT:a:uk_a_business": "UNIQUE KEY uk_a_business (tenant_id, code)",
            "CONSTRAINT:a:chk_a_business": "CONSTRAINT chk_a_business CHECK (code IS NOT NULL)",
        }
        items = [
            {
                "itemId": item_id,
                "itemType": "CONSTRAINT",
                "name": item_id.rsplit(":", 1)[1],
                "currentValue": value,
                "decision": "DEFER",
                "decisionOwner": None,
                "reviewOwner": None,
                "evidenceRefs": [],
            }
            for item_id, value in values.items()
        ]
        register = {
            "currentDdlSha256": current_hash,
            "items": items,
            "summary": {"approvedCount": 0},
        }
        contract = {
            "q07TechnicalConstraintPolicy": {
                "status": "ACCEPTED",
                "ddlSha256": current_hash,
                "decision": "ACCEPT_CURRENT_FOR_SDS",
                "decisionEvidenceRef": "docs/decisions/current-q07-review.md",
                "primaryKeyCount": 1,
                "tenantReferenceKeyCount": 1,
                "sameDomainForeignKeyCount": 1,
                "stableTechnicalCheckGroups": {
                    "softDelete": 1,
                    "temporalOrder": 1,
                    "booleanFlag": 1,
                    "noSelf": 1,
                    "nonnegativeCount": 1,
                },
            },
            "q08OrdinaryIndexPolicy": {
                "status": "ACCEPTED",
                "ddlSha256": current_hash,
                "decision": "ACCEPT_AS_CANDIDATE_BASELINE",
                "decisionEvidenceRef": "docs/decisions/current-q08-review.md",
                "candidateIndexCount": 1,
                "adjustmentPolicy": "FORWARD_MIGRATION_ONLY",
            },
        }

        result = MODULE.apply_accepted_q07_q08_decisions(register, contract)

        decided = {item["itemId"] for item in result["items"] if item["decision"] == "AMEND_CURRENT"}
        self.assertEqual(set(values) - {"CONSTRAINT:a:uk_a_business", "CONSTRAINT:a:chk_a_business"}, decided)
        self.assertEqual(8, result["q07Decision"]["decidedItemCount"])
        self.assertEqual(1, result["q08Decision"]["decidedItemCount"])
        self.assertEqual("REQUIRED_AT_FEATURE_AND_P3_E06", result["q08Decision"]["performanceValidationStatus"])
        self.assertTrue(all(
            any("current-q07-review" in ref for ref in item["evidenceRefs"])
            for item in result["items"] if item["itemId"] in decided - {"CONSTRAINT:a:idx_a_query"}
        ))
        index_item = next(item for item in result["items"] if item["itemId"] == "CONSTRAINT:a:idx_a_query")
        self.assertIn("docs/decisions/current-q08-review.md", index_item["evidenceRefs"])


if __name__ == "__main__":
    unittest.main()
