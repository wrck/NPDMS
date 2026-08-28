#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path
from typing import Any


QUERIES = {
    "snExact": {
        "expectedIndex": "uk_ast_device_tenant_sn",
        "sql": "SELECT d.id, d.sn, d.project_id, d.customer_id FROM ast_device d WHERE d.tenant_id = {tenant_id} AND d.sn = '{sn}' AND d.deleted = b'0' LIMIT 1",
    },
    "projectPage": {
        "expectedIndex": "idx_ast_device_project",
        "sql": "SELECT d.id, d.sn, d.project_id, d.customer_id, d.shipment_time, d.package_no, d.contract_no, d.warranty_status, d.conp_version FROM ast_device d WHERE d.tenant_id = {tenant_id} AND d.project_id = {project_id} AND d.deleted = b'0' ORDER BY d.id DESC LIMIT {page_size}",
    },
    "customerPage": {
        "expectedIndex": "idx_ast_device_customer",
        "sql": "SELECT d.id, d.sn, d.project_id, d.customer_id, d.shipment_time, d.package_no, d.contract_no, d.warranty_status, d.conp_version FROM ast_device d WHERE d.tenant_id = {tenant_id} AND d.customer_id = {customer_id} AND d.deleted = b'0' ORDER BY d.id DESC LIMIT {page_size}",
    },
}


def mysql(container: str, database: str, sql: str) -> str:
    command = [
        "docker", "exec", "-i", container, "sh", "-lc",
        'mysql -N -B -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"',
    ]
    result = subprocess.run(command, input=f"USE `{database}`;\n{sql}\n", text=True, capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip() or f"mysql exited with {result.returncode}")
    return result.stdout.strip()


def walk(value: Any):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def inspect_plan(plan: dict[str, Any]) -> dict[str, Any]:
    tables = []
    rows_examined = []
    for node in walk(plan):
        table = node.get("table")
        if isinstance(table, dict):
            tables.append({
                "tableName": table.get("table_name"),
                "accessType": table.get("access_type"),
                "key": table.get("key"),
                "possibleKeys": table.get("possible_keys", []),
                "rowsExaminedPerScan": table.get("rows_examined_per_scan"),
                "rowsProducedPerJoin": table.get("rows_produced_per_join"),
            })
            rows = table.get("rows_examined_per_scan")
            if isinstance(rows, (int, float)):
                rows_examined.append(int(rows))
    return {
        "tables": tables,
        "rowsExaminedPerScan": max(rows_examined, default=0),
    }


def verify_query(container: str, database: str, name: str, definition: dict[str, str], values: dict[str, Any], max_rows: int) -> dict[str, Any]:
    sql = definition["sql"].format(**values)
    raw = mysql(container, database, f"EXPLAIN FORMAT=JSON {sql};")
    plan = json.loads(raw.replace("\\n", "\n").replace("\\t", "\t"))
    summary = inspect_plan(plan)
    used_indexes = {table["key"] for table in summary["tables"] if table["key"]}
    table_names = {table["tableName"] for table in summary["tables"] if table["tableName"]}
    errors = []
    if definition["expectedIndex"] not in used_indexes:
        errors.append(f"expected index {definition['expectedIndex']} not used")
    if "ast_device_shipment" in table_names:
        errors.append("list plan references ast_device_shipment")
    if summary["rowsExaminedPerScan"] > max_rows:
        errors.append(f"rows_examined_per_scan {summary['rowsExaminedPerScan']} exceeds {max_rows}")
    return {
        "name": name,
        "sql": sql,
        "expectedIndex": definition["expectedIndex"],
        "usedIndexes": sorted(used_indexes),
        "tableNames": sorted(table_names),
        "rowsExaminedPerScan": summary["rowsExaminedPerScan"],
        "tables": summary["tables"],
        "errors": errors,
        "pass": not errors,
        "plan": plan,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", required=True)
    parser.add_argument("--database", default="npdms")
    parser.add_argument("--tenant-id", type=int, default=1)
    parser.add_argument("--sn", default="FAST001_PERF_SN_0000000000")
    parser.add_argument("--project-id", type=int, default=100000)
    parser.add_argument("--customer-id", type=int, default=200000)
    parser.add_argument("--page-size", type=int, default=20)
    parser.add_argument("--max-rows-examined", type=int, default=10000)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    values = {
        "tenant_id": args.tenant_id,
        "sn": args.sn.replace("'", "''"),
        "project_id": args.project_id,
        "customer_id": args.customer_id,
        "page_size": args.page_size,
    }
    results = [
        verify_query(args.container, args.database, name, definition, values, args.max_rows_examined)
        for name, definition in QUERIES.items()
    ]
    payload = {
        "database": args.database,
        "tenantId": args.tenant_id,
        "maxRowsExamined": args.max_rows_examined,
        "queries": results,
        "pass": all(result["pass"] for result in results),
    }
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 0 if payload["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
