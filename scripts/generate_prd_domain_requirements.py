from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


FORMAL_ROW_RE = re.compile(r"^\|\s*([A-Z]+-\d+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|")
STANDARD_HEADING_RE = re.compile(r"^(#{3,4})\s+\d+\.\d+(?:\.\d+)?\s+([A-Z]+-\d+)\s+(.+?)\s*$")
PAREN_HEADING_RE = re.compile(r"^(#{3})\s+\d+\.\d+\s+(.+?)[（(]([A-Z]+-\d+)[）)]\s*$")
ANY_HEADING_RE = re.compile(r"^(#{3,4})\s+")


@dataclass(frozen=True)
class Requirement:
    identifier: str
    name: str
    priority: str
    version: str
    body: str


DOMAIN_ORDER = ("PLT", "CUS", "PROJ", "COM", "SOL", "IMP", "CUT", "ACC", "AST", "RES", "SRV", "KNO", "ANA")

DOMAIN_NAMES = {
    "PLT": "平台公共能力",
    "CUS": "客户与服务关系",
    "PROJ": "项目治理",
    "COM": "合同订单履约",
    "SOL": "交付准备与方案",
    "IMP": "现场实施",
    "CUT": "变更切换与稳定治理",
    "ACC": "验收与项目闭环",
    "AST": "资产管理",
    "RES": "资源与外包",
    "SRV": "服务运营",
    "KNO": "技术知识治理",
    "ANA": "经营分析",
}

DOMAIN_BOUNDARIES = {
    "PLT": "身份、权限、流程、状态机、字典、文件、通知、审计、集成与平台非功能基线。",
    "CUS": "客户档案、项目联系人、服务等级及客户关系数据；不拥有项目交付事实。",
    "PROJ": "项目主档、项目层级、任务WBS、计划、团队、风险、阶段和项目关闭。",
    "COM": "合同、订单、订单行及履约范围的权威引用；本轮PRD无直接正式需求。",
    "SOL": "工前准备、工勘、需求分析、施工计划、实施方案和方案基线。",
    "IMP": "现场到货签收、安装、配置、联调、实施风险及现场实施证据。",
    "CUT": "割接任务、分级、采集清单、方案、审批、执行、回退、观察和闭环。",
    "ACC": "培训、满意度、验收报告、交付件、项目闭环、回访问卷和关闭门禁。",
    "AST": "设备身份、设备档案、配置日志、设备来源同步和资产关联；维保信息仅为设备基本信息。",
    "RES": "服务商、转包、付款、外包审批以及资源与工时协作。",
    "SRV": "服务工单、割接保障工单、巡检任务、巡检报告和服务运营闭环。",
    "KNO": "技术公告、版本影响、知识命中与处置；本轮PRD无直接正式需求。",
    "ANA": "工时、人效、项目状态和经营指标的只读统计分析。",
}

PREFIX_OWNER = {
    "PM": "PROJ",
    "PRE": "SOL",
    "PLN": "SOL",
    "SCH": "SOL",
    "EXE": "IMP",
    "ACC": "ACC",
    "CLO": "ACC",
    "WO": "SRV",
    "SUB": "RES",
    "CUS": "CUS",
    "EQP": "AST",
    "RPT": "ANA",
    "CUT": "CUT",
    "INS": "SRV",
    "INT": "PLT",
    "NFR": "PLT",
    "AUT": "PLT",
    "CHG": "PLT",
}

INTEGRATION_OWNER = {
    "INT-01": "PROJ",
    "INT-02": "AST",
    "INT-03": "CUS",
    "INT-04": "KNO",
    "INT-05": "PLT",
    "INT-06": "AST",
    "INT-07": "RES",
    "INT-08": "SRV",
    "INT-09": "PLT",
    "INT-10": "PLT",
    "INT-11": "PLT",
    "INT-12": "PLT",
}

OUT_SCOPE = {
    "WO-07": ("SRV", "平台通用割接时效管控", "仅保留CUT-05专项提前时间规则"),
    "WO-11": ("SRV", "维保机会点工单", "维保经营能力不建设"),
    "FR-PROJ-023": ("PROJ", "项目日报与周报", "结构化项目数据及看板聚合继续保留"),
    "FR-SRV-019": ("SRV", "独立维保档案经营能力", "设备档案维保基本信息保留"),
    "FR-SRV-020": ("SRV", "续保空间分层视图", "不建设续保空间能力"),
    "FR-SRV-021": ("SRV", "续保任务下发与跟踪", "不建设续保任务能力"),
    "FR-SRV-022": ("SRV", "维保客户回访问卷", "不建设维保经营回访"),
    "FR-SRV-023": ("SRV", "续保率与空间分析", "不建设续保率、续保空间和过保空间报表"),
}


