#!/usr/bin/env python3
"""Shared, evidence-backed approval policy for the P3-E09 data-model gate."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


APPROVAL_SUBMISSION_PREFIX = "docs/engineering/gates/phase-3/submissions/P3-E09/"
ATTESTATION_PREFIX = APPROVAL_SUBMISSION_PREFIX + "attestations/"
REQUIRED_OWNER_ROLES = {
    "DATA_ARCHITECTURE_OWNER",
    "BUSINESS_OWNER",
    "MIGRATION_OWNER",
    "INDEPENDENT_REVIEWER",
}
OWNER_ROLES = REQUIRED_OWNER_ROLES - {"INDEPENDENT_REVIEWER"}
ALLOWED_ATTESTATION_METHODS = {
    "APPROVAL_SYSTEM_RECORD",
    "GIT_SIGNED_COMMIT",
    "MANUAL_SIGNED_RECORD",
}


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def item_ids_sha256(items: list[dict[str, object]]) -> str:
    identifiers = sorted(str(item.get("itemId")) for item in items)
    canonical = json.dumps(identifiers, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(canonical)


def _nonempty(value: object) -> bool:
    return value is not None and value != "" and value != [] and value != {}


def _resolve_ref(root: Path, reference: object) -> Path | None:
    if not isinstance(reference, str) or not reference:
        return None
    target = reference.split("#", 1)[0]
    path = (root / target).resolve()
    try:
        path.relative_to(root.resolve())
    except ValueError:
        return None
    return path


def validate_approval_submission(
    root: Path,
    submission_ref: str,
    register: dict[str, object],
) -> tuple[list[str], dict[str, object] | None]:
    """Validate an immutable, versioned four-role sign-off submission."""
    errors: list[str] = []
    if not submission_ref.startswith(APPROVAL_SUBMISSION_PREFIX) or not submission_ref.endswith(".json"):
        return ["final approval requires a versioned P3-E09 approval submission"], None
    submission_path = _resolve_ref(root, submission_ref)
    if submission_path is None or not submission_path.is_file():
        return ["versioned P3-E09 approval submission does not exist"], None
    try:
        payload = json.loads(submission_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"cannot read P3-E09 approval submission: {exc}"], None

    if payload.get("schemaVersion") != 2 or payload.get("id") != "P3-E09":
        errors.append("P3-E09 approval submission schema/id mismatch")
    if payload.get("status") != "VERIFIED" or payload.get("verificationResult") != "PASS":
        errors.append("P3-E09 approval submission must be VERIFIED/PASS")
    if not _nonempty(payload.get("capturedAt")):
        errors.append("P3-E09 approval submission requires capturedAt")

    items = register.get("items", [])
    binding = payload.get("approvalBinding", {})
    expected_binding = {
        "currentDdlSha256": register.get("currentDdlSha256"),
        "itemsSha256": register.get("itemsSha256"),
        "itemCount": len(items),
        "itemIdsSha256": item_ids_sha256(items),
    }
    if binding != expected_binding:
        errors.append("P3-E09 approval submission binding mismatch")
    facts = payload.get("confirmedFacts", {})
    ddl_bound_fields = (
        "approvedDdlSha256", "currentDdlSha256", "targetCatalogDdlSha256",
        "mappingDdlSha256", "validationDdlSha256", "manifestDdlSha256",
    )
    if not isinstance(facts, dict) or any(
        facts.get(field) != register.get("currentDdlSha256") for field in ddl_bound_fields
    ):
        errors.append("P3-E09 confirmedFacts must bind every DDL artifact to the approved current DDL")

    signoffs = payload.get("signoffs")
    if not isinstance(signoffs, dict) or set(signoffs) != REQUIRED_OWNER_ROLES:
        errors.append("P3-E09 approval submission requires exact four-role signoffs")
        return errors, payload

    signer_ids: dict[str, str] = {}
    signoff_evidence_refs: dict[str, str] = {}
    signoff_evidence_hashes: dict[str, str] = {}
    for role in sorted(REQUIRED_OWNER_ROLES):
        signoff = signoffs.get(role, {})
        if not isinstance(signoff, dict) or signoff.get("status") != "APPROVED":
            errors.append(f"P3-E09 {role} signoff must be APPROVED")
            continue
        signer_id = signoff.get("signerId")
        evidence_ref = signoff.get("evidenceRef")
        evidence_path = _resolve_ref(root, evidence_ref)
        if not _nonempty(signer_id) or not _nonempty(signoff.get("signedAt")):
            errors.append(f"P3-E09 {role} signoff requires signerId and signedAt")
        else:
            signer_ids[role] = str(signer_id)
        if signoff.get("attestationMethod") not in ALLOWED_ATTESTATION_METHODS:
            errors.append(f"P3-E09 {role} signoff has invalid attestationMethod")
        if (
            not isinstance(evidence_ref, str)
            or not evidence_ref.startswith(ATTESTATION_PREFIX)
            or evidence_path is None
            or not evidence_path.is_file()
            or evidence_path == submission_path
        ):
            errors.append(f"P3-E09 {role} signoff evidence must be an independent existing file")
        elif signoff.get("evidenceSha256") != sha256_bytes(evidence_path.read_bytes()):
            errors.append(f"P3-E09 {role} signoff evidence hash mismatch")
        else:
            signoff_evidence_refs[role] = str(evidence_ref)
            signoff_evidence_hashes[role] = str(signoff.get("evidenceSha256"))

    reviewer = signer_ids.get("INDEPENDENT_REVIEWER")
    owner_signers = {signer_ids.get(role) for role in OWNER_ROLES}
    if reviewer and reviewer in owner_signers:
        errors.append("P3-E09 independent reviewer must be distinct from decision owners")
    if len({value for value in signer_ids.values() if value}) != len(signer_ids):
        errors.append("P3-E09 signoff roles require distinct signer identities")
    if len(set(signoff_evidence_refs.values())) != len(signoff_evidence_refs):
        errors.append("P3-E09 signoff roles require distinct attestation evidence files")
    if len(set(signoff_evidence_hashes.values())) != len(signoff_evidence_hashes):
        errors.append("P3-E09 signoff roles require independently signed attestation contents")
    if payload.get("reviewOwner") != reviewer:
        errors.append("P3-E09 approval submission reviewOwner mismatch")
    return errors, payload


def validate_register_approval(
    root: Path,
    register: dict[str, object],
    approved_count: int,
) -> list[str]:
    approval = register.get("approval", {})
    if not isinstance(approval, dict) or not _nonempty(approval.get("approvedDdlSha256")):
        return []
    errors: list[str] = []
    refs = approval.get("evidenceRefs", [])
    submission_refs = [ref for ref in refs if isinstance(ref, str) and ref.startswith(APPROVAL_SUBMISSION_PREFIX)]
    if len(submission_refs) != 1:
        return ["final approval requires exactly one versioned P3-E09 approval submission"]
    submission_errors, payload = validate_approval_submission(root, submission_refs[0], register)
    errors.extend(submission_errors)
    if payload is None:
        return errors
    signoffs = payload.get("signoffs", {})
    reviewer = signoffs.get("INDEPENDENT_REVIEWER", {}).get("signerId")
    items = register.get("items", [])
    if approved_count != len(items) or any(item.get("reviewOwner") != reviewer for item in items):
        errors.append("all DDL decision items must be signed by the registered independent reviewer")
    expected_approval = {
        "approvedDdlSha256": register.get("currentDdlSha256"),
        "itemsSha256": register.get("itemsSha256"),
        "decisionOwner": "REQUIREMENT_OWNER",
        "reviewOwner": reviewer,
        "signedAt": payload.get("capturedAt"),
        "evidenceRefs": refs,
    }
    if approval != expected_approval:
        errors.append("DDL register approval must be derived from the P3-E09 approval submission")
    return errors
