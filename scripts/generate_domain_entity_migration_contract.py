#!/usr/bin/env python3
"""Generate source-specific migration contracts for every Phase 2 data object."""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import subprocess
from pathlib import Path


TARGETS: dict[str, tuple[str, ...]] = {
    "Project": ("proj_project",), "ProjectHierarchy": ("proj_project",), "ProjectAncestorProjection": ("proj_project_tree_path",),
    "ProjectTemplate": ("proj_project_template_revision",), "ProjectTask": ("proj_project_task",), "TaskAncestorProjection": ("proj_task_tree_path",),
    "TaskDependency": ("proj_task_dependency",), "ProjectMemberAssignment": ("proj_project_member_assignment",),
    "ProjectPortfolio": ("proj_project_portfolio", "proj_project_portfolio_member", "proj_project_portfolio_revision"),
    "ProjectStageSnapshot": ("proj_project_stage_snapshot",), "BorrowedProjectConversion": ("proj_project_conversion",),
    "ConversionItem": ("proj_project_conversion_item",), "ConversionDeviceDisposition": ("proj_project_conversion_device",),
    "MultiPhaseProjectGroup": ("proj_multi_phase_project_group",), "MultiPhaseProjectMember": ("proj_multi_phase_project_member",),
    "CrossPhaseContentReference": ("proj_project_cross_phase_reference",), "Preparation": ("sol_preparation", "sol_preparation_item"),
    "ConstructionPlan": ("sol_construction_plan", "sol_construction_plan_revision", "sol_construction_plan_item", "sol_construction_plan_change"),
    "Solution": ("sol_solution", "sol_solution_revision", "sol_solution_review"),
    "DynamicFormSchema": ("sol_dynamic_form_schema", "sol_dynamic_form_schema_revision"), "DynamicFormInstance": ("sol_dynamic_form_instance",),
    "ArrivalAcceptance": ("imp_arrival_acceptance", "imp_arrival_line", "imp_arrival_difference"),
    "InstallationRecord": ("imp_installation_record", "imp_installation_item", "imp_installation_evidence"),
    "ConfigurationCollectionResult": ("imp_configuration_collection_result", "imp_configuration_collection_parse_attempt"),
    "JointDebuggingResult": ("imp_joint_debugging_result", "imp_joint_debugging_item"), "ImplementationRisk": ("imp_risk", "imp_risk_treatment"),
    "ImplementationQualityCheck": ("imp_quality_check", "imp_quality_item", "imp_quality_remediation", "imp_quality_review"),
    "ImplementationSafetyCheck": ("imp_safety_check", "imp_safety_item", "imp_safety_remediation", "imp_safety_exemption"),
    "DeliveryEvidence": ("imp_delivery_evidence", "imp_delivery_evidence_revision"),
    "ImplementationReadinessSnapshot": ("imp_implementation_readiness_snapshot",), "Acceptance": ("acc_acceptance", "acc_acceptance_item", "acc_confirmation"),
    "DeliveryArtifact": ("acc_delivery_artifact", "acc_artifact_review", "acc_archive_record"),
    "ProjectClosure": ("acc_project_closure", "acc_closure_review"), "ClosureGateSnapshot": ("acc_closure_gate_snapshot",),
    "ServiceHandover": ("acc_service_handover", "acc_handover_item", "acc_handover_result"),
    "CutoverTask": ("cut_task",), "CutoverAssessment": ("cut_assessment",),
    "CutoverPlan": ("cut_plan_revision", "cut_step"), "CutoverExecution": ("cut_execution", "cut_execution_step", "cut_observation"),
    "InspectionTask": ("srv_inspection_task", "srv_inspection_task_rule_snapshot"), "InspectionRule": ("srv_inspection_rule", "srv_inspection_rule_revision"),
    "InspectionReport": ("srv_inspection_report_revision",), "ServiceIssue": ("srv_service_issue", "srv_service_issue_remediation"),
    "WorkOrder": ("srv_work_order", "srv_work_order_handling_record"), "TimeClaim": ("srv_time_claim", "srv_time_adjustment"),
    "ServiceStatus": ("srv_service_status",), "Customer": ("cus_customer",), "CustomerContact": ("cus_customer_contact", "cus_project_customer_contact_relation"),
    "CustomerRelationshipSnapshot": ("cus_customer_relationship_snapshot",), "Device": ("ast_device",), "DeviceArchive": ("ast_device", "ast_device_version", "ast_device_config_log"),
    "DeviceCurrentAssignment": ("ast_device_current_assignment", "ast_device_assignment_history"),
    "DeviceAssignmentHistory": ("ast_device_assignment_history",), "DeviceAncestorProjection": ("ast_device_project_ancestor",),
    "AssetSyncSnapshot": ("ast_asset_sync_batch", "ast_asset_sync_item", "ast_device"), "MaintenanceFact": ("ast_maintenance_fact",),
    "RMAReplacement": ("ast_rma_replacement",), "Contract": ("com_contract",), "SalesOrder": ("com_sales_order",),
    "OrderLine": ("com_order_line",), "DeliveryScope": ("com_delivery_scope",), "FulfillmentSnapshot": ("com_fulfillment_snapshot",),
    "ReconciliationRecord": ("com_reconciliation_record",), "Supplier": ("res_supplier", "res_qualification"),
    "SubcontractRequest": ("res_subcontract_request",), "PaymentGate": ("res_payment_gate",), "MetricDefinition": ("ana_metric_definition",), "MetricSnapshot": ("ana_metric_snapshot",),
    "PortfolioView": ("ana_portfolio_projection",), "Todo": ("plt_todo",), "AuthorizationGrant": ("plt_authorization_grant",),
    "ChangeRequest": ("plt_change_request",), "FileArtifact": ("plt_file_artifact", "plt_file_version", "plt_file_reference"),
    "AuditRecord": ("plt_operation_audit",), "DeviceCredential": ("plt_device_credential",),
    "CredentialGrant": ("plt_credential_grant",), "CollectionTask": ("plt_collection_task",),
    "DispatchAttempt": ("plt_dispatch_attempt",), "CallbackRecord": ("plt_callback_record",),
    "CollectionResultReference": ("plt_collection_result_reference",), "TechnicalNoticeReference": ("kno_technical_notice", "kno_notice_business_reference"),
    "NoticeBusinessReference": ("kno_notice_business_reference",),
}

