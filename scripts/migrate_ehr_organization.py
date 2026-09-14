#!/usr/bin/env python3
"""Migrate legacy dppms EHR companies and departments into NPDMS.

Passwords are accepted only through environment variables so that credentials do
not become part of the repository or the process command line.
"""

from __future__ import annotations

import argparse
import os
from dataclasses import dataclass
from typing import Any, Iterable

import mysql.connector


CREATOR = "ehr-org-migration"


@dataclass(frozen=True)
class Company:
    source_id: int
    code: str
    name: str
    status: int


@dataclass(frozen=True)
class Department:
    source_id: int
    code: str
    name: str
    source_company_id: int | None
    source_parent_id: int | None
    status: int


def target_status(is_disabled: Any) -> int:
    """Yudao status: 0 enabled, 1 disabled. Legacy NULL means not disabled."""
    return 1 if is_disabled in (1, True, b"\x01") else 0


def required_text(value: Any, field: str, source_id: int, max_length: int) -> str:
    text = "" if value is None else str(value).strip()
    if not text:
        raise ValueError(f"source {source_id}: {field} is blank")
    if len(text) > max_length:
        raise ValueError(
            f"source {source_id}: {field} length {len(text)} exceeds target {max_length}"
        )
    return text


def load_source(connection: Any) -> tuple[list[Company], list[Department]]:
    cursor = connection.cursor(dictionary=True)
    cursor.execute(
        "SELECT compID, compCode, compName, isDisabled FROM ehr_company ORDER BY compID"
    )
    companies = [
        Company(
            source_id=row["compID"],
            code=required_text(row["compCode"], "compCode", row["compID"], 64),
            name=required_text(row["compName"], "compName", row["compID"], 128),
            status=target_status(row["isDisabled"]),
        )
        for row in cursor.fetchall()
    ]
    cursor.execute(
        "SELECT depID, depCode, depName, compID, adminID, isDisabled "
        "FROM ehr_department ORDER BY depID"
    )
    departments = [
        Department(
            source_id=row["depID"],
            code=required_text(row["depCode"], "depCode", row["depID"], 64),
            name=required_text(row["depName"], "depName", row["depID"], 30),
            source_company_id=row["compID"],
            source_parent_id=row["adminID"],
            status=target_status(row["isDisabled"]),
        )
        for row in cursor.fetchall()
    ]
    cursor.close()
    validate_source(companies, departments)
    return companies, departments


def validate_source(companies: Iterable[Company], departments: Iterable[Department]) -> None:
    companies = list(companies)
    departments = list(departments)
    company_ids = {row.source_id for row in companies}
    department_ids = {row.source_id for row in departments}
    if len({row.code for row in companies}) != len(companies):
        raise ValueError("duplicate company code in source")
    if len({row.code for row in departments}) != len(departments):
        raise ValueError("duplicate department code in source")
    for row in departments:
        if row.source_company_id is not None and row.source_company_id not in company_ids:
            raise ValueError(f"department {row.source_id}: company is missing")
        if row.source_parent_id not in (None, 0) and row.source_parent_id not in department_ids:
            raise ValueError(f"department {row.source_id}: parent is missing")
        if row.source_parent_id == row.source_id:
            raise ValueError(f"department {row.source_id}: self parent")
    parent_by_id = {row.source_id: row.source_parent_id for row in departments}
    for row in departments:
        seen: set[int] = set()
        current = row.source_id
        while current in parent_by_id and parent_by_id[current] not in (None, 0):
            if current in seen:
                raise ValueError(f"department {row.source_id}: parent cycle")
            seen.add(current)
            current = parent_by_id[current]  # type: ignore[assignment]


def existing_by_code(cursor: Any, table: str, tenant_id: int) -> dict[str, dict[str, Any]]:
    cursor.execute(
        f"SELECT id, code, name, status, creator FROM {table} "
        "WHERE tenant_id = %s AND deleted = b'0' AND code IS NOT NULL",
        (tenant_id,),
    )
    return {row["code"]: row for row in cursor.fetchall()}


