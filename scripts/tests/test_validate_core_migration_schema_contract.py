from __future__ import annotations

import hashlib
import importlib.util
import json
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
        contract["forbiddenV1V2Tables"] = sorted(MODULE.EXPECTED_FORBIDDEN_V1V2_TABLES)
        contract["v17Delta"] = {
            "decisionRef": "ADR-0027",
            "status": "BLOCKED_BY_REVIEW",
            "requirementRefs": sorted(MODULE.EXPECTED_V17_REQUIREMENTS),
            "objectTargetTables": {
                name: sorted(tables)
                for name, tables in MODULE.EXPECTED_V17_OBJECT_TABLES.items()
            },
            "historicalReadOnlyTables": [],
            "appendOnlyTables": [
                "acc_satisfaction_questionnaire",
                "acc_satisfaction_response",
                "acc_satisfaction_result",
                "cut_cutover_closure",
            ],
            "tableContracts": MODULE.v17_table_contract_payload(),
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
CREATE TABLE imp_configuration_collection_result (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  collection_task_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  device_id BIGINT NOT NULL,
  project_snapshot JSON NOT NULL,
  device_snapshot JSON NOT NULL,
  result_type_code VARCHAR(32) NOT NULL,
  result_version_no INT UNSIGNED NOT NULL,
  source_code VARCHAR(32) NOT NULL,
  script_version VARCHAR(64) NULL,
  parser_version VARCHAR(64) NOT NULL,
  raw_log_file_id BIGINT NOT NULL,
  raw_log_sha256 CHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuration_collection_result (tenant_id, collection_task_id, result_type_code, result_version_no)
) ENGINE = InnoDB;
CREATE TABLE imp_configuration_collection_parse_attempt (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  collection_result_id BIGINT NOT NULL,
  attempt_no INT UNSIGNED NOT NULL,
  parser_version VARCHAR(64) NOT NULL,
  parse_status_code VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(512) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)
) ENGINE = InnoDB;
CREATE TABLE imp_configuration_component_candidate (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  parse_attempt_id BIGINT NOT NULL,
  candidate_no INT UNSIGNED NOT NULL,
  parse_revision_no INT UNSIGNED NOT NULL,
  chassis_sn VARCHAR(128) NOT NULL,
  slot_code VARCHAR(64) NOT NULL,
  card_sn VARCHAR(128) NULL,
  card_model_code VARCHAR(128) NULL,
  parser_version VARCHAR(64) NOT NULL,
  card_configuration_ref VARCHAR(512) NOT NULL,
  match_status_code VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(512) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuration_component_candidate (tenant_id, parse_attempt_id, candidate_no)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_collection_task (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  business_purpose_code VARCHAR(64) NOT NULL,
  applicable_timing_code VARCHAR(64) NOT NULL,
  source_context VARCHAR(32) NOT NULL,
  source_object_type VARCHAR(64) NOT NULL,
  source_object_id BIGINT NOT NULL,
  source_object_version VARCHAR(64) NOT NULL,
  payment_stage_code VARCHAR(64) NULL,
  payment_stage_key VARCHAR(64) GENERATED ALWAYS AS (COALESCE(payment_stage_code, '')) STORED,
  delivery_scope_snapshot JSON NULL,
  delivery_scope_sha256 CHAR(64) NULL,
  task_revision_no INT UNSIGNED NOT NULL,
  prior_task_id BIGINT NULL,
  remediation_ref VARCHAR(512) NULL,
  template_id BIGINT NOT NULL,
  template_version VARCHAR(64) NOT NULL,
  frozen_threshold DECIMAL(10,4) NOT NULL,
  state_machine_version VARCHAR(64) NOT NULL,
  status_code VARCHAR(32) NOT NULL,
  current_responsible_user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_task_revision (tenant_id, project_id, source_context, source_object_type, source_object_id, source_object_version, business_purpose_code, applicable_timing_code, payment_stage_key, task_revision_no)
) ENGINE = InnoDB;
CREATE TABLE acc_satisfaction_questionnaire (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  source_questionnaire_key VARCHAR(128) NULL,
  source_questionnaire_version VARCHAR(64) NULL,
  questionnaire_revision_no INT UNSIGNED NOT NULL,
  prior_questionnaire_id BIGINT NULL,
  remediation_ref VARCHAR(512) NULL,
  template_id BIGINT NOT NULL,
  template_version VARCHAR(64) NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  required_question_count INT UNSIGNED NOT NULL,
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
  response_valid TINYINT NOT NULL,
  signature_valid TINYINT NOT NULL,
  required_validation_summary JSON NOT NULL,
  item_validation_summary JSON NOT NULL,
  signature_ref VARCHAR(512) NOT NULL,
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
  response_valid TINYINT NOT NULL,
  signature_valid TINYINT NOT NULL,
  required_items_valid TINYINT NOT NULL,
  validation_summary JSON NOT NULL,
  score DECIMAL(10,4) NOT NULL,
  frozen_threshold DECIMAL(10,4) NOT NULL,
  passed TINYINT NOT NULL,
  blocking_reason VARCHAR(1000) NULL,
  archive_status_code VARCHAR(32) NOT NULL,
  archive_artifact_id BIGINT NULL,
  archive_payload_sha256 CHAR(64) NULL,
  archive_time DATETIME(3) NULL,
  decision_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_satisfaction_result_sequence (tenant_id, questionnaire_id, result_no),
  UNIQUE KEY uk_satisfaction_result_response (tenant_id, response_id)
) ENGINE = InnoDB;
CREATE TABLE cut_cutover_support_arrangement (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  cutover_task_id BIGINT NOT NULL,
  plan_revision_id BIGINT NOT NULL,
  arrangement_no INT UNSIGNED NOT NULL,
  person_type_code VARCHAR(32) NOT NULL,
  person_name VARCHAR(128) NOT NULL,
  internal_user_id BIGINT NULL,
  contact_info VARCHAR(512) NOT NULL,
  arrival_time DATETIME(3) NULL,
  role_code VARCHAR(64) NOT NULL,
  task_duty VARCHAR(1000) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_support_arrangement_no (tenant_id, plan_revision_id, arrangement_no)
) ENGINE = InnoDB;
CREATE TABLE cut_cutover_closure (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  cutover_task_id BIGINT NOT NULL,
  plan_revision_id BIGINT NOT NULL,
  precheck_normal TINYINT NULL,
  execution_normal TINYINT NULL,
  test_normal TINYINT NULL,
  rollback_occurred TINYINT NULL,
  rollback_description VARCHAR(1000) NULL,
  legacy_item_text TEXT NULL,
  collection_result_refs JSON NULL,
  attachment_refs JSON NULL,
  result_code VARCHAR(32) NULL,
  submitted_by BIGINT NULL,
  submitted_time DATETIME(3) NULL,
  archive_time DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cutover_closure_task (tenant_id, cutover_task_id)
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
"""

    def valid_contract(self) -> dict[str, object]:
        ddl_sha = hashlib.sha256(self.valid_ddl().encode("utf-8")).hexdigest().upper()
        return {
            "schemaVersion": 1,
            "decisionRef": "ADR-0022",
            "coverage": "CORE_MIGRATION_SUBSET",
            "crossDomainReferencePolicy": "LOGICAL_REFERENCE",
            "v3DesignOnlyTables": sorted(MODULE.V3_DESIGN_ONLY_TABLES),
            "forbiddenV1V2Tables": sorted(MODULE.EXPECTED_FORBIDDEN_V1V2_TABLES),
            "currentTableScope": {
                table: MODULE.EXPECTED_CURRENT_TABLE_SCOPE[table]
                for table in MODULE.parse_tables(self.valid_ddl())
            },
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
                "status": "RECONFIRMATION_REQUIRED",
                "ddlSha256": ddl_sha,
                "proposedDecision": "ACCEPT_CURRENT_FOR_SDS",
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
                "status": "RECONFIRMATION_REQUIRED",
                "ddlSha256": ddl_sha,
                "proposedDecision": "ACCEPT_AS_CANDIDATE_BASELINE",
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

    def test_accepts_current_hash_reconfirmed_q07_q08(self) -> None:
        contract = self.valid_contract()
        ddl = self.valid_ddl()
        for policy_name, decision in (
            ("q07TechnicalConstraintPolicy", "ACCEPT_CURRENT_FOR_SDS"),
            ("q08OrdinaryIndexPolicy", "ACCEPT_AS_CANDIDATE_BASELINE"),
        ):
            policy = contract[policy_name]
            policy["status"] = "ACCEPTED"
            policy["decision"] = decision
            policy["decisionEvidenceRef"] = "docs/decisions/current-hash-review.md"
            policy.pop("proposedDecision", None)
        self.assertEqual([], MODULE.validate_contract(contract, ddl))

    def test_accepted_q07_q08_requires_existing_decision_evidence(self) -> None:
        contract = self.valid_contract()
        for policy_name in ("q07TechnicalConstraintPolicy", "q08OrdinaryIndexPolicy"):
            contract[policy_name]["status"] = "ACCEPTED"
            contract[policy_name]["decisionEvidenceRef"] = "missing.md"
        self.assertEqual(2, len(MODULE.accepted_decision_reference_errors(ROOT, contract)))

    def test_current_contract_has_exact_nine_group_requirement_confirmation(self) -> None:
        contract = json.loads((ROOT / MODULE.CONTRACT).read_text(encoding="utf-8"))
        packet = json.loads((ROOT / MODULE.P3E09_CONFIRMATION_PACKET).read_text(encoding="utf-8"))
        self.assertEqual([], MODULE.validate_p3e09_requirement_confirmation(contract, packet))

        broken_packet = json.loads(json.dumps(packet))
        broken_packet["groups"] = broken_packet["groups"][:-1]
        errors = MODULE.validate_p3e09_requirement_confirmation(contract, broken_packet)
        self.assertTrue(any("exact nine" in error for error in errors))

        broken_contract = json.loads(json.dumps(contract))
        broken_contract["p3e09RequirementOwnerConfirmation"]["groups"]["Q14"]["itemIdsSha256"] = "0" * 64
        errors = MODULE.validate_p3e09_requirement_confirmation(broken_contract, packet)
        self.assertTrue(any("Q14 item hash" in error for error in errors))

        broken_contract = json.loads(json.dumps(contract))
        broken_contract["p3e09RequirementOwnerConfirmation"]["preConfirmationPacketFileSha256"] = "0" * 64
        errors = MODULE.validate_p3e09_requirement_confirmation(broken_contract, packet, ROOT)
        self.assertTrue(any("pre-confirmation hash mismatch" in error for error in errors))

    def test_accepts_current_hash_v17_explicit_item_decision(self) -> None:
        contract = self.valid_v17_delta_contract()
        ddl = self.valid_v17_delta_ddl()
        delta = contract["v17Delta"]
        delta["status"] = "ACCEPTED"
        delta["ddlSha256"] = hashlib.sha256(ddl.encode("utf-8")).hexdigest().upper()
        delta["acceptedDdlItems"] = ["TABLE:cut_cutover_closure"]
        delta["itemEvidenceRefs"] = {"TABLE:cut_cutover_closure": "docs/decisions/current-hash-review.md"}
        self.assertEqual([], MODULE.validate_v17_delta(contract, self.valid_v17_object_table_map(), ddl))

    def test_rejects_full_platform_claim_and_v3_table(self) -> None:
        contract = self.valid_contract()
        contract["coverage"] = "FULL_PLATFORM_MODEL"
        ddl = self.valid_ddl() + "\nCREATE TABLE kno_technical_advisory (id BIGINT) ENGINE = InnoDB;"
        errors = MODULE.validate_contract(contract, ddl)
        self.assertTrue(any("CORE_MIGRATION_SUBSET" in error for error in errors))
        self.assertTrue(any("V3 design-only" in error for error in errors))

    def test_rejects_missing_or_unproven_current_table_scope(self) -> None:
        contract = self.valid_contract()
        scope = contract["currentTableScope"]
        assert isinstance(scope, dict)
        scope.pop("proj_project")
        errors = MODULE.validate_contract(contract, self.valid_ddl())
        self.assertTrue(any("exact table set" in error for error in errors))

        contract = self.valid_contract()
        scope = contract["currentTableScope"]
        assert isinstance(scope, dict)
        scope["proj_project"] = {"requirementRefs": []}
        errors = MODULE.validate_contract(contract, self.valid_ddl())
        self.assertTrue(any("scope mapping mismatch: proj_project" in error for error in errors))
        self.assertTrue(any("no V1/V2 scope evidence: proj_project" in error for error in errors))

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

    def test_v17_delta_rejects_forbidden_object_mapping(self) -> None:
        object_map = self.valid_v17_object_table_map()
        objects = object_map["objects"]
        assert isinstance(objects, dict)
        objects["HistoricalTimeRecord"] = {"targetTables": ["srv_historical_time_record"]}
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), object_map, self.valid_v17_delta_ddl()
        )
        self.assertTrue(any("srv_historical_time_record" in error and "object table map" in error for error in errors))

    def test_v17_delta_rejects_extra_existing_table_in_object_mapping(self) -> None:
        object_map = self.valid_v17_object_table_map()
        objects = object_map["objects"]
        assert isinstance(objects, dict)
        mapped = objects["DeviceComponentRelation"]
        assert isinstance(mapped, dict)
        mapped["targetTables"].append("proj_project")
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), object_map, self.valid_v17_delta_ddl()
        )
        self.assertTrue(any("DeviceComponentRelation" in error for error in errors))

    def test_v17_delta_rejects_wrong_unique_key_column_order(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "UNIQUE KEY uk_configuration_parse_attempt (tenant_id, collection_result_id, attempt_no)",
            "UNIQUE KEY uk_configuration_parse_attempt (tenant_id, attempt_no, collection_result_id)",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("uk_configuration_parse_attempt" in error for error in errors))

    def test_v17_delta_rejects_missing_required_column_even_if_key_name_remains(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "  parser_version VARCHAR(64) NOT NULL,\n",
            "",
            1,
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("parser_version" in error for error in errors))

    def test_v17_delta_rejects_forbidden_v3_and_out_of_scope_work_order_tables(self) -> None:
        ddl = self.valid_v17_delta_ddl() + """
CREATE TABLE srv_work_order (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
CREATE TABLE srv_work_order_sla (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
"""
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("srv_work_order" in error for error in errors))
        self.assertTrue(any("srv_work_order_sla" in error for error in errors))

    def test_v17_delta_rejects_work_order_semantics_in_support_arrangement(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "  task_duty VARCHAR(1000) NOT NULL,",
            "  task_duty VARCHAR(1000) NOT NULL,\n  status_code VARCHAR(32) NOT NULL,",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("cut_cutover_support_arrangement.status_code" in error for error in errors))

    def test_v17_delta_rejects_step_or_observation_reference_in_closure(self) -> None:
        ddl = self.valid_v17_delta_ddl().replace(
            "  archive_time DATETIME(3) NULL,",
            "  archive_time DATETIME(3) NULL,\n  observation_id BIGINT NULL,",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("cut_cutover_closure.observation_id" in error for error in errors))

    def test_v17_delta_rejects_v3_table_and_cross_context_foreign_key(self) -> None:
        ddl = self.valid_v17_delta_ddl() + """
CREATE TABLE kno_technical_advisory (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;
"""
        ddl = ddl.replace(
            "PRIMARY KEY (id),\n  UNIQUE KEY uk_device_component_current_slot",
            "PRIMARY KEY (id),\n"
            "  CONSTRAINT fk_component_project FOREIGN KEY (tenant_id, id) REFERENCES proj_project (tenant_id, id),\n"
            "  UNIQUE KEY uk_device_component_current_slot",
        )
        errors = MODULE.validate_v17_delta(
            self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
        )
        self.assertTrue(any("V3" in error for error in errors))
        self.assertTrue(any("cross-domain foreign key" in error for error in errors))

    def test_v17_delta_rejects_deferred_and_excluded_current_tables(self) -> None:
        for forbidden in (
            "srv_historical_work_order", "srv_historical_time_record",
            "plt_directory_sync_snapshot", "srv_work_order", "srv_work_order_sla",
            "srv_renewal", "proj_daily_report", "proj_weekly_report",
            "cut_cutover_support_task", "cut_cutover_support_history",
            "cut_cutover_support_responsibility_interval", "cut_execution",
            "cut_execution_step", "cut_observation",
        ):
            with self.subTest(table=forbidden):
                ddl = self.valid_v17_delta_ddl() + (
                    f"\nCREATE TABLE {forbidden} (id BIGINT NOT NULL, PRIMARY KEY (id)) ENGINE = InnoDB;\n"
                )
                errors = MODULE.validate_v17_delta(
                    self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
                )
                self.assertTrue(any(forbidden in error and "must not appear" in error for error in errors))

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
ALTER TABLE ast_device_component_relation
  ADD CONSTRAINT fk_component_project FOREIGN KEY (tenant_id, id)
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
            "uk_cutover_support_arrangement_no": "uk_missing_support_arrangement_no",
            "uk_cutover_closure_task": "uk_missing_cutover_closure_task",
            "uk_device_component_current_slot": "uk_missing_component_current_slot",
        }
        for expected_name, replacement in replacements.items():
            with self.subTest(constraint=expected_name):
                ddl = self.valid_v17_delta_ddl().replace(expected_name, replacement)
                errors = MODULE.validate_v17_delta(
                    self.valid_v17_delta_contract(), self.valid_v17_object_table_map(), ddl
                )
                self.assertTrue(any(expected_name in error for error in errors))


class V18PhysicalCarrierContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.contract = json.loads(
            (ROOT / "docs/traceability/core-migration-schema-contract.json").read_text(encoding="utf-8")
        )
        self.object_table_map = json.loads(
            (ROOT / "docs/traceability/domain-object-table-map.json").read_text(encoding="utf-8")
        )
        self.ddl = (
            ROOT
            / "specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql"
        ).read_bytes().decode("utf-8-sig")

    def test_v18_physical_carriers_match_accepted_contract(self) -> None:
        self.assertEqual(
            [],
            MODULE.validate_v18_delta(self.contract, self.object_table_map, self.ddl),
        )

    def test_v18_physical_carriers_require_all_six_tables(self) -> None:
        ddl = self.ddl.replace("CREATE TABLE cut_cutover_checklist_item_result", "CREATE TABLE removed_cut_result")
        errors = MODULE.validate_v18_delta(self.contract, self.object_table_map, ddl)
        self.assertTrue(any("cut_cutover_checklist_item_result" in error for error in errors), errors)

    def test_v18_cut_result_rejects_dac_or_dispatch_status_copy(self) -> None:
        ddl = self.ddl.replace(
            "result_source_code VARCHAR(32)",
            "dispatch_status_code VARCHAR(32)",
        )
        errors = MODULE.validate_v18_delta(self.contract, self.object_table_map, ddl)
        self.assertTrue(any("dispatch_status_code" in error for error in errors), errors)

    def test_v18_cut_result_requires_current_selection_interval(self) -> None:
        ddl = self.ddl.replace(
            "selection_ended_at DATETIME(3) NULL COMMENT",
            "selection_closed_at DATETIME(3) NULL COMMENT",
        )
        errors = MODULE.validate_v18_delta(self.contract, self.object_table_map, ddl)
        self.assertTrue(any("selection_ended_at" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
