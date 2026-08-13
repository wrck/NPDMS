#!/usr/bin/env python3
"""Validate the machine-readable Phase 3 evidence register."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


EXPECTED_IDS = {f"P3-E{index:02d}" for index in range(1, 10)}
VALID_STATUS = {"OPEN", "EVIDENCE_SUBMITTED", "VERIFIED", "REJECTED", "NOT_APPLICABLE"}
BASELINE_REQUIRED: set[str] = {"P3-E09"}
EXPECTED_BLOCKS = {
    "P3-E01": {"SECURITY_TRUST_BOUNDARY", "PRODUCTION_DEPLOYMENT", "PRODUCTION_RELEASE"},
    "P3-E02": {"PRODUCTION_DEPLOYMENT", "PERFORMANCE_ACCEPTANCE", "PRODUCTION_RELEASE"},
    "P3-E03": {"RECOVERY_ACCEPTANCE", "PRODUCTION_RELEASE"},
    "P3-E04": {"NFR_02", "DEVICE_CREDENTIAL_RELEASE", "PRODUCTION_RELEASE"},
    "P3-E05": {"OBSERVABILITY_ACCEPTANCE", "HIGH_RISK_AUDIT", "PRODUCTION_RELEASE"},
    "P3-E06": {"PERFORMANCE_ACCEPTANCE", "PRODUCTION_RELEASE"},
    "P3-E07": {"FEATURE_INTEGRATION", "FEATURE_RELEASE"},
    "P3-E08": {"FRONTEND_FEATURE_ACCEPTANCE", "FRONTEND_RELEASE"},
    "P3-E09": {"PHASE_3_BASELINE", "DATA_MODEL_BASELINE", "HISTORICAL_DATA_MIGRATION", "DATA_CUTOVER"},
}
DIRECTION_DECISIONS = {
    "P3-E01": "A",
    "P3-E02": "A",
    "P3-E03": "A",
    "P3-E04": "A",
    "P3-E05": "A",
    "P3-E06": "A",
    "P3-E07": "B",
    "P3-E09": "A",
}
DECISION_REF = "docs/decisions/0004-phase3-production-assurance-directions.md"
RECOVERY_OBJECTIVE_REF = "docs/decisions/0005-production-recovery-objectives.md"
PERMANENT_AUDIT_REF = "docs/decisions/0006-permanent-business-audit-retention.md"
PERMANENT_AUDIT_POLICY = "PERMANENT_NON_DELETABLE"
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
MODEL_DECISION_REF = "docs/decisions/0023-p3-e09-key-collation-and-state-guard-policy.md"
Q07_DECISION = {
    "status": "ACCEPTED",
    "technicalConstraintCount": 222,
    "primaryKeyCount": 50,
    "primaryKeyShape": {"singleId": 49, "compositeProjection": 1},
    "tenantReferenceKeyCount": 50,
    "sameDomainForeignKeyCount": 48,
    "stableTechnicalCheckCount": 74,
}
Q08_DECISION = {
    "status": "CANDIDATE_BASELINE_ACCEPTED",
    "candidateIndexCount": 108,
    "featureQueryPlanValidationRequired": True,
    "p3e06PerformanceValidationRequired": True,
    "adjustmentPolicy": "FORWARD_MIGRATION_ONLY",
}
NETWORK_SECURITY_RETENTION = {
    "policyCode": "NETWORK_SECURITY_LOG_P1Y",
    "totalRetention": "P1Y",
    "onlineRetention": "P180D",
    "immutableColdRetention": "P185D",
}
TRACE_RETENTION = {
    "standard": {
        "policyCode": "TRACE_STANDARD_P90D",
        "totalRetention": "P90D",
        "onlineRetention": "P30D",
        "coldRetention": "P60D",
    },
    "errorHighRisk": {
        "policyCode": "TRACE_ERROR_HIGH_RISK_P180D",
        "totalRetention": "P180D",
        "onlineRetention": "P30D",
        "coldRetention": "P150D",
    },
}
METRIC_RETENTION = {
    "rawHighResolution": {
        "policyCode": "METRIC_RAW_P90D",
        "retention": "P90D",
    },
    "fiveMinuteHourlyAggregate": {
        "policyCode": "METRIC_AGGREGATE_P13M",
        "retention": "P13M",
    },
}
DEBUG_LOG_RETENTION = {
    "policyCode": "DEBUG_LOG_DEFAULT_P7D",
    "defaultRetention": "P7D",
    "exceptionPolicyCode": "DEBUG_LOG_EXCEPTION_MAX_P30D",
    "maximumExceptionRetention": "P30D",
    "exceptionRequiredFields": ["reason", "owner", "expiresAt"],
}
TRACE_SAMPLING_POLICY = {
    "standardSuccessSampleRate": 0.10,
    "forcedSampleRate": 1.0,
    "forcedCategories": ["ERROR", "HIGH_RISK_SECURITY_OPERATION", "AUDIT_WRITE_FAILURE", "RELEASE_MIGRATION"],
    "unsampledStillProducesMetrics": True,
    "unsampledStillProducesPermanentAudit": True,
}
BACKUP_RETENTION_POLICY = {"dailyRetention": "P35D", "monthlyRetention": "P13M", "yearlyRetention": "P7Y", "continuousLogMaxGap": "PT1H"}
RECOVERY_TOPOLOGY = {"primary": "METRO_WARM_STANDBY", "fallback": "OFFLINE_COLD_BACKUP_FALLBACK", "approvedRto": "PT4H"}
RECOVERY_EXERCISE_POLICY = {"isolatedRestoreFrequency": "P3M", "fullWarmStandbySwitchFrequency": "P1Y"}
RECOVERY_SWITCH_AUTHORIZATION = {"initiator": "OPERATIONS_OWNER", "requiredConfirmer": "BUSINESS_OWNER", "securityIncidentAdditionalConfirmer": "SECURITY_OWNER", "auditPolicy": "PERMANENT_NON_DELETABLE"}
EXPORT_AUTHORIZATION_POLICY = {
    "approvalRequired": False,
    "requiredControls": ["EXPORT_FUNCTION_PERMISSION", "DATA_SCOPE", "FIELD_PERMISSION", "REAL_TIME_DOWNLOAD_RECHECK"],
    "exportAuditPolicy": "PERMANENT_NON_DELETABLE",
    "exportFileTtl": "PT24H",
    "exportRecordRetention": "PERMANENT_NON_DELETABLE",
}
LOCAL_REPOSITORY_ASSESSMENTS = {
    "P3-E01": "NO_PRODUCTION_EVIDENCE",
    "P3-E02": "DEVELOPMENT_SINGLE_NODE_ONLY",
    "P3-E03": "NO_RECOVERY_EXERCISE_EVIDENCE",
    "P3-E04": "NO_ENTERPRISE_KMS_EVIDENCE",
    "P3-E05": "CAPABILITY_ONLY_NO_PRODUCTION_BACKEND_EVIDENCE",
    "P3-E06": "LOCAL_HEALTH_SCRIPTS_ONLY",
}


def validate(path: Path, *, require_ready: bool = False) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read evidence register: {exc}"]

    if payload.get("schemaVersion") != 1:
        errors.append("unsupported evidence register schemaVersion")
    if payload.get("phase") != "SDS_PHASE_3" or payload.get("baseline") != "PRD_V1.7":
        errors.append("evidence register phase/baseline mismatch")

    items = payload.get("items")
    if not isinstance(items, list):
        return errors + ["evidence register items must be a list"]
    identifiers = [item.get("id") for item in items if isinstance(item, dict)]
    if len(identifiers) != len(set(identifiers)):
        errors.append("duplicate evidence item id")
    if set(identifiers) != EXPECTED_IDS:
        errors.append(f"evidence item coverage mismatch; missing={sorted(EXPECTED_IDS - set(identifiers))}, extra={sorted(set(identifiers) - EXPECTED_IDS)}")

    for item in items:
        if not isinstance(item, dict):
            errors.append("evidence item must be an object")
            continue
        identifier = item.get("id", "UNKNOWN")
        status = item.get("status")
        if status not in VALID_STATUS:
            errors.append(f"{identifier} invalid status: {status}")
        for field in ("decisionOwner", "reviewOwner", "confirmedFacts", "evidenceRefs", "blocks"):
            if field not in item:
                errors.append(f"{identifier} missing field: {field}")
        if not isinstance(item.get("confirmedFacts"), dict):
            errors.append(f"{identifier} confirmedFacts must be an object")
        if not isinstance(item.get("evidenceRefs"), list) or not isinstance(item.get("blocks"), list):
            errors.append(f"{identifier} evidenceRefs/blocks must be lists")
        if status == "VERIFIED":
            if not item.get("decisionOwner") or not item.get("reviewOwner"):
                errors.append(f"{identifier} VERIFIED requires decisionOwner and reviewOwner")
            if not item.get("confirmedFacts") or not item.get("evidenceRefs"):
                errors.append(f"{identifier} VERIFIED requires confirmed facts and evidence refs")
        if status == "NOT_APPLICABLE" and identifier != "P3-E07":
            errors.append(f"{identifier} cannot be NOT_APPLICABLE")

    by_id = {item["id"]: item for item in items if isinstance(item, dict) and "id" in item}
    if payload.get("decisionBaseline") != DECISION_REF:
        errors.append("Phase 3 direction decision baseline mismatch")
    for identifier, expected_decision in DIRECTION_DECISIONS.items():
        item = by_id.get(identifier, {})
        facts = item.get("confirmedFacts", {})
        if facts.get("directionDecision") != expected_decision or facts.get("directionStatus") != "ACCEPTED":
            errors.append(f"{identifier} direction decision must remain {expected_decision}/ACCEPTED")
        if item.get("decisionOwner") != "REQUIREMENT_OWNER" or DECISION_REF not in item.get("evidenceRefs", []):
            errors.append(f"{identifier} direction decision owner/reference mismatch")
    for identifier, assessment in LOCAL_REPOSITORY_ASSESSMENTS.items():
        item = by_id.get(identifier, {})
        if item.get("confirmedFacts", {}).get("localRepositoryAssessment") != assessment:
            errors.append(f"{identifier} local repository evidence assessment mismatch")
        if "docs/engineering/gates/phase-3/runtime-fact-inventory.md" not in item.get("evidenceRefs", []):
            errors.append(f"{identifier} local repository assessment reference missing")
    e03 = by_id.get("P3-E03", {})
    e03_facts = e03.get("confirmedFacts", {})
    if (e03_facts.get("approvedRpo"), e03_facts.get("approvedRto"), e03_facts.get("businessObjectiveStatus")) != ("PT1H", "PT4H", "ACCEPTED"):
        errors.append("P3-E03 approved recovery objectives must remain PT1H/PT4H/ACCEPTED")
    if RECOVERY_OBJECTIVE_REF not in e03.get("evidenceRefs", []):
        errors.append("P3-E03 recovery objective decision reference missing")
    if e03_facts.get("backupRetention") != BACKUP_RETENTION_POLICY:
        errors.append("P3-E03 backup retention must remain P35D daily, P13M monthly, P7Y yearly and PT1H continuous-log gap")
    if e03_facts.get("recoveryTopology") != RECOVERY_TOPOLOGY:
        errors.append("P3-E03 recovery topology must remain metro warm standby with offline cold backup fallback")
    if e03_facts.get("recoveryExercisePolicy") != RECOVERY_EXERCISE_POLICY:
        errors.append("P3-E03 recovery exercise frequency must remain quarterly isolated restore and annual full warm switch")
    if e03_facts.get("switchAuthorization") != RECOVERY_SWITCH_AUTHORIZATION:
        errors.append("P3-E03 switch authorization must remain operations initiate, business confirm, security additionally confirms incidents")
    if any(ref not in e03.get("evidenceRefs", []) for ref in (BACKUP_RETENTION_REF, RECOVERY_TOPOLOGY_REF, RECOVERY_EXERCISE_REF, RECOVERY_SWITCH_AUTH_REF)):
        errors.append("P3-E03 backup retention/recovery topology decision references missing")
    e05 = by_id.get("P3-E05", {})
    e05_facts = e05.get("confirmedFacts", {})
    if e05_facts.get("permanentAuditPolicy") != PERMANENT_AUDIT_POLICY:
        errors.append("P3-E05 permanent audit policy must remain PERMANENT_NON_DELETABLE")
    if PERMANENT_AUDIT_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 permanent audit retention decision reference missing")
    if e05_facts.get("networkSecurityLogRetention") != NETWORK_SECURITY_RETENTION:
        errors.append("P3-E05 network/security log retention must remain P1Y with P180D online and P185D immutable cold storage")
    if NETWORK_SECURITY_RETENTION_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 network/security log retention decision reference missing")
    if e05_facts.get("traceRetention") != TRACE_RETENTION:
        errors.append("P3-E05 trace retention must remain P90D standard and P180D error/high-risk")
    if TRACE_RETENTION_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 trace retention decision reference missing")
    if e05_facts.get("metricRetention") != METRIC_RETENTION:
        errors.append("P3-E05 metric retention must remain P90D raw and P13M five-minute/hourly aggregate")
    if METRIC_RETENTION_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 metric retention decision reference missing")
    if e05_facts.get("debugLogRetention") != DEBUG_LOG_RETENTION:
        errors.append("P3-E05 debug log retention must remain P7D by default and P30D maximum by registered exception")
    if DEBUG_LOG_RETENTION_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 debug log retention decision reference missing")
    if e05_facts.get("traceSamplingPolicy") != TRACE_SAMPLING_POLICY:
        errors.append("P3-E05 trace sampling must remain 10% standard success and 100% forced categories")
    if TRACE_SAMPLING_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 trace sampling decision reference missing")
    if e05_facts.get("exportAuthorizationPolicy") != EXPORT_AUTHORIZATION_POLICY:
        errors.append("P3-E05 export authorization must remain permission/data-scope driven without extra approval")
    if EXPORT_POLICY_REF not in e05.get("evidenceRefs", []) or EXPORT_EXPIRATION_REF not in e05.get("evidenceRefs", []):
        errors.append("P3-E05 export authorization decision reference missing")
    e08 = by_id.get("P3-E08", {})
    if e08.get("confirmedFacts", {}).get("result") != "FAIL" or e08.get("status") == "VERIFIED":
        errors.append("P3-E08 must retain the currently verified ts:check failure until closure evidence replaces it")
    for identifier, expected_blocks in EXPECTED_BLOCKS.items():
        actual_blocks = set(by_id.get(identifier, {}).get("blocks", []))
        if actual_blocks != expected_blocks:
            errors.append(f"{identifier} gate scope mismatch; expected={sorted(expected_blocks)}, actual={sorted(actual_blocks)}")
        if identifier != "P3-E09" and "PHASE_3_BASELINE" in actual_blocks:
            errors.append(f"{identifier} runtime evidence must not block PHASE_3_BASELINE")
    for identifier in {"P3-E01", "P3-E04"}:
        item = by_id.get(identifier, {})
        if item.get("confirmedFacts", {}).get("evidenceStage") != "DEPLOYMENT_TIME":
            errors.append(f"{identifier} evidenceStage must remain DEPLOYMENT_TIME")
        if DEPLOYMENT_TIME_SELECTION_REF not in item.get("evidenceRefs", []):
            errors.append(f"{identifier} deployment-time decision reference missing")
    e09 = by_id.get("P3-E09", {})
    facts = e09.get("confirmedFacts", {})
    if facts.get("currentDdlSha256") == facts.get("legacyCatalogDdlSha256") or facts.get("driftDecision") != "DEFER":
        errors.append("P3-E09 must retain the current DDL drift and DEFER decision until AI-MIG-000 is approved")
    if facts.get("modelDecisionStatus") != "REQUIREMENT_OWNER_ACCEPTED_REVIEW_PENDING":
        errors.append("P3-E09 Q01-Q08 model decisions must remain requirement-owner accepted and review pending")
    if facts.get("q07Decision") != Q07_DECISION:
        errors.append("P3-E09 Q07 accepted technical constraint decision mismatch")
    if facts.get("q08Decision") != Q08_DECISION:
        errors.append("P3-E09 Q08 candidate index decision mismatch")
    if MODEL_DECISION_REF not in e09.get("evidenceRefs", []):
        errors.append("P3-E09 Q07/Q08 decision reference missing")

    ready = all(by_id.get(identifier, {}).get("status") == "VERIFIED" for identifier in BASELINE_REQUIRED)
    expected_overall = "READY_FOR_SDS_BASELINE" if ready else "NOT_READY_FOR_SDS_BASELINE"
    if payload.get("overallStatus") != expected_overall:
        errors.append(f"overallStatus must be {expected_overall}")
    if require_ready and not ready:
        errors.append(f"Phase 3 evidence not ready; unverified={sorted(identifier for identifier in BASELINE_REQUIRED if by_id.get(identifier, {}).get('status') != 'VERIFIED')}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--register",
        type=Path,
        default=Path("docs/engineering/gates/phase-3/phase3-evidence-register.json"),
    )
    parser.add_argument("--require-ready", action="store_true")
    args = parser.parse_args()
    errors = validate(args.register, require_ready=args.require_ready)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print("[PASS] Phase 3 evidence register structure and current gate state")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
