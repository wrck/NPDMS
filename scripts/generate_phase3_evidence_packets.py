#!/usr/bin/env python3
"""Generate owner-fillable Phase 3 evidence packets from the accepted ADR."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from validate_phase3_evidence_submission import REQUIRED_FACTS
from p3e09_approval_policy import item_ids_sha256, model_baseline_review_status


DECISION_REF = "docs/decisions/0004-phase3-production-assurance-directions.md"
RECOVERY_OBJECTIVE_REF = "docs/decisions/0005-production-recovery-objectives.md"
PERMANENT_AUDIT_REF = "docs/decisions/0006-permanent-business-audit-retention.md"
NETWORK_SECURITY_RETENTION_REF = "docs/decisions/0007-network-security-log-retention.md"
TRACE_RETENTION_REF = "docs/decisions/0008-trace-retention.md"
METRIC_RETENTION_REF = "docs/decisions/0009-metric-retention.md"
DEBUG_LOG_RETENTION_REF = "docs/decisions/0010-debug-log-retention.md"
TRACE_SAMPLING_REF = "docs/decisions/0011-production-trace-sampling.md"
BACKUP_RETENTION_REF = "docs/decisions/0012-production-backup-retention.md"
RECOVERY_TOPOLOGY_REF = "docs/decisions/0013-warm-standby-and-offline-cold-backup.md"
EXPORT_POLICY_REF = "docs/decisions/0014-permission-driven-business-data-export.md"
RECOVERY_EXERCISE_REF = "docs/decisions/0015-recovery-exercise-frequency.md"
EXPORT_EXPIRATION_REF = "docs/decisions/0016-export-file-expiration.md"
RECOVERY_SWITCH_AUTH_REF = "docs/decisions/0017-disaster-recovery-switch-authorization.md"
DEPLOYMENT_TIME_SELECTION_REF = "docs/decisions/0018-deployment-time-environment-and-kms-selection.md"
DATABASE_NAMING_REF = "docs/decisions/0019-domain-coded-database-naming.md"
PROJECT_CODE_REF = "docs/decisions/0020-project-code-identity-and-namespace.md"
MARKET_RELATION_REF = "docs/decisions/0021-customer-market-relation-classification.md"
CORE_MIGRATION_SCHEMA_REF = "docs/decisions/0022-core-migration-schema-and-key-policy.md"
MODEL_DECISION_REF = "docs/decisions/0023-p3-e09-key-collation-and-state-guard-policy.md"
V17_DDL_DELTA_REF = "docs/decisions/0025-v1.7-p3-e09-ddl-delta.md"
CURRENT_HASH_REQUIREMENT_CONFIRMATION_REF = "docs/decisions/0028-p3-e09-current-hash-requirement-owner-confirmation.md"
P3E09_CONFIRMATION_PACKET_REF = "specs/001-project-delivery-platform/evidence/migration/p3-e09-confirmation-packet.json"
CORE_SCHEMA_CONTRACT = Path("docs/traceability/core-migration-schema-contract.json")
DDL_DECISION_REGISTER = Path("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json")
DDL_PATH = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
DDL_EXECUTION_EVIDENCE = Path("specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json")
P3E09_MODEL_BASELINE_BLOCKS = ["HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"]
P3E09_USAGE_RESTRICTION = "仅用于SDS数据模型基线事实校验；不得用于授权历史数据迁移或数据切换。"
P3E09_INDEPENDENT_REVIEW_REF = "docs/engineering/gates/phase-3/independent-review.md"
P3E09_REVIEW_OWNER = "INDEPENDENT_REVIEWER"
BACKUP_RETENTION_POLICY = {
    "dailyRetention": "P35D",
    "monthlyRetention": "P13M",
    "yearlyRetention": "P7Y",
    "continuousLogMaxGap": "PT1H",
}
RECOVERY_TOPOLOGY = {
    "primary": "METRO_WARM_STANDBY",
    "fallback": "OFFLINE_COLD_BACKUP_FALLBACK",
    "approvedRto": "PT4H",
}
RECOVERY_EXERCISE_POLICY = {"isolatedRestoreFrequency": "P3M", "fullWarmStandbySwitchFrequency": "P1Y"}
RECOVERY_SWITCH_AUTHORIZATION = {
    "initiator": "OPERATIONS_OWNER",
    "requiredConfirmer": "BUSINESS_OWNER",
    "securityIncidentAdditionalConfirmer": "SECURITY_OWNER",
    "auditPolicy": "PERMANENT_NON_DELETABLE",
}
EXPORT_AUTHORIZATION_POLICY = {
    "approvalRequired": False,
    "requiredControls": ["EXPORT_FUNCTION_PERMISSION", "DATA_SCOPE", "FIELD_PERMISSION", "REAL_TIME_DOWNLOAD_RECHECK"],
    "exportAuditPolicy": "PERMANENT_NON_DELETABLE",
    "exportFileTtl": "PT24H",
    "exportRecordRetention": "PERMANENT_NON_DELETABLE",
}
PERMANENT_AUDIT_POLICY = {
    "policyCode": "PERMANENT_NON_DELETABLE",
    "permanentCategories": [
        "BUSINESS_FACT",
        "APPROVAL_HISTORY",
        "EXPLICITLY_REQUIRED_OPERATION_AUDIT",
    ],
    "deletionAllowed": False,
    "coldStorageAllowed": True,
    "finiteCategories": {
        "NETWORK_SECURITY_OPERATION_LOG": {
            "policyCode": "NETWORK_SECURITY_LOG_P1Y",
            "totalRetention": "P1Y",
            "onlineRetention": "P180D",
            "immutableColdRetention": "P185D",
        },
        "STANDARD_TRACE": {
            "policyCode": "TRACE_STANDARD_P90D",
            "totalRetention": "P90D",
            "onlineRetention": "P30D",
            "coldRetention": "P60D",
        },
        "ERROR_HIGH_RISK_TRACE": {
            "policyCode": "TRACE_ERROR_HIGH_RISK_P180D",
            "totalRetention": "P180D",
            "onlineRetention": "P30D",
            "coldRetention": "P150D",
        },
        "RAW_HIGH_RESOLUTION_METRIC": {
            "policyCode": "METRIC_RAW_P90D",
            "retention": "P90D",
        },
        "FIVE_MINUTE_HOURLY_METRIC_AGGREGATE": {
            "policyCode": "METRIC_AGGREGATE_P13M",
            "retention": "P13M",
        },
        "DEBUG_LOG": {
            "policyCode": "DEBUG_LOG_DEFAULT_P7D",
            "defaultRetention": "P7D",
            "exceptionPolicyCode": "DEBUG_LOG_EXCEPTION_MAX_P30D",
            "maximumExceptionRetention": "P30D",
            "exceptionRequiredFields": ["reason", "owner", "expiresAt"],
        }
    },
}
TRACE_SAMPLING_POLICY = {
    "standardSuccessSampleRate": 0.10,
    "forcedSampleRate": 1.0,
    "forcedCategories": [
        "ERROR",
        "HIGH_RISK_SECURITY_OPERATION",
        "AUDIT_WRITE_FAILURE",
        "RELEASE_MIGRATION",
    ],
    "unsampledStillProducesMetrics": True,
    "unsampledStillProducesPermanentAudit": True,
}
PACKET_DEFINITIONS = {
    "P3-E01": ("A", "复用企业现有网关/LB、证书和网络区", ["TECHNICAL_ARCHITECTURE_OWNER", "OPERATIONS_OWNER"]),
    "P3-E02": ("A", "使用企业托管MySQL/Redis HA服务", ["DBA_OWNER", "OPERATIONS_OWNER"]),
    "P3-E03": ("A", "业务Owner先批准RPO/RTO，再由DBA/运维设计并演练", ["BUSINESS_OWNER", "DBA_OWNER", "OPERATIONS_OWNER"]),
    "P3-E04": ("A", "使用企业KMS/Secrets Manager", ["SECURITY_OWNER", "OPERATIONS_OWNER"]),
    "P3-E05": ("A", "OpenTelemetry统一采集并接入企业现有后端", ["OBSERVABILITY_OWNER", "SECURITY_OWNER", "COMPLIANCE_OWNER"]),
    "P3-E06": ("A", "建设独立近生产性能环境", ["TEST_OWNER", "OPERATIONS_OWNER", "DATA_OWNER"]),
    "P3-E07": ("B", "建立平台级接口配置注册表，Feature引用不可变版本", ["INTEGRATION_ARCHITECTURE_OWNER", "EXTERNAL_SYSTEM_OWNER"]),
    "P3-E09": ("A", "逐表、列、索引和约束生成差异并逐项裁决", ["DATA_ARCHITECTURE_OWNER", "BUSINESS_OWNER", "MIGRATION_OWNER"]),
}


def build_packets() -> dict[str, dict[str, object]]:
    packets: dict[str, dict[str, object]] = {}
    for identifier, (decision, direction, owner_roles) in PACKET_DEFINITIONS.items():
        facts = {field: None for field in sorted(REQUIRED_FACTS[identifier])}
        facts.update(
            {
                "directionDecision": decision,
                "directionStatus": "ACCEPTED",
                "chosenDirection": direction,
                "evidenceOwnerRoles": owner_roles,
            }
        )
        if identifier == "P3-E09":
            ddl_sha256 = hashlib.sha256(DDL_PATH.read_bytes()).hexdigest().upper()
            execution = json.loads(DDL_EXECUTION_EVIDENCE.read_text(encoding="utf-8"))
            contract = json.loads(CORE_SCHEMA_CONTRACT.read_text(encoding="utf-8"))
            register = json.loads(DDL_DECISION_REGISTER.read_text(encoding="utf-8"))
            if execution.get("status") != "PASS" or execution.get("ddlSha256") != ddl_sha256:
                raise ValueError("P3-E09 isolated MySQL execution evidence is absent or stale")
            deferred_count = sum(item.get("decision") == "DEFER" for item in register["items"])
            approved_hash = register.get("approval", {}).get("approvedDdlSha256")
            if approved_hash is not None:
                raise ValueError("P3-E09 model baseline must keep approvedDdlSha256 explicitly null")
            model_status, review_fields = model_baseline_review_status(Path.cwd(), register, execution.get("status"))
            if deferred_count != 0:
                model_status = "PARTIALLY_ACCEPTED_RECONFIRMATION_REQUIRED"
            model_baseline = {
                "status": model_status,
                "currentDdlSha256": ddl_sha256,
                "deferredItemCount": deferred_count,
                "approvedDdlSha256": None,
                "blocks": P3E09_MODEL_BASELINE_BLOCKS,
            }
            if contract.get("p3e09ModelBaseline") != model_baseline:
                raise ValueError("P3-E09 core migration schema contract model baseline drift")
            if register.get("modelBaseline") != model_baseline:
                raise ValueError("P3-E09 DDL decision register model baseline drift")
            facts.update(
                {
                    "currentDdlSha256": ddl_sha256,
                    "targetCatalogDdlSha256": ddl_sha256,
                    "mappingDdlSha256": ddl_sha256,
                    "validationDdlSha256": ddl_sha256,
                    "manifestDdlSha256": ddl_sha256,
                    "itemsSha256": register["itemsSha256"],
                    "itemIdsSha256": item_ids_sha256(register["items"]),
                    "mysql84DdlSha256": ddl_sha256,
                    "driftDecisionRegister": "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json",
                    "modelDecisionStatus": model_baseline["status"],
                    "deferredItemCount": deferred_count,
                    "approvedDdlSha256": None,
                    "independentReviewResult": "GO" if model_status == "MODEL_BASELINE_READY" else None,
                    "independentReviewRef": P3E09_INDEPENDENT_REVIEW_REF if model_status == "MODEL_BASELINE_READY" else None,
                    "candidateCommit": review_fields.get("candidateCommit") if model_status == "MODEL_BASELINE_READY" else None,
                    "v17DeltaStatus": contract["v17Delta"]["status"],
                    "requirementOwnerConfirmation": {
                        "status": contract["p3e09RequirementOwnerConfirmation"]["status"],
                        "decisionRef": contract["p3e09RequirementOwnerConfirmation"]["decisionRef"],
                        "groupCount": len(contract["p3e09RequirementOwnerConfirmation"]["groups"]),
                        "confirmedUniqueItemCount": contract["p3e09RequirementOwnerConfirmation"]["confirmedUniqueItemCount"],
                        "reviewStatus": contract["p3e09RequirementOwnerConfirmation"]["reviewStatus"],
                    },
                    "q07Decision": {
                        "status": contract["q07TechnicalConstraintPolicy"]["status"],
                        "technicalConstraintCount": (
                            contract["q07TechnicalConstraintPolicy"]["primaryKeyCount"]
                            + contract["q07TechnicalConstraintPolicy"]["tenantReferenceKeyCount"]
                            + contract["q07TechnicalConstraintPolicy"]["sameDomainForeignKeyCount"]
                            + sum(contract["q07TechnicalConstraintPolicy"]["stableTechnicalCheckGroups"].values())
                        ),
                        "primaryKeyCount": contract["q07TechnicalConstraintPolicy"]["primaryKeyCount"],
                        "primaryKeyShape": contract["q07TechnicalConstraintPolicy"]["primaryKeyShape"],
                        "tenantReferenceKeyCount": contract["q07TechnicalConstraintPolicy"]["tenantReferenceKeyCount"],
                        "sameDomainForeignKeyCount": contract["q07TechnicalConstraintPolicy"]["sameDomainForeignKeyCount"],
                        "stableTechnicalCheckCount": sum(contract["q07TechnicalConstraintPolicy"]["stableTechnicalCheckGroups"].values()),
                    },
                    "q08Decision": {
                        "status": contract["q08OrdinaryIndexPolicy"]["status"],
                        "candidateIndexCount": contract["q08OrdinaryIndexPolicy"]["candidateIndexCount"],
                        "featureQueryPlanValidationRequired": contract["q08OrdinaryIndexPolicy"]["featureQueryPlanValidationRequired"],
                        "p3e06PerformanceValidationRequired": contract["q08OrdinaryIndexPolicy"]["p3e06PerformanceValidationRequired"],
                        "adjustmentPolicy": contract["q08OrdinaryIndexPolicy"]["adjustmentPolicy"],
                    },
                    "isolatedMysqlExecution": {
                        "status": execution["status"],
                        "mysqlVersion": execution["mysqlVersion"],
                        "tableCount": execution["tableCount"],
                        "columnCount": execution["columnCount"],
                        "constraintCount": execution["constraintCount"],
                    },
                }
            )
        if identifier in {"P3-E01", "P3-E04"}:
            facts["evidenceStage"] = "DEPLOYMENT_TIME"
        if identifier == "P3-E03":
            facts.update({"approvedRpo": "PT1H", "approvedRto": "PT4H", "retention": BACKUP_RETENTION_POLICY, "recoveryTopology": RECOVERY_TOPOLOGY, "exercisePolicy": RECOVERY_EXERCISE_POLICY, "switchAuthorization": RECOVERY_SWITCH_AUTHORIZATION})
        if identifier == "P3-E05":
            facts["retentionPolicy"] = PERMANENT_AUDIT_POLICY
            facts["samplingPolicy"] = TRACE_SAMPLING_POLICY
            facts["exportAuthorizationPolicy"] = EXPORT_AUTHORIZATION_POLICY
        evidence_refs = [DECISION_REF]
        if identifier == "P3-E03":
            evidence_refs.append(RECOVERY_OBJECTIVE_REF)
            evidence_refs.extend([BACKUP_RETENTION_REF, RECOVERY_TOPOLOGY_REF, RECOVERY_EXERCISE_REF, RECOVERY_SWITCH_AUTH_REF])
        if identifier == "P3-E05":
            evidence_refs.append(PERMANENT_AUDIT_REF)
            evidence_refs.append(NETWORK_SECURITY_RETENTION_REF)
            evidence_refs.append(TRACE_RETENTION_REF)
            evidence_refs.append(METRIC_RETENTION_REF)
            evidence_refs.append(DEBUG_LOG_RETENTION_REF)
            evidence_refs.append(TRACE_SAMPLING_REF)
            evidence_refs.append(EXPORT_POLICY_REF)
            evidence_refs.append(EXPORT_EXPIRATION_REF)
        if identifier == "P3-E09":
            evidence_refs.extend([
                DATABASE_NAMING_REF,
                PROJECT_CODE_REF,
                MARKET_RELATION_REF,
                CORE_MIGRATION_SCHEMA_REF,
                MODEL_DECISION_REF,
                V17_DDL_DELTA_REF,
                CURRENT_HASH_REQUIREMENT_CONFIRMATION_REF,
                P3E09_CONFIRMATION_PACKET_REF,
                "specs/001-project-delivery-platform/evidence/migration/ddl-drift-review.json",
                "specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json",
                "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json",
                "specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md",
                "specs/001-project-delivery-platform/evidence/migration/ddl-mysql84-execution-evidence.json",
                P3E09_INDEPENDENT_REVIEW_REF,
            ])
        if identifier in {"P3-E01", "P3-E04"}:
            evidence_refs.append(DEPLOYMENT_TIME_SELECTION_REF)
        packet = {
            "schemaVersion": 2 if identifier == "P3-E09" else 1,
            "id": identifier,
            "status": "DRAFT",
            "decisionOwner": "REQUIREMENT_OWNER",
            "evidenceOwnerRoles": owner_roles,
            "reviewOwner": P3E09_REVIEW_OWNER if identifier == "P3-E09" and facts.get("modelDecisionStatus") == "MODEL_BASELINE_READY" else None,
            "environmentOrReleaseId": None,
            "capturedAt": None,
            "confirmedFacts": facts,
            "evidenceRefs": evidence_refs,
            "verificationResult": None,
            "unresolvedItems": ["由evidenceOwnerRoles对应Owner填写全部空值并附受控证据引用"],
            "revalidationTriggers": [],
        }
        if identifier == "P3-E09":
            packet["blocks"] = P3E09_MODEL_BASELINE_BLOCKS
            packet["usageRestriction"] = P3E09_USAGE_RESTRICTION
            packet["approvalBinding"] = {
                "currentDdlSha256": register["currentDdlSha256"],
                "itemsSha256": None,
                "itemCount": len(register["items"]),
                "itemIdsSha256": item_ids_sha256(register["items"]),
            }
        packets[identifier] = packet
    return packets


def render(payload: object) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2) + "\n"


def expected_files(output_dir: Path) -> dict[Path, str]:
    packets = build_packets()
    files = {output_dir / f"{identifier.lower()}-submission.json": render(packet) for identifier, packet in packets.items()}
    manifest = {
        "schemaVersion": 1,
        "decisionBaseline": DECISION_REF,
        "status": "OWNER_INPUT_PENDING",
        "packets": [
            {
                "id": identifier,
                "path": f"docs/engineering/gates/phase-3/evidence-packet-templates/{identifier.lower()}-submission.json",
                "evidenceOwnerRoles": PACKET_DEFINITIONS[identifier][2],
            }
            for identifier in PACKET_DEFINITIONS
        ],
    }
    files[output_dir / "manifest.json"] = render(manifest)
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("docs/engineering/gates/phase-3/evidence-packet-templates"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    files = expected_files(args.output_dir)
    if args.check:
        drift = [str(path) for path, content in files.items() if not path.exists() or path.read_text(encoding="utf-8") != content]
        if drift:
            print(f"[FAIL] Phase 3 evidence packet drift: {drift}")
            return 1
        print(f"[PASS] {len(PACKET_DEFINITIONS)} Phase 3 evidence packets and manifest")
        return 0
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for path, content in files.items():
        path.write_text(content, encoding="utf-8")
    print(f"[WRITE] {len(PACKET_DEFINITIONS)} Phase 3 evidence packets and manifest")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
