#!/usr/bin/env python3
"""Validate owner-submitted Phase 3 production evidence before register promotion."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


REQUIRED_FACTS = {
    "P3-E01": {"environmentId", "entryGateway", "domainRef", "tlsTermination", "certificateOwner", "networkZones", "applicationNodes", "dataNodes", "allowedFlows", "objectStorage", "dacExecutionZone", "operationsOwner", "topologyVersion"},
    "P3-E02": {"serviceMode", "mysqlVersion", "redisVersion", "haMode", "nodeSpecs", "storageCapacity", "connectionLimits", "redisPersistence", "redisEvictionPolicy", "monitoringOwner", "failoverEvidenceId", "capacityEvidenceId"},
    "P3-E03": {"approvedRpo", "approvedRto", "backupMedia", "backupFrequency", "retention", "encryption", "restoreOrder", "exerciseOwner", "exerciseEvidenceId", "actualRpo", "actualRto", "businessProbeResult"},
    "P3-E04": {"keyFacility", "algorithmMode", "masterDataKeySeparation", "keyVersioning", "accessIdentities", "auditInterface", "revocationProcedure", "rotationOwner", "incidentOwner", "rotationExerciseId", "secretScanResult"},
    "P3-E05": {"logBackend", "metricBackend", "traceBackend", "alertBackend", "securityEventBackend", "collectionProtocol", "accessRoles", "redactionPolicy", "retentionPolicy", "samplingPolicy", "dashboardRefs", "alertRefs", "runbookRefs", "timeSync", "telemetryOwner", "testTriggerEvidenceId"},
    "P3-E06": {"environmentTopology", "nodeSpecs", "productionDifferences", "scalingModel", "networkConditions", "dataSetVersion", "migrationVolume", "accountDistribution", "externalDependencyMode", "loadScriptSha256", "monitoringRefs", "cleanupProcedure", "testOwner"},
    "P3-E07": {"requirementIds", "featureId", "externalSystem", "systemOwner", "direction", "endpointRef", "authenticationRef", "networkAllowlistRef", "mappingVersion", "sourceKey", "idempotencyKey", "timeoutMs", "retryPolicy", "compensation", "reconciliation", "degradation", "sandboxEvidenceId", "releaseApprovalId"},
    "P3-E08": {"implementationCommit", "nodeVersion", "pnpmVersion", "lockfileSha256", "command", "exitCode", "errorCount", "remediationOwner", "affectedPages", "lintResult", "buildResult", "browserRegressionEvidenceId"},
    "P3-E09": {"dataElementExcelSha256", "sourceSchemaSha256", "sourceWatermark", "currentDdlSha256", "driftDecisionRegister", "approvedDdlSha256", "targetCatalogDdlSha256", "mappingDdlSha256", "validationDdlSha256", "manifestDdlSha256", "generatorVersion", "releaseManifestId", "migrationOwner", "verificationResult"},
}
VALID_STATUS = {"DRAFT", "EVIDENCE_SUBMITTED", "VERIFIED", "REJECTED"}
SECRET_NAME = re.compile(r"(?:password|passwd|secretValue|privateKey|tokenValue|connectionString)$", re.I)
DIRECTION_DECISIONS = {
    "P3-E01": "A", "P3-E02": "A", "P3-E03": "A", "P3-E04": "A",
    "P3-E05": "A", "P3-E06": "A", "P3-E07": "B", "P3-E09": "A",
}


def nonempty(value: object) -> bool:
    return value is not None and value != "" and value != [] and value != {}


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read evidence submission: {exc}"]
    if payload.get("schemaVersion") != 1:
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
        missing = sorted(key for key in REQUIRED_FACTS[identifier] if not nonempty(facts.get(key)))
        if missing:
            errors.append(f"{identifier} missing confirmed facts: {missing}")
        if not nonempty(payload.get("decisionOwner")) or not nonempty(payload.get("evidenceRefs")):
            errors.append(f"{identifier} submitted evidence requires decisionOwner and evidenceRefs")
        expected_direction = DIRECTION_DECISIONS.get(identifier)
        if expected_direction and (facts.get("directionDecision") != expected_direction or facts.get("directionStatus") != "ACCEPTED"):
            errors.append(f"{identifier} submitted evidence must retain ADR-0004 direction {expected_direction}/ACCEPTED")
    if status == "VERIFIED":
        if not nonempty(payload.get("reviewOwner")) or payload.get("verificationResult") != "PASS":
            errors.append(f"{identifier} VERIFIED requires reviewOwner and verificationResult=PASS")
        if identifier == "P3-E08" and (facts.get("exitCode") != 0 or facts.get("errorCount") != 0):
            errors.append("P3-E08 cannot be VERIFIED until ts:check exitCode=0 and errorCount=0")
        if identifier == "P3-E09":
            approved = facts.get("approvedDdlSha256")
            hash_fields = ("currentDdlSha256", "targetCatalogDdlSha256", "mappingDdlSha256", "validationDdlSha256", "manifestDdlSha256")
            if not approved or any(facts.get(field) != approved for field in hash_fields):
                errors.append("P3-E09 VERIFIED requires all DDL-bound artifacts to reference approvedDdlSha256")
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
