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
    "ProjectTemplate": ("proj_project_template_revision", "proj_project_template_task_definition"),
    "ProjectTemplateMatchHistory": ("proj_project_template_match_history",), "ProjectTask": ("proj_project_task",),
    "TaskWorkBinding": ("proj_project_task_execution_contract",), "TaskCompletionRule": ("proj_project_task_execution_contract",),
    "TaskCompletionEvaluation": ("proj_project_task_completion_evaluation",), "TaskAncestorProjection": ("proj_task_tree_path",),
    "TaskDependency": ("proj_task_dependency",), "ProjectMemberAssignment": ("proj_project_member_assignment",),
    "ProjectPortfolio": ("proj_project_portfolio", "proj_project_portfolio_member", "proj_project_portfolio_revision"),
    "ProjectStageSnapshot": ("proj_project_stage_snapshot",), "BorrowedProjectConversion": ("proj_project_conversion",),
    "ConversionItem": ("proj_project_conversion_item",), "ConversionDeviceDisposition": ("proj_project_conversion_device",),
    "MultiPhaseProjectGroup": ("proj_multi_phase_project_group",), "MultiPhaseProjectMember": ("proj_multi_phase_project_member",),
    "CrossPhaseContentReference": ("proj_project_cross_phase_reference",), "Preparation": ("sol_preparation", "sol_preparation_item"),
    "ConstructionPlan": ("sol_construction_plan", "sol_construction_plan_revision", "sol_construction_plan_item", "sol_construction_plan_change"),
    "Solution": ("sol_solution", "sol_solution_revision", "sol_solution_review"),
    "PreparationDynamicFormInstance": ("sol_dynamic_form_instance",),
    "DynamicFormTemplate": ("plt_dynamic_form_template",),
    "DynamicFormTemplateRevision": ("plt_dynamic_form_template_revision",),
    "DynamicFormInstance": ("plt_dynamic_form_instance",),
    "ArrivalAcceptance": ("imp_arrival_acceptance", "imp_arrival_line", "imp_arrival_difference"),
    "InstallationRecord": ("imp_installation_record", "imp_installation_item", "imp_installation_evidence"),
    "ConfigurationCollectionResult": ("imp_configuration_collection_result", "imp_configuration_collection_parse_attempt", "imp_configuration_component_candidate"),
    "JointDebuggingResult": ("imp_joint_debugging_result", "imp_joint_debugging_item"), "ImplementationRisk": ("imp_risk", "imp_risk_treatment"),
    "ImplementationQualityCheck": ("imp_quality_check", "imp_quality_item", "imp_quality_remediation", "imp_quality_review"),
    "DeliveryEvidence": ("imp_delivery_evidence", "imp_delivery_evidence_revision"),
    "ImplementationReadinessSnapshot": ("imp_implementation_readiness_snapshot",), "Acceptance": ("acc_acceptance", "acc_acceptance_item", "acc_confirmation"),
    "SatisfactionCollection": ("acc_satisfaction_collection_task", "acc_satisfaction_questionnaire", "acc_satisfaction_response", "acc_satisfaction_result"),
    "DeliveryArtifact": ("acc_delivery_artifact", "acc_artifact_review", "acc_archive_record"),
    "ProjectClosure": ("acc_project_closure", "acc_closure_review"), "ClosureGateSnapshot": ("acc_closure_gate_snapshot",),
    "ServiceHandover": ("acc_service_handover", "acc_handover_item", "acc_handover_result"),
    "CutoverTask": ("cut_task",), "CutoverAssessment": ("cut_assessment",),
    "CutoverChecklist": ("cut_cutover_checklist", "cut_cutover_checklist_item", "cut_cutover_checklist_item_result"),
    "CutoverPlan": ("cut_plan_revision", "cut_step"), "CutoverConfigurationRevision": ("cut_cutover_configuration_revision", "cut_cutover_checklist_item_definition_revision", "cut_cutover_checklist_binding_rule_revision"), "CutoverSupportArrangement": ("cut_cutover_support_arrangement",),
    "CutoverClosure": ("cut_cutover_closure",),
    "InspectionTask": ("srv_inspection_task", "srv_inspection_task_rule_snapshot"), "InspectionRule": ("srv_inspection_rule", "srv_inspection_rule_revision"),
    "InspectionReport": ("srv_inspection_report_revision",), "ServiceIssue": ("srv_service_issue", "srv_service_issue_remediation"),
    "ServiceStatus": ("srv_service_status",), "Customer": ("cus_customer_master", "cus_customer_external_mapping", "cus_customer_field_history"),
    "MarketRelation": ("cus_market_relation",), "CustomerLocationReference": ("cus_customer_location_reference",),
    "CustomerScopeSlice": ("cus_customer_scope_slice",), "CustomerContact": ("cus_customer_contact", "cus_project_customer_contact_relation"),
    "CustomerRelationshipSnapshot": ("cus_customer_relationship_snapshot",), "CustomerServiceLevelRevision": ("cus_customer_service_level_revision",), "Device": ("ast_device",), "DeviceArchive": ("ast_device", "ast_device_version", "ast_device_config_log"),
    "DeviceComponentRelation": ("ast_device_component_relation",),
    "DeviceCurrentAssignment": ("ast_device_current_assignment", "ast_device_assignment_history"),
    "DeviceAssignmentHistory": ("ast_device_assignment_history",), "DeviceAncestorProjection": ("ast_device_project_ancestor",),
    "AssetSyncSnapshot": ("ast_asset_sync_batch", "ast_asset_sync_item", "ast_device"), "MaintenanceFact": ("ast_maintenance_fact",),
    "RMAReplacement": ("ast_rma_replacement",), "Contract": ("com_contract",), "SalesOrder": ("com_sales_order",),
    "OrderLine": ("com_sales_order_line",), "DeliveryScope": ("com_delivery_scope",), "DeliveryScopeDetail": ("com_delivery_scope_detail",), "Supplier": ("res_supplier", "res_qualification"),
    "SubcontractRequest": ("res_subcontract_request",), "PaymentGate": ("res_payment_gate",), "MetricDefinition": ("ana_metric_definition",), "MetricSnapshot": ("ana_metric_snapshot",),
    "PortfolioView": ("ana_portfolio_projection",), "Todo": ("plt_todo",), "AuthorizationGrant": ("plt_authorization_grant",),
    "ChangeRequest": ("plt_change_request",), "FileArtifact": ("plt_file_artifact", "plt_file_version", "plt_file_reference"),
    "AuditRecord": ("plt_operation_audit",), "DeviceCredential": ("plt_device_credential",),
    "CredentialGrant": ("plt_credential_grant",), "CollectionTask": ("plt_collection_task",),
    "DispatchAttempt": ("plt_dispatch_attempt",), "CallbackRecord": ("plt_callback_record",),
    "CollectionResultReference": ("plt_collection_result_reference",), "TechnicalNoticeReference": (),
    "NoticeBusinessReference": (),
}

TARGET_POLICIES = {
    "ProjectTemplateMatchHistory": {"targetTablePolicy": "FEATURE_FORWARD_MIGRATION", "featureRequirementId": "PM-07"},
    "TechnicalNoticeReference": {"targetTablePolicy": "FEATURE_FORWARD_MIGRATION", "featureRequirementId": "INT-04"},
    "NoticeBusinessReference": {"targetTablePolicy": "FEATURE_FORWARD_MIGRATION", "featureRequirementId": "INT-04"},
    "CustomerServiceLevelRevision": {"targetTablePolicy": "FEATURE_FORWARD_MIGRATION", "featureRequirementId": "CUS-02"},
    "CutoverConfigurationRevision": {"targetTablePolicy": "FEATURE_FORWARD_MIGRATION", "featureRequirementId": "CUT-07"},
}

