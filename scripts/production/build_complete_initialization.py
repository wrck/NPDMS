"""Package the verified B531 plus explicitly reviewed, locked-source DDL gaps.

Does not replay the historical migration directory, import source business rows,
change existing release files, or connect to a database.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

from release import inventory, verify


SOURCES = {
    "revision": "V399__requirement_revision_active_identity_uniqueness.sql",
    "evidence": "V411__project_legacy_detail_evidence.sql",
    "fields": "V412__dppms_native_detail_field_completion.sql",
    "warranty": "V414__dppms_native_source_business_records.sql",
    "identity": "V435__migration_global_source_identity_index.sql",
    "contacts": "V436__approved_final_customer_project_contacts.sql",
    "auditors": "V438__preserve_project_contract_auditor_comments.sql",
    "scope": "V482__sync_published_project_scope.sql",
}

NEW_TABLES = {
    "evidence": ["proj_legacy_detail_snapshot", "proj_legacy_detail_record"],
    "warranty": ["ast_device_warranty_service_record", "ast_device_warranty_registration",
                 "ast_device_warranty_change_record", "ast_device_warranty_fact_project"],
    "scope": ["int_sync_project_scope_publication", "int_sync_project_scope_member"],
}


def statement(source, verb, name):
    pattern = r"^" + verb + r"\s+TABLE\s+(?:IF NOT EXISTS\s+)?`?" + re.escape(name) + r"`?\s+.*?;"
    matches = re.findall(pattern, source, re.M | re.S)
    if len(matches) != 1:
        raise ValueError("Expected exactly one source statement for " + name)
    return matches[0]


def render(sources):
    parts = ["-- B531 companion, for the new production initialization package only.",
             "-- Approved source DDL only: no historical replay, business updates or seed replacement.",
             "SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;"]
    for group, names in NEW_TABLES.items():
        for name in names:
            sql = statement(sources[group], "CREATE", name)
            # New security checksums follow the approved all-utf8mb4 policy;
            # binary comparison remains binary. No literal/value rewriting.
            sql = sql.replace("CHARACTER SET ascii COLLATE ascii_bin", "CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
            parts += ["-- Source: " + SOURCES[group], sql]
    for group, names in [
        ("fields", ["ast_device_warranty_record", "ast_device_network_version_event"]),
        ("contacts", ["cus_project_customer_contact_relation"]),
        ("auditors", ["com_project_contract_relation"]),
        ("revision", ["sol_requirement_analysis_revision"]),
        ("identity", ["plt_migration_source_record"]),
    ]:
        for name in names:
            parts += ["-- Source: " + SOURCES[group], statement(sources[group], "ALTER", name)]
    return "\n\n".join(parts) + "\n"


def build(root, base, output, commit):
    if output.exists():
        raise ValueError("Never overwrite a frozen release")
    commit = subprocess.check_output(["git", "rev-parse", commit + "^{commit}"], cwd=root, text=True).strip()
    verify(base / "migrations", "migrations")
    parent = json.loads((base / "source-manifest.json").read_text(encoding="utf8"))
    baseline = base / "migrations" / parent["migrationFile"]
    if hashlib.sha256(baseline.read_bytes()).hexdigest() != parent["migrationSha256"]:
        raise ValueError("Parent baseline checksum mismatch")
    sources, provenance = {}, {}
    for key, filename in SOURCES.items():
        path = "sql/migrations/" + filename
        data = subprocess.check_output(["git", "show", commit + ":" + path], cwd=root)
        sources[key] = data.decode("utf8")
        provenance[path] = hashlib.sha256(data).hexdigest()
    sql = render(sources)
    # Fail before creating output if the allowlist accidentally expands.
    created = re.findall(r"^CREATE TABLE (?:IF NOT EXISTS )?`?(\w+)`?", sql, re.M)
    if sorted(created) != sorted(n for names in NEW_TABLES.values() for n in names):
        raise ValueError("Unexpected table creation scope")
    if re.search(r"^(?:INSERT|UPDATE|DELETE|TRUNCATE|DROP TABLE)\b", sql, re.M | re.I):
        raise ValueError("Business data mutation is not part of this structural delta")
    migrations = output / "migrations"
    migrations.mkdir(parents=True)
    shutil.copyfile(baseline, migrations / baseline.name)
    delta = migrations / "V531_1__complete_approved_source_structure.sql"
    delta.write_text(sql, encoding="utf8", newline="\n")
    manifest = {"release_id": "Production178CompleteV531_1", "role": "migrations", "files": inventory(migrations)}
    (migrations / "release.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf8")
    shutil.copyfile(base / "collation-policy.json", output / "baseline-collation-policy.json")
    shutil.copyfile(base / "bootstrap-admin.sql", output / "bootstrap-admin.sql")
    (output / "parent-source-manifest.json").write_text(json.dumps(parent, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    lineage = {"sourceCommit": commit, "sourceFiles": provenance,
               "parentMigration": {"file": baseline.name, "sha256": parent["migrationSha256"]},
               "deltaMigration": {"file": delta.name, "sha256": manifest["files"][delta.name]},
               "newTables": sorted(created), "newColumnsInExistingTables": 39,
               "nullableColumnChanges": 4, "replacementUniqueIndexes": 3,
               "additionalLookupIndexes": 1, "additionalChecks": 2,
               "seedChanges": 0, "businessDataImported": False,
               "asciiChecksumNormalization": "utf8mb4_bin; exact comparison preserved",
               "excludedRetiredTable": "sol_engineering_briefing_retired",
               "preserved178Exception": "Do not reintroduce chk_proj_member_dates over preserved legacy source dates",
               "scope": "New empty database only; no production execution authorization"}
    (output / "structure-lineage.json").write_text(json.dumps(lineage, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    verify(migrations, "migrations")
    return lineage


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.root, args.base, args.output, args.commit), ensure_ascii=False))
