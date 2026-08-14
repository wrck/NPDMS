#!/usr/bin/env python3
"""Model-fact validation for the P3-E09 SDS baseline gate."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re


DDL_ARTIFACT_HASH_FIELDS = {
    "currentDdlSha256",
    "targetCatalogDdlSha256",
    "mappingDdlSha256",
    "validationDdlSha256",
    "manifestDdlSha256",
}
FORMAL_REVIEW_PREFIXES = ("docs/engineering/gates/", "docs/decisions/")
FORMAL_GO_CONCLUSION = re.compile(r"(?mi)^\s*(?:独立复审\s*)?结论\s*[：:]\s*GO\s*$")
FORMAL_NO_GO_TOKEN = re.compile(r"(?i)(?<![A-Z])NO[-_ ]?GO(?![A-Z])")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def item_ids_sha256(items: list[dict[str, object]]) -> str:
    identifiers = sorted(str(item.get("itemId")) for item in items)
    canonical = json.dumps(identifiers, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(canonical)


def _has_explicit_independent_go(review_text: str) -> bool:
    """Accept only an explicit GO conclusion, never a NO-GO substring."""
    if FORMAL_NO_GO_TOKEN.search(review_text):
        return False
    return bool(FORMAL_GO_CONCLUSION.search(review_text))


def _is_within(path: Path, directory: Path) -> bool:
    try:
        path.relative_to(directory)
    except ValueError:
        return False
    return True


def validate_model_baseline(
    register: dict[str, object],
    evidence: dict[str, object],
    *,
    root: Path | None = None,
) -> list[str]:
    """Validate SDS model facts without creating a migration approval path."""
    errors: list[str] = []
    items = register.get("items")
    if not isinstance(items, list) or not all(isinstance(item, dict) for item in items):
        return ["P3-E09 model baseline requires DDL decision register items"]

    current_ddl = register.get("currentDdlSha256")
    items_sha = register.get("itemsSha256")
    if not current_ddl or not items_sha:
        errors.append("P3-E09 model baseline requires current DDL and item hashes")
    if evidence.get("currentDdlSha256") != current_ddl:
        errors.append("P3-E09 current DDL hash mismatch")
    for field in sorted(DDL_ARTIFACT_HASH_FIELDS - {"currentDdlSha256"}):
        if evidence.get(field) != current_ddl:
            errors.append(f"P3-E09 {field} must bind the current DDL hash")
    if evidence.get("itemsSha256") != items_sha:
        errors.append("P3-E09 item hash mismatch")
    if evidence.get("itemIdsSha256") != item_ids_sha256(items):
        errors.append("P3-E09 item ID hash mismatch")

    deferred_count = sum(item.get("decision") == "DEFER" for item in items)
    if deferred_count != 0 or evidence.get("deferredItemCount") != 0:
        errors.append("P3-E09 model baseline requires DEFER=0")
    if evidence.get("mysql84DdlSha256") != current_ddl:
        errors.append("P3-E09 MySQL 8.4 evidence must bind the current DDL hash")
    if evidence.get("independentReviewResult") != "GO":
        errors.append("P3-E09 model baseline requires independentReviewResult=GO")
    decision_owner = evidence.get("decisionOwner")
    review_owner = evidence.get("reviewOwner")
    if not decision_owner or not review_owner:
        errors.append("P3-E09 model baseline requires decisionOwner and reviewOwner")
    elif decision_owner == review_owner:
        errors.append("P3-E09 decisionOwner and reviewOwner must differ")
    review_ref = evidence.get("independentReviewRef")
    evidence_refs = evidence.get("evidenceRefs")
    if not isinstance(review_ref, str) or not review_ref:
        errors.append("P3-E09 model baseline requires an independent review reference")
    elif not review_ref.startswith(FORMAL_REVIEW_PREFIXES):
        errors.append("P3-E09 independent review reference must be a formal gate or ADR")
    elif not isinstance(evidence_refs, list) or review_ref not in evidence_refs:
        errors.append("P3-E09 independent review reference must be listed in evidenceRefs")
    elif root is None:
        errors.append("P3-E09 independent review reference requires a repository root")
    else:
        root_path = root.resolve()
        review_path = (root_path / review_ref.split("#", 1)[0]).resolve()
        try:
            review_path.relative_to(root_path)
        except ValueError:
            errors.append("P3-E09 independent review reference escapes the repository")
        else:
            if not any(
                _is_within(review_path, (root_path / prefix).resolve())
                for prefix in FORMAL_REVIEW_PREFIXES
            ):
                errors.append("P3-E09 independent review reference must resolve inside a formal gate or ADR")
            elif not review_path.is_file():
                errors.append("P3-E09 independent review reference does not exist")
            else:
                review_text = review_path.read_text(encoding="utf-8")
                if not _has_explicit_independent_go(review_text):
                    errors.append("P3-E09 independent review reference must record an independent GO conclusion")
    if "approvedDdlSha256" not in evidence:
        errors.append("P3-E09 approvedDdlSha256 must be explicitly present and empty for the SDS model baseline")
    elif evidence["approvedDdlSha256"] not in (None, ""):
        errors.append("P3-E09 approvedDdlSha256 must remain empty for the SDS model baseline")
    return errors
