#!/usr/bin/env python3
"""Validate the formal-baseline invariants of the project-delivery PRD."""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from validate_prd_semantics import validate_text as validate_semantics


REQ_ID = r"[A-Z]+-\d{2}"
FORMAL_REQUIRED_MARKERS = {
    "用户角色": r"\|\s*用户角色\s*\|",
    "目标版本": r"\|\s*目标版本\s*\|\s*V[12][^|]*\|",
    "业务场景/描述": r"\*\*(?:业务场景(?:与需求描述)?|功能描述|需求描述)[：:]\*\*",
    "核心业务规则": r"\*\*核心业务规则[：:]\*\*",
    "用户故事": r"\*\*用户故事[：:]\*\*",
    "业务验收标准": r"\*\*(?:业务)?验收标准[：:]\*\*",
    "涉及数据": r"\*\*涉及数据(?:字段)?[：:]\*\*",
    "权限与数据范围": r"\*\*权限与数据范围[：:]\*\*",
    "异常降级留痕": r"\*\*异常、降级及留痕(?:要求)?[：:]\*\*",
    "依赖关系": r"\*\*依赖关系[：:]\*\*",
}


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def section(text: str, heading: str) -> str:
    match = re.search(rf"(?m)^(#{{2,6}})\s+{re.escape(heading)}\s*$", text)
    if not match:
        return ""
    level = len(match.group(1))
    next_heading = re.search(rf"(?m)^#{{2,{level}}}\s+", text[match.end() :])
    end = match.end() + next_heading.start() if next_heading else len(text)
    return text[match.start() : end]


def requirement_blocks(text: str) -> list[tuple[str, str]]:
    matches = list(re.finditer(rf"(?m)^\|\s*需求编号\s*\|\s*({REQ_ID})\s*\|\s*$", text))
    blocks: list[tuple[str, str]] = []
    for match in matches:
        heading_candidates = [text.rfind(f"\n{'#' * level} ", 0, match.start()) for level in (3, 4)]
        start = max(heading_candidates)
        start = 0 if start < 0 else start + 1
        heading = re.match(r"(#{3,4})\s+", text[start:])
        level = len(heading.group(1)) if heading else 4
        next_heading = re.search(rf"(?m)^#{{1,{level}}}\s+", text[match.end() :])
        end = match.end() + next_heading.start() if next_heading else len(text)
        blocks.append((match.group(1), text[start:end]))
    return blocks


def add(checks: list[Check], name: str, passed: bool, detail: str) -> None:
    checks.append(Check(name, passed, detail))