def _find_index_rows(lines: list[str], marker: str, end_marker: str) -> dict[str, tuple[str, str, str]]:
    start = next(i for i, line in enumerate(lines) if marker in line)
    end = next(i for i, line in enumerate(lines[start + 1 :], start + 1) if end_marker in line)
    rows: dict[str, tuple[str, str, str]] = {}
    for line in lines[start:end]:
        match = FORMAL_ROW_RE.match(line)
        if match:
            rows[match.group(1)] = (match.group(2).strip(), match.group(3).strip(), match.group(4).strip())
    return rows


def _find_v3_rows(lines: list[str]) -> dict[str, tuple[str, str, str]]:
    start = next(i for i, line in enumerate(lines) if "#### A.3.1 已编号演进项" in line)
    end = next(i for i, line in enumerate(lines[start + 1 :], start + 1) if "#### A.3.2 跨需求演进方向" in line)
    rows: dict[str, tuple[str, str, str]] = {}
    for line in lines[start:end]:
        match = FORMAL_ROW_RE.match(line)
        if match:
            rows[match.group(1)] = (match.group(2).strip(), match.group(3).strip(), match.group(4).strip())
    return rows


def _find_cross_v3_rows(lines: list[str]) -> dict[str, tuple[str, str, str]]:
    start = next(i for i, line in enumerate(lines) if "#### A.3.2 跨需求演进方向" in line)
    end = next(i for i, line in enumerate(lines[start + 1 :], start + 1) if "### A.4 OUT_OF_SCOPE索引" in line)
    rows: dict[str, tuple[str, str, str]] = {}
    for line in lines[start:end]:
        columns = [column.strip() for column in line.strip().strip("|").split("|")]
        if len(columns) == 4 and columns[0] not in {"来源需求", "---"}:
            rows[columns[0]] = (columns[1], columns[2], columns[3])
    return rows


def _extract_requirements(text: str, formal_ids: set[str]) -> dict[str, Requirement]:
    lines = text.splitlines()
    matches: list[tuple[int, int, str, str, str]] = []
    for index, line in enumerate(lines):
        standard = STANDARD_HEADING_RE.match(line)
        paren = PAREN_HEADING_RE.match(line)
        if standard:
            level, identifier, name = len(standard.group(1)), standard.group(2), standard.group(3)
        elif paren and paren.group(3).split("-", 1)[0] in {"CUT", "INS"}:
            level, name, identifier = len(paren.group(1)), paren.group(2), paren.group(3)
        else:
            continue
        if identifier not in formal_ids:
            continue
        if any(item[2] == identifier for item in matches):
            continue
        end = len(lines)
        for candidate in range(index + 1, len(lines)):
            if (
                STANDARD_HEADING_RE.match(lines[candidate])
                or (
                    PAREN_HEADING_RE.match(lines[candidate])
                    and PAREN_HEADING_RE.match(lines[candidate]).group(3).split("-", 1)[0] in {"CUT", "INS"}
                )
                or lines[candidate].startswith("### ")
            ):
                end = candidate
                break
        body = "\n".join(lines[index + 1 : end]).strip()
        matches.append((index, level, identifier, name.strip(), body))

    requirements: dict[str, Requirement] = {}
    for _, _, identifier, name, body in matches:
        priority = "【待确认】"
        version = "【待确认】"
        for line in body.splitlines():
            columns = [column.strip() for column in line.strip().strip("|").split("|")]
            if len(columns) >= 2 and columns[0] == "优先级":
                priority = columns[1]
            if len(columns) >= 2 and columns[0] == "目标版本":
                version = columns[1]
        requirements[identifier] = Requirement(identifier, name, priority, version, body)
    missing = sorted(formal_ids - requirements.keys())
    if missing:
        raise RuntimeError(f"PRD formal requirement blocks missing: {', '.join(missing)}")
    return requirements


def _owner(identifier: str) -> str:
    if identifier in INTEGRATION_OWNER:
        return INTEGRATION_OWNER[identifier]
    return PREFIX_OWNER.get(identifier.split("-", 1)[0], "PLT")


def _domain_requirements(requirements: dict[str, Requirement], domain: str) -> list[Requirement]:
    return [item for item in requirements.values() if _owner(item.identifier) == domain]


