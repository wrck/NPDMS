#!/usr/bin/env python3
"""List legacy tables described by the structured data-element evidence."""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path


def summarize(path: Path, keywords: list[str]) -> list[tuple[str, int, str]]:
    catalog: dict[str, dict[str, object]] = defaultdict(lambda: {"count": 0, "descriptions": set()})
    with path.open(encoding="utf-8") as source:
        for line in source:
            record = json.loads(line)
            table = record.get("tableName")
            if not table:
                continue
            catalog[table]["count"] = int(catalog[table]["count"]) + 1
            description = record.get("tableDescription")
            if description:
                descriptions = catalog[table]["descriptions"]
                assert isinstance(descriptions, set)
                descriptions.add(description)

    result = []
    for table, data in sorted(catalog.items()):
        descriptions = "/".join(sorted(data["descriptions"]))
        searchable = f"{table} {descriptions}".lower()
        if keywords and not any(keyword.lower() in searchable for keyword in keywords):
            continue
        result.append((table, int(data["count"]), descriptions))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source",
        type=Path,
        default=Path("specs/001-project-delivery-platform/evidence/data-elements/schema-records.jsonl"),
    )
    parser.add_argument("keywords", nargs="*")
    args = parser.parse_args()
    for table, count, descriptions in summarize(args.source, args.keywords):
        print(f"{table}\t{count}\t{descriptions}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
