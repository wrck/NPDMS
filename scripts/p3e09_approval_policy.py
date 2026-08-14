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
FORMAL_REVIEW_FIELDS = (
    "status", "conclusion", "ddlSha256", "itemsSha256",
    "itemCount", "deferCount", "testResult",
)
FORMAL_REVIEW_FIELD = re.compile(
    r"(?mi)^\s*>?\s*(status|conclusion|ddlSha256|itemsSha256|itemCount|deferCount|testResult)\s*[：:]\s*`?([^`\r\n<]+?)`?\s*(?:<br>)?\s*$"
)
FORMAL_REVIEW_CONTRADICTION = re.compile(r"(?mi)\bIN_REVIEW\b|\bPENDING(?:_[A-Z_]+)?\b|不是\s*`?GO`?|不得[^\n]*GO")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def item_ids_sha256(items: list[dict[str, object]]) -> str:
    identifiers = sorted(str(item.get("itemId")) for item in items)
    canonical = json.dumps(identifiers, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(canonical)


def canonical_items_sha256(items: list[dict[str, object]]) -> str:
    canonical = json.dumps(items, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(canonical)


def _has_explicit_independent_go(review_text: str) -> bool:
    """Accept only an explicit GO conclusion, never a NO-GO substring."""
    if FORMAL_NO_GO_TOKEN.search(review_text):
        return False
    return bool(FORMAL_GO_CONCLUSION.search(review_text)) or formal_review_fields(review_text).get("conclusion") == "GO"


def formal_review_field_values(review_text: str) -> dict[str, list[str]]:
    """Read every occurrence so duplicate fixed fields cannot be overwritten."""
    values = {field: [] for field in FORMAL_REVIEW_FIELDS}
    for name, value in FORMAL_REVIEW_FIELD.findall(review_text):
        values[name].append(value.strip())
    return values


def formal_review_fields(review_text: str) -> dict[str, str]:
    """Read the fixed, machine-checkable fields from a formal review record."""
    return {
        field: values[0]
        for field, values in formal_review_field_values(review_text).items()
        if len(values) == 1
    }


def formal_review_errors(
    review_text: str,
    evidence: dict[str, object],
    register: dict[str, object],
) -> list[str]:
    """Ensure an APPROVED/GO review binds exactly to current model facts."""
    field_values = formal_review_field_values(review_text)
    fields = formal_review_fields(review_text)
    errors: list[str] = []
    for field in FORMAL_REVIEW_FIELDS:
        if len(field_values[field]) != 1:
            errors.append(f"P3-E09 independent review fixed field must appear exactly once: {field}")
        elif not fields.get(field):
            errors.append(f"P3-E09 independent review missing fixed field: {field}")
    expected = {
        "status": "APPROVED",
        "conclusion": "GO",
        "ddlSha256": str(register.get("currentDdlSha256", "")),
        "itemsSha256": str(register.get("itemsSha256", "")),
        "itemCount": str(len(register.get("items", []))),
        "deferCount": "0",
        "testResult": "PASS",
    }
    for field, value in expected.items():
        if not value:
            errors.append(f"P3-E09 model facts missing independent review field: {field}")
        elif fields.get(field) != value:
            errors.append(f"P3-E09 independent review {field} mismatch")
    if fields.get("conclusion") != evidence.get("independentReviewResult"):
        errors.append("P3-E09 independent review conclusion must match independentReviewResult")
    if fields.get("testResult") != "PASS":
        errors.append("P3-E09 independent review testResult must be PASS")
    if FORMAL_REVIEW_CONTRADICTION.search(review_text):
        errors.append("P3-E09 independent review contains pending or non-GO contradiction")
    return errors


def model_baseline_review_status(
    root: Path,
    register: dict[str, object],
    isolated_mysql_status: object,
) -> tuple[str, dict[str, str]]:
    """Derive READY only from the one formal independent-review record."""
    review_ref = "docs/engineering/gates/phase-3/independent-review.md"
    review_path = root / review_ref
    if not review_path.is_file():
        return "MODEL_BASELINE_REVIEW_PENDING", {}
    review_text = review_path.read_text(encoding="utf-8")
    fields = formal_review_fields(review_text)
    if fields.get("status") != "APPROVED" or fields.get("conclusion") != "GO":
        return "MODEL_BASELINE_REVIEW_PENDING", fields
    evidence = {
        "independentReviewResult": "GO",
        "isolatedMysqlExecution": {"status": isolated_mysql_status},
    }
    errors = formal_review_errors(review_text, evidence, register)
    if not _has_explicit_independent_go(review_text):
        errors.append("P3-E09 independent review reference must record an independent GO conclusion")
    if errors:
        raise ValueError("P3-E09 formal independent review GO is invalid: " + "; ".join(errors))
    return "MODEL_BASELINE_READY", fields


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
    if isinstance(items, list) and items_sha != canonical_items_sha256(items):
        errors.append("P3-E09 decision register itemsSha256 must match canonical items")
    if evidence.get("itemIdsSha256") != item_ids_sha256(items):
        errors.append("P3-E09 item ID hash mismatch")

    deferred_count = sum(item.get("decision") == "DEFER" for item in items)
    if deferred_count != 0 or evidence.get("deferredItemCount") != 0:
        errors.append("P3-E09 model baseline requires DEFER=0")
    if evidence.get("mysql84DdlSha256") != current_ddl:
        errors.append("P3-E09 MySQL 8.4 evidence must bind the current DDL hash")
    isolated_mysql = evidence.get("isolatedMysqlExecution")
    if not isinstance(isolated_mysql, dict) or isolated_mysql.get("status") != "PASS":
        errors.append("P3-E09 isolatedMysqlExecution.status must be PASS")
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
                errors.extend(formal_review_errors(review_text, evidence, register))
    if "approvedDdlSha256" in evidence:
        errors.append("legacy migration approval field is not allowed in P3-E09 model baseline")
    return errors
