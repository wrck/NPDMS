#!/usr/bin/env python3
"""Validate the formal-baseline invariants of the project-delivery PRD."""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from collections import Counter
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


def cutover_flow_contract(text: str) -> dict[str, bool]:
    """Validate the confirmed CUT P1-P6 business-flow boundary."""
    cutover = section(text, "第十章 割接管理模块功能需求")
    formal_ids = {
        req_id
        for req_id, block in requirement_blocks(text)
        if re.search(r"(?m)^\|\s*目标版本\s*\|\s*V[12](?:[^|]*)\|\s*$", block)
    }
    return {
        "CUT-01核心任务保留": "CUT-01" in formal_ids
        and ("P1首页任务接入" in cutover or "P1是任务接入入口" in cutover)
        and "P6割接跟踪与闭环" in cutover,
        "CUT-11退出当前范围": "CUT-11" not in formal_ids and "割接保障任务（CUT-11）" not in cutover,
        "问卷人工判级": "一线工程师提交问卷和人工等级" in cutover and "服务经理在P5审批中复核" in cutover,
        "P3配置缺口不阻断": "允许一线补充自定义项并标记配置缺口" in cutover and "不直接阻断割接主流程" in cutover,
        "完整方案轻量校验": "文件有效性、安全性、方案归属和人工确认" in cutover and "不强制解析或补齐在线模板字段" in cutover,
        "P5否项驳回": "任一项为“否”必须填写不合理原因并驳回" in cutover,
        "专项提前时间自然日": "按自然日计算" in cutover and "不新增平台通用时效" in cutover,
        "保障人员受控修改": "审批通过后仍允许修改保障人员安排" in cutover and "角色或任务职责变化必须创建新方案版本" in cutover,
        "P6提交即归档": "提交即形成归档闭环事实并结束本次割接流程" in cutover,
        "无步骤观察扩张": not re.search(
            r"(?<!不)建立逐步骤执行状态机|进入稳定观察|满足稳定观察要求|稳定观察(?:通过|未通过)",
            cutover,
        ),
        "无遗留项归档阻断": "全部遗留项闭环后方可归档" not in cutover and "遗留项进入待办跟踪" not in cutover,
    }


