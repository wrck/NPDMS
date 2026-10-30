#!/usr/bin/env python3
"""Build the EMPTY-DATABASE migration lineage without obsolete project fixtures.

Historical upgrade files are inputs only. Continue using this lineage for future
upgrades of a database initialized with compose.fresh.yaml; never switch lineages
or use Flyway repair to hide checksum differences.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "sql/migrations"
OUTPUT = ROOT / ".run/fresh-project-migrations"
OMIT = {54, 55, 56, 59, 61, 62, 73, 75, 79, 162, 207, 208, 209}
FILTER = {72, 74, 100, 105, 161}
GUARD = """-- Fresh lineage only. Refuse to attach it to an existing installation.
DELIMITER $$
CREATE PROCEDURE assert_empty_fresh_project_database()
BEGIN
 IF EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema=DATABASE() AND table_name <> 'flyway_schema_history') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Fresh initialization requires an empty database';
 END IF;
END$$
DELIMITER ;
CALL assert_empty_fresh_project_database();
DROP PROCEDURE assert_empty_fresh_project_database;
"""


def statements(sql):
    """Split SQL, treating each DELIMITER-delimited procedure as one statement."""
    start = i = 0
    quote = None
    delimiter = ";"
    while i < len(sql):
        c = sql[i]
        if not quote and (i == 0 or sql[i - 1] == "\n"):
            directive = re.match(r"[ \t]*DELIMITER[ \t]+(\S+)[ \t]*(?:\r?\n|$)", sql[i:], re.I)
            if directive:
                end = i + len(directive.group(0))
                yield sql[start:end]
                delimiter = directive.group(1)
                start = i = end
                continue
        if quote:
            if c == "\\":
                i += 2
                continue
            if c == quote:
                if i + 1 < len(sql) and sql[i + 1] == quote:
                    i += 2
                    continue
                quote = None
        elif c in "'\"`":
            quote = c
        elif sql.startswith("/*", i):
            end = sql.find("*/", i + 2)
            if end < 0:
                raise ValueError("Unterminated SQL comment")
            i = end + 2
            continue
        elif c == "#" or (sql.startswith("--", i) and (i + 2 == len(sql) or sql[i + 2].isspace())):
            end = sql.find("\n", i)
            i = len(sql) if end < 0 else end + 1
            continue
        elif sql.startswith(delimiter, i):
            end = i + len(delimiter)
            yield sql[start:end]
            start = i = end
            continue
        i += 1
    if quote:
        raise ValueError("Unterminated SQL string")
    if sql[start:].strip():
        yield sql[start:]


def command_text(statement):
    # Only leading comments are removed; comment markers in string literals stay.
    s = statement.lstrip()
    while s.startswith(("--", "#", "/*")):
        if s.startswith("/*"):
            s = s[s.index("*/") + 2:].lstrip()
        else:
            s = s.partition("\n")[2].lstrip()
    return s


def project_fixture(statement, version):
    if version == 161 and re.match(
            r"(?is)(?:CREATE\s+PROCEDURE|DROP\s+PROCEDURE|CALL)\s+`?fcom001_verify_v161_managed_product_codes`?\b",
            command_text(statement)):
        return True
    match = re.match(r"(?is)(?:INSERT\s+(?:IGNORE\s+)?INTO|UPDATE|DELETE\s+FROM)\s+`?(\w+)", command_text(statement))
    if not match:
        return False
    table = match.group(1)
    return table.startswith("proj_") or (version in {72, 161} and table in {
        "com_order_line", "com_sales_order_line", "com_delivery_scope", "com_delivery_scope_detail",
        "com_delivery_scope_project_version", "acc_acceptance_scope_binding", "com_contract",
        "com_order_contract_relation", "com_project_contract_relation"})


def empty_commerce_schema(sql):
    # V160 deliberately supports only the complete V72 demo as an upgrade input.
    # A new empty database has no such input. Reuse its exact ten table definitions
    # and atomic rename, without weakening that historical migration's guards.
    creates = re.findall(r"(?ms)^    CREATE TABLE `fcom001_shadow_.*?^    \) ENGINE=.*?;", sql)
    renames = re.findall(r"(?ms)^    RENAME TABLE\s+.*?;", sql)
    if len(creates) != 10 or len(renames) != 1:
        raise ValueError("V160 DDL shape changed; review fresh schema adapter")
    return "\n\n".join(creates + renames) + "\n"


def project(name, sql):
    version = int(name.split("__")[0][1:].split("_")[0])
    if version in OMIT:
        return f"-- Fresh lineage: obsolete project/template fixture V{version} intentionally omitted.\n"
    if version == 160:
        return "-- Fresh lineage: exact V160 DDL without V72 demo import.\n" + empty_commerce_schema(sql)
    if version == 289:
        # V289 repairs a historically partial V285 deployment. A fresh database
        # runs the complete V285 from this repository and already has both renames.
        prior = (SOURCE / "V285__equipment_to_device_migration.sql").read_text(encoding="utf8")
        kept = []
        for statement in statements(sql):
            command = command_text(statement).strip()
            if command.startswith(("RENAME TABLE", "ALTER TABLE")):
                if command not in prior:
                    raise ValueError("V289 no longer duplicates V285; review fresh adapter")
            else:
                kept.append(statement)
        return "-- Fresh lineage: V285 already applied the verified renames.\n" + "".join(kept)
    if version in FILTER:
        return "-- Fresh lineage: platform definitions retained; obsolete project fixtures omitted.\n" + "".join(
            statement for statement in statements(sql) if not project_fixture(statement, version))
    return sql


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    expected = {"V0__fresh_project_database_guard.sql": GUARD}
    for path in SOURCE.glob("V*.sql"):
        expected[path.name] = project(path.name, path.read_text(encoding="utf-8-sig"))
    if args.check:
        if not OUTPUT.exists() or {p.name for p in OUTPUT.glob("*.sql")} != set(expected):
            raise SystemExit("Fresh migration projection is missing or stale")
        for name, value in expected.items():
            if (OUTPUT / name).read_text(encoding="utf8") != value:
                raise SystemExit("Fresh migration projection differs: " + name)
    else:
        OUTPUT.mkdir(parents=True, exist_ok=True)
        extra = {p.name for p in OUTPUT.glob("*.sql")} - set(expected)
        if extra:
            raise SystemExit(f"Unexpected migration files in generated directory: {sorted(extra)}")
        for name, value in expected.items():
            (OUTPUT / name).write_text(value, encoding="utf8", newline="\n")
    print(f"Fresh migration lineage: {len(expected)} files; historical migrations unchanged")


if __name__ == "__main__":
    main()