def assert_compatible(existing: dict[str, Any], name: str, kind: str, code: str) -> None:
    if existing["name"] != name:
        raise ValueError(
            f"target {kind} code {code} already exists with different name: "
            f"{existing['name']!r} != {name!r}"
        )
    if existing["creator"] != CREATOR:
        raise ValueError(
            f"target {kind} code {code} already exists and is not owned by this migration"
        )


def migrate(
    connection: Any,
    companies: list[Company],
    departments: list[Department],
    tenant_id: int,
    apply: bool,
) -> dict[str, int]:
    cursor = connection.cursor(dictionary=True)
    existing_companies = existing_by_code(cursor, "system_company", tenant_id)
    existing_departments = existing_by_code(cursor, "system_dept", tenant_id)

    company_inserts = 0
    department_inserts = 0
    for row in companies:
        existing = existing_companies.get(row.code)
        if existing:
            assert_compatible(existing, row.name, "company", row.code)
            continue
        company_inserts += 1
        if apply:
            cursor.execute(
                "INSERT INTO system_company "
                "(tenant_id, code, name, status, version, creator, updater, deleted) "
                "VALUES (%s, %s, %s, %s, 0, %s, %s, b'0')",
                (tenant_id, row.code, row.name, row.status, CREATOR, CREATOR),
            )

    for row in departments:
        existing = existing_departments.get(row.code)
        if existing:
            assert_compatible(existing, row.name, "department", row.code)
            continue
        department_inserts += 1
        if apply:
            cursor.execute(
                "INSERT INTO system_dept "
                "(tenant_id, code, name, parent_id, sort, status, version, creator, updater, deleted) "
                "VALUES (%s, %s, %s, 0, %s, %s, 0, %s, %s, b'0')",
                (tenant_id, row.code, row.name, row.source_id, row.status, CREATOR, CREATOR),
            )

    if apply:
        target_departments = existing_by_code(cursor, "system_dept", tenant_id)
        source_code_by_id = {row.source_id: row.code for row in departments}
        for row in departments:
            parent_id = 0
            if row.source_parent_id not in (None, 0):
                parent_code = source_code_by_id[row.source_parent_id]
                parent_id = target_departments[parent_code]["id"]
            cursor.execute(
                "UPDATE system_dept SET parent_id = %s, status = %s, updater = %s "
                "WHERE tenant_id = %s AND code = %s AND deleted = b'0'",
                (parent_id, row.status, CREATOR, tenant_id, row.code),
            )
        connection.commit()
    else:
        connection.rollback()
    cursor.close()
    return {
        "source_companies": len(companies),
        "source_departments": len(departments),
        "company_inserts": company_inserts,
        "department_inserts": department_inserts,
    }


def connection_args(prefix: str, args: argparse.Namespace) -> dict[str, Any]:
    password = os.environ.get(f"{prefix}_PASSWORD")
    if password is None:
        raise ValueError(f"environment variable {prefix}_PASSWORD is required")
    return {
        "host": getattr(args, f"{prefix.lower()}_host"),
        "port": getattr(args, f"{prefix.lower()}_port"),
        "user": getattr(args, f"{prefix.lower()}_user"),
        "password": password,
        "database": getattr(args, f"{prefix.lower()}_database"),
        "charset": "utf8mb4",
        "connection_timeout": 10,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-host", required=True)
    parser.add_argument("--source-port", type=int, default=3306)
    parser.add_argument("--source-user", required=True)
    parser.add_argument("--source-database", default="dppms")
    parser.add_argument("--target-host", required=True)
    parser.add_argument("--target-port", type=int, default=3306)
    parser.add_argument("--target-user", required=True)
    parser.add_argument("--target-database", required=True)
    parser.add_argument("--tenant-id", type=int, default=1)
    parser.add_argument("--apply", action="store_true", help="commit changes; default is dry-run")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    source = mysql.connector.connect(**connection_args("SOURCE", args))
    target = mysql.connector.connect(**connection_args("TARGET", args))
    try:
        companies, departments = load_source(source)
        result = migrate(target, companies, departments, args.tenant_id, args.apply)
        mode = "APPLIED" if args.apply else "DRY_RUN"
        print(mode, " ".join(f"{key}={value}" for key, value in result.items()))
    finally:
        source.close()
        target.close()


if __name__ == "__main__":
    main()