MODEL_ENTITY_CONTRACTS = {
    "DeliveryEvidence": {"owner": "IMP", "requirementIds": ["IMP-01"]},
    "DeviceAssignmentHistory": {"owner": "AST", "requirementIds": ["EQP-01", "EQP-02", "EQP-03", "EQP-05", "EQP-07"]},
    "DeviceAncestorProjection": {"owner": "AST", "requirementIds": ["EQP-01", "EQP-03"]},
    "MetricDefinition": {"owner": "ANA", "requirementIds": ["ANA-01"]},
    "NoticeBusinessReference": {"owner": "KNO", "requirementIds": ["INT-04"]},
    "DispatchAttempt": {"owner": "PLT", "requirementIds": ["INT-12"]},
    "CallbackRecord": {"owner": "PLT", "requirementIds": ["INT-12"]},
    "CutoverSupportArrangement": {"owner": "CUT", "requirementIds": ["CUT-04"]},
    "CutoverClosure": {"owner": "CUT", "requirementIds": ["CUT-06"]},
    "CutoverConfigurationRevision": {"owner": "CUT", "requirementIds": ["CUT-07", "CUT-09", "CUT-10"]},
    "CustomerServiceLevelRevision": {"owner": "CUS", "requirementIds": ["CUS-02"]},
    "DynamicFormTemplate": {"owner": "PLT", "requirementIds": ["SOL-01"], "crossContextFoundation": True, "ownerEvidence": "specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md"},
    "DynamicFormTemplateRevision": {"owner": "PLT", "requirementIds": ["SOL-01"], "crossContextFoundation": True, "ownerEvidence": "specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md"},
    "DynamicFormInstance": {"owner": "PLT", "requirementIds": ["SOL-01"], "crossContextFoundation": True, "ownerEvidence": "specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md"},
}

EXCLUDED_SOURCES = [{
    "sourceType": "LEGACY_TABLE",
    "sourceObject": "pm_project_maintenance",
    "disposition": "EXCLUDED",
    "mappingStatus": "NO_MIGRATION",
    "gate": "USER_CONFIRMED_EXCLUSION",
    "transform": "NO_MIGRATION: requirement owner confirmed on 2026-08-13 that the complete table is excluded; retain table-level extraction audit metadata only",
    "evidenceRef": "data-elements://schema-records.jsonl#table=pm_project_maintenance",
    "exclusionAudit": {
        "decisionDate": "2026-08-13",
        "decisionSource": "REQUIREMENT_OWNER_CONFIRMATION",
        "sourceTable": "pm_project_maintenance",
        "rowCount": None,
        "extractionBatchSha256": None,
        "auditStatus": "PENDING_EXTRACTION_AUDIT",
    },
}]


def source(source_type: str, source_object: str, disposition: str, transform: str, mapping_status: str, gate: str, **details: object) -> dict[str, object]:
    return {"sourceType": source_type, "sourceObject": source_object, "disposition": disposition, "transform": transform, "mappingStatus": mapping_status, "gate": gate, **details}


def binding(source_field: str, target_field: str, transform: str, evidence_ref: str) -> dict[str, str]:
    return {"sourceField": source_field, "targetField": target_field, "transform": transform, "evidenceRef": evidence_ref}


