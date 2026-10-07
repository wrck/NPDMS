"""Offline Struts PMS archive. No network/DB client, no project lifecycle writes.
JSONL input contains one project bundle per line; see the accompanying runbook.
Dry run is the default. --apply writes only an append-only local SQLite archive.
"""
from contextlib import closing
import argparse
import hashlib
import json
import sqlite3
from pathlib import Path
from datetime import datetime, timezone

SCHEMA = 1
FIELDS = {
    "header": set("projectId projectCode projectName projectState isback compId customerProjectName majorProjectLevel salesType projectStartTime projectRefreshTime projectCloseTime createTime createBy updateTime updateBy effectiveFrom effectiveTo".split()) | {f"column{i:03}" for i in range(1, 15)},
    "members": set("id projectId projectType memberRole memberCode memberName fromFlag createTime createBy updateTime updateBy effectiveFrom effectiveTo dataState".split()),
    "groups": set("id projectGroupCode projectCode smsProjectCode createTime createBy".split()),
    "contracts": set("id contractNo projectGroupCode createTime createBy".split()),
    "products": set("id projectId contractNo itemCode itemName model projectQuantity orderQuantity deliverQuantity openQuantity orderNumber lineNum".split()),
}
TABLES = {"header": "pm_project_header", "members": "pm_project_member",
          "groups": "pm_project_group_relationship", "contracts": "pm_project_contract",
          "products": "pm_project_product_line"}


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


def digest(value):
    return hashlib.sha256(value).hexdigest()


def now():
    return datetime.now(timezone.utc).isoformat()


def initialize(db):
    # Reject an existing unknown version before DDL, metadata inserts or commits.
    metadata = db.execute("SELECT type FROM sqlite_master WHERE name='archive_metadata' COLLATE NOCASE").fetchone()
    if metadata and (metadata[0] != "table" or
                     db.execute("SELECT schema_version FROM archive_metadata").fetchall() != [(SCHEMA,)]):
        raise ValueError("UNSUPPORTED_ARCHIVE_SCHEMA")
    db.executescript("""
    PRAGMA foreign_keys=ON;
    CREATE TABLE IF NOT EXISTS archive_metadata(schema_version INTEGER PRIMARY KEY);
    INSERT OR IGNORE INTO archive_metadata VALUES (1);
    CREATE TABLE IF NOT EXISTS archive_batch(
      batch_id TEXT PRIMARY KEY, tenant_id INTEGER NOT NULL, source_system TEXT NOT NULL,
      input_sha256 TEXT NOT NULL, input_rows INTEGER NOT NULL, imported_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS archive_project(
      archive_key TEXT PRIMARY KEY, tenant_id INTEGER NOT NULL, source_system TEXT NOT NULL,
      source_pk TEXT NOT NULL, checksum TEXT NOT NULL, payload_json TEXT NOT NULL,
      UNIQUE(tenant_id, source_system, source_pk));
    CREATE TABLE IF NOT EXISTS archive_item(
      batch_id TEXT NOT NULL REFERENCES archive_batch(batch_id), line_no INTEGER NOT NULL,
      archive_key TEXT REFERENCES archive_project(archive_key), disposition TEXT NOT NULL,
      input_sha256 TEXT NOT NULL, issue_code TEXT,
      PRIMARY KEY(batch_id,line_no));
    CREATE TABLE IF NOT EXISTS archive_withdrawal(
      batch_id TEXT PRIMARY KEY REFERENCES archive_batch(batch_id), reason TEXT NOT NULL,
      withdrawn_at TEXT NOT NULL);
    CREATE VIEW IF NOT EXISTS active_archive_project AS
      SELECT p.* FROM archive_project p WHERE EXISTS (
        SELECT 1 FROM archive_item i WHERE i.archive_key=p.archive_key
        AND NOT EXISTS (SELECT 1 FROM archive_withdrawal w WHERE w.batch_id=i.batch_id));
    """)
    for table in ("archive_metadata", "archive_batch", "archive_project", "archive_item", "archive_withdrawal"):
        # All identifiers here are internal constants, never input data.
        for operation in ("UPDATE", "DELETE"):
            db.execute(f"CREATE TRIGGER IF NOT EXISTS immutable_{table}_{operation} BEFORE {operation} ON {table} BEGIN SELECT RAISE(ABORT,'IMMUTABLE_ARCHIVE'); END")
    db.commit()
    if db.execute("SELECT schema_version FROM archive_metadata").fetchall() != [(SCHEMA,)]:
        raise ValueError("UNSUPPORTED_ARCHIVE_SCHEMA")


