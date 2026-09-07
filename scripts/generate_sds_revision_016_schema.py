#!/usr/bin/env python3
"""Render the frozen revision-016 carrier design, never a deployable migration.

The canonical column/constraint definitions live in the physical contract JSON.
This renderer has no database connection and cannot update an application schema.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

CONTRACT = Path('docs/traceability/sds-revision-016-physical-contract.json')
OUTPUT = Path('specs/001-project-delivery-platform/appendices/sds-revision-016-carriers.mysql.sql')
IDENTIFIER = re.compile(r'^[a-z][a-z0-9_]*$')

def render(contract: dict) -> str:
    tables = contract.get('tables')
    if not isinstance(tables, dict) or not tables:
        raise ValueError('empty physical contract')
    lines = [
        '-- Generated from docs/traceability/sds-revision-016-physical-contract.json.',
        '-- Prospective SDS carrier design only. NOT a Flyway migration or upgrade script.',
        '-- Execute only in a new isolated validation schema; no production authorization.',
        '-- Cross-Context references are logical and MUST be revalidated through Owner APIs.',
        'SET NAMES utf8mb4;', '',
    ]
    for name, table in tables.items():
        if not IDENTIFIER.fullmatch(name):
            raise ValueError(f'invalid table name: {name}')
        columns = table.get('columns', [])
        names = [column[0] for column in columns]
        if len(set(names)) != len(names) or not {'id','tenant_id'} <= set(names):
            raise ValueError(f'duplicate or missing identity columns: {name}')
        parts = []
        for column, definition in columns:
            if not IDENTIFIER.fullmatch(column) or ';' in definition:
                raise ValueError(f'invalid column: {name}.{column}')
            parts.append(f'  `{column}` {definition}')
        for constraint in table.get('constraints', []):
            if ';' in constraint:
                raise ValueError(f'invalid constraint: {name}')
            parts.append('  ' + constraint)
        lines += [f'-- Owner {table["owner"]}; Requirements: {", ".join(table["requirementIds"])}',
                  f'CREATE TABLE `{name}` (', ',\n'.join(parts),
                  ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;', '']
    return '\n'.join(lines)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    text = render(json.loads((args.root / CONTRACT).read_text(encoding='utf-8')))
    output = args.root / OUTPUT
    if args.check:
        if not output.is_file() or output.read_text(encoding='utf-8') != text:
            print('[FAIL] prospective SDS schema drift')
            return 1
        print('[PASS] prospective SDS schema exactly matches physical contract; NOT runtime migration')
    else:
        output.write_text(text,encoding='utf-8',newline='\n')
        print(f'WROTE {OUTPUT}')
    return 0
if __name__ == '__main__':
    raise SystemExit(main())