MODEL_ENTITY_CONTRACTS = {
    "DeliveryEvidence": {"owner": "IMP", "requirementIds": ["IMP-01", "IMP-02"]},
    "DeviceAssignmentHistory": {"owner": "AST", "requirementIds": ["EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
    "DeviceAncestorProjection": {"owner": "AST", "requirementIds": ["EQP-01", "EQP-03"]},
    "MetricDefinition": {"owner": "ANA", "requirementIds": ["ANA-01"]},
    "NoticeBusinessReference": {"owner": "KNO", "requirementIds": ["INT-04"]},
    "DispatchAttempt": {"owner": "PLT", "requirementIds": ["INT-12"]},
    "CallbackRecord": {"owner": "PLT", "requirementIds": ["INT-12"]},
}


def source(source_type: str, source_object: str, disposition: str, transform: str, mapping_status: str, gate: str) -> dict[str, str]:
    return {"sourceType": source_type, "sourceObject": source_object, "disposition": disposition, "transform": transform, "mappingStatus": mapping_status, "gate": gate}


OVERRIDES: dict[str, list[dict[str, str]]] = {
    "Project": [source("LEGACY_TABLE", "pm_project", "STRUCTURED", "map stable project fields; empty names become migration issues; legacy ID becomes external key", "READY_FOR_FIELD_MAPPING", "AI-MIG-000")],
    "ProjectHierarchy": [source("LEGACY_TABLE", "pm_project_group*", "EXCLUDED", "technical project-contract bridge only; migrate legacy projects as roots", "CONFIRMED_EXCLUDED", "AI-MIG-000")],
    "ProjectAncestorProjection": [source("DERIVED_TARGET", "ProjectHierarchy", "REBUILD", "rebuild complete ancestor projection after adjacency import", "REBUILD_AFTER_OWNERS", "PROJECT_TREE_VERIFY")],
    "ProjectTask": [source("LEGACY_TABLE", "pm_project_task", "STRUCTURED", "map task facts; hierarchy and dependency remain separate", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "TaskAncestorProjection": [source("DERIVED_TARGET", "ProjectTask", "REBUILD", "rebuild from imported task adjacency", "REBUILD_AFTER_OWNERS", "TASK_TREE_VERIFY")],
    "ProjectPortfolio": [
        source("CURRENT_TABLE", "pms_project_portfolio", "CURRENT_FORWARD", "preserve portfolio identity and active members", "CURRENT_FORWARD_REQUIRED", "NEXT_FLYWAY"),
        source("CURRENT_TABLE", "pms_project_portfolio_rule", "CURRENT_FORWARD", "convert mutable rule rows into immutable portfolio revisions and freeze referenced member/rule version", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY"),
    ],
    "BorrowedProjectConversion": [source("LEGACY_TABLE", "pm_presales_lend_2_delivery_off_from_sap", "PENDING_SOURCE_CONFIRMATION", "create conversion only after source project and formal sales business resolve", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "ConversionItem": [source("LEGACY_TABLE", "pm_presales_project_product_line", "PENDING_SOURCE_CONFIRMATION", "map each source object to read-only reference or derived copy with source version", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "ConversionDeviceDisposition": [source("LEGACY_TABLE", "fb_shipment_barcode", "RELATION", "derive per-device disposition only from complete shipment/RMA/assignment event chain", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "Preparation": [source("CURRENT_TABLE", "pms_eng_site_survey|pms_eng_requirement|pms_eng_resource_ready|pms_eng_briefing", "CURRENT_FORWARD", "split source type, preserve every submission, map to Preparation revisions", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ConstructionPlan": [source("CURRENT_TABLE", "pms_schedule_backward|pms_plan_change_request|pms_project_task", "CURRENT_FORWARD", "separate plan baseline, items and change revisions; do not infer approval from duration cache", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "Solution": [source("CURRENT_TABLE", "pms_eng_solution", "CURRENT_FORWARD", "create immutable solution revisions and review records", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "DynamicFormSchema": [source("CURRENT_TABLE", "pms_eng_form_template", "CURRENT_FORWARD", "convert published template versions into immutable schema revisions", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "DynamicFormInstance": [source("CURRENT_TABLE", "pms_eng_form_instance", "CURRENT_FORWARD", "freeze schema version and preserve raw values plus structured query fields", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ArrivalAcceptance": [source("CURRENT_TABLE", "pms_eng_arrival", "CURRENT_FORWARD", "map arrival batch/result; shipment quantity is reconciliation evidence, not acceptance", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InstallationRecord": [source("CURRENT_TABLE", "pms_eng_installation", "CURRENT_FORWARD", "map installation facts and evidence without overwriting history", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ConfigurationCollectionResult": [source("CURRENT_TABLE", "pms_eng_configuration|pms_equipment_config_log", "CURRENT_FORWARD", "map task/device/result versions and parse attempts; never migrate connection secrets", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "JointDebuggingResult": [source("CURRENT_TABLE", "pms_eng_joint_test", "CURRENT_FORWARD", "map per-task result revision and issue references", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ImplementationRisk": [source("CURRENT_TABLE", "pms_eng_risk", "CURRENT_FORWARD", "map implementation risk and append-only treatments; keep separate from cutover risk", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ImplementationReadinessSnapshot": [source("DERIVED_TARGET", "ArrivalAcceptance|InstallationRecord|Solution|ImplementationRisk|ImplementationQualityCheck|ImplementationSafetyCheck", "REBUILD", "rebuild from Owner facts at a declared watermark", "REBUILD_AFTER_OWNERS", "READINESS_REBUILD")],
    "DeliveryEvidence": [source("CURRENT_TABLE", "pms_eng_deliverable", "CURRENT_FORWARD", "map implementation-stage evidence identity, immutable revisions, file references and upload results", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "DeliveryArtifact": [source("CURRENT_TABLE", "pms_acc_deliverable_checklist|pms_acc_archive_document|pms_acc_completion_certificate", "CURRENT_FORWARD", "separate artifact identity, checklist, review and archive records", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ClosureGateSnapshot": [source("DERIVED_TARGET", "Acceptance|DeliveryArtifact|ServiceIssue", "REBUILD", "rebuild current gate; historical snapshot contains only provable inputs", "REBUILD_AFTER_OWNERS", "CLOSURE_REBUILD")],
    "ServiceHandover": [
        source("CURRENT_TABLE", "pms_acc_maintenance_transition", "CURRENT_FORWARD", "map only provable leftover/service handover fields to ServiceHandover", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY"),
        source("CURRENT_FIELD_PATTERN", "pms_acc_maintenance_transition.renew*", "EXCLUDED", "retain as compatibility evidence; never expose in new handover writes", "CONFIRMED_EXCLUDED", "SCOPE_EXCLUSION"),
    ],
    "CutoverPlan": [source("CURRENT_TABLE", "pms_cut_plan", "CURRENT_FORWARD", "convert plans and steps into immutable plan revisions", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionTask": [source("CURRENT_TABLE", "pms_srv_task|pms_srv_execution|pms_srv_offline_file", "CURRENT_FORWARD", "map only records classified as inspection and freeze rule snapshot", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionRule": [source("CURRENT_TABLE", "pms_srv_rule", "CURRENT_FORWARD", "convert published rules into immutable revisions", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionReport": [source("CURRENT_TABLE", "pms_srv_report", "CURRENT_FORWARD", "map immutable report revisions and external result references", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ServiceIssue": [source("CURRENT_TABLE", "pms_srv_issue", "CURRENT_FORWARD", "map inspection issues only; ITR issues remain external Owner copies", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "WorkOrder": [source("EXTERNAL_SYSTEM", "DingTalk|ITR", "EXTERNAL_SYNC", "synchronize by stable source key/version; exclude work-order timeliness fields", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "TimeClaim": [source("EXTERNAL_SYSTEM", "DingTalk", "EXTERNAL_SYNC", "map original value, direction and signed adjustment after stable key confirmation", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "ServiceStatus": [
        source("LEGACY_TABLE", "fb_service|view_warranty*|warranty_info|warranty_change_logs", "STRUCTURED", "map objective service dates/levels/source facts only", "PENDING_FIELD_MAPPING", "AI-MIG-000"),
        source("LEGACY_FIELD_PATTERN", "view_warranty*.renew*", "EXCLUDED", "retain compatibility evidence; exclude renewal actions/spaces/reports", "CONFIRMED_EXCLUDED", "SCOPE_EXCLUSION"),
    ],
    "Customer": [source("CURRENT_TABLE", "pms_customer", "CURRENT_FORWARD", "align local customer model and preserve source mapping", "CURRENT_FORWARD_REQUIRED", "NEXT_FLYWAY"), source("EXTERNAL_SYSTEM", "CRM", "EXTERNAL_SYNC", "CRM authority fields synchronize by source key/version", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "CustomerContact": [source("CURRENT_TABLE", "pms_customer_contact", "CURRENT_FORWARD", "align contact fields and temporal project relation", "CURRENT_FORWARD_REQUIRED", "NEXT_FLYWAY"), source("EXTERNAL_SYSTEM", "CRM", "EXTERNAL_SYNC", "synchronize CRM-owned contact fields", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "CustomerRelationshipSnapshot": [source("DERIVED_TARGET", "Customer|CustomerContact|Project", "REBUILD", "freeze minimum relationship data at business event time", "REBUILD_AFTER_OWNERS", "RELATIONSHIP_REBUILD")],
    "Device": [source("LEGACY_TABLE", "fb_shipment_barcode", "STRUCTURED", "deduplicate SN master while preserving every shipment lifecycle source row", "READY_FOR_FIELD_MAPPING", "AI-MIG-000"), source("EXTERNAL_SYSTEM", "MES|ITR", "EXTERNAL_SYNC", "synchronize authoritative identity fields by source key/version", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "DeviceArchive": [source("CURRENT_TABLE", "pms_equipment_version|pms_equipment_config_log", "CURRENT_FORWARD", "map version/config history with effective time and source", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY"), source("LEGACY_TABLE", "pm_project_soft_version*", "STRUCTURED", "map provable software version history; conflicts become migration issues", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceCurrentAssignment": [source("LEGACY_TABLE", "pm_project_shipment", "RELATION", "create assignment only when project/SN/time/transfer evidence resolves; otherwise issue", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceAssignmentHistory": [source("LEGACY_TABLE", "pm_project_shipment", "RELATION", "build non-overlapping assignment intervals only from resolvable device/project/time evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceAncestorProjection": [source("DERIVED_TARGET", "DeviceCurrentAssignment|ProjectHierarchy", "REBUILD", "rebuild ancestor statistics projection at tree and assignment watermarks", "REBUILD_AFTER_OWNERS", "DEVICE_ANCESTOR_REBUILD")],
    "AssetSyncSnapshot": [source("EXTERNAL_SYSTEM", "MES|ITR", "EXTERNAL_SYNC", "record source watermarks, versions and field differences", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "MaintenanceFact": [source("LEGACY_TABLE", "fb_shipment_barcode|fb_service|view_warranty*", "STRUCTURED", "map objective warranty/service facts with source and rule version", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "RMAReplacement": [source("LEGACY_TABLE", "rma_app_info|rma_applicant|fb_shipment_barcode", "RELATION", "separate RMA replacement from secondary-SN relations; unknown behavior codes remain issues", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "Contract": [source("LEGACY_TABLE", "sms_ofst_contract_head_sap|pm_order_data_from_erp", "STRUCTURED", "resolve contract by tenant/company/contract number; fb_contract never creates master", "READY_FOR_FIELD_MAPPING", "AI-MIG-000")],
    "SalesOrder": [source("LEGACY_TABLE", "pm_order_data_from_erp", "STRUCTURED", "merge deterministic business key; conflicting groups become issues, never choose max ID", "READY_FOR_FIELD_MAPPING", "AI-MIG-000")],
    "OrderLine": [source("LEGACY_TABLE", "pm_order_line_from_erp", "STRUCTURED", "map stable order-line key and signed quantities; empty/ambiguous keys become issues", "READY_FOR_FIELD_MAPPING", "AI-MIG-000")],
    "DeliveryScope": [source("LEGACY_TABLE", "pm_project_product_line", "RELATION", "map project/order-line/allocation; missing allocation remains pending and excluded from metrics", "READY_FOR_FIELD_MAPPING", "AI-MIG-000")],
    "FulfillmentSnapshot": [source("DERIVED_TARGET", "DeliveryScope|ArrivalAcceptance|InstallationRecord|Acceptance", "REBUILD", "rebuild versioned fulfillment view after Owner import", "REBUILD_AFTER_OWNERS", "FULFILLMENT_REBUILD")],
    "ReconciliationRecord": [source("DERIVED_TARGET", "Contract|SalesOrder|OrderLine|DeliveryScope", "NEW_ONLY", "create reconciliation runs from migrated facts and external receipts", "NEW_ONLY", "POST_IMPORT_RECONCILIATION")],
    "Supplier": [source("LEGACY_TABLE", "pm_subcontract_facilitator", "STRUCTURED", "map supplier identity and qualification evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "SubcontractRequest": [source("LEGACY_TABLE", "pm_subcontract_project_header|pm_subcontract_project_line|pm_subcontract_project_price|pm_subcontract_project_callback", "STRUCTURED", "map request scope, price revision and approval/callback evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "PaymentGate": [source("LEGACY_TABLE", "pm_subcontract_project_payment|pm_subcontract_project_payment_sse", "STRUCTURED", "map approved prerequisites and external finance result reference", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "MetricDefinition": [source("NONE_NEW", "MetricDefinition", "NEW_ONLY", "create versioned metric definitions only after the suggested model is approved; do not migrate report formulas by name", "NEW_ONLY_SUGGESTED", "FEATURE_RELEASE")],
    "MetricSnapshot": [
        source("DERIVED_TARGET", "Project|ProjectTask|DeliveryScope|FulfillmentSnapshot", "REBUILD", "recalculate by metric version and watermark", "REBUILD_AFTER_OWNERS", "METRIC_REBUILD"),
        source("LEGACY_TABLE", "pm_project_weekly*", "EXCLUDED", "retain historical document only; never import as metric truth", "CONFIRMED_EXCLUDED", "SCOPE_EXCLUSION"),
    ],
    "PortfolioView": [source("DERIVED_TARGET", "ProjectPortfolio|MetricSnapshot", "REBUILD", "rebuild authorized read projection", "REBUILD_AFTER_OWNERS", "PORTFOLIO_REBUILD")],
    "Todo": [source("LEGACY_TABLE", "dp_act_unify_task|act_ru_task|act_hi_taskinst", "PENDING_SOURCE_CONFIRMATION", "import only active tasks that resolve to an Owner business node; completion is not business completion", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "AuthorizationGrant": [source("CURRENT_TABLE", "pms_eng_authorization", "PENDING_SOURCE_CONFIRMATION", "map only provable resource/action/scope/effective interval; never import full authorization code", "PENDING_SOURCE_CONFIRMATION", "NEXT_FLYWAY")],
    "ChangeRequest": [source("CURRENT_TABLE", "pms_plan_change_request", "CURRENT_FORWARD", "map only requests with resolvable target object/version/approval; do not generalize unrelated changes", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "FileArtifact": [source("CURRENT_TABLE", "pms_acc_archive_document|pms_acc_deliverable_checklist|pms_eng_deliverable", "CURRENT_FORWARD", "deduplicate file identity; map content hash/version/business references separately", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "AuditRecord": [source("LEGACY_TABLE", "pm_project_log|act_hi_comment", "SNAPSHOT", "import as LEGACY audit evidence; never impersonate new-platform actor or transition", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceCredential": [
        source("NONE_NEW", "DeviceCredential", "NEW_ONLY", "new credentials are encrypted and created under the new five-tuple authorization model", "NEW_ONLY", "P3-E04"),
        source("CURRENT_FIELD_PATTERN", "pms_eng_authorization.license_key", "PENDING_SOURCE_CONFIRMATION", "migrate only if proven to be a connection credential and safely re-encrypted; otherwise isolate", "PENDING_SOURCE_CONFIRMATION", "P3-E04")
    ],
    "CredentialGrant": [source("NONE_NEW", "CredentialGrant", "NEW_ONLY", "create creator-default and explicit five-tuple grants only in new platform", "NEW_ONLY", "P3-E04")],
    "DispatchAttempt": [source("NONE_NEW", "DispatchAttempt", "NEW_ONLY", "record new-platform dispatch attempts; historical external logs remain outside unless a stable task key is proven", "NEW_ONLY", "FEATURE_RELEASE")],
    "CallbackRecord": [source("NONE_NEW", "CallbackRecord", "NEW_ONLY", "record callbacks received by the new platform; do not fabricate callback history from business result rows", "NEW_ONLY", "FEATURE_RELEASE")],
    "CollectionTask": [source("EXTERNAL_SYSTEM", "ExistingCollectionPlatform", "EXTERNAL_SYNC", "import only stable external task/result references; temporary passwords never migrate", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "CollectionResultReference": [source("CURRENT_TABLE", "pms_eng_configuration|pms_srv_report|pms_cut_execution", "RELATION", "map external object key, file/hash and consumed result version", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "TechnicalNoticeReference": [
        source("EXTERNAL_SYSTEM", "ITR", "EXTERNAL_SYNC", "synchronize notice/version and business references as V2 authority", "PENDING_INTEGRATION_CONFIG", "P3-E07"),
        source("CURRENT_TABLE", "pms_eng_announcement|pms_eng_announcement_check", "COMPATIBILITY_ONLY", "retain local legacy notices read-only; do not expose local publish/disable governance", "CONFIRMED_COMPATIBILITY", "SCOPE_EXCLUSION")
    ],
    "NoticeBusinessReference": [source("CURRENT_TABLE", "pms_eng_announcement_check", "COMPATIBILITY_ONLY", "retain only resolvable legacy notice-business references and never infer local publication authority", "CONFIRMED_COMPATIBILITY", "SCOPE_EXCLUSION")],
}

# Objects referenced by multiple domains retain the data Owner declared by the
# Phase 1 Context boundaries. Other domains consume them through contracts.
OWNER_OVERRIDES = {
    "FileArtifact": "PLT",
    "CollectionTask": "PLT",
    "WorkOrder": "SRV",
    "MetricSnapshot": "ANA",
    "InspectionReport": "SRV",
    "Contract": "COM",
    "SalesOrder": "COM",
    "AuthorizationGrant": "PLT",
    "MaintenanceFact": "AST",
    "ServiceStatus": "SRV",
}


def table_catalog(sql_root: Path) -> dict[str, str]:
    catalog: dict[str, str] = {}
    pattern = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", re.I)
    for path in sorted(sql_root.rglob("*.sql")):
        text = path.read_text(encoding="utf-8-sig")
        for table in pattern.findall(text):
            catalog[table] = path.relative_to(sql_root.parent.parent).as_posix()
    return catalog


def legacy_tables(path: Path) -> set[str]:
    result: set[str] = set()
    with path.open(encoding="utf-8") as source_file:
        for line in source_file:
            item = json.loads(line)
            if item.get("tableName"):
                result.add(item["tableName"])
    return result


def matches_any(value: str, catalog: set[str]) -> bool:
    return any(fnmatch.fnmatch(item, value) for item in catalog)


def expand_sources(raw_sources: list[dict[str, str]], current_catalog: dict[str, str], legacy_catalog: set[str], commit: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for item in raw_sources:
        entry = dict(item)
        source_type = entry["sourceType"]
        source_objects = entry["sourceObject"].split("|")
        if source_type == "CURRENT_TABLE":
            missing = [name for name in source_objects if name not in current_catalog]
            if missing:
                raise ValueError(f"current implementation source table not found: {missing}")
            entry["evidenceRef"] = ";".join(f"implementation://{commit}/{current_catalog[name]}#table={name}" for name in source_objects)
        elif source_type == "CURRENT_FIELD_PATTERN":
            table = source_objects[0].split(".", 1)[0]
            if table not in current_catalog:
                raise ValueError(f"current implementation source table not found: {table}")
            entry["evidenceRef"] = f"implementation://{commit}/{current_catalog[table]}#field-pattern={entry['sourceObject']}"
        elif source_type in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}:
            tables = [name.split(".", 1)[0] for name in source_objects]
            missing = [name for name in tables if not matches_any(name, legacy_catalog)]
            if missing:
                raise ValueError(f"legacy structured evidence table not found: {missing}")
            entry["evidenceRef"] = ";".join(f"data-elements://schema-records.jsonl#table={name}" for name in tables)
        elif source_type == "EXTERNAL_SYSTEM":
            entry["evidenceRef"] = f"design://12-integration-design.md#systems={entry['sourceObject']}"
        elif source_type == "DERIVED_TARGET":
            entry["evidenceRef"] = f"phase2-contract://objects={entry['sourceObject']}"
        elif source_type == "NONE_NEW":
            entry["evidenceRef"] = f"phase2-contract://object={entry['sourceObject']}"
        else:
            raise ValueError(f"unsupported source type: {source_type}")
        result.append(entry)
    return result


def parse_phase2_contracts(path: Path) -> dict[str, dict[str, set[str]]]:
    text = path.read_text(encoding="utf-8")
    result: dict[str, dict[str, set[str]]] = {}
    for block in re.split(r"(?=^### [A-Z]+-\d+\s*$)", text, flags=re.M):
        requirement = re.search(r"^### ([A-Z]+-\d+)\s*$", block, re.M)
        objects = re.search(r"^- 数据对象：(.+?)\s*$", block, re.M)
        tables = re.search(r"^- 数据表：(.+?)\s*$", block, re.M)
        if not requirement or not objects or not tables:
            continue
        table_names = {item.strip() for item in re.split(r"[、/]", tables.group(1)) if item.strip() and item.strip() != "N/A"}
        for object_name in (item.strip() for item in re.split(r"[、/]", objects.group(1)) if item.strip()):
            contract = result.setdefault(object_name, {"requirements": set(), "tables": set()})
            contract["requirements"].add(requirement.group(1))
            contract["tables"].update(table_names)
    return result


def parse_requirement_owners(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 3 or not re.fullmatch(r"[A-Z]+-\d+", cells[0]):
            continue
        result[cells[0]] = cells[2].split("（", 1)[0].strip()
    return result


def build(args: argparse.Namespace) -> dict[str, object]:
    phase2_contracts = parse_phase2_contracts(args.phase2_map)
    phase2_objects = set(phase2_contracts)
    expected_objects = phase2_objects | set(MODEL_ENTITY_CONTRACTS)
    if set(TARGETS) != expected_objects:
        raise ValueError(f"target object coverage mismatch; missing={sorted(expected_objects-set(TARGETS))}, extra={sorted(set(TARGETS)-expected_objects)}")
    maintained_map = json.loads(args.object_table_map.read_text(encoding="utf-8")).get("objects", {})
    if set(maintained_map) != expected_objects:
        raise ValueError("maintained object-table map coverage differs from the complete domain entity set")
    for object_name, target_tables in TARGETS.items():
        if maintained_map[object_name].get("targetTables") != list(target_tables):
            raise ValueError(f"{object_name} generator target tables differ from the maintained 09 object-table map")
    requirement_owners = parse_requirement_owners(args.requirement_matrix)
    database_design = args.database_design.read_text(encoding="utf-8")
    current_catalog = table_catalog(args.implementation / "sql" / "migrations")
    legacy_catalog = legacy_tables(args.legacy_schema)
    commit = subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
    tree_state = subprocess.run(["git", "status", "--porcelain"], cwd=args.implementation, check=True, text=True, encoding="utf-8", stdout=subprocess.PIPE).stdout.strip()
    if tree_state:
        raise ValueError("implementation repository must be clean before migration evidence is generated")
    records = []
    for object_name, target_tables in TARGETS.items():
        contract = phase2_contracts.get(object_name)
        model_contract = MODEL_ENTITY_CONTRACTS.get(object_name)
        if contract is None:
            contract = {"requirements": set(model_contract["requirementIds"]), "tables": set()}
        undeclared_targets = {table for table in target_tables if f"`{table}`" not in database_design}
        if undeclared_targets:
            raise ValueError(f"{object_name} target tables are not declared by 09 database design: {sorted(undeclared_targets)}")
        missing_owner_requirements = contract["requirements"] - set(requirement_owners)
        if missing_owner_requirements:
            raise ValueError(f"{object_name} requirements missing Owner: {sorted(missing_owner_requirements)}")
        requirement_owner_set = {requirement_owners[item] for item in contract["requirements"]}
        owner = (model_contract or {}).get("owner") or OWNER_OVERRIDES.get(object_name)
        if owner is None:
            if len(requirement_owner_set) != 1:
                raise ValueError(f"{object_name} has conflicting Owners without an explicit Context Owner: {sorted(requirement_owner_set)}")
            owner = next(iter(requirement_owner_set))
        elif owner not in requirement_owner_set:
            raise ValueError(f"{object_name} explicit Owner {owner} is not backed by any declaring requirement")
        raw_sources = OVERRIDES.get(object_name)
        if raw_sources is None:
            current_candidates: list[tuple[str, str]] = []
            for table in target_tables:
                current_candidates.append((table, table))
                current_candidates.append(("pms_" + table, table))
                if table.startswith("proj_"):
                    current_candidates.append(("pms_" + table[len("proj_"):], table))
            current_target = next((current for current, _target in current_candidates if current in current_catalog), None)
            if current_target:
                raw_sources = [source("CURRENT_TABLE", current_target, "CURRENT_FORWARD", "preserve valid facts and adapt to the Phase 2 target contract with a new Flyway migration", "CURRENT_FORWARD_REQUIRED", "NEXT_FLYWAY")]
            else:
                raw_sources = [source("NONE_NEW", object_name, "NEW_ONLY", "create only from new-platform business commands; no proven historical source", "NEW_ONLY", "FEATURE_RELEASE")]
        records.append({
            "object": object_name,
            "owner": owner,
            "ownerEvidence": "docs/design/phase-1-domain-ownership.md;docs/design/02-domain-model.md",
            "requirementIds": sorted(contract["requirements"]),
            "targetTables": list(target_tables),
            "sources": expand_sources(raw_sources, current_catalog, legacy_catalog, commit),
        })
    object_table_map = {
        record["object"]: {"owner": record["owner"], "requirementIds": record["requirementIds"], "targetTables": record["targetTables"]}
        for record in records
    }
    if object_table_map != maintained_map:
        raise ValueError("generated Owner/Requirement/target table contract differs from the maintained object-table map")
    return {
        "schemaVersion": 1,
        "status": "BASELINE_ADDENDUM",
        "baseline": "PRD_V1.6",
        "implementationRepo": str(args.implementation.resolve()),
        "implementationCommit": commit,
        "implementationTreeState": "CLEAN",
        "objectTableMap": object_table_map,
        "records": records,
    }


def render_markdown(payload: dict[str, object]) -> str:
    lines = [
        "# 领域实体迁移显式契约",
        "",
        "> 状态：`BASELINE ADDENDUM`",
        "> 基线：PRD V1.6 / SDS Phase 2 BASELINE",
        f"> 实现证据提交：`{payload['implementationCommit']}`",
        "> 生成源：`scripts/generate_domain_entity_migration_contract.py`；JSON为机器真值",
        "",
        "每一行只表示一个目标对象的一种来源处置；互斥来源不得合并为对象级策略。Owner由Requirement→Phase 1 Owner映射校验，目标表必须属于Phase 2显式契约。",
        "",
        "|目标对象|Owner|Requirement ID|目标表|来源类型|来源对象|证据定位|处置|转换|映射状态|Gate|",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for record in payload["records"]:
        for item in record["sources"]:
            values = [record["object"], record["owner"], "、".join(record["requirementIds"]), "、".join(record["targetTables"]), item["sourceType"], item["sourceObject"], item["evidenceRef"], item["disposition"], item["transform"], item["mappingStatus"], item["gate"]]
            lines.append("|" + "|".join(str(value).replace("|", "<br>") for value in values) + "|")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase2-map", type=Path, default=Path("docs/traceability/phase2-contract-map.md"))
    parser.add_argument("--requirement-matrix", type=Path, default=Path("docs/traceability/requirement-matrix.md"))
    parser.add_argument("--database-design", type=Path, default=Path("docs/design/09-database-design.md"))
    parser.add_argument("--legacy-schema", type=Path, default=Path("specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl"))
    parser.add_argument("--implementation", type=Path, default=Path(r"E:\AICoding\Projects\NPDMS"))
    parser.add_argument("--json-output", type=Path, default=Path("docs/traceability/domain-entity-migration-contract.json"))
    parser.add_argument("--md-output", type=Path, default=Path("docs/traceability/domain-entity-migration-contract.md"))
    parser.add_argument("--object-table-map", type=Path, default=Path("docs/traceability/domain-object-table-map.json"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    payload = build(args)
    json_content = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    md_content = render_markdown(payload)
    if args.check:
        drift = []
        if not args.json_output.exists() or args.json_output.read_text(encoding="utf-8") != json_content:
            drift.append(str(args.json_output))
        if not args.md_output.exists() or args.md_output.read_text(encoding="utf-8") != md_content:
            drift.append(str(args.md_output))
        if drift:
            for path in drift:
                print(f"[FAIL] domain entity migration contract drift: {path}")
            return 1
        print(f"[PASS] migration contract from maintained object-table map; objects={len(payload['records'])} sources={sum(len(record['sources']) for record in payload['records'])}")
        return 0
    args.json_output.write_text(json_content, encoding="utf-8", newline="\n")
    args.md_output.write_text(md_content, encoding="utf-8", newline="\n")
    print(f"WROTE migration contract from maintained object-table map; objects={len(payload['records'])} sources={sum(len(record['sources']) for record in payload['records'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
