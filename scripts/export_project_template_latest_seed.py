#!/usr/bin/env python3
"""PM-03/F-PROJ-009: export the latest published project template as a seed migration.

Reads the template row, its published revision and the business view revisions
referenced by the execution snapshot from a source database (read-only) and emits
a V336-style seed migration: temp tables, collision-guarded idempotent merge.
The output captures an exact snapshot; superseded revisions, drafts and runtime
match history are not seeded, and BPM process deployments stay runtime artifacts.

Passwords are accepted only through environment variables so that credentials do
not become part of the repository or the process command line.
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import re
from pathlib import Path

import mysql.connector

ROOT = Path(__file__).resolve().parents[1]

VIEW_TABLE = "plt_business_view_revision"
VIEW_NATURAL_KEY = ("tenant_id", "entity_type", "view_key", "revision_no")
TEMPLATE_TABLE = "proj_project_template"
TEMPLATE_NATURAL_KEY = ("tenant_id", "code")
REVISION_TABLE = "proj_project_template_revision"
REVISION_NATURAL_KEY = ("tenant_id", "template_id", "revision_no")
TABLES = [(VIEW_TABLE, VIEW_NATURAL_KEY), (TEMPLATE_TABLE, TEMPLATE_NATURAL_KEY),
          (REVISION_TABLE, REVISION_NATURAL_KEY)]

PLAIN_TYPES = {"int", "tinyint", "smallint", "mediumint", "bigint", "decimal", "double", "float"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--database", default="npdms_domain_test")
    parser.add_argument("--template-id", type=int, required=True)
    parser.add_argument("--revision-id", type=int, required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def connect(args: argparse.Namespace):
    password = os.environ.get("SOURCE_DB_PASSWORD")
    if password is None:
        raise ValueError("environment variable SOURCE_DB_PASSWORD is required")
    return mysql.connector.connect(host=args.host, port=args.port, user=args.user,
                                   password=password, database=args.database,
                                   charset="utf8mb4", connection_timeout=10)


def load_columns(cur, table: str) -> list[tuple[str, str]]:
    cur.execute(f"SHOW COLUMNS FROM `{table}`")
    return [(row[0], row[1]) for row in cur.fetchall()]


def base_type(data_type: str) -> str:
    return data_type.split("(")[0].split()[0]


def select_expression(column: str, data_type: str) -> str:
    if base_type(data_type) in PLAIN_TYPES:
        return f"`{column}`"
    if base_type(data_type) == "bit":
        return f"`{column}`+0"
    return f"HEX(`{column}`)"


def is_plain(data_type: str) -> bool:
    return base_type(data_type) in PLAIN_TYPES


def is_bit(data_type: str) -> bool:
    return base_type(data_type) == "bit"


def fetch_rows(cur, table: str, columns: list[tuple[str, str]], where: str) -> list[dict]:
    exprs = ", ".join(f"{select_expression(c, t)} AS `{c}`" for c, t in columns)
    cur.execute(f"SELECT {exprs} FROM `{table}` WHERE {where}")
    return [dict(zip((c for c, _ in columns), row)) for row in cur.fetchall()]


def sql_literal(value, data_type: str) -> str:
    if value is None:
        return "NULL"
    if is_bit(data_type):
        return f"b'{int(value)}'"
    if is_plain(data_type):
        return str(value)
    return f"CONVERT(X'{value}' USING utf8mb4)"


def referenced_view_ids(cur, revision_id: int) -> list[int]:
    cur.execute(f"SELECT execution_snapshot FROM `{REVISION_TABLE}` WHERE id=%s", (revision_id,))
    row = cur.fetchone()
    if row is None or row[0] is None:
        raise SystemExit(f"revision {revision_id} has no execution_snapshot to scan")
    ids = sorted({int(m) for m in re.findall(r'"businessViewRevisionId"\s*:\s*"(\d+)"', row[0])})
    if not ids:
        raise SystemExit(f"revision {revision_id} references no business view revisions")
    return ids


def decode_hex(value) -> str:
    return bytes.fromhex(value).decode("utf-8")


def emit_procedure(rows_by_table: dict[str, list[dict]], columns_by_table: dict[str, list],
                   template_code: str, revision_no: int, published_time: str,
                   revision_id: int) -> str:
    today = dt.date.today().isoformat()
    proc = f"seed_project_template_latest_{today.replace('-', '')}"
    lines = [
        f"-- GENERATED: scripts/export_project_template_latest_seed.py; PM-03/F-PROJ-009.",
        f"-- Exact latest published snapshot of template {template_code} (revision {revision_id},"
        f" rev{revision_no}, published {published_time}) plus the business view",
        "-- revisions its execution snapshot references. No project/history writes.",
        "-- Superseded revisions, drafts and runtime match history are not seeded; BPM process",
        "-- deployments stay runtime artifacts provisioned outside migrations.",
        "-- Preserve historical Flyway files. Retire nothing.",
        f"DROP PROCEDURE IF EXISTS {proc};",
        "DELIMITER $$",
        f"CREATE PROCEDURE {proc}()",
        "BEGIN",
        "DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;",
        "START TRANSACTION;",
    ]
    for index, (table, natural_key) in enumerate(TABLES):
        columns = columns_by_table[table]
        temp = f"tmp_project_seed_latest_{index}"
        col_list = ", ".join(f"`{c}`" for c, _ in columns)
        lines.append(f"DROP TEMPORARY TABLE IF EXISTS {temp};")
        lines.append(f"CREATE TEMPORARY TABLE {temp} LIKE `{table}`;")
        lines.append(f"INSERT INTO {temp} ({col_list}) VALUES")
        tuples = []
        for row in rows_by_table[table]:
            values = ", ".join(sql_literal(row[c], t) for c, t in columns)
            tuples.append(f"({values})")
        lines.append(",\n".join(tuples) + ";")
        natural_key_clause = " AND ".join(f"t.`{c}`<=>s.`{c}`" for c in natural_key)
        join_clause = f"t.id=s.id OR ({natural_key_clause})"
        compare_clause = " AND ".join(f"BINARY t.`{c}`<=>BINARY s.`{c}`" for c, _ in columns)
        lines.append(
            f"IF EXISTS (SELECT 1 FROM `{table}` t JOIN {temp} s ON ({join_clause}) "
            f"WHERE NOT ({compare_clause})) THEN")
        lines.append(f"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Project template seed collision: {table}';")
        lines.append("END IF;")
        lines.append(
            f"INSERT INTO `{table}` ({col_list}) SELECT {', '.join('s.`' + c + '`' for c, _ in columns)} "
            f"FROM {temp} s WHERE NOT EXISTS (SELECT 1 FROM `{table}` t WHERE t.id=s.id);")
    lines += [
        "COMMIT;",
        *[f"DROP TEMPORARY TABLE tmp_project_seed_latest_{i};" for i in range(len(TABLES))],
        f"END$$",
        "DELIMITER ;",
        f"CALL {proc}();",
        f"DROP PROCEDURE {proc};",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    args = parse_args()
    conn = connect(args)
    try:
        cur = conn.cursor()
        columns_by_table = {table: load_columns(cur, table) for table, _ in TABLES}
        templates = fetch_rows(cur, TEMPLATE_TABLE, columns_by_table[TEMPLATE_TABLE],
                               f"id={args.template_id}")
        if len(templates) != 1:
            raise SystemExit(f"template {args.template_id} not found")
        template = templates[0]
        revisions = fetch_rows(cur, REVISION_TABLE, columns_by_table[REVISION_TABLE],
                               f"id={args.revision_id}")
        if len(revisions) != 1:
            raise SystemExit(f"revision {args.revision_id} not found")
        revision = revisions[0]
        template_code = decode_hex(template["code"])
        revision_status = decode_hex(revision["status"])
        published_time = decode_hex(revision["published_time"])
        if revision_status != "PUBLISHED":
            raise SystemExit(f"revision {args.revision_id} is not PUBLISHED")
        view_ids = referenced_view_ids(cur, args.revision_id)
        id_list = ", ".join(str(i) for i in view_ids)
        views = fetch_rows(cur, VIEW_TABLE, columns_by_table[VIEW_TABLE], f"id IN ({id_list})")
        found = {row["id"] for row in views}
        missing = sorted(set(view_ids) - found)
        if missing:
            raise SystemExit(f"referenced business view revisions missing in source: {missing}")
        rows_by_table = {VIEW_TABLE: views, TEMPLATE_TABLE: templates, REVISION_TABLE: revisions}
        output = ROOT / args.output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            emit_procedure(rows_by_table, columns_by_table, template_code,
                           revision["revision_no"], published_time, args.revision_id),
            encoding="utf-8", newline="\n")
        print(f"template {template_code} rev{revision['revision_no']}"
              f" + {len(views)} view revisions -> {output}")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
