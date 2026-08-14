#!/usr/bin/env python3
"""Model-fact validation for the P3-E09 SDS baseline gate."""

from __future__ import annotations

import hashlib
import json


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest().upper()


def item_ids_sha256(items: list[dict[str, object]]) -> str:
    identifiers = sorted(str(item.get("itemId")) for item in items)
    canonical = json.dumps(identifiers, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return sha256_bytes(canonical)


def validate_model_baseline(
    register: dict[str, object],
    evidence: dict[str, object],
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
    if evidence.get("approvedDdlSha256") not in (None, ""):
        errors.append("P3-E09 approvedDdlSha256 must remain empty for the SDS model baseline")
    return errors