def has_identity(value):
    return type(value) in (str, int) and str(value).strip().casefold() not in {
        "", "none", "null", "undefined", "nan"
    }


def validate(bundle, tenant, system):
    if not isinstance(bundle, dict) or set(bundle) != {"tenantId", "sourceSystem", "sourcePk", *FIELDS}:
        raise ValueError("BUNDLE_FIELDS_INVALID")
    if type(bundle["tenantId"]) is not int or bundle["tenantId"] != tenant or bundle["sourceSystem"] != system:
        raise ValueError("SOURCE_SCOPE_MISMATCH")
    if not isinstance(bundle["sourcePk"], str) or not has_identity(bundle["sourcePk"]):
        raise ValueError("SOURCE_PK_REQUIRED")
    header = bundle["header"]
    if (not isinstance(header, dict) or not has_identity(header.get("projectId"))
            or str(header["projectId"]) != bundle["sourcePk"]):
        raise ValueError("HEADER_ID_MISMATCH")
    if not header.get("projectCode") or header.get("projectState") is None:
        raise ValueError("HEADER_IDENTITY_OR_STATE_MISSING")
    for section, allowed in FIELDS.items():
        rows = [bundle[section]] if section == "header" else bundle[section]
        if not isinstance(rows, list):
            raise ValueError("RELATION_LIST_REQUIRED")
        identities = set()
        for row in rows:
            if not isinstance(row, dict) or set(row) - allowed:
                raise ValueError("UNMAPPED_OR_SENSITIVE_FIELD")
            if any(isinstance(v, (dict, list)) for v in row.values()):
                raise ValueError("SCALAR_FIELDS_REQUIRED")
            if section in ("members", "products") and str(row.get("projectId")) != bundle["sourcePk"]:
                raise ValueError("ORPHAN_PROJECT_RELATION")
            if section == "groups" and row.get("projectCode") != header["projectCode"]:
                raise ValueError("ORPHAN_GROUP_RELATION")
            identity = row.get("id") if section != "header" else header["projectId"]
            if not has_identity(identity) or str(identity) in identities:
                raise ValueError("RELATION_ID_MISSING_OR_DUPLICATED")
            identities.add(str(identity))
    groups = {r.get("projectGroupCode") for r in bundle["groups"]}
    for row in bundle["contracts"]:
        if not row.get("contractNo") or not row.get("projectGroupCode") or row["projectGroupCode"] not in groups:
            raise ValueError("ORPHAN_CONTRACT_RELATION")
    contract_numbers = {r["contractNo"] for r in bundle["contracts"]}
    if any(row.get("contractNo") not in contract_numbers for row in bundle["products"]):
        raise ValueError("ORPHAN_PRODUCT_CONTRACT")
    # Canonical ordering makes relation export order irrelevant to idempotency.
    return {**bundle, **{key: sorted(bundle[key], key=lambda row: str(row["id"]))
                         for key in FIELDS if key != "header"}}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def import_bytes(db, content, tenant, system):
    if type(tenant) is not int or tenant <= 0 or system != "PMS_STRUTS":
        raise ValueError("EXPLICIT_ARCHIVE_SCOPE_REQUIRED")
    lines = content.decode("utf-8-sig").splitlines()
    batch = digest(f"{tenant}|{system}|{digest(content)}".encode())
    if db.execute("SELECT 1 FROM archive_batch WHERE batch_id=?", (batch,)).fetchone():
        return report(db, batch, True)
    # One transaction per export; bad bundles are isolated, not partially imported.
    with db:
        db.execute("INSERT INTO archive_batch VALUES(?,?,?,?,?,?)", (batch, tenant, system, digest(content), len(lines), now()))
        for number, line in enumerate(lines, 1):
            checksum = digest(line.encode())
            archive_key = None
            disposition, issue = "RETAINED", None
            try:
                value = json.loads(line, object_pairs_hook=unique_object, parse_constant=lambda _: (_ for _ in ()).throw(ValueError("NON_FINITE_NUMBER")))
                value = validate(value, tenant, system)
                payload = canonical(value)
                source_checksum = digest(payload.encode())
                archive_key = digest(canonical([tenant, system, "pm_project_header", value["sourcePk"]]).encode())
                existing = db.execute("SELECT checksum FROM archive_project WHERE archive_key=?", (archive_key,)).fetchone()
                if existing and existing[0] != source_checksum:
                    raise ValueError("SOURCE_IDENTITY_CONTENT_CONFLICT")
                if existing:
                    disposition = "REPLAY"
                else:
                    db.execute("INSERT INTO archive_project VALUES(?,?,?,?,?,?)",
                               (archive_key, tenant, system, value["sourcePk"], source_checksum, payload))
            except (ValueError, TypeError) as error:
                disposition, archive_key = "QUARANTINED", None
                issue = "INVALID_JSON" if isinstance(error, json.JSONDecodeError) else str(error)
            # Quarantine stores only line number/hash/code; never raw unknown fields/secrets.
            db.execute("INSERT INTO archive_item VALUES(?,?,?,?,?,?)",
                       (batch, number, archive_key, disposition, checksum, issue))
    return report(db, batch, False)


