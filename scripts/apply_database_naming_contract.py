#!/usr/bin/env python3
"""Apply ADR-0019 naming decisions to the physical DDL deterministically."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


DEFAULT_DDL = Path("specs/001-project-delivery-platform/appendices/project-order-physical-schema.mysql.sql")
DEFAULT_CONTRACT = Path("docs/traceability/database-naming-contract.json")
CREATE_BLOCK = re.compile(
    r"(CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?)([a-zA-Z0-9_]+)(`?\s*\()(.*?)(\)\s*ENGINE\s*=.*?;)",
    re.I | re.S,
)


def replace_identifiers_outside_literals(text: str, mapping: dict[str, str]) -> str:
    """Replace complete SQL identifiers while preserving quoted string literals."""
    if not mapping:
        return text
    pattern = re.compile(r"(?<![a-zA-Z0-9_])(" + "|".join(re.escape(key) for key in sorted(mapping, key=len, reverse=True)) + r")(?![a-zA-Z0-9_])")
    parts = re.split(r"('(?:''|[^'])*')", text)
    for index in range(0, len(parts), 2):
        parts[index] = pattern.sub(lambda match: mapping[match.group(1)], parts[index])
    return "".join(parts)


def transform_ddl(text: str, contract: dict[str, object]) -> str:
    table_mapping = {item["source"]: item["target"] for item in contract["tables"]}
    field_by_table: dict[str, dict[str, str]] = {}
    for item in contract["fields"]:
        field_by_table.setdefault(item["sourceTable"], {})[item["sourceColumn"]] = item["targetColumn"]

    def transform_block(match: re.Match[str]) -> str:
        prefix, table, opener, body, suffix = match.groups()
        source_table = table if table in table_mapping else next((source for source, target in table_mapping.items() if target == table), table)
        transformed_body = replace_identifiers_outside_literals(body, field_by_table.get(source_table, {}))
        target_table = table_mapping.get(table, table)
        return prefix + target_table + opener + transformed_body + suffix

    transformed = CREATE_BLOCK.sub(transform_block, text)
    return replace_identifiers_outside_literals(transformed, table_mapping)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--ddl", type=Path, default=DEFAULT_DDL)
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    ddl = args.ddl if args.ddl.is_absolute() else root / args.ddl
    contract_path = args.contract if args.contract.is_absolute() else root / args.contract
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    before = ddl.read_text(encoding="utf-8-sig")
    after = transform_ddl(before, contract)
    if args.check:
        if before != after:
            print("[FAIL] DDL naming contract has unapplied changes")
            return 1
        print(f"[PASS] DDL naming contract applied; tables={len(contract['tables'])} fields={len(contract['fields'])}")
        return 0
    ddl.write_text(after, encoding="utf-8", newline="\n")
    print(f"[PASS] wrote {ddl.relative_to(root).as_posix()}; tables={len(contract['tables'])} fields={len(contract['fields'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
