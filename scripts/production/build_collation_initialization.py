"""Derive a NEW empty-database baseline from a hash-verified formal release.

Only CREATE TABLE character declarations change. Seed SQL, immutable snapshots,
triggers and constraints remain byte-identical. No database connections.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

from build_initialization import normalized_ddl


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build(source, policy_path, output):
    if output.exists():
        raise ValueError("Never overwrite a frozen release")
    manifest = json.loads((source / "source-manifest.json").read_text(encoding="utf8"))
    original = (source / "migrations" / manifest["migrationFile"]).read_bytes()
    if sha(original) != manifest["migrationSha256"]:
        raise ValueError("Source migration checksum mismatch")
    policy = json.loads(policy_path.read_text(encoding="utf8"))
    columns = {(c["table"], c["column"]): c for c in policy["columns"]}
    tables = {t["table"]: t for t in policy["tables"]}
    if len(columns) != len(policy["columns"]) or len(tables) != len(policy["tables"]):
        raise ValueError("Duplicate policy identity")
    used = set()
    seen = set()
    changes = []

    def transform(match):
        name, ddl = match[1], match[0][:-1]
        if name not in manifest["tables"] or name not in tables:
            raise ValueError("Unreviewed table: " + name)
        seen.add(name)
        lines = ddl.splitlines(keepends=True)
        for i, line in enumerate(lines):
            col = re.match(r"  `([^`]+)` ", line)
            if col and re.match(r"  `[^`]+` (?:char|varchar|tinytext|text|mediumtext|longtext|enum|set)\b", line):
                if (name, col[1]) not in columns:
                    raise ValueError("Missing character policy: " + name + "." + col[1])
            if not col or (name, col[1]) not in columns:
                continue
            c = columns[name, col[1]]
            prefix = "  `" + col[1] + "` " + c["type"]
            if not line.startswith(prefix):
                raise ValueError("Column type drift: " + name + "." + col[1])
            attrs = re.sub(r"^(?: CHARACTER SET \w+)?(?: COLLATE \w+)?", "", line[len(prefix):])
            lines[i] = prefix + " CHARACTER SET utf8mb4 COLLATE " + c["targetCollation"] + attrs
            used.add((name, col[1]))
            if c["sourceCollation"] != c["targetCollation"]:
                changes.append(c)
        lines[-1], count = re.subn(r"DEFAULT CHARSET=\w+(?: COLLATE=\w+)?",
                                  "DEFAULT CHARSET=utf8mb4 COLLATE=" + tables[name]["targetCollation"], lines[-1])
        if count != 1:
            raise ValueError("Unrecognized table default: " + name)
        ddl = "".join(lines)
        manifest["tables"][name]["ddlSha256"] = sha(normalized_ddl(ddl).encode())
        return ddl + ";"

    text = original.decode("utf8")
    pattern = r"CREATE TABLE `([^`]+)` \(\r?\n.*?\r?\n\) ENGINE=[^\n]*?;"
    result = re.sub(pattern, transform, text, flags=re.S)
    if seen != set(manifest["tables"]) or used != set(columns):
        raise ValueError("Incomplete table/character-column policy coverage")
    # This catches accidental edits to seed values, trigger bodies or the guard.
    if re.sub(pattern, "<DDL>", text, flags=re.S) != re.sub(pattern, "<DDL>", result, flags=re.S):
        raise ValueError("Non-DDL content changed")
    filename = "B531__production_178_unicode_initialization.sql"
    manifest.update({"parentMigrationSha256": sha(original), "parentMigrationFile": manifest["migrationFile"],
                     "migrationFile": filename, "migrationSha256": sha(result.encode()),
                     "collationPolicySha256": sha(policy_path.read_bytes()),
                     "forEmptyDatabasesOnly": True, "businessDataImported": False,
                     "releaseReadiness": "Requires source data comparison and isolated validation; not deployment authorization"})
    manifest["structuralNormalization"].append("Explicit reviewed utf8mb4 column/table policy; all non-DDL bytes preserved")
    (output / "migrations").mkdir(parents=True)
    (output / "migrations" / filename).write_bytes(result.encode())
    (output / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    (output / "collation-policy.json").write_bytes(policy_path.read_bytes())
    return {"tables": len(seen), "characterColumns": len(used), "changedColumns": len(changes),
            "nonDdlBytesPreserved": True, "migrationSha256": manifest["migrationSha256"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(args.source, args.policy, args.output)))
