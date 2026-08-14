#!/usr/bin/env python3
"""Validate ADR-0022/ADR-0027 core migration schema and key policies."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
EXECUTION_EVIDENCE = Path("specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json")
OBJECT_TABLE_MAP = Path("docs/traceability/domain-object-table-map.json")
P3E09_CONFIRMATION_PACKET = Path("specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.json")
EXPECTED_P3E09_CONFIRMATION_GROUPS = {"Q07", "Q08", "V1.7", "Q09", "Q10", "Q11", "Q12", "Q13", "Q14"}
EXPECTED_V17_OBJECT_TABLES = {
    "ConfigurationCollectionResult": {
        "imp_configuration_collection_result",
        "imp_configuration_collection_parse_attempt",
        "imp_configuration_component_candidate",
    },
    "SatisfactionCollection": {
        "acc_satisfaction_collection_task",
        "acc_satisfaction_questionnaire",
        "acc_satisfaction_response",
        "acc_satisfaction_result",
    },
    "CutoverSupportArrangement": {"cut_cutover_support_arrangement"},
    "CutoverClosure": {"cut_cutover_closure"},
    "DeviceComponentRelation": {"ast_device_component_relation"},
}
EXPECTED_V17_REQUIREMENTS = {
    "ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04", "CUT-04", "CUT-06",
    "EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07", "EXE-03",
}
EXPECTED_V17_UNIQUE_KEYS = {
    "imp_configuration_collection_result": {
        "uk_configuration_collection_result": ("tenant_id", "collection_task_id", "result_type_code", "result_version_no"),
    },
    "imp_configuration_collection_parse_attempt": {
        "uk_configuration_parse_attempt": ("tenant_id", "collection_result_id", "attempt_no"),
    },
    "imp_configuration_component_candidate": {
        "uk_configuration_component_candidate": ("tenant_id", "parse_attempt_id", "candidate_no"),
    },
    "acc_satisfaction_collection_task": {
        "uk_satisfaction_task_revision": (
            "tenant_id", "project_id", "source_context", "source_object_type", "source_object_id",
            "source_object_version", "business_purpose_code", "applicable_timing_code",
            "payment_stage_key", "task_revision_no",
        ),
    },
    "acc_satisfaction_questionnaire": {
        "uk_satisfaction_questionnaire_revision": ("tenant_id", "task_id", "questionnaire_revision_no"),
    },
    "acc_satisfaction_response": {
        "uk_satisfaction_response_sequence": ("tenant_id", "questionnaire_id", "response_no"),
        "uk_satisfaction_response_request": ("tenant_id", "questionnaire_id", "request_id"),
    },
    "acc_satisfaction_result": {
        "uk_satisfaction_result_sequence": ("tenant_id", "questionnaire_id", "result_no"),
        "uk_satisfaction_result_response": ("tenant_id", "response_id"),
    },
    "cut_cutover_support_arrangement": {
        "uk_cutover_support_arrangement_no": ("tenant_id", "plan_revision_id", "arrangement_no"),
    },
    "cut_cutover_closure": {
        "uk_cutover_closure_task": ("tenant_id", "cutover_task_id"),
    },
    "ast_device_component_relation": {
        "uk_device_component_current_slot": ("tenant_id", "chassis_device_id", "current_slot_code"),
    },
}
V3_DESIGN_ONLY_TABLES = {
    "kno_device_technical_advisory_match",
    "kno_technical_advisory",
    "kno_technical_advisory_product_relation",
    "kno_technical_advisory_read_record",
}
EXPECTED_FORBIDDEN_V1V2_TABLES = V3_DESIGN_ONLY_TABLES | {
    "srv_work_order", "srv_work_order_handling_record", "srv_work_order_sla",
    "srv_time_claim", "srv_time_adjustment",
    "srv_historical_work_order", "srv_historical_time_record",
    "srv_renewal", "srv_renewal_operation",
    "proj_daily_report", "proj_weekly_report",
    "plt_directory_sync_snapshot",
    "cut_cutover_support_task", "cut_cutover_support_history",
    "cut_cutover_support_responsibility_interval", "cut_execution",
    "cut_execution_step", "cut_observation",
}

# Every current table must have an explicit V1/V2 business scope or an accepted
# common migration rule.  The values below are deliberately table-grained so a
# broad domain prefix cannot hide a deferred or excluded table.
EXPECTED_CURRENT_TABLE_SCOPE = {
    "cus_customer": {"requirementRefs": ["CUS-03", "INT-03"]},
    "cus_market_relation": {"requirementRefs": ["CUS-03", "INT-03"]},
    "cus_customer_contact": {"requirementRefs": ["CUS-04"]},
    "ast_product": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "proj_project": {"requirementRefs": ["INT-01", "PM-01", "PM-07", "PM-10"]},
    "proj_project_relation": {"requirementRefs": ["PM-02", "PM-04"]},
    "proj_project_party": {"requirementRefs": ["PM-01"]},
    "proj_project_company_department_relation": {"requirementRefs": ["PM-01", "PM-08"]},
    "proj_project_member_assignment": {"requirementRefs": ["PM-01", "PM-08", "PM-09"]},
    "plt_business_document": {"requirementRefs": ["PLT-02"]},
    "plt_document_version": {"requirementRefs": ["PLT-02"]},
    "acc_deliverable_template": {"requirementRefs": ["ACC-04"]},
    "acc_project_deliverable": {"requirementRefs": ["ACC-04"]},
    "proj_project_portfolio": {"requirementRefs": ["PROJ-12"]},
    "proj_project_portfolio_member": {"requirementRefs": ["PROJ-12"]},
    "com_contract": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_contract_receivable": {"requirementRefs": ["COM-01", "COM-02"]},
    "com_shipment_contract_reference": {"requirementRefs": ["COM-01", "COM-02"]},
    "com_shipment_package": {"requirementRefs": ["COM-01", "COM-02"]},
    "com_project_contract_relation": {"requirementRefs": ["COM-01"]},
    "com_sales_order": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_order_contract_relation": {"requirementRefs": ["COM-01"]},
    "com_sales_order_line": {"requirementRefs": ["COM-01"]},
    "com_delivery_scope": {"requirementRefs": ["COM-01"]},
    "com_delivery_scope_detail": {"requirementRefs": ["COM-01"]},
    "ast_device_sn": {"requirementRefs": ["EQP-01", "EQP-04"]},
    "ast_device_shipment_event": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "ast_device_project_assignment": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "ast_device_relation": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "ast_device_configuration": {"requirementRefs": ["EQP-01", "EQP-02", "EXE-03"]},
    "ast_device_configuration_feature": {"requirementRefs": ["EQP-01", "EQP-02", "EXE-03"]},
    "ast_device_configuration_service": {"requirementRefs": ["EQP-01", "EQP-02", "EXE-03"]},
    "ast_network_topology": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "ast_network_topology_device_relation": {"requirementRefs": ["EQP-01", "EQP-03"]},
    "ast_device_version": {"requirementRefs": ["EQP-01", "EQP-03", "INT-02"]},
    "ast_product_release": {"requirementRefs": ["EQP-01", "EQP-03", "INT-02"]},
    "srv_service_incident": {"requirementRefs": ["EQP-07", "INT-02"]},
    "srv_service_incident_device_relation": {"requirementRefs": ["EQP-07", "INT-02"]},
    "com_crm_execution_order": {"requirementRefs": ["INT-01"]},
    "com_crm_execution_config": {"requirementRefs": ["INT-01"]},
    "com_order_execution_relation": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_order_line_execution_relation": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_execution_order_merge_batch": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_execution_order_merge_member": {"requirementRefs": ["COM-01", "INT-01"]},
    "com_order_change_relation": {"requirementRefs": ["COM-01", "INT-01"]},
    "plt_sync_batch": {"requirementRefs": ["INT-01", "INT-02", "INT-03", "INT-05", "INT-09"]},
    "plt_migration_source_record": {"technicalRuleRefs": ["ADR-0022#migration-lineage-and-issue"]},
    "plt_external_key_mapping": {"technicalRuleRefs": ["ADR-0022#external-key-mapping"]},
    "plt_migration_issue": {"technicalRuleRefs": ["ADR-0022#migration-lineage-and-issue"]},
    "ana_project_delivery_summary": {"requirementRefs": ["ANA-01", "RPT-02"]},
    "imp_configuration_collection_result": {"requirementRefs": ["EXE-03"]},
    "imp_configuration_collection_parse_attempt": {"requirementRefs": ["EXE-03"]},
    "imp_configuration_component_candidate": {"requirementRefs": ["EXE-03"]},
    "acc_satisfaction_collection_task": {"requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    "acc_satisfaction_questionnaire": {"requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    "acc_satisfaction_response": {"requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    "acc_satisfaction_result": {"requirementRefs": ["ACC-02", "CLO-01", "CLO-02", "SUB-03", "SUB-04"]},
    "cut_cutover_support_arrangement": {"requirementRefs": ["CUT-04"]},
    "cut_cutover_closure": {"requirementRefs": ["CUT-06"]},
    "ast_device_component_relation": {"requirementRefs": ["EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07", "EXE-03"]},
}

# Required business/lineage columns are deliberately machine-readable.  Types are
# regexes over the MySQL declaration and nullable is the required NULL contract.
EXPECTED_V17_REQUIRED_COLUMNS = {
    "imp_configuration_collection_result": {
        "collection_task_id": (r"BIGINT", False), "project_id": (r"BIGINT", False),
        "device_id": (r"BIGINT", False), "project_snapshot": (r"JSON", False),
        "device_snapshot": (r"JSON", False), "result_type_code": (r"VARCHAR\(32\)", False),
        "result_version_no": (r"INT\s+UNSIGNED", False), "source_code": (r"VARCHAR\(32\)", False),
        "script_version": (r"VARCHAR\(64\)", True), "parser_version": (r"VARCHAR\(64\)", False),
        "raw_log_file_id": (r"BIGINT", False), "raw_log_sha256": (r"CHAR\(64\)", False),
    },
    "imp_configuration_collection_parse_attempt": {
        "collection_result_id": (r"BIGINT", False), "attempt_no": (r"INT\s+UNSIGNED", False),
        "parser_version": (r"VARCHAR\(64\)", False), "parse_status_code": (r"VARCHAR\(32\)", False),
        "evidence_ref": (r"VARCHAR\(512\)", False),
    },
    "imp_configuration_component_candidate": {
        "parse_attempt_id": (r"BIGINT", False), "candidate_no": (r"INT\s+UNSIGNED", False),
        "parse_revision_no": (r"INT\s+UNSIGNED", False), "chassis_sn": (r"VARCHAR\(128\)", False),
        "slot_code": (r"VARCHAR\(64\)", False), "parser_version": (r"VARCHAR\(64\)", False),
        "card_configuration_ref": (r"VARCHAR\(512\)", False), "evidence_ref": (r"VARCHAR\(512\)", False),
    },
    "acc_satisfaction_collection_task": {
        "project_id": (r"BIGINT", False), "business_purpose_code": (r"VARCHAR\(64\)", False),
        "applicable_timing_code": (r"VARCHAR\(64\)", False), "source_object_version": (r"VARCHAR\(64\)", False),
        "payment_stage_code": (r"VARCHAR\(64\)", True), "delivery_scope_snapshot": (r"JSON", True),
        "delivery_scope_sha256": (r"CHAR\(64\)", True), "prior_task_id": (r"BIGINT", True),
        "remediation_ref": (r"VARCHAR\(512\)", True), "template_id": (r"BIGINT", False),
        "payment_stage_key": (r"VARCHAR\(64\)", True),
    },
    "acc_satisfaction_questionnaire": {
        "task_id": (r"BIGINT", False), "questionnaire_revision_no": (r"INT\s+UNSIGNED", False),
        "source_questionnaire_key": (r"VARCHAR\(128\)", True), "source_questionnaire_version": (r"VARCHAR\(64\)", True),
        "prior_questionnaire_id": (r"BIGINT", True), "remediation_ref": (r"VARCHAR\(512\)", True),
        "template_id": (r"BIGINT", False), "template_version": (r"VARCHAR\(64\)", False),
        "rule_version": (r"VARCHAR\(64\)", False), "required_question_count": (r"INT\s+UNSIGNED", False),
        "frozen_question_json": (r"JSON", False),
    },
    "acc_satisfaction_response": {
        "questionnaire_id": (r"BIGINT", False), "answer_json": (r"JSON", False),
        "response_valid": (r"TINYINT", False), "signature_valid": (r"TINYINT", False),
        "required_validation_summary": (r"JSON", False), "item_validation_summary": (r"JSON", False),
        "signature_ref": (r"VARCHAR\(512\)", False),
    },
    "acc_satisfaction_result": {
        "questionnaire_id": (r"BIGINT", False), "response_id": (r"BIGINT", False),
        "response_valid": (r"TINYINT", False), "signature_valid": (r"TINYINT", False),
        "required_items_valid": (r"TINYINT", False), "validation_summary": (r"JSON", False),
        "blocking_reason": (r"VARCHAR\(1000\)", True), "archive_status_code": (r"VARCHAR\(32\)", False),
        "archive_artifact_id": (r"BIGINT", True), "archive_payload_sha256": (r"CHAR\(64\)", True),
        "archive_time": (r"DATETIME\(3\)", True),
    },
    "cut_cutover_support_arrangement": {
        "cutover_task_id": (r"BIGINT", False), "plan_revision_id": (r"BIGINT", False),
        "arrangement_no": (r"INT\s+UNSIGNED", False), "person_type_code": (r"VARCHAR\(32\)", False),
        "person_name": (r"VARCHAR\(128\)", False), "internal_user_id": (r"BIGINT", True),
        "contact_info": (r"VARCHAR\(512\)", False), "arrival_time": (r"DATETIME\(3\)", True),
        "role_code": (r"VARCHAR\(64\)", False), "task_duty": (r"VARCHAR\(1000\)", False),
    },
    "cut_cutover_closure": {
        "cutover_task_id": (r"BIGINT", False), "plan_revision_id": (r"BIGINT", False),
        "precheck_normal": (r"TINYINT", True), "execution_normal": (r"TINYINT", True),
        "test_normal": (r"TINYINT", True), "rollback_occurred": (r"TINYINT", True),
        "rollback_description": (r"VARCHAR\(1000\)", True), "legacy_item_text": (r"TEXT", True),
        "collection_result_refs": (r"JSON", True), "attachment_refs": (r"JSON", True),
        "result_code": (r"VARCHAR\(32\)", True), "submitted_by": (r"BIGINT", True),
        "submitted_time": (r"DATETIME\(3\)", True), "archive_time": (r"DATETIME\(3\)", True),
    },
    "ast_device_component_relation": {
        "chassis_device_id": (r"BIGINT", False), "slot_code": (r"VARCHAR\(64\)", False),
        "effective_to": (r"DATETIME\(3\)", True), "current_slot_code": (r"VARCHAR\(64\)", True),
    },
}
EXPECTED_V17_FORBIDDEN_COLUMNS = {
    table: sorted({"password", "private_key", "secret", "token"} | (
        {"deleted", "version", "updater", "update_time"}
        if table in {
            "acc_satisfaction_questionnaire", "acc_satisfaction_response", "acc_satisfaction_result",
            "cut_cutover_support_history", "cut_cutover_support_responsibility_interval",
        } else set()
    ))
    for table in EXPECTED_V17_REQUIRED_COLUMNS
}
EXPECTED_V17_FORBIDDEN_COLUMNS["cut_cutover_support_arrangement"] += [
    "current_handler_user_id", "current_responsibility_interval_id",
    "current_responsible_user_id", "effective_from", "effective_to", "status_code", "support_task_id",
]
EXPECTED_V17_FORBIDDEN_COLUMNS["cut_cutover_closure"] += [
    "current_responsibility_interval_id", "execution_step_id", "observation_id", "support_task_id",
]
EXPECTED_V17_GENERATED_EXPRESSIONS = {
    "acc_satisfaction_collection_task.payment_stage_key": "COALESCE(payment_stage_code, '')",
    "ast_device_component_relation.current_slot_code": "CASE WHEN effective_to IS NULL THEN slot_code ELSE NULL END",
}
EXPECTED_NORMALIZATION = {
    "businessCode": "TRIM_UPPERCASE",
    "opaqueExternalKey": "BINARY_EXACT",
    "hash": "BINARY_EXACT",
    "displayName": "UNICODE_CASE_INSENSITIVE",
}
EXPECTED_ACCEPTED_DDL_ITEMS = {
    "COLUMN:plt_external_key_mapping:target_role",
    "COLUMN:plt_external_key_mapping:target_sequence",
    "CONSTRAINT:plt_external_key_mapping:uk_external_key_source_target",
    "CONSTRAINT:plt_external_key_mapping:chk_external_key_target_sequence",
}
EXPECTED_Q03_FACTS = {
    "deviceProjectAssignment": "ONE_CURRENT_DIRECT_PROJECT_PER_DEVICE",
    "customerPrimaryContact": "ONE_CURRENT_PRIMARY_CONTACT_PER_CUSTOMER",
    "projectPrimaryCompanyDepartment": "ONE_CURRENT_PRIMARY_RELATION_PER_PROJECT_ROLE",
    "deliveryScope": "ONE_CURRENT_HEADER_PER_PROJECT_ORDER_LINE_WITH_DETAILS",
    "orderExecution": "MULTIPLE_PRIMARY_EXECUTIONS_ALLOWED",
}
Q03_CURRENT_MARKERS = {
    "ast_device_project_assignment": (
        "current_device_id", "uk_device_current_assignment", {"tenant_id", "current_device_id"}
    ),
    "cus_customer_contact": (
        "primary_customer_id", "uk_customer_primary_contact", {"tenant_id", "primary_customer_id"}
    ),
    "proj_project_company_department_relation": (
        "primary_project_id", "uk_project_primary_company_department", {"tenant_id", "primary_project_id", "relation_role"}
    ),
    "com_delivery_scope": (
        "current_order_line_id", "uk_scope_current", {"tenant_id", "project_id", "current_order_line_id"}
    ),
}
EXPECTED_Q07_POLICY = {
    "historicalViolationPolicy": "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE",
}
EXPECTED_Q08_POLICY = {
    "featureQueryPlanValidationRequired": True,
    "p3e06PerformanceValidationRequired": True,
    "adjustmentPolicy": "FORWARD_MIGRATION_ONLY",
}
TEMPORAL_CHECKS = {
    "chk_device_configuration_dates", "chk_device_assignment_dates", "chk_device_version_dates",
    "chk_network_topology_dates", "chk_contract_dates", "chk_contract_receivable_dates",
    "chk_scope_dates", "chk_project_contract_dates", "chk_shipment_package_warranty_dates",
    "chk_sync_batch_time", "chk_project_company_department_dates", "chk_project_member_dates",
    "chk_project_party_dates", "chk_service_incident_times",
    "chk_configuration_parse_attempt_time", "chk_cutover_support_window",
    "chk_cutover_responsibility_dates", "chk_device_component_dates",
}
NO_SELF_CHECKS = {
    "chk_device_relation_self", "chk_device_secondary_self", "chk_order_change_self",
    "chk_project_relation_self",
}
NONNEGATIVE_CHECKS = {
    "chk_external_key_target_sequence", "chk_migration_source_target_count",
    "chk_sync_batch_count", "chk_project_depth",
    "chk_configuration_collection_result_version",
    "chk_configuration_parse_attempt_no", "chk_configuration_component_candidate_no",
    "chk_satisfaction_task_revision", "chk_satisfaction_questionnaire_revision",
    "chk_satisfaction_response_sequence", "chk_satisfaction_result_sequence",
    "chk_cutover_support_history_sequence", "chk_cutover_responsibility_interval_sequence",
    "chk_cutover_support_arrangement_no",
}


def parse_tables(ddl: str) -> dict[str, str]:
    return {
        match.group(1): match.group(2)
        for match in re.finditer(
            r"CREATE\s+TABLE\s+(\w+)\s*\((.*?)\)\s*ENGINE\s*=",
            ddl,
            re.IGNORECASE | re.DOTALL,
        )
    }


def unique_keys(body: str) -> list[tuple[str, str]]:
    return [
        (match.group(1), " ".join(match.group(2).split()))
        for match in re.finditer(r"UNIQUE\s+KEY\s+(\w+)\s*\((.*?)\)", body, re.IGNORECASE | re.DOTALL)
    ]


def normalized_columns(columns: str) -> tuple[str, ...]:
    return tuple(item.strip().strip("`").lower() for item in columns.split(",") if item.strip())


def column_declarations(body: str) -> dict[str, str]:
    """Return top-level column declarations, including multiline generated expressions."""
    declarations: dict[str, str] = {}
    current_name: str | None = None
    current_lines: list[str] = []
    nonempty = [line for line in body.splitlines() if line.strip()]
    top_indent = min(len(line) - len(line.lstrip()) for line in nonempty) if nonempty else 0
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        is_top_level = len(line) - len(line.lstrip()) == top_indent
        is_constraint = re.match(
            r"(?:PRIMARY|UNIQUE|KEY|CONSTRAINT|FOREIGN|CHECK)\b", stripped, re.IGNORECASE
        )
        column = None if is_constraint or not is_top_level else re.match(r"`?(\w+)`?\s+", stripped)
        if column:
            if current_name is not None:
                declarations[current_name] = " ".join(current_lines).rstrip(",")
            current_name = column.group(1).lower()
            current_lines = [stripped]
        elif current_name is not None:
            current_lines.append(stripped)
    if current_name is not None:
        declarations[current_name] = " ".join(current_lines).rstrip(",")
    return declarations


def normalize_sql_expression(value: str) -> str:
    return " ".join(value.replace("`", "").split()).strip().upper()


def v17_table_contract_payload() -> dict[str, object]:
    return {
        table: {
            "requiredColumns": {
                name: {"typePattern": type_pattern, "nullable": nullable}
                for name, (type_pattern, nullable) in columns.items()
            },
            "forbiddenColumns": EXPECTED_V17_FORBIDDEN_COLUMNS[table],
            "uniqueKeys": {
                name: list(columns)
                for name, columns in EXPECTED_V17_UNIQUE_KEYS[table].items()
            },
            "generatedExpressions": {
                key.split(".", 1)[1]: expression
                for key, expression in EXPECTED_V17_GENERATED_EXPRESSIONS.items()
                if key.startswith(table + ".")
            },
        }
        for table, columns in EXPECTED_V17_REQUIRED_COLUMNS.items()
    }


def q07_q08_actual_counts(tables: dict[str, str], ddl: str) -> tuple[dict[str, object], int]:
    primary_count = 0
    single_id_primary_count = 0
    tenant_reference_count = 0
    foreign_key_count = 0
    ordinary_index_count = 0
    check_groups = {
        "softDelete": 0,
        "temporalOrder": 0,
        "booleanFlag": 0,
        "noSelf": 0,
        "nonnegativeCount": 0,
    }
    for body in tables.values():
        primary_matches = re.findall(r"(?m)^\s*PRIMARY\s+KEY\s*\((.*?)\)", body, re.IGNORECASE)
        primary_count += len(primary_matches)
        single_id_primary_count += sum(1 for columns in primary_matches if columns.strip().lower() == "id")
        for name, _columns in unique_keys(body):
            if name.endswith("tenant_row") or name == "uk_document_version_owner":
                tenant_reference_count += 1
        ordinary_index_count += len(re.findall(r"(?m)^\s*KEY\s+\w+\s*\(", body, re.IGNORECASE))
        for match in re.finditer(
            r"(?m)^\s*CONSTRAINT\s+(\w+)\s+CHECK\s*\((.*?)\)(?:\s*,?\s*$)",
            body,
            re.IGNORECASE | re.DOTALL,
        ):
            name, expression = match.group(1), match.group(2)
            if name.endswith("_deleted"):
                check_groups["softDelete"] += 1
            elif name in TEMPORAL_CHECKS:
                check_groups["temporalOrder"] += 1
            elif name in NO_SELF_CHECKS:
                check_groups["noSelf"] += 1
            elif name in NONNEGATIVE_CHECKS:
                check_groups["nonnegativeCount"] += 1
            elif re.search(r"\bIN\s*\(\s*0\s*,\s*1\s*\)", expression, re.IGNORECASE):
                check_groups["booleanFlag"] += 1
    foreign_key_count = len(re.findall(r"\bFOREIGN\s+KEY\s*\(", ddl, re.IGNORECASE))
    return {
        "primaryKeyCount": primary_count,
        "primaryKeyShape": {
            "singleId": single_id_primary_count,
            "compositeProjection": primary_count - single_id_primary_count,
        },
        "tenantReferenceKeyCount": tenant_reference_count,
        "sameDomainForeignKeyCount": foreign_key_count,
        "stableTechnicalCheckGroups": check_groups,
    }, ordinary_index_count


def validate_v17_delta(
    contract: dict[str, object], object_table_map: dict[str, object], ddl: str
) -> list[str]:
    """Validate the closed V1.7 table delta without claiming P3-E09 approval."""
    errors: list[str] = []
    tables = parse_tables(ddl)
    expected_tables = set().union(*EXPECTED_V17_OBJECT_TABLES.values())
    delta = contract.get("v17Delta", {})
    if not isinstance(delta, dict):
        return ["V1.7 delta contract must be an object"]
    delta_status = delta.get("status")
    if delta.get("decisionRef") != "ADR-0027" or delta_status not in {"BLOCKED_BY_REVIEW", "ACCEPTED"}:
        errors.append("V1.7 delta metadata/status is invalid")
    if delta_status == "ACCEPTED":
        ddl_sha = hashlib.sha256(ddl.encode("utf-8")).hexdigest().upper()
        accepted_items = delta.get("acceptedDdlItems", [])
        item_refs = delta.get("itemEvidenceRefs", {})
        if delta.get("ddlSha256") != ddl_sha or not isinstance(accepted_items, list) or not accepted_items:
            errors.append("accepted V1.7 delta must bind current DDL and explicit itemIds")
        if not isinstance(item_refs, dict) or set(item_refs) != set(accepted_items) or any(not value for value in item_refs.values()):
            errors.append("accepted V1.7 delta must provide evidence for every accepted itemId")
    if set(delta.get("requirementRefs", [])) != EXPECTED_V17_REQUIREMENTS:
        errors.append("V1.7 delta requirement reference set mismatch")
    if delta.get("tableContracts") != v17_table_contract_payload():
        errors.append("V1.7 per-table column and constraint contract mismatch")
    declared = delta.get("objectTargetTables", {})
    if not isinstance(declared, dict):
        errors.append("V1.7 objectTargetTables must be an object")
        declared = {}
    objects = object_table_map.get("objects", {})
    if not isinstance(objects, dict):
        objects = {}
    for object_name, expected in EXPECTED_V17_OBJECT_TABLES.items():
        if set(declared.get(object_name, [])) != expected:
            errors.append(f"V1.7 contract table mapping mismatch: {object_name}")
        mapped = objects.get(object_name, {})
        mapped_tables = mapped.get("targetTables", []) if isinstance(mapped, dict) else []
        if set(mapped_tables) != expected:
            errors.append(f"V1.7 object table map mismatch: {object_name}")
    forbidden_mapped = sorted({
        table
        for mapped in objects.values() if isinstance(mapped, dict)
        for table in mapped.get("targetTables", []) if isinstance(table, str)
        if table in EXPECTED_FORBIDDEN_V1V2_TABLES
    })
    if forbidden_mapped:
        errors.append(f"forbidden current tables must not appear in object table map: {forbidden_mapped}")
    declared_tables = {
        table for values in declared.values() if isinstance(values, list) for table in values
    }
    if declared_tables != expected_tables:
        errors.append("V1.7 delta must declare exactly the 10 in-scope target tables")
    for table in sorted(expected_tables):
        if table not in tables:
            errors.append(f"V1.7 target table missing: {table}")
            continue
        body = tables[table]
        declarations = column_declarations(body)
        for column, (type_pattern, nullable) in EXPECTED_V17_REQUIRED_COLUMNS[table].items():
            declaration = declarations.get(column)
            if declaration is None:
                errors.append(f"V1.7 required column missing: {table}.{column}")
                continue
            if not re.search(type_pattern, declaration, re.IGNORECASE):
                errors.append(f"V1.7 required column type mismatch: {table}.{column}")
            actual_nullable = not bool(re.search(r"\bNOT\s+NULL\b", declaration, re.IGNORECASE))
            if actual_nullable != nullable:
                errors.append(f"V1.7 required column nullability mismatch: {table}.{column}")
        for column in EXPECTED_V17_FORBIDDEN_COLUMNS[table]:
            if column in declarations:
                errors.append(f"V1.7 forbidden column present: {table}.{column}")
        actual_keys = {name: normalized_columns(columns) for name, columns in unique_keys(body)}
        for constraint, expected_columns in EXPECTED_V17_UNIQUE_KEYS[table].items():
            if actual_keys.get(constraint) != expected_columns:
                errors.append(f"V1.7 unique constraint shape mismatch: {table}.{constraint}")
        for key, expected_expression in EXPECTED_V17_GENERATED_EXPRESSIONS.items():
            expected_table, column = key.split(".", 1)
            if expected_table != table:
                continue
            declaration = declarations.get(column, "")
            generated = re.search(
                r"GENERATED\s+ALWAYS\s+AS\s*\((.*?)\)\s*STORED",
                declaration,
                re.IGNORECASE | re.DOTALL,
            )
            if not generated or normalize_sql_expression(generated.group(1)) != normalize_sql_expression(expected_expression):
                errors.append(f"V1.7 generated expression mismatch: {table}.{column}")

    forbidden_tables = set(contract.get("forbiddenV1V2Tables", []))
    if forbidden_tables != EXPECTED_FORBIDDEN_V1V2_TABLES:
        errors.append("V1/V2 forbidden table machine list mismatch")
    present_forbidden = sorted(forbidden_tables & tables.keys())
    if present_forbidden:
        errors.append(f"V3/OUT_OF_SCOPE tables must not appear in V1.7 delta DDL: {present_forbidden}")
    for table, body in tables.items():
        owner = table.split("_", 1)[0]
        for match in re.finditer(
            r"CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)",
            body,
            re.IGNORECASE | re.DOTALL,
        ):
            target = match.group(2)
            if owner != target.split("_", 1)[0]:
                errors.append(f"cross-domain foreign key {match.group(1)}: {table} -> {target}")
    for match in re.finditer(
        r"ALTER\s+TABLE\s+(\w+)\s+ADD\s+CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)",
        ddl,
        re.IGNORECASE | re.DOTALL,
    ):
        source, constraint, target = match.group(1), match.group(2), match.group(3)
        if source.split("_", 1)[0] != target.split("_", 1)[0]:
            errors.append(f"cross-domain foreign key {constraint}: {source} -> {target}")

    if delta.get("historicalReadOnlyTables") != []:
        errors.append("V1.7 historical read-only table set must be empty until a real source and model are approved")

    expected_append_only = {
        "acc_satisfaction_questionnaire", "acc_satisfaction_response", "acc_satisfaction_result",
        "cut_cutover_closure",
    }
    if set(delta.get("appendOnlyTables", [])) != expected_append_only:
        errors.append("V1.7 append-only table set mismatch")
    for table in expected_append_only:
        body = tables.get(table, "")
        for column in {"deleted", "version", "updater", "update_time"}:
            if re.search(rf"(?m)^\s*{column}\s+", body, re.IGNORECASE):
                errors.append(f"V1.7 append-only table contains mutable column: {table}.{column}")

    relation = tables.get("ast_device_component_relation", "")
    generated = re.search(
        r"\bcurrent_slot_code\b.*?GENERATED\s+ALWAYS\s+AS\s*\((.*?)\)\s*STORED",
        relation,
        re.IGNORECASE | re.DOTALL,
    )
    if not generated:
        errors.append("V1.7 device component generated current marker missing")
    elif re.search(r"\b(?:status|deleted)\b", generated.group(1), re.IGNORECASE):
        errors.append("V1.7 device component current marker must depend on effective_to only")
    relation_unique = dict(unique_keys(relation)).get("uk_device_component_current_slot", "")
    relation_columns = {value.strip().lower() for value in relation_unique.split(",") if value.strip()}
    if relation_columns != {"tenant_id", "chassis_device_id", "current_slot_code"}:
        errors.append("V1.7 device component current unique key grain mismatch")
    return errors


def validate_contract(contract: dict[str, object], ddl: str) -> list[str]:
    errors: list[str] = []
    tables = parse_tables(ddl)

    for match in re.finditer(r",\s*\)\s*ENGINE\s*=", ddl, re.IGNORECASE | re.DOTALL):
        line = ddl.count("\n", 0, match.start()) + 1
        errors.append(f"DDL trailing comma before table close at line {line}")

    if contract.get("schemaVersion") != 1 or contract.get("decisionRef") != "ADR-0022":
        errors.append("core migration contract metadata mismatch")
    if contract.get("coverage") != "CORE_MIGRATION_SUBSET":
        errors.append("coverage must be CORE_MIGRATION_SUBSET, never a full-platform claim")
    if contract.get("crossDomainReferencePolicy") != "LOGICAL_REFERENCE":
        errors.append("cross-domain reference policy must be LOGICAL_REFERENCE")
    if set(contract.get("v3DesignOnlyTables", [])) != V3_DESIGN_ONLY_TABLES:
        errors.append("V3 design-only table set mismatch")
    if set(contract.get("forbiddenV1V2Tables", [])) != EXPECTED_FORBIDDEN_V1V2_TABLES:
        errors.append("V1/V2 forbidden table machine list mismatch")
    current_scope = contract.get("currentTableScope", {})
    if isinstance(current_scope, dict):
        if set(current_scope) != set(tables):
            errors.append("current DDL table scope must cover the exact table set")
        for table, scope in current_scope.items():
            if scope != EXPECTED_CURRENT_TABLE_SCOPE.get(table):
                errors.append(f"current DDL table scope mapping mismatch: {table}")
            if not isinstance(scope, dict) or not (
                scope.get("requirementRefs") or scope.get("technicalRuleRefs")
            ):
                errors.append(f"current DDL table has no V1/V2 scope evidence: {table}")
    if set(contract.get("acceptedDdlItems", [])) != EXPECTED_ACCEPTED_DDL_ITEMS:
        errors.append("ADR-0022 accepted DDL item set mismatch")
    if contract.get("q03CurrentBusinessFacts") != EXPECTED_Q03_FACTS:
        errors.append("ADR-0023 Q03 current business fact set mismatch")
    ddl_sha = hashlib.sha256(ddl.encode("utf-8")).hexdigest().upper()
    q07 = contract.get("q07TechnicalConstraintPolicy", {})
    q08 = contract.get("q08OrdinaryIndexPolicy", {})
    if not isinstance(q07, dict) or q07.get("ddlSha256") != ddl_sha or any(
        q07.get(key) != value for key, value in EXPECTED_Q07_POLICY.items()
    ):
        errors.append("ADR-0023 Q07 technical constraint policy mismatch")
    if not isinstance(q08, dict) or q08.get("ddlSha256") != ddl_sha or any(
        q08.get(key) != value for key, value in EXPECTED_Q08_POLICY.items()
    ):
        errors.append("ADR-0023 Q08 ordinary index policy mismatch")
    for name, policy, proposed in (
        ("Q07", q07, "ACCEPT_CURRENT_FOR_SDS"),
        ("Q08", q08, "ACCEPT_AS_CANDIDATE_BASELINE"),
    ):
        status = policy.get("status") if isinstance(policy, dict) else None
        if status not in {"RECONFIRMATION_REQUIRED", "ACCEPTED"}:
            errors.append(f"ADR-0023 {name} status is invalid")
        elif status == "RECONFIRMATION_REQUIRED" and policy.get("proposedDecision") != proposed:
            errors.append(f"ADR-0023 {name} proposed decision mismatch")
        elif status == "ACCEPTED" and (policy.get("decision") != proposed or not policy.get("decisionEvidenceRef")):
            errors.append(f"ADR-0023 {name} accepted decision lacks current-hash evidence")
    actual_q07, actual_q08_count = q07_q08_actual_counts(tables, ddl)
    if isinstance(q07, dict) and any(q07.get(key) != value for key, value in actual_q07.items()):
        errors.append("ADR-0023 Q07 accepted constraint counts differ from current DDL")
    if isinstance(q08, dict) and q08.get("candidateIndexCount") != actual_q08_count:
        errors.append("ADR-0023 Q08 candidate index count differs from current DDL")

    present_v3 = sorted(V3_DESIGN_ONLY_TABLES & tables.keys())
    if present_v3:
        errors.append(f"V3 design-only tables must not appear in core DDL: {present_v3}")

    for table, body in tables.items():
        owner = table.split("_", 1)[0]
        for match in re.finditer(
            r"CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)",
            body,
            re.IGNORECASE | re.DOTALL,
        ):
            target = match.group(2)
            if owner != target.split("_", 1)[0]:
                errors.append(f"cross-domain foreign key {match.group(1)}: {table} -> {target}")
    for match in re.finditer(
        r"ALTER\s+TABLE\s+(\w+)\s+ADD\s+CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)",
        ddl,
        re.IGNORECASE | re.DOTALL,
    ):
        source, constraint, target = match.group(1), match.group(2), match.group(3)
        if source.split("_", 1)[0] != target.split("_", 1)[0]:
            errors.append(f"cross-domain foreign key {constraint}: {source} -> {target}")

    mapping = contract.get("externalKeyMapping", {})
    mapping_body = tables.get(str(mapping.get("table", "")), "") if isinstance(mapping, dict) else ""
    for column in ("target_role", "target_sequence"):
        if not re.search(rf"(?m)^\s*{column}\s+", mapping_body, re.IGNORECASE):
            errors.append(f"plt_external_key_mapping missing {column}")
    if mapping_body:
        if not re.search(r"target_role\s+VARCHAR\(32\)\s+NOT\s+NULL\s+DEFAULT\s+'PRIMARY'", mapping_body, re.IGNORECASE):
            errors.append("target_role must default to PRIMARY")
        if not re.search(r"target_sequence\s+INT\s+UNSIGNED\s+NOT\s+NULL\s+DEFAULT\s+0", mapping_body, re.IGNORECASE):
            errors.append("target_sequence must be unsigned and default to 0")
        mapping_unique = dict(unique_keys(mapping_body)).get("uk_external_key_source_target", "")
        for column in ("target_role", "target_sequence", "target_table", "target_id"):
            if column not in mapping_unique:
                errors.append(f"external key unique constraint missing {column}")
        if not re.search(r"CHECK\s*\(\s*target_sequence\s*>=\s*0\s*\)", mapping_body, re.IGNORECASE):
            errors.append("target_sequence non-negative CHECK missing")

    normalization = contract.get("normalization", {})
    if not isinstance(normalization, dict) or any(normalization.get(k) != v for k, v in EXPECTED_NORMALIZATION.items()):
        errors.append("normalization policy mismatch")
    permanent = contract.get("permanentKeys", {})
    if not isinstance(permanent, dict) or permanent.get("reuseAllowed") is not False or permanent.get("uniqueKeyIncludesDeleted") is not False:
        errors.append("permanent business key policy mismatch")

    permanent_names = set(permanent.get("ddlUniqueKeys", [])) if isinstance(permanent, dict) else set()
    for table, body in tables.items():
        for name, columns in unique_keys(body):
            if (name in permanent_names or "source" in name) and re.search(r"\bdeleted\b", columns, re.IGNORECASE):
                errors.append(f"permanent/source unique key must not include deleted: {table}.{name}")
            if re.search(r"\beffective_to\b", columns, re.IGNORECASE):
                errors.append(f"current uniqueness must not depend on nullable effective_to: {table}.{name}")

    if contract.get("currentRecordUniqueness") != "GENERATED_CURRENT_MARKER":
        errors.append("current record uniqueness policy mismatch")
    for table, (marker, unique_name, expected_columns) in Q03_CURRENT_MARKERS.items():
        body = tables.get(table, "")
        if not body:
            errors.append(f"Q03 current relation table missing: {table}")
            continue
        generated = re.search(
            rf"\b{re.escape(marker)}\b[^\n]*GENERATED\s+ALWAYS\s+AS\s*\((.*?)\)\s*STORED",
            body,
            re.IGNORECASE | re.DOTALL,
        )
        if not generated:
            errors.append(f"Q03 generated current marker missing: {table}.{marker}")
        elif re.search(r"\b(?:status|scope_status|assignment_status)\b", generated.group(1), re.IGNORECASE):
            errors.append(f"Q03 current marker must not depend on extendable status: {table}.{marker}")
        unique_columns = dict(unique_keys(body)).get(unique_name)
        if unique_columns is None:
            errors.append(f"Q03 current unique key missing: {table}.{unique_name}")
        else:
            actual_columns = {value.strip().lower() for value in unique_columns.split(",")}
            if actual_columns != expected_columns:
                errors.append(f"Q03 current unique key grain mismatch: {table}.{unique_name}")

    if "com_delivery_scope_detail" not in tables:
        errors.append("Q03 delivery scope detail table missing")

    order_execution = tables.get("com_order_execution_relation", "")
    if not order_execution:
        errors.append("Q03 order execution relation table missing")
    else:
        if re.search(r"(?m)^\s*primary_order_id\s+", order_execution, re.IGNORECASE):
            errors.append("Q03 multiple primary executions must not use a unique primary marker")
        if not re.search(r"is_primary\s+TINYINT\s+NOT\s+NULL\s+DEFAULT\s+1", order_execution, re.IGNORECASE):
            errors.append("Q03 order execution relation must default the execution relation as primary")
        order_unique = dict(unique_keys(order_execution))
        if set(value.strip().lower() for value in order_unique.get("uk_order_execution", "").split(",")) != {"tenant_id", "order_id", "execution_id"}:
            errors.append("Q03 order execution relation grain mismatch")
        for name, columns in order_unique.items():
            if name == "uk_order_primary_execution" or "is_primary" in columns.lower() or "primary_order_id" in columns.lower():
                errors.append("Q03 multiple primary executions must not be constrained as unique")
    if contract.get("historicalAnomalyPolicy") != "MIGRATION_ISSUE_WITH_SOURCE_EVIDENCE":
        errors.append("historical anomaly policy mismatch")
    return errors


def accepted_decision_reference_errors(root: Path, contract: dict[str, object]) -> list[str]:
    errors: list[str] = []
    for policy_name in ("q07TechnicalConstraintPolicy", "q08OrdinaryIndexPolicy"):
        policy = contract.get(policy_name, {})
        if not isinstance(policy, dict) or policy.get("status") != "ACCEPTED":
            continue
        reference = policy.get("decisionEvidenceRef")
        if not isinstance(reference, str) or not (root / reference.split("#", 1)[0]).is_file():
            errors.append(f"{policy_name} accepted decision evidence does not exist")
    return errors


def confirmation_ids_sha256(item_ids: list[str]) -> str:
    canonical = json.dumps(sorted(item_ids), ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest().upper()


def validate_p3e09_requirement_confirmation(
    contract: dict[str, object], packet: dict[str, object]
) -> list[str]:
    errors: list[str] = []
    confirmation = contract.get("p3e09RequirementOwnerConfirmation", {})
    accepted_policies = all(
        contract.get(name, {}).get("status") == "ACCEPTED"
        for name in ("q07TechnicalConstraintPolicy", "q08OrdinaryIndexPolicy", "v17Delta")
    )
    if not accepted_policies:
        return errors
    if not isinstance(confirmation, dict) or confirmation.get("status") != "ACCEPTED":
        return ["accepted P3-E09 policies require the nine-group Requirement Owner confirmation"]
    if confirmation.get("decision") != "ALL_RECOMMENDED_A" or confirmation.get("reviewStatus") != "REVIEW_PENDING":
        errors.append("P3-E09 Requirement Owner confirmation state mismatch")
    if confirmation.get("approvedDdlSha256") is not None:
        errors.append("Requirement Owner confirmation must not fabricate approvedDdlSha256")
    ddl_sha = contract["q07TechnicalConstraintPolicy"].get("ddlSha256")
    if confirmation.get("ddlSha256") != ddl_sha or packet.get("currentDdlSha256") != ddl_sha:
        errors.append("P3-E09 confirmation DDL hash mismatch")
    if confirmation.get("packetRef") != P3E09_CONFIRMATION_PACKET.as_posix():
        errors.append("P3-E09 confirmation packet reference mismatch")
    if packet.get("deferredItemCount") != 692 or packet.get("coveredDeferredItemCount") != 692:
        errors.append("P3-E09 confirmation packet must cover all 692 deferred items")
    packet_groups = {
        group.get("code"): group for group in packet.get("groups", []) if isinstance(group, dict)
    }
    contract_groups = confirmation.get("groups", {})
    if set(packet_groups) != EXPECTED_P3E09_CONFIRMATION_GROUPS or set(contract_groups) != EXPECTED_P3E09_CONFIRMATION_GROUPS:
        errors.append("P3-E09 confirmation must contain the exact nine decision groups")
        return errors
    union_ids: set[str] = set()
    for code in sorted(EXPECTED_P3E09_CONFIRMATION_GROUPS):
        group = packet_groups[code]
        item_ids = [item.get("itemId") for item in group.get("items", []) if isinstance(item, dict)]
        union_ids.update(item_ids)
        contract_group = contract_groups[code]
        if group.get("recommendedDecision") != "A" or contract_group.get("decision") != "A":
            errors.append(f"P3-E09 group {code} decision mismatch")
        if len(item_ids) != group.get("itemCount") or contract_group.get("itemCount") != len(item_ids):
            errors.append(f"P3-E09 group {code} item count mismatch")
        if contract_group.get("itemIdsSha256") != confirmation_ids_sha256(item_ids):
            errors.append(f"P3-E09 group {code} item hash mismatch")
    if confirmation.get("confirmedUniqueItemCount") != len(union_ids) or len(union_ids) != 695:
        errors.append("P3-E09 confirmed unique item coverage mismatch")
    if confirmation.get("confirmedItemIdsSha256") != confirmation_ids_sha256(list(union_ids)):
        errors.append("P3-E09 confirmed item union hash mismatch")
    return errors


def validate_execution_evidence(evidence: dict[str, object], ddl_bytes: bytes, table_count: int) -> list[str]:
    errors: list[str] = []
    ddl_sha = hashlib.sha256(ddl_bytes).hexdigest().upper()
    if evidence.get("status") != "PASS" or evidence.get("purpose") != "P3_E09_ISOLATED_MYSQL_DDL_EXECUTION":
        errors.append("isolated MySQL DDL execution evidence is not PASS")
    if evidence.get("ddlSha256") != ddl_sha:
        errors.append("isolated MySQL DDL execution evidence hash is stale")
    if evidence.get("tableCount") != table_count or evidence.get("expectedTableCount") != table_count:
        errors.append("isolated MySQL DDL execution table count mismatch")
    if not str(evidence.get("mysqlVersion", "")).startswith("8.4."):
        errors.append("isolated DDL execution must use MySQL 8.4.x")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", type=Path, default=CONTRACT)
    parser.add_argument("--ddl", type=Path, default=DDL)
    parser.add_argument("--execution-evidence", type=Path, default=EXECUTION_EVIDENCE)
    parser.add_argument("--object-table-map", type=Path, default=OBJECT_TABLE_MAP)
    args = parser.parse_args()
    ddl_bytes = args.ddl.read_bytes()
    ddl_text = ddl_bytes.decode("utf-8")
    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    errors = validate_contract(contract, ddl_text)
    errors.extend(accepted_decision_reference_errors(Path.cwd(), contract))
    confirmation_packet = json.loads((Path.cwd() / P3E09_CONFIRMATION_PACKET).read_text(encoding="utf-8"))
    errors.extend(validate_p3e09_requirement_confirmation(contract, confirmation_packet))
    errors.extend(validate_v17_delta(
        contract,
        json.loads(args.object_table_map.read_text(encoding="utf-8")),
        ddl_text,
    ))
    errors.extend(validate_execution_evidence(
        json.loads(args.execution_evidence.read_text(encoding="utf-8")),
        ddl_bytes,
        len(parse_tables(ddl_text)),
    ))
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] ADR-0022/ADR-0027 core migration schema contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