OVERRIDES: dict[str, list[dict[str, str]]] = {
    "Project": [source("LEGACY_TABLE", "pm_project", "STRUCTURED", "map stable project fields; empty names become migration issues; legacy ID becomes external key", "READY_FOR_FIELD_MAPPING", "AI-MIG-000",
        targetFieldBindings=[
            binding("pm_project.projectCode", "proj_project.project_code", "direct after normalization and permanent-key conflict check", "data-elements://schema-records.jsonl#项目管理!A20"),
            binding("pm_project.projectName", "proj_project.project_name", "direct; empty value becomes migration issue", "data-elements://schema-records.jsonl#项目管理!A21"),
            binding("pm_project.projectType", "proj_project.project_type", "direct with source value preserved", "data-elements://schema-records.jsonl#项目管理!A19"),
        ],
        statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "sourceFields": ["pm_project.projectState", "pm_project.disabled"], "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
        terminalDisposition="CREATE_PROJECT_ONLY_AFTER_PERMANENT_KEY_AND_REQUIRED_NAME_VALIDATE;PRESERVE_RAW_AND_EXTERNAL_KEY_MAPPING")],
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
    "PreparationDynamicFormInstance": [source("NONE_NEW", "PreparationDynamicFormInstance", "NEW_ONLY", "preserve existing F-SOL-002 site-survey form facts only; PRE-04 does not reuse this table; do not migrate or dual-write legacy pms_eng_form_instance", "NEW_ONLY", "FEATURE_RELEASE")],
    "DynamicFormTemplate": [source("CURRENT_TABLE", "pms_eng_form_template", "COMPATIBILITY_ONLY", "audit reusable interaction and field semantics only; keep the legacy table and behavior unchanged and create no migration or dual write", "NO_MIGRATION", "F-PLT-002")],
    "DynamicFormTemplateRevision": [source("NONE_NEW", "DynamicFormTemplateRevision", "NEW_ONLY", "create only from new PLATFORM template commands; never synthesize a revision from legacy rows", "NEW_ONLY", "F-PLT-002")],
    "DynamicFormInstance": [source("CURRENT_TABLE", "pms_eng_form_instance", "COMPATIBILITY_ONLY", "create new PLATFORM manual and trusted business-owner instances; audit reusable interaction only; keep the legacy table and behavior unchanged and create no migration, dual write or automatic PRE-04 candidate-data conversion", "NO_MIGRATION", "F-PLT-002/F-SOL-003")],
    "ArrivalAcceptance": [source("CURRENT_TABLE", "pms_eng_arrival", "CURRENT_FORWARD", "map arrival batch/result; shipment quantity is reconciliation evidence, not acceptance", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InstallationRecord": [source("CURRENT_TABLE", "pms_eng_installation", "CURRENT_FORWARD", "map installation facts and evidence without overwriting history", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ConfigurationCollectionResult": [source("CURRENT_TABLE", "pms_eng_configuration|pms_equipment_config_log", "CURRENT_FORWARD", "map task/device/result versions, immutable raw logs, parse attempts and component candidates; never migrate connection secrets", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "JointDebuggingResult": [source("CURRENT_TABLE", "pms_eng_joint_test", "CURRENT_FORWARD", "map per-task result revision and issue references", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ImplementationRisk": [source("CURRENT_TABLE", "pms_eng_risk", "CURRENT_FORWARD", "map implementation risk and append-only treatments; keep separate from cutover risk", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ImplementationReadinessSnapshot": [source("DERIVED_TARGET", "ArrivalAcceptance|InstallationRecord|Solution|ImplementationRisk|ImplementationQualityCheck", "REBUILD", "rebuild from Owner facts at a declared watermark", "REBUILD_AFTER_OWNERS", "READINESS_REBUILD")],
    "TaskWorkBinding": [source("NONE_NEW", "TaskWorkBinding", "NEW_ONLY", "forward-initialize existing ProjectTask rows with explicit TASK_NATIVE contract version 1; create non-native bindings only from a published template or approved rebinding command; never infer from names, menus, URLs or modules", "NEW_ONLY", "NEXT_FLYWAY")],
    "TaskCompletionRule": [source("NONE_NEW", "TaskCompletionRule", "NEW_ONLY", "create atomically with the WorkBinding contract version; do not infer target facts or rule versions from legacy completed status", "NEW_ONLY", "NEXT_FLYWAY")],
    "TaskCompletionEvaluation": [source("NONE_NEW", "TaskCompletionEvaluation", "NEW_ONLY", "append only for new-platform completion commands after task, contract, rule and fact version validation; never fabricate historical evaluations", "NEW_ONLY", "FEATURE_RELEASE")],
    "DeliveryEvidence": [source("CURRENT_TABLE", "pms_eng_deliverable", "CURRENT_FORWARD", "map implementation-stage evidence identity, immutable revisions, file references and upload results", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "DeliveryArtifact": [source("CURRENT_TABLE", "pms_acc_deliverable_checklist|pms_acc_archive_document|pms_acc_completion_certificate", "CURRENT_FORWARD", "separate artifact identity, checklist, review and archive records", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "SatisfactionCollection": [
        source("LEGACY_TABLE", "pm_cl_quesnaire_template_header|pm_cl_quesnaire_template_line|pm_cl_quesnaire_template_options|pm_cl_quesnaire_result_header|pm_cl_quesnaire_result_line", "STRUCTURED", "map template, question, option, response and score versions; retain original identifiers and never overwrite submitted answers", "PENDING_FIELD_MAPPING", "AI-MIG-000",
            targetFieldBindings=[
                binding("pm_cl_quesnaire_template_header.id", "acc_satisfaction_questionnaire.template_id", "TARGET_KEY_LOOKUP resolves legacy template identity; never reuse the raw ID", "data-elements://schema-records.jsonl#项目管理!A595"),
                binding("pm_cl_quesnaire_template_header.questionnairePassScore", "acc_satisfaction_questionnaire.frozen_threshold", "decimal conversion; invalid values become migration issues", "data-elements://schema-records.jsonl#项目管理!A599"),
                binding("pm_cl_quesnaire_template_line.questionContent|questionType|questionScore", "acc_satisfaction_questionnaire.frozen_question_json", "assemble ordered immutable question snapshot; no legacy required flag exists, so required semantics remain unresolved", "data-elements://schema-records.jsonl#项目管理!A611:A625"),
                binding("pm_cl_quesnaire_template_options.questionOptionNum|questionOptionsContent|questionOptionScore", "acc_satisfaction_questionnaire.frozen_question_json", "nest options under resolved question identity", "data-elements://schema-records.jsonl#项目管理!A628:A640"),
                binding("pm_cl_quesnaire_result_line.questionAnswer|questionScore", "acc_satisfaction_response.answer_json", "assemble immutable answers by resolved template line", "data-elements://schema-records.jsonl#项目管理!A580:A592"),
                binding("pm_cl_quesnaire_result_header.quesMarkScore", "acc_satisfaction_result.score", "decimal conversion with source value preserved", "data-elements://schema-records.jsonl#项目管理!A568:A577"),
                binding("pm_cl_quesnaire_result_header.quesMarkResult", "acc_satisfaction_result.passed", "map only through approved AI-MIG-000 status/value mapping", "data-elements://schema-records.jsonl#项目管理!A576"),
            ],
            statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "sourceFields": ["pm_cl_quesnaire_template_header.questionnaireStatus", "pm_cl_quesnaire_result_header.status", "pm_cl_quesnaire_result_header.quesMarkResult"], "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
            terminalDisposition="CREATE_IMMUTABLE_QUESTIONNAIRE_RESPONSE_RESULT_WHEN_IDENTITY_AND_VALIDITY_RESOLVE;OTHERWISE_PRESERVE_RAW_AND_BLOCK_GATE"),
        source("LEGACY_TABLE", "pm_cl_callback|pm_cl_callback_quesnaire|pm_subcontract_project_callback", "RELATION", "link only provable project/subcontract business objects, responsible users and questionnaire results; never infer customer answers from callback or approval status", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000",
            targetFieldBindings=[
                binding("pm_cl_callback.projectId", "acc_satisfaction_collection_task.project_id", "EXTERNAL_KEY_MAPPING lookup resolves the stable target project key", "data-elements://schema-records.jsonl#项目管理!A519"),
                binding("pm_cl_callback.id", "acc_satisfaction_collection_task.source_object_id", "EXTERNAL_KEY_MAPPING preserves callback source identity; do not infer approval", "data-elements://schema-records.jsonl#项目管理!A518"),
                binding("pm_cl_callback_quesnaire.quesnaireId", "acc_satisfaction_questionnaire.source_questionnaire_key", "store the source questionnaire key; target id remains NEW_GENERATED", "data-elements://schema-records.jsonl#项目管理!A536"),
                binding("pm_cl_callback_quesnaire.quesnaireVersion", "acc_satisfaction_questionnaire.source_questionnaire_version", "store the source questionnaire version independently from its key", "data-elements://schema-records.jsonl#项目管理!A537"),
                binding("pm_subcontract_project_callback.subcontractId", "acc_satisfaction_collection_task.source_object_id", "TARGET_KEY_LOOKUP resolves the subcontract request and its version before binding", "data-elements://schema-records.jsonl#项目管理!A850"),
                binding("pm_subcontract_project_callback.quesnaireId", "acc_satisfaction_questionnaire.source_questionnaire_key", "store the source questionnaire key; target id remains NEW_GENERATED", "data-elements://schema-records.jsonl#项目管理!A853"),
                binding("pm_subcontract_project_callback.quesnaireVersion", "acc_satisfaction_questionnaire.source_questionnaire_version", "store the source questionnaire version independently from its key", "data-elements://schema-records.jsonl#项目管理!A854"),
            ],
            statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "sourceFields": ["pm_cl_callback.applyState", "pm_cl_callback_quesnaire.state", "pm_subcontract_project_callback.state"], "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
            terminalDisposition="LINK_ONLY_PROVABLE_PROJECT_OR_SUBCONTRACT_VERSION_AND_QUESTIONNAIRE;NEVER_DERIVE_CUSTOMER_ANSWER_SIGNATURE_OR_PASS_FROM_CALLBACK_STATUS"),
    ],
    "ClosureGateSnapshot": [source("DERIVED_TARGET", "Acceptance|DeliveryArtifact|ServiceIssue", "REBUILD", "rebuild current gate; historical snapshot contains only provable inputs", "REBUILD_AFTER_OWNERS", "CLOSURE_REBUILD")],
    "ServiceHandover": [
        source("CURRENT_TABLE", "pms_acc_maintenance_transition", "CURRENT_FORWARD", "map only provable leftover/service handover fields to ServiceHandover", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY"),
        source("CURRENT_FIELD_PATTERN", "pms_acc_maintenance_transition.renew*", "EXCLUDED", "retain as compatibility evidence; never expose in new handover writes", "CONFIRMED_EXCLUDED", "SCOPE_EXCLUSION"),
    ],
    "CutoverPlan": [source("CURRENT_TABLE", "pms_cut_plan", "CURRENT_FORWARD", "convert plans and operation/validation/rollback content into immutable plan revisions; do not create execution-step state", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "CutoverConfigurationRevision": [source("NONE_NEW", "CutoverConfigurationRevision", "NEW_ONLY", "create CUT-07/CUT-09/CUT-10 configuration, checklist item definition and binding rule revisions only from new-platform commands; reuse base-platform dictionaries and never infer configuration master data from plans or legacy risk items", "NEW_ONLY", "FEATURE_RELEASE")],
    "CutoverChecklist": [source("CURRENT_TABLE", "pms_cut_risk", "CURRENT_FORWARD", "map only provable task reference, original item code/name/type, description and answer facts; never infer item definition version, UI schema, binding rule, required flag, CollectionTask, automatic result, business pass or configuration gap", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "CutoverSupportArrangement": [source("CURRENT_TABLE", "pms_cut_plan", "CURRENT_FORWARD", "map only provable support contact, contact information, arrival time, role and duty fields as plan-owned details; never infer work-order status or responsibility intervals", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "CutoverClosure": [source("CURRENT_TABLE", "pms_cut_execution", "CURRENT_FORWARD", "map only provable P6 result, rollback description, attachment, legacy-item text and final result fields; exclude step and observation lifecycle fields", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionTask": [source("CURRENT_TABLE", "pms_srv_task|pms_srv_execution|pms_srv_offline_file", "CURRENT_FORWARD", "map only records classified as inspection and freeze rule snapshot", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionRule": [source("CURRENT_TABLE", "pms_srv_rule", "CURRENT_FORWARD", "convert published rules into immutable revisions", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "InspectionReport": [source("CURRENT_TABLE", "pms_srv_report", "CURRENT_FORWARD", "map immutable report revisions and external result references", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ServiceIssue": [source("CURRENT_TABLE", "pms_srv_issue", "CURRENT_FORWARD", "map inspection issues only; ITR issues remain external Owner copies", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY")],
    "ServiceStatus": [
        source("LEGACY_TABLE", "fb_service|view_warranty*|warranty_info|warranty_change_logs", "STRUCTURED", "map objective service dates/levels/source facts only", "PENDING_FIELD_MAPPING", "AI-MIG-000"),
        source("LEGACY_FIELD_PATTERN", "view_warranty*.renew*", "EXCLUDED", "retain compatibility evidence; exclude renewal actions/spaces/reports", "CONFIRMED_EXCLUDED", "SCOPE_EXCLUSION"),
    ],
    "Customer": [source("CURRENT_TABLE", "pms_customer", "CURRENT_FORWARD", "preserve customer id; map code/name/status/source/audit fields into the CUS-owned master and retain immutable field history", "IMPLEMENTED_FORWARD_MIGRATION", "F_CUS_001_V106", implementationEvidenceTable="cus_customer_master"), source("EXTERNAL_SYSTEM", "CRM", "EXTERNAL_SYNC", "CRM authority fields synchronize by source key/version", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "MarketRelation": [source("EXTERNAL_SYSTEM", "CRM", "EXTERNAL_SYNC", "synchronize the exact MarketRelation market/system/expend/industry code and name tuple without persisting relationId", "PENDING_INTEGRATION_CONFIG", "INT-03")],
    "CustomerLocationReference": [source("NONE_NEW", "CustomerLocationReference", "NEW_ONLY", "create temporal references only after AST validates an Address or Site stable identity", "IMPLEMENTED_NEW_ONLY", "F_CUS_001_V106", implementationEvidenceTable="cus_customer_location_reference")],
    "CustomerScopeSlice": [source("NONE_NEW", "CustomerScopeSlice", "NEW_ONLY", "create explicit user or role slices; OR values within a dimension, AND dimensions within a slice, and OR independent slices", "IMPLEMENTED_NEW_ONLY", "F_CUS_001_V107", implementationEvidenceTable="cus_customer_scope_slice")],
    "CustomerContact": [source("CURRENT_TABLE", "pms_customer_contact", "CURRENT_FORWARD", "align contact fields and temporal project relation", "CURRENT_FORWARD_REQUIRED", "NEXT_FLYWAY"), source("EXTERNAL_SYSTEM", "CRM", "EXTERNAL_SYNC", "synchronize CRM-owned contact fields", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "CustomerRelationshipSnapshot": [source("DERIVED_TARGET", "Customer|CustomerContact|Project", "REBUILD", "freeze minimum relationship data at business event time", "REBUILD_AFTER_OWNERS", "RELATIONSHIP_REBUILD")],
    "CustomerServiceLevelRevision": [source("NONE_NEW", "CustomerServiceLevelRevision", "NEW_ONLY", "create temporal customer service-level and policy revisions only from CUS-02 commands; never infer historical levels from customer, contact or relationship snapshots", "NEW_ONLY", "FEATURE_RELEASE")],
    "Device": [source("LEGACY_TABLE", "fb_shipment_barcode", "STRUCTURED", "deduplicate SN master while preserving every shipment lifecycle source row", "READY_FOR_FIELD_MAPPING", "AI-MIG-000"), source("EXTERNAL_SYSTEM", "MES|ITR", "EXTERNAL_SYNC", "synchronize authoritative identity fields by source key/version", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "DeviceArchive": [source("CURRENT_TABLE", "pms_equipment_version|pms_equipment_config_log", "CURRENT_FORWARD", "map version/config history with effective time and source", "PENDING_FIELD_MAPPING", "NEXT_FLYWAY"), source("LEGACY_TABLE", "pm_project_soft_version*", "STRUCTURED", "map provable software version history; conflicts become migration issues", "PENDING_FIELD_MAPPING", "AI-MIG-000",
        targetFieldBindings=[
            binding("pm_project_soft_version*.projectId", "ast_device_version.project_id", "EXTERNAL_KEY_MAPPING lookup resolves the target project key", "data-elements://schema-records.jsonl#项目管理!A378"),
            binding("pm_project_soft_version*.barCode", "ast_device_version.device_id", "TARGET_KEY_LOOKUP resolves the device by exact SN; never copy the source ID", "data-elements://schema-records.jsonl#项目管理!A382"),
            binding("pm_project_soft_version*.conp|cpld|boot", "ast_device_version.version_value", "expand each populated component into its own version row", "data-elements://schema-records.jsonl#项目管理!A383:A392"),
        ],
        statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
        terminalDisposition="CREATE_VERSION_ROWS_ONLY_FOR_RESOLVED_PROJECT_DEVICE_COMPONENT;CONFLICTS_REMAIN_MIGRATION_ISSUES")],
    "DeviceComponentRelation": [source("DERIVED_TARGET", "ConfigurationCollectionResult|DeviceArchive", "RELATION", "create effective chassis-slot-card intervals only from parsed or manually verified evidence; ambiguous candidates remain pending", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000",
        targetFieldBindings=[
            binding("ConfigurationCollectionResult.chassis_sn", "ast_device_component_relation.chassis_sn", "direct from verified parse candidate", "design://08-data-model.md#ConfigurationCollectionResult"),
            binding("ConfigurationCollectionResult.slot_code", "ast_device_component_relation.slot_code", "direct from verified parse candidate", "design://08-data-model.md#ConfigurationCollectionResult"),
            binding("ConfigurationCollectionResult.card_sn|card_model_code", "ast_device_component_relation.card_sn", "bind only verified card identity; preserve model in relation", "design://08-data-model.md#ConfigurationCollectionResult"),
            binding("ConfigurationCollectionResult.evidence_ref", "ast_device_component_relation.evidence_ref", "preserve immutable raw-log and parse evidence reference", "design://08-data-model.md#ConfigurationCollectionResult"),
        ],
        statusMapping={"policy": "MATCH_STATUS_TO_RELATION_CREATION", "unknown": "KEEP_CANDIDATE_PENDING"},
        terminalDisposition="CREATE_EFFECTIVE_INTERVAL_ONLY_AFTER_VERIFIED_IDENTITY;AMBIGUOUS_CANDIDATE_REMAINS_PENDING")],
    "DeviceCurrentAssignment": [source("LEGACY_TABLE", "pm_project_shipment", "RELATION", "create assignment only when project/SN/time/transfer evidence resolves; otherwise issue", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceAssignmentHistory": [source("LEGACY_TABLE", "pm_project_shipment", "RELATION", "build non-overlapping assignment intervals only from resolvable device/project/time evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "DeviceAncestorProjection": [source("DERIVED_TARGET", "DeviceCurrentAssignment|ProjectHierarchy", "REBUILD", "rebuild ancestor statistics projection at tree and assignment watermarks", "REBUILD_AFTER_OWNERS", "DEVICE_ANCESTOR_REBUILD")],
    "AssetSyncSnapshot": [source("EXTERNAL_SYSTEM", "MES|ITR", "EXTERNAL_SYNC", "record source watermarks, versions and field differences", "PENDING_INTEGRATION_CONFIG", "P3-E07")],
    "MaintenanceFact": [source("LEGACY_TABLE", "fb_shipment_barcode|fb_service|view_warranty*", "STRUCTURED", "map objective warranty/service facts with source and rule version", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "RMAReplacement": [source("LEGACY_TABLE", "rma_app_info|rma_applicant|fb_shipment_barcode", "RELATION", "separate RMA replacement from secondary-SN relations; unknown behavior codes remain issues", "PENDING_SOURCE_CONFIRMATION", "AI-MIG-000")],
    "Contract": [source("LEGACY_TABLE", "sms_ofst_contract_head_sap|pm_order_data_from_erp", "STRUCTURED", "resolve contract by tenant/company/contract number; fb_contract never creates master", "READY_FOR_FIELD_MAPPING", "AI-MIG-000",
        targetFieldBindings=[
            binding("sms_ofst_contract_head_sap.contract_num", "com_contract.contract_no", "direct after company ownership resolution", "data-elements://schema-records.jsonl#系统支撑!A1446"),
            binding("sms_ofst_contract_head_sap.dataSource", "com_contract.master_source_system", "direct authority marker", "data-elements://schema-records.jsonl#系统支撑!A1483"),
            binding("sms_ofst_contract_head_sap.client_supplier_code", "com_contract.customer_code", "copy the customer code independently from the name snapshot", "data-elements://schema-records.jsonl#系统支撑!A1450"),
            binding("sms_ofst_contract_head_sap.client_supplier_name", "com_contract.customer_name", "copy the customer name snapshot independently from its code", "data-elements://schema-records.jsonl#系统支撑!A1451"),
            binding("pm_order_data_from_erp.contractNo", "com_contract.contract_no", "relation evidence only; cannot create master without company evidence", "data-elements://schema-records.jsonl#系统支撑!A706"),
            binding("pm_order_data_from_erp.customerCode", "com_contract.customer_code", "reconciliation code evidence; conflicting customer blocks merge", "data-elements://schema-records.jsonl#系统支撑!A710"),
            binding("pm_order_data_from_erp.customerName", "com_contract.customer_name", "reconciliation name snapshot; never treat it as a customer code", "data-elements://schema-records.jsonl#系统支撑!A711"),
        ],
        statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
        terminalDisposition="CREATE_MASTER_ONLY_FROM_AUTHORITATIVE_COMPANY_AND_CONTRACT_NUMBER;ORDER_SOURCE_IS_RECONCILIATION_ONLY")],
    "SalesOrder": [source("LEGACY_TABLE", "pm_order_data_from_erp", "STRUCTURED", "merge deterministic business key; conflicting groups become issues, never choose max ID", "READY_FOR_FIELD_MAPPING", "AI-MIG-000",
        targetFieldBindings=[
            binding("pm_order_data_from_erp.orderNumber", "com_sales_order.order_no", "direct within source/company/order-type business key", "data-elements://schema-records.jsonl#系统支撑!A705"),
            binding("pm_order_data_from_erp.orderCreateTime", "com_sales_order.order_create_time", "direct", "data-elements://schema-records.jsonl#系统支撑!A708"),
            binding("pm_order_data_from_erp.customerCode", "com_sales_order.customer_code", "copy the source customer code without choosing an ambiguous master", "data-elements://schema-records.jsonl#系统支撑!A710"),
            binding("pm_order_data_from_erp.customerName", "com_sales_order.customer_name", "copy the source customer name snapshot independently from its code", "data-elements://schema-records.jsonl#系统支撑!A711"),
        ],
        statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
        terminalDisposition="MERGE_ONLY_DETERMINISTIC_BUSINESS_KEY_GROUPS;CONFLICTING_GROUPS_REMAIN_MIGRATION_ISSUES")],
    "OrderLine": [
        source("LEGACY_TABLE", "pm_order_line_from_erp", "STRUCTURED", "map stable order-line key and signed quantities; empty/ambiguous keys become issues", "READY_FOR_FIELD_MAPPING", "AI-MIG-000",
            targetFieldBindings=[
                binding("pm_order_line_from_erp.source", "com_sales_order_line.source_system", "normalize the declared ERP source code; empty source becomes a migration issue", "data-elements://schema-records.jsonl#系统支撑!A786"),
                binding("pm_order_line_from_erp.compCode", "com_sales_order_line.company_code", "direct after company-code normalization; empty code becomes a migration issue", "data-elements://schema-records.jsonl#系统支撑!A783"),
                binding("pm_order_line_from_erp.orderNumber", "com_sales_order_line.order_no", "direct after resolving the parent order by the full approved business key", "data-elements://schema-records.jsonl#系统支撑!A774"),
                binding("pm_order_line_from_erp.lineNum", "com_sales_order_line.line_no", "direct after normalization; empty or duplicate line numbers become migration issues", "data-elements://schema-records.jsonl#系统支撑!A775"),
                binding("pm_order_line_from_erp.lineType", "com_sales_order_line.line_type", "map only through the approved AI-MIG-000 line-type value map", "data-elements://schema-records.jsonl#系统支撑!A782"),
                binding("pm_order_line_from_erp.itemCode", "com_sales_order_line.item_code", "copy as the legacy ERP item code; never promote it to product_code", "data-elements://schema-records.jsonl#系统支撑!A776"),
                binding("pm_order_line_from_erp.itemDesc", "com_sales_order_line.item_desc", "copy the source item description independently from its code", "data-elements://schema-records.jsonl#系统支撑!A777"),
                binding("pm_order_line_from_erp.orderQuantity", "com_sales_order_line.order_qty", "decimal conversion preserving sign and source value", "data-elements://schema-records.jsonl#系统支撑!A778"),
                binding("pm_order_line_from_erp.openQuantity", "com_sales_order_line.open_qty", "decimal conversion preserving sign and source value", "data-elements://schema-records.jsonl#系统支撑!A779"),
                binding("pm_order_line_from_erp.bundleCode", "com_sales_order_line.bundle_code", "copy as the source bundle code", "data-elements://schema-records.jsonl#系统支撑!A780"),
                binding("pm_order_line_from_erp.warrantyMonth", "com_sales_order_line.warranty_month", "integer conversion; invalid values become migration issues", "data-elements://schema-records.jsonl#系统支撑!A781"),
                binding("pm_order_line_from_erp.profitCenter", "com_sales_order_line.profit_center", "copy as the source profit-center code", "data-elements://schema-records.jsonl#系统支撑!A784"),
                binding("pm_order_line_from_erp.realOrderExecNumber", "com_sales_order_line.real_execution_no", "copy as the source actual execution number", "data-elements://schema-records.jsonl#系统支撑!A785"),
                binding("pm_order_line_from_erp.syncTime", "com_sales_order_line.source_sync_time", "preserve the latest successful source synchronization time", "data-elements://schema-records.jsonl#系统支撑!A788"),
            ],
            statusMapping={"policy": "NO_SOURCE_STATUS_TARGET_STATUS_REQUIRES_APPROVED_CONSTANT", "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
            terminalDisposition="CREATE_LINE_ONLY_AFTER_PARENT_ORDER_FULL_KEY_AND_REQUIRED_LINE_FACTS_RESOLVE;OTHERWISE_PRESERVE_RAW_AND_BLOCK_GATE"),
        source("CURRENT_FORWARD_TABLE", "com_order_line@V70", "STRUCTURED", "F-COM-001 controlled forward conversion to the canonical sales-order-line Owner", "APPROVED", "F-COM-001",
            requiredTargetMappings={"com_sales_order_line.status": "APPROVED_CONSTANT:ENABLED;FAIL_BATCH_ON_MISSING_OR_CONFLICT"},
            evidenceRef="feature-contract://F-COM-001#v70Conversion"),
    ],
    "DeliveryScope": [source("LEGACY_TABLE", "pm_project_product_line", "RELATION", "map project/order-line/allocation; missing allocation remains pending and excluded from metrics", "READY_FOR_FIELD_MAPPING", "AI-MIG-000",
        targetFieldBindings=[
            binding("pm_project_product_line.projectId", "com_delivery_scope.project_id", "EXTERNAL_KEY_MAPPING lookup resolves the target project key", "data-elements://schema-records.jsonl#项目管理!A166"),
            binding("pm_project_product_line.itemCode", "com_delivery_scope.item_code", "copy the product code independently from its name", "data-elements://schema-records.jsonl#项目管理!A168"),
            binding("pm_project_product_line.itemName", "com_delivery_scope.item_desc", "copy the product name as the item description snapshot; never treat it as an item code", "data-elements://schema-records.jsonl#项目管理!A169"),
            binding("pm_project_product_line.projectQuantity", "com_delivery_scope.allocated_qty", "verified project implementation quantity", "data-elements://schema-records.jsonl#项目管理!A170"),
            binding("pm_project_product_line.orderNumber|lineNum", "com_delivery_scope.order_line_id", "TARGET_KEY_LOOKUP resolves the order line using the full approved business key", "data-elements://schema-records.jsonl#项目管理!A174:A175"),
        ],
        statusMapping={"policy": "AI_MIG_000_EXPLICIT_VALUE_MAP", "unknown": "MIGRATION_ISSUE_AND_PRESERVE_RAW"},
        terminalDisposition="CREATE_SCOPE_ONLY_WHEN_PROJECT_ORDER_LINE_AND_ALLOCATION_RESOLVE;OTHERWISE_PENDING_AND_EXCLUDED_FROM_METRICS"),
        source("CURRENT_FORWARD_TABLE", "com_delivery_scope@V70", "STRUCTURED", "F-COM-001 controlled forward conversion to the canonical delivery-scope Owner", "APPROVED", "F-COM-001",
            requiredTargetMappings={
                "com_delivery_scope.project_code": "proj_project.project_code:EXACT_SAME_TENANT_VERSION;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.order_source_system": "com_sales_order_line.source_system:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.order_company_code": "com_sales_order_line.company_code:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.order_type": "com_sales_order_line.order_type:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.order_no": "com_sales_order_line.order_no:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.line_no": "com_sales_order_line.line_no:EXACT_RESOLVED_PARENT;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.allocation_source": "APPROVED_CONSTANT:LEGACY;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
                "com_delivery_scope.status": "APPROVED_CONSTANT:ENABLED;FAIL_BATCH_ON_MISSING_OR_CONFLICT",
            },
            evidenceRef="feature-contract://F-COM-001#v70Conversion"),
    ],
    "DeliveryScopeDetail": [
        source("NONE_NEW", "DeliveryScopeDetail", "NEW_ONLY", "create details only for explicit location/product/device-type/batch allocations; never synthesize historical detail quantity or location from the legacy header", "NEW_ONLY", "FEATURE_RELEASE"),
        source("CURRENT_FORWARD_TABLE", "com_delivery_scope_detail@V70", "STRUCTURED", "F-COM-001 controlled forward conversion to the canonical delivery-scope-detail Owner", "APPROVED", "F-COM-001",
            requiredTargetMappings={"com_delivery_scope_detail.detail_sequence": "ROW_NUMBER() OVER (PARTITION BY tenant_id,delivery_scope_id ORDER BY id) ON FROZEN_INPUT_WATERMARK;FAIL_BATCH_ON_OVERFLOW_OR_INPUT_CHANGE"},
            evidenceRef="feature-contract://F-COM-001#v70Conversion"),
    ],
    "Supplier": [source("LEGACY_TABLE", "pm_subcontract_facilitator", "STRUCTURED", "map supplier identity and qualification evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "SubcontractRequest": [source("LEGACY_TABLE", "pm_subcontract_project_header|pm_subcontract_project_line|pm_subcontract_project_price|pm_subcontract_project_callback", "STRUCTURED", "map request scope, price revision and approval/callback evidence", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "PaymentGate": [source("LEGACY_TABLE", "pm_subcontract_project_payment|pm_subcontract_project_payment_sse", "STRUCTURED", "map approved prerequisites and external finance result reference", "PENDING_FIELD_MAPPING", "AI-MIG-000")],
    "MetricDefinition": [source("NONE_NEW", "MetricDefinition", "NEW_ONLY", "create versioned metric definitions only after the suggested model is approved; do not migrate report formulas by name", "NEW_ONLY_SUGGESTED", "FEATURE_RELEASE")],
    "MetricSnapshot": [
        source("DERIVED_TARGET", "Project|ProjectTask|DeliveryScope", "REBUILD", "recalculate by metric version and watermark", "REBUILD_AFTER_OWNERS", "METRIC_REBUILD"),
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
    "DeviceComponentRelation": "AST",
    "SatisfactionCollection": "ACC",
    "MetricSnapshot": "ANA",
    "InspectionReport": "SRV",
    "Contract": "COM",
    "SalesOrder": "COM",
    "AuthorizationGrant": "PLT",
    "MaintenanceFact": "AST",
    "ServiceStatus": "SRV",
}

CANONICAL_GIT_SHA = re.compile(r"[0-9a-f]{40}")


def is_canonical_git_sha(value: object) -> bool:
    return isinstance(value, str) and CANONICAL_GIT_SHA.fullmatch(value) is not None


def resolve_git_commit(repository: Path, ref: str) -> str:
    """Resolve a caller-friendly ref once, before it can enter persisted evidence."""
    if not repository.is_dir():
        raise ValueError(f"implementation repository unavailable: {repository}")
    try:
        commit = subprocess.run(
            ["git", "rev-parse", "--verify", f"{ref}^{{commit}}"], cwd=repository,
            check=True, text=True, encoding="utf-8",
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        ).stdout.strip().lower()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise ValueError(f"frozen implementation commit does not exist: {ref}") from exc
    if not is_canonical_git_sha(commit):
        raise ValueError(f"resolved implementation commit is not a canonical 40-character lowercase SHA: {commit}")
    return commit


def git_sql_blobs(repository: Path, commit: str, migration_root: str = "sql/migrations") -> dict[str, str]:
    """Read migration SQL from an immutable Git commit, never from HEAD/worktree."""
    if not repository.is_dir():
        raise ValueError(f"implementation repository unavailable: {repository}")
    if not is_canonical_git_sha(commit):
        raise ValueError("implementationCommit must be a canonical 40-character lowercase SHA")
    try:
        subprocess.run(
            ["git", "cat-file", "-e", f"{commit}^{{commit}}"], cwd=repository,
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        raise ValueError(f"frozen implementation commit does not exist: {commit}") from exc
    listing = subprocess.run(
        ["git", "ls-tree", "-r", "--name-only", commit, "--", migration_root],
        cwd=repository, check=True, text=True, encoding="utf-8",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    ).stdout.splitlines()
    paths = sorted(path for path in listing if path.endswith(".sql"))
    if not paths:
        raise ValueError(f"migration path does not exist at frozen commit: {commit}:{migration_root}")
    blobs: dict[str, str] = {}
    for path in paths:
        result = subprocess.run(
            ["git", "show", f"{commit}:{path}"], cwd=repository, check=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        blobs[path] = result.stdout.decode("utf-8-sig")
    return blobs


def git_table_catalog(repository: Path, commit: str, migration_root: str = "sql/migrations") -> dict[str, str]:
    catalog: dict[str, str] = {}
    pattern = re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", re.I)
    for path, text in git_sql_blobs(repository, commit, migration_root).items():
        for table in pattern.findall(text):
            catalog[table] = path
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


def binding_statistics(records: list[dict[str, object]], v17_target_tables: set[str]) -> dict[str, int]:
    all_bindings = 0
    v17_bindings = 0
    v17_legacy_bindings = 0
    legacy_source_tables: set[str] = set()
    legacy_source_fields: list[str] = []
    for record in records:
        for source_entry in record["sources"]:
            bindings = source_entry.get("targetFieldBindings", [])
            all_bindings += len(bindings)
            for binding_entry in bindings:
                if binding_entry["targetField"].split(".", 1)[0] not in v17_target_tables:
                    continue
                v17_bindings += 1
                if source_entry["sourceType"] not in {"LEGACY_TABLE", "LEGACY_FIELD_PATTERN"}:
                    continue
                v17_legacy_bindings += 1
                inherited_table = ""
                for token in binding_entry["sourceField"].split("|"):
                    if "." in token:
                        inherited_table, field = token.rsplit(".", 1)
                    else:
                        field = token
                    legacy_source_tables.add(inherited_table)
                    legacy_source_fields.append(f"{inherited_table}.{field}")
    return {
        "allBindingCount": all_bindings,
        "v17BindingCount": v17_bindings,
        "v17LegacyBindingCount": v17_legacy_bindings,
        "v17LegacySourceTableCount": len(legacy_source_tables),
        "v17LegacySourceFieldCount": len(legacy_source_fields),
        "v17LegacyUniqueSourceFieldCount": len(set(legacy_source_fields)),
    }


def expand_sources(raw_sources: list[dict[str, object]], current_catalog: dict[str, str], legacy_catalog: set[str], commit: str, target_tables: tuple[str, ...]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for item in raw_sources:
        entry = dict(item)
        implementation_evidence_table = entry.get("implementationEvidenceTable")
        source_type = entry["sourceType"]
        source_objects = entry["sourceObject"].split("|")
        if source_type == "CURRENT_TABLE":
            missing = [name for name in source_objects if name not in current_catalog]
            if missing:
                raise ValueError(f"current implementation source table not found: {missing}")
            if implementation_evidence_table:
                if implementation_evidence_table not in current_catalog:
                    raise ValueError(f"implementation evidence table not found: {implementation_evidence_table}")
                entry["evidenceRef"] = f"implementation://{commit}/{current_catalog[implementation_evidence_table]}#table={entry['sourceObject']}"
            else:
                entry["evidenceRef"] = ";".join(f"implementation://{commit}/{current_catalog[name]}#table={name}" for name in source_objects)
        elif source_type == "CURRENT_FIELD_PATTERN":
            table = source_objects[0].split(".", 1)[0]
            if table not in current_catalog:
                raise ValueError(f"current implementation source table not found: {table}")
            entry["evidenceRef"] = f"implementation://{commit}/{current_catalog[table]}#field-pattern={entry['sourceObject']}"
        elif source_type == "CURRENT_FORWARD_TABLE":
            if not entry.get("evidenceRef"):
                raise ValueError(f"current-forward source requires explicit feature evidence: {entry['sourceObject']}")
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
            if implementation_evidence_table:
                if implementation_evidence_table not in current_catalog:
                    raise ValueError(f"implementation evidence table not found: {implementation_evidence_table}")
                entry["evidenceRef"] = f"implementation://{commit}/{current_catalog[implementation_evidence_table]}#table={implementation_evidence_table}"
            else:
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
        match = re.fullmatch(r"([A-Z]+(?:-[A-Z0-9]+)?-\d+)(?:@V[12])?", cells[0]) if cells else None
        if not match:
            continue
        owner_index = 4 if "@V" in cells[0] else 2
        if len(cells) <= owner_index:
            continue
        identifier = match.group(1)
        owner = cells[owner_index].split("（", 1)[0].strip()
        if identifier in result and result[identifier] != owner:
            raise ValueError(f"requirement has conflicting slice Owners: {identifier}")
        result[identifier] = owner
    return result


def build(args: argparse.Namespace) -> dict[str, object]:
    phase2_contracts = parse_phase2_contracts(args.phase2_map)
    phase2_objects = set(phase2_contracts)
    expected_objects = phase2_objects | set(MODEL_ENTITY_CONTRACTS)
    if set(TARGETS) != expected_objects:
        raise ValueError(f"target object coverage mismatch; missing={sorted(expected_objects-set(TARGETS))}, extra={sorted(set(TARGETS)-expected_objects)}")
    maintained_map = json.loads(args.object_table_map.read_text(encoding="utf-8")).get("objects", {})
    core_schema_contract = json.loads(args.core_schema_contract.read_text(encoding="utf-8"))
    v17_target_tables = {
        table
        for tables in core_schema_contract["v17Delta"]["objectTargetTables"].values()
        for table in tables
    }
    if set(maintained_map) != expected_objects:
        raise ValueError("maintained object-table map coverage differs from the complete domain entity set")
    for object_name, target_tables in TARGETS.items():
        if maintained_map[object_name].get("targetTables") != list(target_tables):
            raise ValueError(f"{object_name} generator target tables differ from the maintained 09 object-table map")
        expected_policy = TARGET_POLICIES.get(object_name, {"targetTablePolicy": "CURRENT_PHYSICAL_TARGET"})
        actual_policy = {
            key: maintained_map[object_name].get(key, "CURRENT_PHYSICAL_TARGET" if key == "targetTablePolicy" else None)
            for key in expected_policy
        }
        if actual_policy != expected_policy:
            raise ValueError(f"{object_name} generator target policy differs from the maintained 09 object-table map")
    requirement_owners = parse_requirement_owners(args.requirement_matrix)
    database_design = args.database_design.read_text(encoding="utf-8")
    commit_ref = args.implementation_commit
    if not commit_ref and args.json_output.exists():
        commit_ref = json.loads(args.json_output.read_text(encoding="utf-8")).get("implementationCommit")
    if not commit_ref:
        raise ValueError("frozen implementation commit is required; pass --implementation-commit or retain it in the existing contract")
    commit = resolve_git_commit(args.implementation, commit_ref)
    current_catalog = git_table_catalog(args.implementation, commit)
    legacy_catalog = legacy_tables(args.legacy_schema)
    records = []
    for object_name, target_tables in TARGETS.items():
        contract = phase2_contracts.get(object_name)
        model_contract = MODEL_ENTITY_CONTRACTS.get(object_name)
        if contract is None:
            contract = {"requirements": set(model_contract["requirementIds"]), "tables": set()}
        target_policy = TARGET_POLICIES.get(object_name, {"targetTablePolicy": "CURRENT_PHYSICAL_TARGET"})
        undeclared_targets = {table for table in target_tables if f"`{table}`" not in database_design}
        if undeclared_targets:
            raise ValueError(f"{object_name} target tables are not declared by 09 database design: {sorted(undeclared_targets)}")
        if target_policy["targetTablePolicy"] == "FEATURE_FORWARD_MIGRATION":
            requirement_id = target_policy["featureRequirementId"]
            if f"物理表由{requirement_id} Feature前向迁移确定" not in database_design:
                raise ValueError(f"{object_name} Feature-forward policy is not declared by 09 database design")
            leaked_tables = set(target_tables) & v17_target_tables
            if leaked_tables:
                raise ValueError(f"{object_name} Feature-forward tables leaked into current core DDL: {sorted(leaked_tables)}")
        missing_owner_requirements = contract["requirements"] - set(requirement_owners)
        if missing_owner_requirements:
            raise ValueError(f"{object_name} requirements missing Owner: {sorted(missing_owner_requirements)}")
        requirement_owner_set = {requirement_owners[item] for item in contract["requirements"]}
        owner = (model_contract or {}).get("owner") or OWNER_OVERRIDES.get(object_name)
        if owner is None:
            if len(requirement_owner_set) != 1:
                raise ValueError(f"{object_name} has conflicting Owners without an explicit Context Owner: {sorted(requirement_owner_set)}")
            owner = next(iter(requirement_owner_set))
        elif requirement_owner_set and owner not in requirement_owner_set and not (model_contract or {}).get("crossContextFoundation"):
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
        record = {
            "object": object_name,
            "owner": owner,
            "ownerEvidence": (model_contract or {}).get("ownerEvidence", "docs/design/phase-1-domain-ownership.md;docs/design/02-domain-model.md"),
            "requirementIds": sorted(contract["requirements"]),
            "targetTables": list(target_tables),
            "sources": expand_sources(raw_sources, current_catalog, legacy_catalog, commit, target_tables),
        }
        if model_contract:
            for key in ("governanceRefs", "decisionRefs"):
                if model_contract.get(key):
                    record[key] = model_contract[key]
        if target_policy["targetTablePolicy"] != "CURRENT_PHYSICAL_TARGET":
            record.update(target_policy)
        records.append(record)
    object_table_map = {
        record["object"]: {
            "owner": record["owner"],
            "requirementIds": record["requirementIds"],
            "targetTables": record["targetTables"],
            **({"governanceRefs": record["governanceRefs"]} if record.get("governanceRefs") else {}),
            **({"decisionRefs": record["decisionRefs"]} if record.get("decisionRefs") else {}),
            **({"targetTablePolicy": record["targetTablePolicy"]} if record.get("targetTablePolicy") else {}),
            **({"featureRequirementId": record["featureRequirementId"]} if record.get("featureRequirementId") else {}),
        }
        for record in records
    }
    if object_table_map != maintained_map:
        raise ValueError("generated Owner/Requirement/target table contract differs from the maintained object-table map")
    return {
        "schemaVersion": 1,
        "status": "BASELINE",
        "baseline": "PRD_V1.8",
        "implementationRepo": str(args.implementation.resolve()),
        "implementationCommit": commit,
        "implementationEvidenceMode": "PINNED_GIT_COMMIT",
        "bindingStatistics": binding_statistics(records, v17_target_tables),
        "excludedSources": EXCLUDED_SOURCES,
        "objectTableMap": object_table_map,
        "records": records,
    }


def render_markdown(payload: dict[str, object]) -> str:
    lines = [
        "# 领域实体迁移显式契约",
        "",
        "> 状态：`BASELINE`",
        "> 基线：PRD V1.8 / SDS Phase 2 BASELINE",
        f"> 实现证据提交：`{payload['implementationCommit']}`",
        "> 生成源：`scripts/generate_domain_entity_migration_contract.py`；JSON为机器真值",
        "",
        "每一行只表示一个目标对象的一种来源处置；互斥来源不得合并为对象级策略。Owner由Requirement→Phase 1 Owner映射校验；当前目标表必须属于09物理设计，Feature前向迁移对象则保持空目标表直至该Feature批准物理模型。",
        "",
        "|目标对象|Owner|Requirement ID|目标表|来源类型|来源对象|证据定位|处置|转换|映射状态|Gate|",
        "|---|---|---|---|---|---|---|---|---|---|---|",
    ]
    for record in payload["records"]:
        for item in record["sources"]:
            target_display = "、".join(record["targetTables"]) or f"{record['targetTablePolicy']}({record.get('featureRequirementId', '-')})"
            values = [record["object"], record["owner"], "、".join(record["requirementIds"]), target_display, item["sourceType"], item["sourceObject"], item["evidenceRef"], item["disposition"], item["transform"], item["mappingStatus"], item["gate"]]
            lines.append("|" + "|".join(str(value).replace("|", "<br>") for value in values) + "|")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase2-map", type=Path, default=Path("docs/traceability/phase2-contract-map.md"))
    parser.add_argument("--requirement-matrix", type=Path, default=Path("docs/traceability/requirement-matrix.md"))
    parser.add_argument("--database-design", type=Path, default=Path("docs/design/09-database-design.md"))
    parser.add_argument("--legacy-schema", type=Path, default=Path("specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl"))
    parser.add_argument("--implementation", type=Path, default=Path(r"E:\AICoding\Projects\NPDMS"))
    parser.add_argument("--implementation-commit", help="immutable Git commit containing the registered sql/migrations evidence")
    parser.add_argument("--json-output", type=Path, default=Path("docs/traceability/domain-entity-migration-contract.json"))
    parser.add_argument("--md-output", type=Path, default=Path("docs/traceability/domain-entity-migration-contract.md"))
    parser.add_argument("--object-table-map", type=Path, default=Path("docs/traceability/domain-object-table-map.json"))
    parser.add_argument("--core-schema-contract", type=Path, default=Path("docs/traceability/core-migration-schema-contract.json"))
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
