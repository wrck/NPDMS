#!/usr/bin/env python3
"""Generate the concrete P3-E09 data-model decision catalog."""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path


REGISTER = Path("specs/001-project-delivery-platform/evidence/migration/ddl-item-decision-register.json")
INVENTORY = Path("specs/001-project-delivery-platform/evidence/migration/ddl-current-constraint-inventory.json")
OUTPUT = Path("specs/001-project-delivery-platform/evidence/migration/ddl-model-decision-catalog.md")


def constraint_kind(value: str) -> str:
    normalized = value.upper().strip()
    if normalized.startswith("PRIMARY KEY"):
        return "PRIMARY_KEY"
    if " FOREIGN KEY " in f" {normalized} ":
        return "FOREIGN_KEY"
    if normalized.startswith("UNIQUE KEY"):
        return "UNIQUE_KEY"
    if " CHECK " in f" {normalized} ":
        return "CHECK"
    if normalized.startswith("KEY "):
        return "INDEX"
    return "OTHER"


def escape(value: object) -> str:
    return str(value).replace("|", "\\|").replace("\n", " ")


def render(root: Path) -> str:
    register_path = root / REGISTER
    inventory_path = root / INVENTORY
    register = json.loads(register_path.read_text(encoding="utf-8"))
    inventory = json.loads(inventory_path.read_text(encoding="utf-8"))

    table_columns: dict[str, list[dict[str, object]]] = defaultdict(list)
    table_items: dict[str, dict[str, object]] = {}
    for item in register["items"]:
        if item.get("comparisonStatus") == "REMOVED":
            continue
        if item["itemType"] == "TABLE":
            table_items[item["table"]] = item
        elif item["itemType"] == "COLUMN":
            table_columns[item["table"]].append(item)

    constraints: dict[str, list[tuple[str, str]]] = defaultdict(list)
    table_options: dict[str, str] = {}
    for record in inventory["records"]:
        table = record["table"]
        table_options[table] = record["tableOptions"]
        for value in record["constraints"]:
            constraints[constraint_kind(value)].append((table, value))

    register_sha = hashlib.sha256(register_path.read_bytes()).hexdigest().upper()
    inventory_sha = hashlib.sha256(inventory_path.read_bytes()).hexdigest().upper()
    lines = [
        "# P3-E09 数据模型逐项裁决清单",
        "",
        "> 状态：`REVIEW_REQUIRED`",
        f"> 决策登记SHA-256：`{register_sha}`",
        f"> 约束清单SHA-256：`{inventory_sha}`",
        "> 本清单只展开现有机器证据，不自动批准数据模型。",
        "",
        "## 1. 核对结论与裁决分组",
        "",
        "|分组|数量|当前事实|建议裁决方式|",
        "|---|---:|---|---|",
        f"|表|{len(table_items)}|当前核心迁移子集；新增、修改和移除事实见逐项登记|按ADR及Reviewer证据逐项裁决|",
        f"|字段|{sum(len(items) for items in table_columns.values()):,}|当前DDL字段；不包含已移除V3治理表字段|按业务语义、类型和约束分类裁决|",
        f"|表选项|{len(table_options)}|旧基线未保存|需确认字符比较与存储规则|",
        f"|主键|{len(constraints['PRIMARY_KEY'])}|旧基线未保存|结构性规则，可分类确认|",
        f"|外键|{len(constraints['FOREIGN_KEY'])}|旧基线未保存|影响迁移顺序和异常隔离|",
        f"|普通索引|{len(constraints['INDEX'])}|旧基线未保存|影响查询性能和写入成本|",
        f"|唯一键|{len(constraints['UNIQUE_KEY'])}|旧基线未保存|影响重复业务数据，必须业务审查|",
        f"|CHECK|{len(constraints['CHECK'])}|旧基线未保存|影响异常历史数据，必须业务审查|",
        "",
        "## 2. 表与字段完整清单",
        "",
        "以下仅列当前核心迁移DDL中的表与字段；相对旧目录的`MATCH/ADDED/MODIFIED`状态以逐项决策登记为准。",
        "",
        "|编号|表|字段数|字段清单|",
        "|---|---|---:|---|",
    ]
    for index, table in enumerate(sorted(table_items), 1):
        columns = sorted(table_columns[table], key=lambda item: str(item.get("name", "")))
        column_names = "、".join(f"`{item['name']}`" for item in columns)
        lines.append(f"|T-{index:03d}|`{table}`|{len(columns)}|{column_names}|")

    lines.extend(["", "## 3. 表选项完整清单", "", "|编号|表|当前表选项|建议|", "|---|---|---|---|"])
    for index, table in enumerate(sorted(table_options), 1):
        lines.append(f"|O-{index:03d}|`{table}`|`{escape(table_options[table])}`|待确认字符比较规则后分类接受|")

    sections = [
        ("PRIMARY_KEY", "4. 主键完整清单", "PK", "结构性规则；建议接受"),
        ("FOREIGN_KEY", "5. 外键完整清单", "FK", "影响迁移顺序；建议接受并隔离违规历史数据"),
        ("INDEX", "6. 普通索引完整清单", "IX", "查询设计规则；建议接受，后续以压测验证"),
        ("UNIQUE_KEY", "7. 唯一键完整清单", "UK", "影响重复数据；需逐组业务确认"),
        ("CHECK", "8. CHECK规则完整清单", "CK", "影响异常历史数据；需逐组业务确认"),
    ]
    for kind, title, prefix, recommendation in sections:
        lines.extend(["", f"## {title}", "", "|编号|表|当前定义|业务影响/建议|", "|---|---|---|---|"])
        for index, (table, value) in enumerate(sorted(constraints[kind]), 1):
            lines.append(f"|{prefix}-{index:03d}|`{table}`|`{escape(value)}`|{recommendation}|")

    lines.extend([
        "",
        "## 9. 裁决边界",
        "",
        "- `ACCEPT_CURRENT`表示接受当前DDL作为目标数据模型，不代表历史数据天然满足约束。",
        "- 历史数据违反已批准约束时进入迁移问题池并保留来源证据，不得静默删除、改写或临时放宽模型掩盖问题。",
        "- 唯一键和CHECK规则将在业务确认后回写逐项决策登记；纯性能索引仍需在P3-E06压测中验证。",
        "- 本清单不授权连接或修改旧库，不授权执行生产迁移。",
        "",
    ])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    output = root / args.output
    content = render(root)
    if args.check:
        if not output.exists() or output.read_text(encoding="utf-8") != content:
            print(f"[FAIL] P3-E09 decision catalog drift: {output}")
            return 1
        print("[PASS] P3-E09 concrete data-model decision catalog")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(content, encoding="utf-8")
    print(f"[WRITE] {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