def validate(prd_path: Path, report_path: Path, version: str, status: str) -> list[Check]:
    prd = read_text(prd_path)
    report = read_text(report_path)
    checks: list[Check] = []

    add(
        checks,
        "文档版本",
        bool(re.search(rf"(?m)^\|\s*文档版本\s*\|\s*{re.escape(version)}\s*\|$", prd)),
        f"期望 {version}",
    )
    add(
        checks,
        "文档状态",
        bool(re.search(rf"(?m)^\|\s*文档状态\s*\|\s*{re.escape(status)}\s*\|$", prd)),
        f"期望 {status}",
    )

    unresolved = [(i + 1, line.strip()) for i, line in enumerate(prd.splitlines()) if re.search(r"【待确认】|\bTBD\b|\bTODO\b", line, re.I)]
    add(checks, "活动未决标记", not unresolved, "；".join(f"L{line}:{value}" for line, value in unresolved[:8]) or "0项")

    blocks = requirement_blocks(prd)
    ids = [req_id for req_id, _ in blocks]
    duplicates = sorted({req_id for req_id in ids if ids.count(req_id) > 1})
    add(checks, "需求编号唯一", bool(ids) and not duplicates, f"解析{len(ids)}项；重复={','.join(duplicates) or '无'}")

    formal: list[tuple[str, str]] = []
    v3_in_body: list[str] = []
    missing_versions: list[str] = []
    for req_id, block in blocks:
        version_match = re.search(r"(?m)^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", block)
        if not version_match:
            missing_versions.append(req_id)
        elif version_match.group(1) in {"V1", "V2"}:
            formal.append((req_id, block))
        else:
            v3_in_body.append(req_id)
    add(checks, "逐项版本归属", not missing_versions, f"缺少={','.join(missing_versions[:20]) or '无'}")
    add(checks, "V3与正式正文分离", not v3_in_body, f"仍在详细需求块={','.join(v3_in_body) or '无'}")

    missing_by_field: dict[str, list[str]] = {name: [] for name in FORMAL_REQUIRED_MARKERS}
    bad_acceptance: list[str] = []
    banned_formal: list[str] = []
    forbidden_terms = re.compile(r"日报|周报|续保空间|续保率|过保空间|维保机会|平台通用割接时效")
    exclusion_terms = re.compile(r"不依赖|不提供|不得|排除|不建设|不包含|不产生|不作为|已移出|不恢复")
    for req_id, block in formal:
        for name, pattern in FORMAL_REQUIRED_MARKERS.items():
            if not re.search(pattern, block):
                missing_by_field[name].append(req_id)
        if not (re.search(r"(?m)^- \*\*WHEN\*\*", block) and re.search(r"(?m)^- \*\*THEN\*\*", block)):
            bad_acceptance.append(req_id)
        active_forbidden = any(
            forbidden_terms.search(line) and not exclusion_terms.search(line)
            for line in block.splitlines()
        )
        if active_forbidden or req_id in {"WO-07", "WO-11"}:
            banned_formal.append(req_id)

    for name, missing in missing_by_field.items():
        add(checks, f"V1/V2字段-{name}", not missing, f"缺少{len(missing)}项：{','.join(missing[:20]) or '无'}")
    add(checks, "V1/V2验收可观察", not bad_acceptance, f"缺少WHEN/THEN={','.join(bad_acceptance[:20]) or '无'}")
    add(checks, "V1/V2验收归属正确", not bad_acceptance, "验收条款必须位于对应需求块并包含WHEN/THEN")
    add(checks, "排除能力未进入正式需求", not banned_formal, f"冲突={','.join(sorted(set(banned_formal))) or '无'}")

    v3 = section(prd, "V3演进范围")
    add(checks, "V3演进章节", bool(v3), "要求存在二级章节‘V3演进范围’")
    detailed_v3 = bool(re.search(r"\*\*(?:业务)?验收标准[：:]\*\*|^- \*\*WHEN\*\*", v3, re.M)) if v3 else False
    add(checks, "V3无当前验收承诺", bool(v3) and not detailed_v3, "V3只保留目标、范围、前置条件和演进方向")

    excluded = section(prd, "OUT_OF_SCOPE范围排除清单")
    required_excluded = ["WO-07", "WO-11", "FR-PROJ-023", "FR-SRV-019", "FR-SRV-020", "FR-SRV-021", "FR-SRV-022", "FR-SRV-023"]
    missing_excluded = [item for item in required_excluded if item not in excluded]
    add(checks, "排除清单完整", bool(excluded) and not missing_excluded, f"缺少={','.join(missing_excluded) or '无'}")

    appendix_a = section(prd, "附录A 需求索引与验收覆盖")
    formal_index = section(appendix_a, "A.1 V1/V2正式需求索引") if appendix_a else ""
    formal_index_ids = re.findall(rf"(?m)^\|\s*({REQ_ID})\s*\|", formal_index)
    add(checks, "正式索引存在", bool(formal_index_ids), f"索引{len(formal_index_ids)}项")
    add(checks, "正式索引与正文一致", set(formal_index_ids) == {req_id for req_id, _ in formal}, f"索引{len(set(formal_index_ids))}项/正文{len(formal)}项")
    formal_versions = {
        candidate: sum(
            bool(re.search(rf"(?m)^\|\s*目标版本\s*\|\s*{candidate}(?:[^|]*)\|\s*$", block))
            for _, block in formal
        )
        for candidate in ("V1", "V2")
    }
    footer = prd.rsplit("**文档结束**", 1)[-1] if "**文档结束**" in prd else ""
    add(checks, "文末基线版本一致", f"PRD {version}正式基线" in footer, f"文末应声明PRD {version}正式基线")
    add(checks, "文末正式需求数一致", f"{len(formal)}项V1/V2正式需求" in footer, f"正文={len(formal)}项")
    add(
        checks,
        "文末主版本统计一致",
        all(f"{candidate} {count}项" in footer for candidate, count in formal_versions.items()),
        "、".join(f"{candidate}={count}" for candidate, count in formal_versions.items()),
    )
    add(checks, "INT-12进入正式索引", "INT-12" in formal_index_ids, "INT-12必须为V1公共能力")
    add(checks, "排除编号未进正式索引", not ({"WO-07", "WO-11"} & set(formal_index_ids)), "WO-07/WO-11仅用于排除追溯")

    appendix_b = section(prd, "附录B 集成系统与平台组件清单")
    external = section(appendix_b, "B.1 外部系统") if appendix_b else ""
    components = section(appendix_b, "B.2 平台组件") if appendix_b else ""
    add(checks, "集成清单分层", bool(external) and bool(components), "外部系统与平台组件必须分开计数")
    add(checks, "CRM统一命名", bool(external) and not re.search(r"(?m)^\|[^\n]*\|\s*SMS\s*\|", external), "SMS不得作为独立外部系统")
    add(checks, "PMS内部边界", bool(external) and not re.search(r"(?m)^\|[^\n]*\|\s*PMS\s*\|", external), "PMS不得作为外部系统")
    add(checks, "采集平台组件边界", "现有采集平台" in components, "采集平台作为平台组件/子应用")

    appendix_c = section(prd, "附录C 核心业务对象与数据元索引")
    core_objects = ["项目", "项目任务", "设备", "设备凭证", "采集任务"]
    missing_objects = [name for name in core_objects if name not in appendix_c]
    add(checks, "核心对象索引", bool(appendix_c) and not missing_objects, f"缺少={','.join(missing_objects) or '无'}")

    semantic_issues = validate_semantics(prd)
    semantic_ids = sorted({issue.req_id for issue in semantic_issues})
    add(
        checks,
        "V1/V2语义质量",
        not semantic_issues,
        f"问题{len(semantic_issues)}项；需求={','.join(semantic_ids[:20]) or '无'}",
    )

    digest = hashlib.sha256(prd_path.read_bytes()).hexdigest().upper()
    report_hashes = {value.upper() for value in re.findall(r"`([0-9A-Fa-f]{64})`", report)}
    add(checks, "差异报告PRD版本", version in report, f"报告应登记{version}")
    add(checks, "差异报告SHA-256", digest in report_hashes, f"当前={digest}")

    return checks


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prd", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--expected-version", required=True)
    parser.add_argument("--expected-status", required=True)
    args = parser.parse_args()

    checks = validate(args.prd, args.report, args.expected_version, args.expected_status)
    for check in checks:
        print(f"[{'PASS' if check.passed else 'FAIL'}] {check.name}: {check.detail}")
    failed = sum(not check.passed for check in checks)
    print(f"SUMMARY: {len(checks) - failed} passed, {failed} failed, {len(checks)} total")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
