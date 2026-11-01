"""Render a new, immutable empty-database release from reviewed 178 snapshots.

No database connections. Does not replay or derive anything from the old B498.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from initialization_policy import validate_configuration

EXCLUDED_TABLES = {"flyway_schema_history": "Target owns a new Flyway history",
                   "sol_engineering_briefing_retired": "V312 retired source; no current mapper; source has zero rows"}


def quote(name):
    if not re.fullmatch(r"[A-Za-z0-9_]+", name):
        raise ValueError("Unexpected SQL identifier: " + name)
    return "`" + name + "`"


def literal(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    if isinstance(value, dict):
        if "$bytes" in value:
            return "X'" + value["$bytes"] + "'"
        if "$decimal" in value:
            return value["$decimal"]
        if "$date" in value:
            value = value["$date"]
        else:
            raise ValueError("Unexpected snapshot value")
    return "CONVERT(X'" + str(value).encode("utf8").hex() + "' USING utf8mb4)"


def normalized_ddl(sql):
    sql = re.sub(r" AUTO_INCREMENT=\d+", "", sql)
    # MySQL 8.4.10 SHOW CREATE includes CHARACTER SET where 8.4.11 can omit
    # it. A named collation uniquely fixes its charset; keep the collation.
    return re.sub(r"CHARACTER SET (\w+) COLLATE (\w+)",
                  lambda m: "COLLATE " + m[2] if m[2].startswith(m[1] + "_") else m[0], sql)


def row_digest(rows):
    encoded = sorted(json.dumps(r, ensure_ascii=False, sort_keys=True, separators=(",", ":")) for r in rows)
    return hashlib.sha256("\n".join(encoded).encode("utf8")).hexdigest()


def render(structure, configuration):
    validate_configuration(configuration)
    if (structure["endpoint"] != "172.17.90.178:13306/npdms"
            or structure["endpoint"] != configuration["source"]
            or not structure["readOnly"] or not configuration["readOnly"]):
        raise ValueError("Source snapshots must refer to the same read-only source")
    if structure["server"]["db"] != "npdms" or not structure["server"]["version"].startswith("8.4."):
        raise ValueError("Unexpected source database")
    tables = {k: v for k, v in structure["tables"].items() if k not in EXCLUDED_TABLES}
    if any(v["TABLE_TYPE"] != "BASE TABLE" for v in tables.values()):
        raise ValueError("Views require explicit dependency review")
    if set(configuration["tables"]) - set(tables):
        raise ValueError("Selected configuration refers to an excluded table")
    header = """-- Source: 172.17.90.178:13306/npdms; approved formal configuration only.
-- EMPTY DATABASE ONLY. Existing databases must keep their original history.
-- No source users/passwords/connections, business instances or test fixtures.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
DELIMITER $$
CREATE PROCEDURE assert_production_empty_database()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.tables
           WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production initialization requires an empty database';
 END IF;
