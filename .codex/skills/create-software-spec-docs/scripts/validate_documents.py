#!/usr/bin/env python3
"""Validate a filled SRS/SDS/TAS Markdown document against its template contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


CONFIG = {
    "srs": ("01-software-requirements-specification-template.md", "FR-"),
    "sds": ("02-software-solution-design-template.md", "BD-"),
    "tas": ("03-software-test-and-acceptance-template.md", "TC-"),
}


def headings(text: str) -> list[tuple[int, str, int]]:
    result = []
    for line_number, line in enumerate(text.splitlines(), 1):
        match = re.match(r"^(#{1,6})\s+(.+)$", line)
        if match:
            result.append((len(match.group(1)), match.group(2).strip(), line_number))
    return result


def table_headers(lines: list[str]) -> list[tuple[str, ...]]:
    result = []
    for index, line in enumerate(lines[:-1]):
        if not line.startswith("|") or not lines[index + 1].startswith("|"):
            continue
        separators = [cell.strip() for cell in lines[index + 1].strip("|").split("|")]
        if separators and all(re.fullmatch(r":?-{3,}:?", cell) for cell in separators):
            result.append(tuple(cell.strip().replace("`", "") for cell in line.strip("|").split("|")))
    return result


def units(text: str, prefix: str) -> list[dict]:
    lines = text.splitlines()
    starts = [index for index, line in enumerate(lines) if line.startswith(f"### {prefix}")]
    result = []
    for start in starts:
        end = next(
            (index for index in range(start + 1, len(lines)) if re.match(r"^#{2,3}\s+", lines[index])),
            len(lines),
        )
        block = lines[start:end]
        result.append(
            {
                "title": lines[start][4:].strip(),
                "children": [line[5:].strip() for line in block if line.startswith("#### ")],
                "tables": table_headers(block),
            }
        )
    return result


def ordered_subset(expected: list[tuple[str, ...]], actual: list[tuple[str, ...]]) -> bool:
    cursor = 0
    for item in actual:
        if cursor < len(expected) and item == expected[cursor]:
            cursor += 1
    return cursor == len(expected)


def validate_terms(text: str, term: str, allowed_sections: list[str]) -> list[dict]:
    findings = []
    heading_stack: dict[int, str] = {}
    for line_number, line in enumerate(text.splitlines(), 1):
        heading = re.match(r"^(#{2,6})\s+(.+)$", line)
        if heading:
            level = len(heading.group(1))
            heading_stack = {key: value for key, value in heading_stack.items() if key < level}
            heading_stack[level] = heading.group(2).strip()
        active_headings = [heading_stack[key] for key in sorted(heading_stack)]
        if term.casefold() in line.casefold() and not any(
            title.startswith(section) for title in active_headings for section in allowed_sections
        ):
            findings.append(
                {
                    "code": "restricted-term",
                    "line": line_number,
                    "message": f"术语 {term!r} 出现在允许章节之外：{' > '.join(active_headings) or '文档前言'}",
                }
            )
    return findings


def validate(kind: str, input_path: Path, template_path: Path, term: str | None, allowed: list[str]) -> dict:
    source = input_path.read_text(encoding="utf-8")
    template = template_path.read_text(encoding="utf-8")
    _, prefix = CONFIG[kind]
    findings: list[dict] = []

    template_headings = headings(template)
    source_headings = headings(source)
    fixed_template = [
        (level, title)
        for level, title, _ in template_headings
        if level in (2, 3) and not (level == 3 and title.startswith(prefix))
    ]
    fixed_source = [
        (level, title)
        for level, title, _ in source_headings
        if level in (2, 3) and not (level == 3 and title.startswith(prefix))
    ]
    if fixed_source != fixed_template:
        findings.append(
            {
                "code": "fixed-heading-sequence",
                "message": "固定章节的标题、层级或顺序与模板不一致",
                "expected": fixed_template,
                "actual": fixed_source,
            }
        )

    template_units = units(template, prefix)
    source_units = units(source, prefix)
    if not source_units:
        findings.append({"code": "missing-design-unit", "message": f"至少需要一个 {prefix} 规格单元"})
    elif template_units:
        expected_children = template_units[0]["children"]
        expected_tables = template_units[0]["tables"]
        for unit in source_units:
            if unit["children"] != expected_children:
                findings.append(
                    {
                        "code": "unit-child-sequence",
                        "unit": unit["title"],
                        "message": "规格单元子标题与模板不一致",
                        "expected": expected_children,
                        "actual": unit["children"],
                    }
                )
            if not ordered_subset(expected_tables, unit["tables"]):
                findings.append(
                    {
                        "code": "unit-table-schema",
                        "unit": unit["title"],
                        "message": "规格单元缺少模板规定的表格或表头顺序错误",
                        "expected": expected_tables,
                        "actual": unit["tables"],
                    }
                )

    template_tables = list(dict.fromkeys(table_headers(template.splitlines())))
    source_tables = table_headers(source.splitlines())
    missing_tables = [header for header in template_tables if header not in source_tables]
    if missing_tables:
        findings.append(
            {
                "code": "missing-template-table-schema",
                "message": "文档缺少模板规定的表头；不适用章节也应保留同构表格并填写不适用",
                "missing": missing_tables,
            }
        )

    placeholders = sorted(
        set(re.findall(r"〈[^〉]+〉|(?:FR|BD|TC)-XXX(?:-\d{3})?", source))
    )
    if placeholders:
        findings.append(
            {"code": "template-placeholder", "message": "正式文档仍包含模板占位符", "values": placeholders}
        )

    if term:
        findings.extend(validate_terms(source, term, allowed))

    return {
        "kind": kind,
        "input": str(input_path.resolve()),
        "template": str(template_path.resolve()),
        "passed": not findings,
        "unit_count": len(source_units),
        "fixed_heading_count": len(fixed_source),
        "table_count": len(source_tables),
        "findings": findings,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=CONFIG, required=True)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--template", type=Path)
    parser.add_argument("--restricted-term", help="仅允许在指定章节出现的技术或产品名称")
    parser.add_argument("--allow-section", action="append", default=[], help="允许受限术语出现的章节标题前缀")
    parser.add_argument("--json", type=Path, help="写入 JSON 报告")
    args = parser.parse_args()

    default_template = Path(__file__).resolve().parent.parent / "assets" / "templates" / CONFIG[args.kind][0]
    report = validate(
        args.kind,
        args.input,
        args.template or default_template,
        args.restricted_term,
        args.allow_section,
    )
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
