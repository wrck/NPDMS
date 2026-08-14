#!/usr/bin/env python3
"""Validate owner-submitted Phase 3 production evidence before register promotion."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from p3e09_approval_policy import DDL_ARTIFACT_HASH_FIELDS, validate_model_baseline


REQUIRED_FACTS = {
    "P3-E01": {"environmentId", "entryGateway", "domainRef", "tlsTermination", "certificateOwner", "networkZones", "applicationNodes", "dataNodes", "allowedFlows", "objectStorage", "dacExecutionZone", "operationsOwner", "topologyVersion"},
    "P3-E02": {"serviceMode", "mysqlVersion", "redisVersion", "haMode", "nodeSpecs", "storageCapacity", "connectionLimits", "redisPersistence", "redisEvictionPolicy", "monitoringOwner", "failoverEvidenceId", "capacityEvidenceId"},
    "P3-E03": {"approvedRpo", "approvedRto", "backupMedia", "backupFrequency", "retention", "encryption", "restoreOrder", "exerciseOwner", "exerciseEvidenceId", "actualRpo", "actualRto", "businessProbeResult"},
    "P3-E04": {"keyFacility", "algorithmMode", "masterDataKeySeparation", "keyVersioning", "accessIdentities", "auditInterface", "revocationProcedure", "rotationOwner", "incidentOwner", "rotationExerciseId", "secretScanResult"},
    "P3-E05": {"logBackend", "metricBackend", "traceBackend", "alertBackend", "securityEventBackend", "collectionProtocol", "accessRoles", "redactionPolicy", "retentionPolicy", "samplingPolicy", "dashboardRefs", "alertRefs", "runbookRefs", "timeSync", "telemetryOwner", "testTriggerEvidenceId"},
    "P3-E06": {"environmentTopology", "nodeSpecs", "productionDifferences", "scalingModel", "networkConditions", "dataSetVersion", "migrationVolume", "accountDistribution", "externalDependencyMode", "loadScriptSha256", "monitoringRefs", "cleanupProcedure", "testOwner"},
    "P3-E07": {"requirementIds", "featureId", "externalSystem", "systemOwner", "direction", "endpointRef", "authenticationRef", "networkAllowlistRef", "mappingVersion", "sourceKey", "idempotencyKey", "timeoutMs", "retryPolicy", "compensation", "reconciliation", "degradation", "sandboxEvidenceId", "releaseApprovalId"},
    "P3-E08": {"implementationCommit", "nodeVersion", "pnpmVersion", "lockfileSha256", "command", "exitCode", "errorCount", "remediationOwner", "affectedPages", "lintResult", "buildResult", "browserRegressionEvidenceId"},
}
REQUIRED_P3_E09_FACTS = {
    "itemsSha256", "itemIdsSha256", "deferredItemCount", "mysql84DdlSha256",
    "independentReviewResult", "independentReviewRef", "approvedDdlSha256",
    *DDL_ARTIFACT_HASH_FIELDS,
}
REQUIRED_FACTS["P3-E09"] = REQUIRED_P3_E09_FACTS
EMPTY_ALLOWED_REQUIRED_FACTS = {"P3-E09": {"approvedDdlSha256"}}
VALID_STATUS = {"DRAFT", "EVIDENCE_SUBMITTED", "VERIFIED", "REJECTED"}
SECRET_NAME = re.compile(r"(?:password|passwd|secretValue|privateKey|tokenValue|connectionString)$", re.I)
DIRECTION_DECISIONS = {
    "P3-E01": "A", "P3-E02": "A", "P3-E03": "A", "P3-E04": "A",
    "P3-E05": "A", "P3-E06": "A", "P3-E07": "B", "P3-E09": "A",
}
RECOVERY_OBJECTIVES = {"approvedRpo": "PT1H", "approvedRto": "PT4H"}
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


def nonempty(value: object) -> bool:
    return value is not None and value != "" and value != [] and value != {}


def repository_root(path: Path) -> Path | None:
    for candidate in (path.resolve().parent, *path.resolve().parents):
        if (candidate / "docs/traceability/core-migration-schema-contract.json").is_file():
            return candidate
    return None


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read evidence submission: {exc}"]
    expected_schema = 2 if payload.get("id") == "P3-E09" else 1
    if payload.get("schemaVersion") != expected_schema:
        errors.append("unsupported evidence submission schemaVersion")
    identifier = payload.get("id")
    if identifier not in REQUIRED_FACTS:
        return errors + [f"unsupported evidence id: {identifier}"]
    status = payload.get("status")
    if status not in VALID_STATUS:
        errors.append(f"invalid submission status: {status}")
    facts = payload.get("confirmedFacts")
    if not isinstance(facts, dict):
        return errors + ["confirmedFacts must be an object"]
    secret_fields = sorted(key for key in facts if SECRET_NAME.search(key))
    if secret_fields:
        errors.append(f"secret values/connection strings must not be embedded: {secret_fields}")
    if status in {"EVIDENCE_SUBMITTED", "VERIFIED"}:
        empty_allowed = EMPTY_ALLOWED_REQUIRED_FACTS.get(identifier, set())
        missing = sorted(
            key for key in REQUIRED_FACTS[identifier]
            if key not in facts or (key not in empty_allowed and not nonempty(facts[key]))
        )
        if missing:
            errors.append(f"{identifier} missing confirmed facts: {missing}")
        if not nonempty(payload.get("decisionOwner")) or not nonempty(payload.get("evidenceRefs")):
            errors.append(f"{identifier} submitted evidence requires decisionOwner and evidenceRefs")
        expected_direction = DIRECTION_DECISIONS.get(identifier)
        if expected_direction and (facts.get("directionDecision") != expected_direction or facts.get("directionStatus") != "ACCEPTED"):
            errors.append(f"{identifier} submitted evidence must retain ADR-0004 direction {expected_direction}/ACCEPTED")
        if identifier == "P3-E03" and any(facts.get(field) != value for field, value in RECOVERY_OBJECTIVES.items()):
            errors.append("P3-E03 submitted evidence must retain ADR-0005 recovery objectives PT1H/PT4H")
        if identifier == "P3-E03" and facts.get("retention") != BACKUP_RETENTION_POLICY:
            errors.append("P3-E03 submitted evidence must retain ADR-0012 backup retention policy")
        if identifier == "P3-E03" and facts.get("recoveryTopology") != RECOVERY_TOPOLOGY:
            errors.append("P3-E03 submitted evidence must retain ADR-0013 recovery topology")
        if identifier == "P3-E03" and facts.get("exercisePolicy") != RECOVERY_EXERCISE_POLICY:
            errors.append("P3-E03 submitted evidence must retain ADR-0015 recovery exercise frequency")
        if identifier == "P3-E03" and facts.get("switchAuthorization") != RECOVERY_SWITCH_AUTHORIZATION:
            errors.append("P3-E03 submitted evidence must retain ADR-0017 recovery switch authorization")
        if identifier == "P3-E05" and facts.get("retentionPolicy") != PERMANENT_AUDIT_POLICY:
            errors.append("P3-E05 submitted evidence must retain ADR-0006 PERMANENT_NON_DELETABLE audit policy")
        if identifier == "P3-E05" and facts.get("samplingPolicy") != TRACE_SAMPLING_POLICY:
            errors.append("P3-E05 submitted evidence must retain ADR-0011 trace sampling policy")
        if identifier == "P3-E05" and facts.get("exportAuthorizationPolicy") != EXPORT_AUTHORIZATION_POLICY:
            errors.append("P3-E05 submitted evidence must retain ADR-0014 export authorization policy")
        if identifier == "P3-E09" and facts.get("approvedDdlSha256") not in (None, ""):
            errors.append("P3-E09 approvedDdlSha256 must remain empty for the SDS model baseline")
    if status == "VERIFIED":
        if not nonempty(payload.get("reviewOwner")) or payload.get("verificationResult") != "PASS":
            errors.append(f"{identifier} VERIFIED requires reviewOwner and verificationResult=PASS")
        if identifier == "P3-E08" and (facts.get("exitCode") != 0 or facts.get("errorCount") != 0):
            errors.append("P3-E08 cannot be VERIFIED until ts:check exitCode=0 and errorCount=0")
        if identifier == "P3-E09":
            root = repository_root(path)
            if root is None:
                errors.append("P3-E09 VERIFIED submission must be stored in the project repository")
            else:
                register_path = root / "specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json"
                try:
                    register = json.loads(register_path.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError) as exc:
                    errors.append(f"cannot read P3-E09 DDL register: {exc}")
                else:
                    errors.extend(validate_model_baseline(
                        register,
                        {**facts, "decisionOwner": payload.get("decisionOwner"), "reviewOwner": payload.get("reviewOwner"), "evidenceRefs": payload.get("evidenceRefs")},
                        root=root,
                    ))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("submission", type=Path)
    args = parser.parse_args()
    errors = validate(args.submission)
    if errors:
        for error in errors:
            print(f"[FAIL] {error}")
        return 1
    print(f"[PASS] Phase 3 evidence submission: {args.submission}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
