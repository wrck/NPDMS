from __future__ import annotations

import hashlib
import importlib.util
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "validate_core_migration_schema_contract.py"
SPEC = importlib.util.spec_from_file_location("validate_core_migration_schema_contract", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)
sys.path.insert(0, str(ROOT / "scripts"))
APPLY_PATH = ROOT / "scripts" / "apply_core_migration_schema_contract.py"
APPLY_SPEC = importlib.util.spec_from_file_location("apply_core_migration_schema_contract", APPLY_PATH)
APPLIER = importlib.util.module_from_spec(APPLY_SPEC)
assert APPLY_SPEC.loader is not None
APPLY_SPEC.loader.exec_module(APPLIER)


class CoreMigrationSchemaContractTest(unittest.TestCase):
    def valid_v17_delta_contract(self) -> dict[str, object]:
        contract = self.valid_contract()
        contract["v17Delta"] = {
            "decisionRef": "ADR-0025",
            "status": "BLOCKED_BY_REVIEW",
            "requirementRefs": [
                "ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04", "CUT-11",
                "SRV-01", "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07",
                "EXE-03", "INT-05",
            ],
            "objectTargetTables": {
                "ConfigurationCollectionResult": [
                    "imp_configuration_collection_parse_attempt",
                    "imp_configuration_component_candidate",
                ],
                "SatisfactionCollection": [
                    "acc_satisfaction_collection_task",
                    "acc_satisfaction_questionnaire",
                    "acc_satisfaction_response",
                    "acc_satisfaction_result",
                ],
                "CutoverSupportTask": [
                    "cut_cutover_support_task",
                    "cut_cutover_support_history",
                ],
                "ResponsibilityInterval": [
                    "cut_cutover_support_responsibility_interval",
                ],
                "HistoricalWorkOrderRecord": ["srv_historical_work_order"],
                "HistoricalTimeRecord": ["srv_historical_time_record"],
                "DeviceComponentRelation": ["ast_device_component_relation"],
                "DirectorySyncSnapshot": ["plt_directory_sync_snapshot"],
            },
            "historicalReadOnlyTables": [
                "srv_historical_work_order",
                "srv_historical_time_record",
            ],
            "appendOnlyTables": [
                "acc_satisfaction_questionnaire",
                "acc_satisfaction_response",
                "acc_satisfaction_result",
                "cut_cutover_support_history",
                "cut_cutover_support_responsibility_interval",
            ],
        }
        return contract

    def valid_v17_object_table_map(self) -> dict[str, object]:
        contract = self.valid_v17_delta_contract()["v17Delta"]
        assert isinstance(contract, dict)
        object_tables = contract["objectTargetTables"]
        assert isinstance(object_tables, dict)
        return {
            "schemaVersion": 1,
            "objects": {
                name: {"targetTables": list(tables)}
                for name, tables in object_tables.items()
            },
        }

    def valid_v17_delta_ddl(self) -> str:
        return self.valid_ddl() + """
CREATE TABLE imp_configuration_collection_parse_attempt (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  collection_result_id BIGINT NOT NULL,
  attempt_no INT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)
) ENGINE = InnoDB;
CREATE TABLE imp_configuration_component_candidate (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  parse_attempt_id BIGINT NOT NULL,
  candidate_no INT UNSIGNED NOT NULL,
  chassis_sn VARCHAR(128) NOT NULL,
  slot_code VARCHAR(64) NOT NULL,
  card_sn VARCHAR(128) NULL,
  card_model_code VARCHAR(128) NULL,
  parser_version VARCHAR(64) NOT NULL,
  match_status_code VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(256) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_collection_task (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_context VARCHAR(32) NOT NULL,
  source_object_type VARCHAR(64) NOT NULL,
  source_object_id BIGINT NOT NULL,
  task_revision_no INT UNSIGNED NOT NULL,
  template_version VARCHAR(64) NOT NULL,
  frozen_threshold DECIMAL(10,4) NOT NULL,
  state_machine_version VARCHAR(64) NOT NULL,
  status_code VARCHAR(32) NOT NULL,
  current_responsible_user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_task_revision (tenant_id, source_context, source_object_type, source_object_id, task_revision_no)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_questionnaire (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  questionnaire_revision_no INT UNSIGNED NOT NULL,
  frozen_question_json JSON NOT NULL,
  frozen_threshold DECIMAL(10,4) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_questionnaire_revision (tenant_id, task_id, questionnaire_revision_no)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_response (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  questionnaire_id BIGINT NOT NULL,
  response_no INT UNSIGNED NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  answer_json JSON NOT NULL,
  signature_ref VARCHAR(256) NOT NULL,
  submit_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_response_sequence (tenant_id, questionnaire_id, response_no),
  UNIQUE KEY uk_satisfaction_response_request (tenant_id, questionnaire_id, request_id)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_result (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  questionnaire_id BIGINT NOT NULL,
  response_id BIGINT NOT NULL,
  result_no INT UNSIGNED NOT NULL,
  score DECIMAL(10,4) NOT NULL,
  frozen_threshold DECIMAL(10,4) NOT NULL,
  passed TINYINT NOT NULL,
  decision_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no),
  UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id)
) ENGINE = InnoDB;
CREATE TABLE cut_cutover_support_task (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  task_no VARCHAR(64) NOT NULL,
  cutover_task_id BIGINT NOT NULL,
  support_scope_hash CHAR(64) NOT NULL,
  window_start DATETIME(3) NOT NULL,
  window_end DATETIME(3) NOT NULL,
  state_machine_version VARCHAR(64) NOT NULL,
  status_code VARCHAR(32) NOT NULL,
  current_responsible_user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_support_task_no (tenant_id, task_no),
  UNIQUE KEY uk_cutover_support_scope_window (tenant_id, cutover_task_id, support_scope_hash, window_start, window_end)
) ENGINE = InnoDB;
CREATE TABLE cut_cutover_support_history (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  support_task_id BIGINT NOT NULL,
  history_no INT UNSIGNED NOT NULL,
  action_code VARCHAR(32) NOT NULL,
  status_before_code VARCHAR(32) NULL,
  status_after_code VARCHAR(32) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  evidence_ref VARCHAR(256) NULL,
  occurred_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_support_history_sequence (tenant_id, support_task_id, history_no)
) ENGINE = InnoDB;
CREATE TABLE cut_cutover_support_responsibility_interval (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  support_task_id BIGINT NOT NULL,
  interval_no INT UNSIGNED NOT NULL,
  responsible_user_id BIGINT NOT NULL,
  effective_from DATETIME(3) NOT NULL,
  effective_to DATETIME(3) NULL,
  handover_reason VARCHAR(512) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_responsibility_interval_sequence (tenant_id, support_task_id, interval_no)
) ENGINE = InnoDB;
CREATE TABLE srv_historical_work_order (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_business_key VARCHAR(128) NOT NULL,
  source_type_code VARCHAR(64) NULL,
  source_status_code VARCHAR(64) NULL,
  source_responsible_user_key VARCHAR(128) NULL,
  source_payload JSON NOT NULL,
  source_payload_sha256 CHAR(64) NOT NULL,
  imported_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_historical_work_order_source (tenant_id, source_system, source_business_key)
) ENGINE = InnoDB;
CREATE TABLE srv_historical_time_record (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_business_key VARCHAR(128) NOT NULL,
  source_type_code VARCHAR(64) NULL,
  source_status_code VARCHAR(64) NULL,
  source_responsible_user_key VARCHAR(128) NULL,
  duration_hours DECIMAL(20,6) NULL,
  direction_code VARCHAR(32) NULL,
  signed_adjustment_hours DECIMAL(20,6) NULL,
  source_payload JSON NOT NULL,
  source_payload_sha256 CHAR(64) NOT NULL,
  imported_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_historical_time_record_source (tenant_id, source_system, source_business_key)
) ENGINE = InnoDB;
CREATE TABLE ast_device_component_relation (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  chassis_device_id BIGINT NOT NULL,
  chassis_sn VARCHAR(128) NOT NULL,
  slot_code VARCHAR(64) NOT NULL,
  card_device_id BIGINT NULL,
  card_sn VARCHAR(128) NULL,
  card_model_code VARCHAR(128) NULL,
  relation_source_code VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(256) NOT NULL,
  effective_from DATETIME(3) NOT NULL,
  effective_to DATETIME(3) NULL,
  current_slot_code VARCHAR(64) GENERATED ALWAYS AS (
    CASE WHEN effective_to IS NULL THEN slot_code ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_component_current_slot (tenant_id, chassis_device_id, current_slot_code)
) ENGINE = InnoDB;
CREATE TABLE plt_directory_sync_snapshot (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_key VARCHAR(128) NOT NULL,
  source_version VARCHAR(64) NOT NULL,
  person_no VARCHAR(64) NOT NULL,
  person_name VARCHAR(128) NOT NULL,
  organization_code VARCHAR(64) NOT NULL,
  position_code VARCHAR(64) NULL,
  employment_status_code VARCHAR(32) NOT NULL,
  sync_batch_no VARCHAR(64) NOT NULL,
  sync_watermark VARCHAR(128) NOT NULL,
  source_updated_time DATETIME(3) NOT NULL,
  synced_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_directory_sync_snapshot_source (tenant_id, source_system, source_key)
) ENGINE = InnoDB;
"""

    def valid_contract(self) -> dict[str, object]:
        ddl_sha = hashlib.sha256(self.valid_ddl().encode("utf-8")).hexdigest().upper()
        return {
            "schemaVersion": 1,
            "decisionRef": "ADR-0022",
            "coverage": "CORE_MIGRATION_SUBSET",
            "crossDomainReferencePolicy": "LOGICAL_REFERENCE",
            "v3DesignOnlyTables": sorted(MODULE.V3_DESIGN_ONLY_TABLES),
            "externalKeyMapping": {
                "table": "plt_external_key_mapping",
                "targetRoleColumn": "target_role",
                "targetSequenceColumn": "target_sequence",
                "defaultTargetRole": "PRIMARY",
                "minimumTargetSequence": 0,
            },
            "acceptedDdlItems": sorted(MODULE.EXPECTED_ACCEPTED_DDL_ITEMS),
            "normalization": {
                "businessCode": "TRIM_UPPERCASE",
                "opaqueExternalKey": "BINARY_EXACT",
                "hash": "BINARY_EXACT",
                "displayName": "UNICODE_CASE_INSENSITIVE",
            },
            "permanentKeys": {
                "reuseAllowed": False,
                "objects": ["PROJECT", "CONTRACT", "SALES_ORDER", "DEVICE_SN", "SOURCE_KEY"],
                "uniqueKeyIncludesDeleted": False,
                "ddlUniqueKeys": ["uk_project_code", "uk_contract_business", "uk_sales_order_business", "uk_device_sn"],
            },
            "currentRecordUniqueness": "GENERATED_CURRENT_MARKER",
            "q03CurrentBusinessFacts": {
                "deviceProjectAssignment": "ONE_CURRENT_DIRECT_PROJECT_PER_DEVICE",
                "customerPrimaryContact": "ONE_CURRENT_PRIMARY_CONTACT_PER_CUSTOMER",
                "projectPrimaryCompanyDepartment": "ONE_CURRENT_PRIMARY_RELATION_PER_PROJECT_ROLE",
                "deliveryScope": "ONE_CURRENT_HEADER_PER_PROJECT_ORDER_LINE_WITH_DETAILS",
                "orderExecution": "MULTIPLE_PRIMARY_EXECUTIONS_ALLOWED",
            },
            "q07TechnicalConstraintPolicy": {
                "ddlSha256": ddl_sha,
                "decision": "ACCEPT_CURRENT_FOR_SDS",
                "primaryKeyCount": 8,
                "primaryKeyShape": {"singleId": 8, "compositeProjection": 0},
                "tenantReferenceKeyCount": 0,
                "sameDomainForeignKeyCount": 0,
                "stableTechnicalCheckGroups": {
                    "softDelete": 0,
                    "temporalOrder": 0,
                    "booleanFlag": 0,
                    "noSelf": 0,
                    "nonnegativeCount": 1,
                },
                "historicalViolationPolicy": "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE",
            },
            "q08OrdinaryIndexPolicy": {
                "ddlSha256": ddl_sha,
                "decision": "ACCEPT_AS_CANDIDATE_BASELINE",
                "candidateIndexCount": 0,
                "featureQueryPlanValidationRequired": True,
                "p3e06PerformanceValidationRequired": True,
                "adjustmentPolicy": "FORWARD_MIGRATION_ONLY",
            },
            "historicalAnomalyPolicy": "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE",
        }

    def valid_ddl(self) -> str:
        return """
CREATE TABLE proj_project (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_code VARCHAR(64) NOT NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (tenant_id, project_code)
) ENGINE = InnoDB;
CREATE TABLE ast_device_project_assignment (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  effective_to DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  current_device_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND effective_to IS NULL THEN device_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id)
) ENGINE = InnoDB;
CREATE TABLE cus_customer_contact (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  is_primary TINYINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  primary_customer_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND is_primary = 1 THEN customer_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_primary_contact (tenant_id, primary_customer_id)
) ENGINE = InnoDB;
CREATE TABLE proj_project_company_department_relation (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  relation_role VARCHAR(32) NOT NULL,
  is_primary TINYINT NOT NULL DEFAULT 0,
  effective_to DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  primary_project_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND effective_to IS NULL AND is_primary = 1 THEN project_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_primary_company_department (tenant_id, primary_project_id, relation_role)
) ENGINE = InnoDB;
CREATE TABLE com_delivery_scope (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  order_line_id BIGINT NOT NULL,
  effective_to DATETIME(3) NULL,
  deleted TINYINT NOT NULL DEFAULT 0,
  current_order_line_id BIGINT GENERATED ALWAYS AS (
    CASE WHEN deleted = 0 AND effective_to IS NULL THEN order_line_id ELSE NULL END
  ) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope_current (tenant_id, project_id, current_order_line_id)
) ENGINE = InnoDB;
CREATE TABLE com_delivery_scope_detail (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  delivery_scope_id BIGINT NOT NULL,
  detail_sequence INT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_delivery_scope_detail_sequence (tenant_id, delivery_scope_id, detail_sequence)
) ENGINE = InnoDB;
CREATE TABLE com_order_execution_relation (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  execution_id BIGINT NOT NULL,
  is_primary TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)
) ENGINE = InnoDB;
CREATE TABLE plt_external_key_mapping (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_table VARCHAR(64) NOT NULL,
  source_pk VARCHAR(128) NOT NULL,
  target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
  target_sequence INT UNSIGNED NOT NULL DEFAULT 0,
  target_table VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_external_key_source_target (
    tenant_id, source_system, source_table, source_pk,
    target_role, target_sequence, target_table, target_id
  ),
  CONSTRAINT chk_external_key_target_sequence CHECK (target_sequence >= 0)
) ENGINE = InnoDB;
"""

    def test_accepts_confirmed_contract_and_safe_ddl(self) -> None:
        self.assertEqual([], MODULE.validate_contract(self.valid_contract(), self.valid_ddl()))

    def test_rejects_full_platform_claim_and_v3_table(self) -> None:
        contract = self.valid_contract()
        contract["coverage"] = "FULL_PLATFORM_MODEL"
        ddl = self.valid_ddl() + "\nCREATE TABLE kno_technical_advisory (id BIGINT) ENGINE = InnoDB;"
        errors = MODULE.validate_contract(contract, ddl)
        self.assertTrue(any("CORE_MIGRATION_SUBSET" in error for error in errors))
        self.assertTrue(any("V3 design-only" in error for error in errors))

    def test_rejects_cross_domain_foreign_key(self) -> None:
        ddl = self.valid_ddl().replace(
            "PRIMARY KEY (id),\n  UNIQUE KEY uk_device_current_assignment",
            "PRIMARY KEY (id),\n  CONSTRAINT fk_assignment_project FOREIGN KEY (tenant_id, device_id) REFERENCES proj_project (tenant_id, id),\n  UNIQUE KEY uk_device_current_assignment",
        )
        self.assertTrue(any("cross-domain foreign key" in error for error in MODULE.validate_contract(self.valid_contract(), ddl)))

    def test_rejects_missing_mapping_role_or_sequence(self) -> None:
        ddl = self.valid_ddl().replace("  target_role VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',\n", "")
        self.assertTrue(any("target_role" in error for error in MODULE.validate_contract(self.valid_contract(), ddl)))

    def test_rejects_reusable_business_key_and_nullable_current_key(self) -> None:
        ddl = self.valid_ddl().replace(
            "UNIQUE KEY uk_project_code (tenant_id, project_code)",
            "UNIQUE KEY uk_project_code (tenant_id, project_code, deleted)",
        ).replace(
            "UNIQUE KEY uk_device_current_assignment (tenant_id, current_device_id)",
            "UNIQUE KEY uk_device_current_assignment (tenant_id, device_id, effective_to)",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("deleted" in error for error in errors))
        self.assertTrue(any("effective_to" in error for error in errors))

    def test_rejects_trailing_comma_before_table_close(self) -> None:
        ddl = self.valid_ddl().replace(
            "UNIQUE KEY uk_project_code (tenant_id, project_code)\n) ENGINE",
            "UNIQUE KEY uk_project_code (tenant_id, project_code),\n) ENGINE",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("trailing comma" in error for error in errors))

    def test_rejects_status_coupled_current_markers(self) -> None:
        ddl = self.valid_ddl().replace(
            "CASE WHEN deleted = 0 AND effective_to IS NULL THEN device_id ELSE NULL END",
            "CASE WHEN deleted = 0 AND assignment_status = 'ACTIVE' AND effective_to IS NULL THEN device_id ELSE NULL END",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("extendable status" in error for error in errors))

    def test_rejects_unique_primary_order_execution(self) -> None:
        ddl = self.valid_ddl().replace(
            "UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id)",
            "UNIQUE KEY uk_order_execution (tenant_id, order_id, execution_id),\n"
            "  UNIQUE KEY uk_order_primary_execution (tenant_id, order_id)",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("multiple primary executions" in error for error in errors))

    def test_requires_delivery_scope_detail_table(self) -> None:
        ddl = self.valid_ddl().replace(
            "CREATE TABLE com_delivery_scope_detail (",
            "CREATE TABLE com_delivery_scope_detail_missing (",
        )
        errors = MODULE.validate_contract(self.valid_contract(), ddl)
        self.assertTrue(any("delivery scope detail" in error for error in errors))

    def test_execution_evidence_must_match_current_ddl_hash_and_mysql_version(self) -> None:
        ddl = self.valid_ddl().encode("utf-8")
        evidence = {
            "status": "PASS",
            "purpose": "P3_E09_ISOLATED_MYSQL_DDL_EXECUTION",
            "ddlSha256": hashlib.sha256(ddl).hexdigest().upper(),
            "expectedTableCount": 3,
            "tableCount": 3,
            "mysqlVersion": "8.4.10",
        }
        self.assertEqual([], MODULE.validate_execution_evidence(evidence, ddl, 3))
        evidence["ddlSha256"] = "STALE"
        self.assertTrue(any("stale" in error for error in MODULE.validate_execution_evidence(evidence, ddl, 3)))

    def test_transform_removes_v3_tables_and_cross_domain_fk(self) -> None:
        ddl = self.valid_ddl() + """
CREATE TABLE kno_technical_advisory (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_technical_advisory_read_record (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_technical_advisory_product_relation (id BIGINT) ENGINE = InnoDB;
CREATE TABLE kno_device_technical_advisory_match (id BIGINT) ENGINE = InnoDB;
"""
        transformed, summary = APPLIER.transform_ddl(ddl)
        self.assertEqual(4, summary["removedTables"])
        self.assertEqual(0, summary["removedCrossDomainForeignKeys"])
        self.assertNotIn("CREATE TABLE kno_", transformed)

    def test_v17_delta_accepts_exact_contract_mapping_and_ddl(self) -> None:
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(),
            self.valid_v17_object_table_map(),
            self.valid_v17_delta_ddl(),
        )
        self.assertEqual([], errors)

    def test_v17_delta_reports_each_missing_target_table(self) -> None:
        expected_tables = set().union(*MODULE.EXPECTED_V17_OBJECT_TABLES.values())
        for table in expected_tables:
            with self.subTest(table=table):
                ddl = self.valid_v17_delta_ddl().replace(
                    f"CREATE TABLE {table} (",
                    f"CREATE TABLE {table}_missing (",
                )
                errors = MODULE.validate_v17_delta(
                    self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
                )
                self.assertTrue(any(table in error for error in errors))

    def test_v17_delta_rejects_mapping_replaced_by_existing_table(self) -> None:
        object_map = self.valid_v17_object_table_map()
        objects = object_map["objects"]
        assert isinstance(objects, dict)
        objects["HistoricalTimeRecord"] = {"targetTables": ["proj_project"]}
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), object_map, self.valid_v17_delta_ddl()
        )
        self.assertTrue(any("HistoricalTimeRecord" in error for error in errors))

    def test_v17_delta_rejects_v3_table_and_cross_context_foreign_key(self) -> None:
        ddl = self.valid_v17_delta_ddl() + """
CREATE TABLE kno_technical_advisory (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
"""
        ddl = ddl.replace(
            "PRIMARY KEY (id),\n  UNIQUE KEY uk_directory_sync_snapshot_source",
            "PRIMARY KEY (id),\n"
            "  CONSTRAINT fk_directory_project FOREIGN KEY (tenant_id, id) REFERENCES proj_project (tenant_id, id),\n"
            "  UNIQUE KEY uk_directory_sync_snapshot_source",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("V3" in error for error in errors))
        self.assertTrue(any("cross-domain foreign key" in error for error in errors))

    def test_v17_delta_rejects_mutable_historical_read_model(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "  imported_time DATETIME(3) NOT NULL,\n  PRIMARY KEY (id),\n"
            "  UNIQUE KEY uk_historical_work_order_source",
            "  imported_time DATETIME(3) NOT NULL,\n"
            "  status_code VARCHAR(32) NOT NULL,\n"
            "  deleted TINYINT NOT NULL DEFAULT 0,\n"
            "  PRIMARY KEY (id),\n"
            "  UNIQUE KEY uk_historical_work_order_source",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("srv_historical_work_order" in error and "mutable" in error for error in errors))

    def test_v17_delta_rejects_mutable_append_only_table(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "  submit_time DATETIME(3) NOT NULL,\n  PRIMARY KEY (id),",
            "  submit_time DATETIME(3) NOT NULL,\n"
            "  deleted TINYINT NOT NULL DEFAULT 0,\n"
            "  PRIMARY KEY (id),",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("acc_satisfaction_response.deleted" in error for error in errors))

    def test_v17_delta_rejects_alter_table_cross_context_foreign_key(self) -> None:
        ddl = self.valid_v17_delta_ddl() + """
ALTER TABLE plt_directory_sync_snapshot
  ADD CONSTRAINT fk_directory_project FOREIGN KEY (tenant_id, id)
  REFERENCES proj_project (tenant_id, id);
"""
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("cross-domain foreign key" in error for error in errors))

    def test_v17_delta_rejects_missing_append_only_and_temporal_constraints(self) -> None:
        replacements = {
            "uk_satisfaction_response_sequence": "uk_missing_response_sequence",
            "uk_satisfaction_result_sequence": "uk_missing_result_sequence",
            "uk_cutover_support_history_sequence": "uk_missing_support_history_sequence",
            "uk_cutover_responsibility_interval_sequence": "uk_missing_responsibility_interval_sequence",
            "uk_device_component_current_slot": "uk_missing_component_current_slot",
        }
        for expected_name, replacement in replacements.items():
            with self.subTest(constraint=expected_name):
                ddl = self.valid_v17_delta_ddl().replace(expected_name, replacement)
                errors = MODULE.validate_v17_delta(
                    self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
                )
                self.assertTrue(any(expected_name in error for error in errors))


if __name__ == "__main__":
    unittest.main()