def report(db, batch, replay):
    counts = dict(db.execute("SELECT disposition,COUNT(*) FROM archive_item WHERE batch_id=? GROUP BY disposition", (batch,)))
    expected = db.execute("SELECT input_rows FROM archive_batch WHERE batch_id=?", (batch,)).fetchone()[0]
    return {"batchId": batch, "batchReplay": replay, "inputRows": expected,
            "retained": counts.get("RETAINED", 0), "replayed": counts.get("REPLAY", 0),
            "quarantined": counts.get("QUARANTINED", 0), "reconciled": sum(counts.values()) == expected,
            "withdrawn": bool(db.execute("SELECT 1 FROM archive_withdrawal WHERE batch_id=?", (batch,)).fetchone()),
            "runtimeProjectsCreated": 0,
            "issues": [{"line": n, "code": code} for n, code in db.execute(
                "SELECT line_no,issue_code FROM archive_item WHERE batch_id=? AND issue_code IS NOT NULL ORDER BY line_no", (batch,))]}


def withdraw(db, batch, reason):
    if not reason.strip() or len(reason) > 300:
        raise ValueError("WITHDRAWAL_REASON_REQUIRED_MAX_300")
    with db:
        if not db.execute("SELECT 1 FROM archive_batch WHERE batch_id=?", (batch,)).fetchone():
            raise ValueError("BATCH_NOT_FOUND")
        existing = db.execute("SELECT reason FROM archive_withdrawal WHERE batch_id=?", (batch,)).fetchone()
        if existing and existing[0] != reason:
            raise ValueError("WITHDRAWAL_IDEMPOTENCY_CONFLICT")
        if not existing:
            db.execute("INSERT INTO archive_withdrawal VALUES(?,?,?)", (batch, reason, now()))
    return report(db, batch, True)


def connect(path, apply):
    path = Path(path).resolve()
    if path.suffix != ".sqlite":
        raise ValueError("LOCAL_SQLITE_FILE_REQUIRED")
    if apply:
        db = sqlite3.connect(path)
    else:
        db = sqlite3.connect(":memory:")
        if path.exists():
            with closing(sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)) as source:
                source.backup(db)
    try:
        initialize(db)
    except Exception:
        db.close()
        raise
    return db


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["import", "withdraw", "list"])
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--tenant", type=int, required=True)
    parser.add_argument("--source-system", default="PMS_STRUTS", choices=["PMS_STRUTS"])
    parser.add_argument("--batch")
    parser.add_argument("--reason")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if args.tenant <= 0:
        parser.error("tenant must be positive")
    if args.action == "import" and not args.input:
        parser.error("import requires --input")
    if args.action == "withdraw" and not (args.batch and args.reason):
        parser.error("withdraw requires --batch and --reason")
    with closing(connect(args.archive, args.apply and args.action != "list")) as db:
        if args.action == "import":
            result = import_bytes(db, args.input.read_bytes(), args.tenant, args.source_system)
        elif args.action == "withdraw":
            if not db.execute("SELECT 1 FROM archive_batch WHERE batch_id=? AND tenant_id=? AND source_system=?",
                              (args.batch, args.tenant, args.source_system)).fetchone():
                raise ValueError("BATCH_SCOPE_MISMATCH")
            result = withdraw(db, args.batch, args.reason)
        else:
            result = [json.loads(row[0]) for row in db.execute(
                "SELECT payload_json FROM active_archive_project WHERE tenant_id=? AND source_system=? ORDER BY source_pk",
                (args.tenant, args.source_system))]
    print(json.dumps({"mode": "APPLY" if args.apply and args.action != "list" else "READ_ONLY_OR_DRY_RUN", "result": result}, ensure_ascii=False, indent=2))
    return 2 if isinstance(result, dict) and result.get("quarantined") else 0


if __name__ == "__main__":
    raise SystemExit(main())