END$$
DELIMITER ;
CALL assert_production_empty_database();
DROP PROCEDURE assert_production_empty_database;
SET @production_old_fk_checks=@@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS=0;
"""
    parts = [header]
    for name, table in sorted(tables.items()):
        # Render the source DDL verbatim except for business sequence watermarks.
        parts.append(re.sub(r" AUTO_INCREMENT=\d+", "", table["ddl"]) + ";\n")
    parts.append("-- Restore reviewed configuration before installing immutable-row guards.\n")
    for table, rows in sorted(configuration["tables"].items()):
        columns = {c["COLUMN_NAME"] for c in structure["columns"] if c["TABLE_NAME"] == table}
        computed = {c["COLUMN_NAME"] for c in structure["columns"] if c["TABLE_NAME"] == table
                    and re.search(r"(?:VIRTUAL|STORED) GENERATED", c["EXTRA"])}
        for row in rows:
            if set(row) - columns:
                raise ValueError("Unknown selected columns: " + table)
            writable = {k: v for k, v in row.items() if k not in computed}
            parts.append("INSERT INTO " + quote(table) + " (" + ",".join(map(quote, writable)) + ") VALUES ("
                         + ",".join(literal(v) for v in writable.values()) + ");\n")
    parts.append("SET FOREIGN_KEY_CHECKS=@production_old_fk_checks;\n")
    for trigger in structure["triggers"]:
        if trigger["EVENT_OBJECT_TABLE"] not in tables:
            continue
        parts.append("SET @production_old_sql_mode=@@SQL_MODE;\nSET SQL_MODE=" + literal(trigger["SQL_MODE"]) + ";\n")
        parts.append("DELIMITER $$\nCREATE TRIGGER " + quote(trigger["TRIGGER_NAME"]) + " "
                     + trigger["ACTION_TIMING"] + " " + trigger["EVENT_MANIPULATION"] + " ON "
                     + quote(trigger["EVENT_OBJECT_TABLE"]) + " FOR EACH ROW " + trigger["ACTION_STATEMENT"]
                     + "$$\nDELIMITER ;\nSET SQL_MODE=@production_old_sql_mode;\n")
    return "\n".join(parts), tables


def build(structure_path, configuration_path, output):
    if output.exists():
        raise ValueError("Release output must be new; never overwrite a frozen baseline")
    structure = json.loads(structure_path.read_text(encoding="utf8"))
    configuration = json.loads(configuration_path.read_text(encoding="utf8"))
    sql, tables = render(structure, configuration)
    filename = "B530__production_178_initialization.sql"
    manifest = {
        "source": structure["endpoint"], "schemaCapturedAt": structure["capturedAt"],
        "configurationCapturedAt": configuration["capturedAt"],
        "sourceStructureSha256": hashlib.sha256(structure_path.read_bytes()).hexdigest(),
        "sourceConfigurationSha256": hashlib.sha256(configuration_path.read_bytes()).hexdigest(),
        "sourceHistory": structure["history"], "excludedTables": EXCLUDED_TABLES,
        "tables": {n: {"ddlSha256": hashlib.sha256(normalized_ddl(t["ddl"]).encode()).hexdigest()}
                   for n, t in tables.items()},
        "triggers": [{k: v for k, v in t.items()} for t in structure["triggers"] if t["EVENT_OBJECT_TABLE"] in tables],
        "seedCounts": {t: len(rows) for t, rows in configuration["tables"].items()},
        "seedDigests": {t: {"columns": list(rows[0]), "sha256": row_digest(rows)}
                        for t, rows in configuration["tables"].items() if rows},
        "seedSelection": configuration["selection"], "sanitizations": configuration["sanitizations"],
        "excludedData": ["Business/approval/sync/runtime/audit instances", "Test users and personal profiles",
                         "Upstream OA/code-generator example menus and grants; debug SMS channel/templates",
                         "SAT_MANUAL_EXAMPLE questionnaire (explicit user decision)",
                         "Synthetic UMC inspection rule marked non-production", "Draft project template FCV4_SOURCE_3D5C63BD453D",
                         "Disabled historical cutover configurations and draft task state machine",
                         "Source credentials, external connections and environment storage"],
        "migrationFile": filename, "migrationSha256": hashlib.sha256(sql.encode("utf8")).hexdigest(),
        "structuralNormalization": ["Auto-increment watermarks recalculated from selected seeds",
                                    "Trigger DEFINER binds to target migration identity; bodies/timing/order retained"],
    }
    (output / "migrations").mkdir(parents=True)
    (output / "migrations" / filename).write_text(sql, encoding="utf8", newline="\n")
    (output / "source-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"tables": len(tables), "triggers": len(manifest["triggers"]), "seedRows": sum(manifest["seedCounts"].values()), "release": str(output)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--structure", type=Path, required=True)
    parser.add_argument("--configuration", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.structure, args.configuration, args.output)