def _render_v3(domain: str, v3_rows: dict[str, tuple[str, str, str]], cross_rows: dict[str, tuple[str, str, str]]) -> str:
    rows: list[str] = []
    for identifier, (name, priority, note) in v3_rows.items():
        if _owner(identifier) == domain:
            rows.append(f"| {identifier} | {name} | {priority} | {note} |")
    for source, (name, priority, boundary) in cross_rows.items():
        if _owner(source) == domain:
            rows.append(f"| {source}（跨需求方向） | {name} | {priority} | {boundary} |")
    if not rows:
        return "本领域暂无直接对应的V3演进项；后续新增方向必须先完成Owner、范围和验收边界评审。"
    return "| 编号 | 演进方向 | 优先级 | 当前版本边界 |\n| --- | --- | --- | --- |\n" + "\n".join(rows)


def _render_out_scope(domain: str) -> str:
    rows = [f"| {identifier} | {name} | {note} |" for identifier, (owner, name, note) in OUT_SCOPE.items() if owner == domain]
    if not rows:
        return "本领域暂无直接排除项。"
    return "| 追溯编号 | 排除主题 | 处理结论 |\n| --- | --- | --- |\n" + "\n".join(rows)


def render_domain(domain: str, requirements: dict[str, Requirement], formal_rows: dict[str, tuple[str, str, str]], v3_rows: dict[str, tuple[str, str, str]], cross_rows: dict[str, tuple[str, str, str]]) -> str:
    selected = sorted(_domain_requirements(requirements, domain), key=lambda item: list(requirements).index(item.identifier))
    name = DOMAIN_NAMES[domain]
    rows = [f"| {item.identifier} | {formal_rows[item.identifier][0]} | {item.name} | {item.version} | {item.priority} |" for item in selected]
    detail = []
    for item in selected:
        detail.append(f"## {item.identifier} {item.name}\n\n{item.body}")
    if not detail:
        detail.append("本轮PRD未定义本领域直接负责的V1/V2正式需求，不新增业务能力；本领域仅承接其他领域通过依赖、输入、输出或事件引用的协作边界。")
    return f"""# {domain}领域需求：{name}

> 文档状态：PRD重建候选稿<br>
> 来源基线：`需求/PRD-项目实施交付管理平台.md`（V1.4，基线整改中）<br>
> 领域编码：`{domain}`<br>
> 业务Owner：{name}<br>
> 详细需求：{len(selected)}项

## 1. 领域目标与边界

{DOMAIN_BOUNDARIES[domain]}

本领域只定义自身拥有的业务规则、数据、权限和验收标准；跨领域能力通过需求依赖、输入输出和事件协作，不复制其他领域Owner的业务事实。

## 2. 需求清单

| 需求编号 | PRD来源 | 需求名称 | 目标版本 | 优先级 |
| --- | --- | --- | --- | --- |
{chr(10).join(rows) if rows else '| 无 | 无 | 本轮PRD无直接正式需求 | - | - |'}

## 3. 详细需求

{chr(10) .join(detail)}

## 4. V3演进范围

{_render_v3(domain, v3_rows, cross_rows)}

## 5. OUT_OF_SCOPE追溯

{_render_out_scope(domain)}

## 6. 领域验收门禁

- 本领域正式需求全部直接来源于PRD需求块，业务验收标准以PRD为准。
- V1/V2需求不得以V3方向替代；V3不进入当前开发与验收。
- 领域状态必须遵循“初始化可扩展状态定义+受控状态机”；扩展状态不得绕过权限、责任、审批或证据门禁。
- 跨领域写入必须通过应用服务、接口或事件完成，不直接修改其他领域业务事实。
- 权限拒绝、版本冲突、重复提交、外部失败和人工降级必须保留可追溯记录。
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="按最新PRD格式生成13份领域需求规格")
    parser.add_argument("--prd", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    text = args.prd.read_text(encoding="utf-8")
    lines = text.splitlines()
    formal_rows = _find_index_rows(lines, "### A.1 V1/V2正式需求索引", "### A.2 正式需求统计")
    v3_rows = _find_v3_rows(lines)
    cross_rows = _find_cross_v3_rows(lines)
    requirements = _extract_requirements(text, set(formal_rows))
    if len(requirements) != 100:
        raise RuntimeError(f"expected 100 formal requirements, got {len(requirements)}")
    args.output.mkdir(parents=True, exist_ok=True)
    for domain in DOMAIN_ORDER:
        target = args.output / f"{domain}-{DOMAIN_NAMES[domain]}需求规格.md"
        target.write_text(render_domain(domain, requirements, formal_rows, v3_rows, cross_rows), encoding="utf-8")
        print(f"WROTE {target} ({len(_domain_requirements(requirements, domain))} formal requirements)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