def project_workbench_contract(text: str) -> dict[str, bool]:
    """Validate the confirmed Stage -> ProjectTask business-workbench boundary."""
    blocks = dict(requirement_blocks(text))
    pm03 = blocks.get("PM-03", "")
    pm11 = blocks.get("PM-11", "")
    cut01 = blocks.get("CUT-01", "")
    cut03 = blocks.get("CUT-03", "")
    overview_tabs = ("基本信息", "项目树", "团队成员", "项目任务", "设备清单", "实施范围")
    binding_kinds = ("TASK_NATIVE", "业务对象", "业务组件", "动态表单", "审批", "组合")
    return {
        "模板定义StageTask绑定": all(
            marker in pm03
            for marker in (
                "StageDefinition → TaskDefinition",
                "WorkBinding",
                "PermissionPolicy",
                "CompletionRule",
                "GateRef",
            )
        ),
        "不重复配置业务导航": "不再维护一套与ProjectTask重复的“业务导航配置”" in pm03,
        "WorkBinding类型完整": all(kind in pm03 for kind in binding_kinds),
        "WorkBinding统一必填": "每个ProjectTask必须且只能有一个当前有效`WorkBinding`" in pm03
        and "默认`TASK_NATIVE`" in pm03
        and "通用任务必须显式使用TASK_NATIVE" in pm03,
        "StageTask导航不限制树深": "阶段作为一级导航、ProjectTask作为二级业务导航" in pm11
        and "不把业务任务限制为固定两层" in pm11,
        "通用任务详情基础能力": "`TASK_NATIVE`直接使用ProjectTask通用详情执行" in pm11
        and "ProjectTask既是执行编排节点，也是`TASK_NATIVE`默认业务实体" in pm11,
        "绑定任务按关系执行": "其他`WorkBinding`类型" in pm11
        and all(marker in pm11 for marker in ("业务对象", "业务组件", "动态表单", "审批", "组合视图")),
        "通用详情不替代绑定业务": "通用基础信息不得替代非`TASK_NATIVE`绑定的业务执行" in pm11,
        "项目概览六页签": all(tab in pm11 for tab in overview_tabs),
        "任务完成按绑定类型判定": "CompletionRule" in pm11
        and "`TASK_NATIVE`校验ProjectTask自身必填信息与合法状态" in pm11
        and "其他类型校验绑定业务事实" in pm11
        and "非`TASK_NATIVE`任务不得通过通用“完成任务”动作绕过目标业务事实" in pm11,
        "割接入口与五步工作台": "P1是任务接入入口" in cut01
        and "五步工作台按P2～P6展示" in cut01,
        "CUT03同一P3工作台": "同一个P3任务工作台" in cut03
        and "不新增采集阶段" in cut03
        and "CollectionTask" in cut03,
        "采集结果不等于业务通过": "不把技术回调成功直接解释为风险项通过" in cut03
        and "任何回调不得直接把采集项判定为通过" in cut03,
    }


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
    formal_statistics = section(appendix_a, "A.2 正式需求统计") if appendix_a else ""
    expected_statistics = {
        "V1/V2正式需求总数": len(formal),
        "V1主版本需求": formal_versions["V1"],
        "V2主版本需求": formal_versions["V2"],
    }
    actual_statistics = {
        label: int(value)
        for label, value in re.findall(
            r"(?m)^\|\s*(V1/V2正式需求总数|V1主版本需求|V2主版本需求)\s*\|\s*(\d+)条\s*\|$",
            formal_statistics,
        )
    }
    add(
        checks,
        "附录A.2正式需求统计一致",
        actual_statistics == expected_statistics,
        f"期望={expected_statistics}；实际={actual_statistics}",
    )
    slice_index = section(appendix_a, "A.1.1 Requirement目标版本切片") if appendix_a else ""
    primary_slice_keys = {
        f"{req_id}@{version_match.group(1)}"
        for req_id, block in formal
        if (version_match := re.search(r"(?m)^\|\s*目标版本\s*\|\s*(V[12])(?:[^|]*)\|\s*$", block))
    }
    supplemental_slice_keys = re.findall(
        rf"(?m)^\|\s*({REQ_ID})@(V[12])\s*\|\s*\1\s*\|\s*\2\s*\|",
        slice_index,
    )
    all_slice_keys = list(primary_slice_keys) + [f"{req_id}@{version}" for req_id, version in supplemental_slice_keys]
    duplicate_slice_keys = sorted(key for key in set(all_slice_keys) if all_slice_keys.count(key) > 1)
    slice_versions = Counter(key.rsplit("@", 1)[1] for key in all_slice_keys)
    add(
        checks,
        "Requirement目标版本切片完整",
        len(primary_slice_keys) == 100
        and len(supplemental_slice_keys) == 11
        and len(all_slice_keys) == 111
        and not duplicate_slice_keys
        and slice_versions == Counter({"V1": 53, "V2": 58}),
        f"主切片={len(primary_slice_keys)}；补充={len(supplemental_slice_keys)}；总计={len(all_slice_keys)}；"
        f"V1={slice_versions['V1']}；V2={slice_versions['V2']}；重复={','.join(duplicate_slice_keys) or '无'}",
    )
    expected_slice_statistics = {
        "Requirement目标版本切片总数": 111,
        "V1目标版本切片": 53,
        "V2目标版本切片": 58,
    }
    actual_slice_statistics = {
        label: int(value)
        for label, value in re.findall(
            r"(?m)^\|\s*(Requirement目标版本切片总数|V1目标版本切片|V2目标版本切片)\s*\|\s*(\d+)个\s*\|$",
            formal_statistics,
        )
    }
    add(
        checks,
        "附录A.2目标版本切片统计一致",
        actual_slice_statistics == expected_slice_statistics,
        f"期望={expected_slice_statistics}；实际={actual_slice_statistics}",
    )
    footer = prd.rsplit("**文档结束**", 1)[-1] if "**文档结束**" in prd else ""
    add(checks, "文末基线版本一致", f"PRD {version}正式基线" in footer, f"文末应声明PRD {version}正式基线")
    add(checks, "文末正式需求数一致", f"{len(formal)}项V1/V2正式需求" in footer, f"正文={len(formal)}项")
    add(
        checks,
        "文末主版本统计一致",
        all(f"{candidate} {count}项" in footer for candidate, count in formal_versions.items()),
        "、".join(f"{candidate}={count}" for candidate, count in formal_versions.items()),
    )
    add(
        checks,
        "文末目标版本切片统计一致",
        "111个正式目标版本切片" in footer and "V1 53个" in footer and "V2 58个" in footer,
        "期望总计111、V1 53、V2 58",
    )
    add(checks, "INT-12进入正式索引", "INT-12" in formal_index_ids, "INT-12必须为V1公共能力")
    add(checks, "排除编号未进正式索引", not ({"WO-07", "WO-11"} & set(formal_index_ids)), "WO-07/WO-11仅用于排除追溯")
    v3_numbered = section(appendix_a, "A.3.1 已编号演进项") if appendix_a else ""
    v3_numbered_ids = set(re.findall(r"(?m)^\|\s*([A-Z]+(?:-[A-Z0-9]+)?-\d+)\s*\|", v3_numbered))
    cross_evolution = section(appendix_a, "A.3.2 跨需求演进方向") if appendix_a else ""
    expected_cross_evolution = {"CLO-05→ACC-02", "SUB-03", "EXE-05", "CUT-06", "INT-03"}
    cross_evolution_ids = set(
        re.findall(r"(?m)^\|\s*(CLO-05→ACC-02|SUB-03|EXE-05|CUT-06|INT-03)\s*\|", cross_evolution)
    )
    add(
        checks,
        "V1.8演进统计",
        len(v3_numbered_ids) == 31 and cross_evolution_ids == expected_cross_evolution,
        f"编号V3={len(v3_numbered_ids)}；跨需求={len(cross_evolution_ids)}；"
        f"缺少={','.join(sorted(expected_cross_evolution - cross_evolution_ids)) or '无'}",
    )
    add(
        checks,
        "配置基础前置与明确延期例外",
        all(
            marker in prd
            for marker in (
                "未明确后置的配置能力必须不晚于首个消费业务结果",
                "PRD或需求方已经明确标为V2、V3或后置的内容保持既定版本",
                "CUT-07、CUT-09、CUT-10共同构成CUT-03动态匹配",
            )
        )
        and "规则配置与使用效率增强" not in prd,
        "动态模板/表单/匹配配置须前置；正文明确延期保持原版本；不得保留未定义V2效率增强",
    )
    add(
        checks,
        "V1.8退出需求边界",
        not ({"COM-02", "IMP-02", "ACC-05"} & set(formal_index_ids))
        and "ACC-05" in v3_numbered_ids
        and not ({"COM-02", "IMP-02"} & v3_numbered_ids),
        "ACC-05仅进入V3；COM-02/IMP-02不得进入正式或V3索引",
    )
    state_markers = ("current_stage", "lifecycle_status", "NORMAL_CLOSED", "EXCEPTION_CLOSED", "派生展示状态")
    add(checks, "V1.8项目状态分层", all(marker in prd for marker in state_markers), f"必需标记={','.join(state_markers)}")

    for name, passed in cutover_flow_contract(prd).items():
        add(checks, f"CUT流程-{name}", passed, "割接流程必须符合0807流程设计及已确认业务决策")

    for name, passed in project_workbench_contract(prd).items():
        add(checks, f"工作台-{name}", passed, "项目工作区与割接工作台必须符合已确认线框设计")

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
    appendix_c_rows = [
        tuple(cell.strip() for cell in match.groups())
        for match in re.finditer(
            r"(?m)^\|\s*(\d+)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$",
            appendix_c,
        )
    ]
    appendix_c_names = {row[1] for row in appendix_c_rows}
    satisfaction_rows = [row for row in appendix_c_rows if row[1] == "满意度任务与问卷"]
    add(
        checks,
        "附录C不含工单核心对象",
        "工单" not in appendix_c_names and "WorkOrder" not in appendix_c_names,
        "工单已退出V1/V2核心对象；附录C必须与正文3.3一致",
    )
    add(
        checks,
        "附录C满意度核心对象",
        len(satisfaction_rows) == 1 and "ACC-02" in satisfaction_rows[0][3],
        "必须唯一列出满意度任务与问卷并以正式需求ACC-02为主要来源",
    )

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
