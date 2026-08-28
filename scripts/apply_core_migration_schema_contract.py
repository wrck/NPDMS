#!/usr/bin/env python3
"""Apply ADR-0022's mechanical table and foreign-key boundaries to the review DDL."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

from validate_core_migration_schema_contract import DDL, V3_DESIGN_ONLY_TABLES


TABLE_BLOCK = re.compile(
    r"CREATE\s+TABLE\s+(?P<name>\w+)\s*\(.*?\)\s*ENGINE\s*=.*?;\s*",
    re.IGNORECASE | re.DOTALL,
)


def remove_cross_domain_foreign_keys(table: str, block: str) -> tuple[str, int]:
    owner = table.split("_", 1)[0]
    lines = block.splitlines(keepends=True)
    output: list[str] = []
    removed = 0
    index = 0
    while index < len(lines):
        line = lines[index]
        if re.match(r"\s*CONSTRAINT\s+\w+", line, re.IGNORECASE):
            candidate = line
            width = 1
            if "FOREIGN KEY" not in line.upper() and index + 1 < len(lines) and "FOREIGN KEY" in lines[index + 1].upper():
                candidate += lines[index + 1]
                width = 2
            match = re.search(r"FOREIGN\s+KEY\s*\(.*?\)\s+REFERENCES\s+(\w+)", candidate, re.IGNORECASE | re.DOTALL)
            if match and owner != match.group(1).split("_", 1)[0]:
                removed += 1
                index += width
                continue
        output.append(line)
        index += 1
    return "".join(output), removed


def transform_ddl(ddl: str) -> tuple[str, dict[str, int]]:
    removed_tables = 0
    removed_cross_domain_foreign_keys = 0
    parts: list[str] = []
    cursor = 0
    for match in TABLE_BLOCK.finditer(ddl):
        parts.append(ddl[cursor : match.start()])
        table = match.group("name")
        if table in V3_DESIGN_ONLY_TABLES:
            removed_tables += 1
        else:
            transformed, count = remove_cross_domain_foreign_keys(table, match.group(0))
            parts.append(transformed)
            removed_cross_domain_foreign_keys += count
        cursor = match.end()
    parts.append(ddl[cursor:])
    return "".join(parts), {
        "removedTables": removed_tables,
        "removedCrossDomainForeignKeys": removed_cross_domain_foreign_keys,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ddl", type=Path, default=DDL)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    source = args.ddl.read_text(encoding="utf-8")
    transformed, summary = transform_ddl(source)
    if summary != {"removedTables": 4, "removedCrossDomainForeignKeys": 26}:
        print(f"[FAIL] unexpected ADR-0022 transformation scope: {summary}")
        return 1
    if args.write:
        args.ddl.write_text(transformed, encoding="utf-8")
        print(f"[WRITE] applied ADR-0022: {summary}")
        return 0
    print(f"[CHECK] ADR-0022 transformation pending: {summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
