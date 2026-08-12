#!/usr/bin/env python3
"""Reject template-like or non-verifiable content in formal PRD requirements."""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


REQ_ID_PATTERN = r"(?:PM|PRE|PLN|SCH|EXE|ACC|CLO|WO|SUB|CUS|EQP|RPT|CUT|INS|INT|AUT|CHG|NFR)-\d{2}"
MIN_ACCEPTANCE_SCENARIOS = 2
MIN_DATA_TOKENS = 3

FIELD_LABELS = {
    "业务场景与需求描述": ("业务场景与需求描述", "业务场景", "功能描述", "需求描述"),
    "核心业务规则": ("核心业务规则",),
    "业务验收标准": ("业务验收标准", "验收标准"),
    "涉及数据字段": ("涉及数据字段", "涉及数据"),
    "权限与数据范围": ("权限与数据范围",),
    "异常、降级及留痕要求": ("异常、降级及留痕要求", "异常、降级及留痕"),
}

GENERIC_SNIPPETS = {
    "核心业务规则": {
        "GENERIC_RULE": (
            "本需求的状态、字段、门禁、审批、同步频率和版本边界，"
            "以本条业务场景、已确认事项及验收标准中明确的内容为准"
        ),
    },
    "业务验收标准": {
        "GENERIC_ACCEPTANCE": "依赖条件满足且有权用户在本需求适用场景发起",
        "GENERIC_ACCEPTANCE_RESULT": "产生以下已定义业务结果",
        "GENERIC_ACCEPTANCE_TRACE": "处理结果关联原业务对象",
        "GENERIC_ACCEPTANCE_FAILURE": "必填数据、角色权限、数据范围或前置门禁不满足",
    },
    "权限与数据范围": {
        "GENERIC_PERMISSION": "仅本条“用户角色”及其被明确授权人员可执行",
    },
    "异常、降级及留痕要求": {
        "GENERIC_EXCEPTION": (
            "前置依赖或外部能力不可用时，执行本条或关联集成需求已明确的降级路径"
        ),
    },
}

GENERIC_DATA_PATTERNS = (
    r"相关信息",
    r"业务所需字段",
    r"以实际为准",
    r"相关数据",
    r"对应字段",
    r"必要字段",
    r"本条业务场景中已明确列示的业务字段",
)

ACTION_PATTERN = re.compile(r"查看|创建|编辑|修改|提交|审批|批准|驳回|授权|导出|执行|删除|维护|上传|下载")
SCOPE_PATTERN = re.compile(
    r"本项目|负责项目|当前项目|项目范围|数据范围|授权范围|后代项目|平级项目|"
    r"租户|办事处|本人|创建人|只读|不可见|敏感字段|明文|全部项目|所属项目"
)
FAILURE_TRIGGER_PATTERN = re.compile(
    r"失败|异常|缺失|不足|不存在|不等于|不一致|无权|不可用|超时|超出|跨租户|循环|重复|冲突|驳回|拒绝|未满足|未通过|不允许|不能"
)
BUSINESS_RESULT_PATTERN = re.compile(
    r"状态|保持|阻止|不生成|不改变|不计入|失败|待处理|草稿|驳回|回退|中止|终止|冻结|不可"
)
RECOVERY_PATTERN = re.compile(r"重试|补偿|重新|人工|上传|修正|恢复|回退|撤回|驳回|阻止|保持|降级|兜底")
TRACE_PATTERN = re.compile(r"记录|留痕|审计|日志|前后值|原因|时间|操作人|申请人|版本")
OBSERVABLE_RESULT_PATTERN = re.compile(
    r"状态|记录|生成|创建|保存|新增|不改变|保持|阻止|数量|版本|归档|通知|标记|计入|不计入|关联|返回|展示|显示|提供|加载|允许|校验|计算|更新|指派|汇总|配置|读取|触发|联动|提交|推送|同步|调起"
)
BOUNDARY_PATTERN = re.compile(
    r"失败|异常|缺失|不足|不存在|没有|不等于|不一致|无权|不可|超时|重复|冲突|驳回|拒绝|未满足|未通过|不允许|不能|超出|跨租户|循环|部分|差异"
)


@dataclass(frozen=True)
class SemanticIssue:
    req_id: str
    field: str
    code: str
    detail: str


@dataclass(frozen=True)
class RequirementBlock:
    req_id: str
    text: str


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8-sig")


def requirement_blocks(text: str) -> list[RequirementBlock]:
    markers = list(
        re.finditer(
            rf"(?m)^\|\s*需求编号\s*\|\s*({REQ_ID_PATTERN})\s*\|\s*$",
            text,
        )
    )
    blocks: list[RequirementBlock] = []
    for index, marker in enumerate(markers):
        req_id = marker.group(1)
        requirement_headings = list(
            re.finditer(
                rf"(?m)^#{{1,6}}\s+.*(?<![A-Z0-9-]){re.escape(req_id)}(?![A-Z0-9-]).*$",
                text[: marker.start()],
            )
        )
        start = requirement_headings[-1].start() if requirement_headings else 0
        next_marker = markers[index + 1].start() if index + 1 < len(markers) else len(text)
        major_heading = re.search(r"(?m)^#{1,3}\s+", text[marker.end() : next_marker])
        end = marker.end() + major_heading.start() if major_heading else next_marker
        block = text[start:end]
        version = re.search(r"(?m)^\|\s*目标版本\s*\|\s*(V[123])(?:[^|]*)\|\s*$", block)
        if version and version.group(1) in {"V1", "V2"}:
            blocks.append(RequirementBlock(req_id, block))
    return blocks


def extract_field(block: str, canonical_name: str) -> str:
    alternatives = "|".join(re.escape(label) for label in FIELD_LABELS[canonical_name])
    match = re.search(
        rf"\*\*(?:{alternatives})[：:]\*\*\s*(.*?)"
        rf"(?=\n\*\*[^*\n]+[：:]\*\*|\n#{{1,6}}\s+|\Z)",
        block,
        re.S,
    )
    return match.group(1).strip() if match else ""


def normalize(value: str) -> str:
    value = re.sub(r"(?m)^\s*(?:[-*]|\d+[.、])\s*", "", value)
    value = re.sub(r"\s+", " ", value)
    return value.strip().rstrip("。；;")


def add(issues: list[SemanticIssue], req_id: str, field: str, code: str, detail: str) -> None:
    issues.append(SemanticIssue(req_id, field, code, detail))


def validate_acceptance(issues: list[SemanticIssue], req_id: str, body: str, description: str) -> None:
    for code, snippet in GENERIC_SNIPPETS["业务验收标准"].items():
        if snippet in body:
            add(issues, req_id, "业务验收标准", code, f"命中机械句式：{snippet}")

    whens = re.findall(r"(?m)^\s*-\s*\*\*WHEN\*\*\s+(.+)$", body)
    thens = re.findall(r"(?m)^\s*-\s*\*\*THEN\*\*\s+(.+)$", body)
    if len(whens) < MIN_ACCEPTANCE_SCENARIOS or len(thens) < MIN_ACCEPTANCE_SCENARIOS:
        add(
            issues,
            req_id,
            "业务验收标准",
            "INSUFFICIENT_ACCEPTANCE_SCENARIOS",
            f"需要至少{MIN_ACCEPTANCE_SCENARIOS}组WHEN/THEN，实际WHEN={len(whens)}、THEN={len(thens)}",
        )
    if whens and not any(BOUNDARY_PATTERN.search(when) for when in whens[1:]):
        add(
            issues,
            req_id,
            "业务验收标准",
            "MISSING_BOUNDARY_SCENARIO",
            "第二及后续WHEN未描述边界、权限不足或失败条件",
        )
    for index, then in enumerate(thens, start=1):
        if not OBSERVABLE_RESULT_PATTERN.search(then):
            add(
                issues,
                req_id,
                "业务验收标准",
                "UNOBSERVABLE_ACCEPTANCE",
                f"第{index}个THEN没有可观察的状态、记录、数据或禁止结果",
            )
    normalized_description = normalize(description)
    if len(normalized_description) >= 30 and normalized_description in normalize(body):
        add(
            issues,
            req_id,
            "业务验收标准",
            "COPIED_DESCRIPTION",
            "验收标准直接复制了业务描述，未形成独立可判定结果",
        )


def validate_permission(issues: list[SemanticIssue], req_id: str, body: str, block: str) -> None:
    for code, snippet in GENERIC_SNIPPETS["权限与数据范围"].items():
        if snippet in body:
            add(issues, req_id, "权限与数据范围", code, f"命中机械句式：{snippet}")

    role_match = re.search(r"(?m)^\|\s*用户角色\s*\|\s*([^|]+)\|\s*$", block)
    role_tokens = [] if not role_match else [
        token.strip() for token in re.split(r"[、,，/；;]", role_match.group(1)) if len(token.strip()) >= 2
    ]
    explicit_subject = any(token in body for token in role_tokens) or bool(
        re.search(r"管理员|审批人|负责人|创建人|工程师|客户|系统账号|服务经理|项目经理", body)
    )
    if not explicit_subject:
        add(issues, req_id, "权限与数据范围", "MISSING_PERMISSION_SUBJECT", "未写明具体角色或责任主体")
    if not ACTION_PATTERN.search(body):
        add(issues, req_id, "权限与数据范围", "MISSING_PERMISSION_ACTION", "未写明具体允许或禁止动作")
    if not SCOPE_PATTERN.search(body):
        add(issues, req_id, "权限与数据范围", "MISSING_DATA_SCOPE", "未写明项目、组织、本人或敏感字段数据范围")


def validate_exception(issues: list[SemanticIssue], req_id: str, body: str) -> None:
    for code, snippet in GENERIC_SNIPPETS["异常、降级及留痕要求"].items():
        if snippet in body:
            add(issues, req_id, "异常、降级及留痕要求", code, f"命中机械句式：{snippet}")
    if not FAILURE_TRIGGER_PATTERN.search(body):
        add(issues, req_id, "异常、降级及留痕要求", "MISSING_FAILURE_TRIGGER", "未写明具体失败触发条件")
    if not BUSINESS_RESULT_PATTERN.search(body):
        add(issues, req_id, "异常、降级及留痕要求", "MISSING_FAILURE_STATE", "未写明失败后的业务状态或禁止结果")
    if not RECOVERY_PATTERN.search(body):
        add(issues, req_id, "异常、降级及留痕要求", "MISSING_RECOVERY", "未写明重试、补偿、修正或人工兜底")
    if not TRACE_PATTERN.search(body):
        add(issues, req_id, "异常、降级及留痕要求", "MISSING_EXCEPTION_TRACE", "未写明可追溯记录")


def validate_data(issues: list[SemanticIssue], req_id: str, body: str) -> None:
    if any(re.search(pattern, body) for pattern in GENERIC_DATA_PATTERNS):
        add(issues, req_id, "涉及数据字段", "GENERIC_DATA", "数据字段包含空泛占位表达")
    tokens = {
        normalize(token)
        for token in re.split(r"[、,，；;\n]", body)
        if normalize(token) and not normalize(token).startswith("-")
    }
    if len(tokens) < MIN_DATA_TOKENS:
        add(
            issues,
            req_id,
            "涉及数据字段",
            "INSUFFICIENT_DATA_FIELDS",
            f"至少需要{MIN_DATA_TOKENS}个可区分数据项，实际{len(tokens)}个",
        )


def validate_text(text: str, requirement_ids: set[str] | None = None) -> list[SemanticIssue]:
    all_blocks = requirement_blocks(text)
    all_ids = {block.req_id for block in all_blocks}
    selected = [block for block in all_blocks if requirement_ids is None or block.req_id in requirement_ids]
    issues: list[SemanticIssue] = []

    if requirement_ids is not None:
        for req_id in sorted(requirement_ids - all_ids):
            add(issues, req_id, "需求块", "REQUIREMENT_NOT_FOUND", "未找到V1/V2正式需求块")

    field_values: dict[str, dict[str, str]] = defaultdict(dict)
    for block in selected:
        values = {name: extract_field(block.text, name) for name in FIELD_LABELS}
        for required in (
            "核心业务规则",
            "业务验收标准",
            "涉及数据字段",
            "权限与数据范围",
            "异常、降级及留痕要求",
        ):
            if not values[required]:
                add(issues, block.req_id, required, "MISSING_FIELD", "字段不存在或内容为空")
            else:
                field_values[required][block.req_id] = normalize(values[required])

        rule = values["核心业务规则"]
        for code, snippet in GENERIC_SNIPPETS["核心业务规则"].items():
            if snippet in rule:
                add(issues, block.req_id, "核心业务规则", code, f"命中机械句式：{snippet}")
        if values["业务验收标准"]:
            validate_acceptance(
                issues,
                block.req_id,
                values["业务验收标准"],
                values["业务场景与需求描述"],
            )
        if values["权限与数据范围"]:
            validate_permission(issues, block.req_id, values["权限与数据范围"], block.text)
        if values["异常、降级及留痕要求"]:
            validate_exception(issues, block.req_id, values["异常、降级及留痕要求"])
        if values["涉及数据字段"]:
            validate_data(issues, block.req_id, values["涉及数据字段"])

    for field, values in field_values.items():
        duplicates: dict[str, list[str]] = defaultdict(list)
        for req_id, value in values.items():
            if value:
                duplicates[value].append(req_id)
        for req_ids in duplicates.values():
            if len(req_ids) < 2:
                continue
            detail = f"与{','.join(sorted(req_ids))}的{field}全文相同"
            for req_id in sorted(req_ids):
                add(issues, req_id, field, "DUPLICATE_FIELD", detail)

    return sorted(issues, key=lambda item: (item.req_id, item.field, item.code, item.detail))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prd", required=True, type=Path)
    parser.add_argument("--requirement", action="append", default=[])
    args = parser.parse_args()

    requirement_ids = set(args.requirement) or None
    issues = validate_text(read_text(args.prd), requirement_ids)
    if not issues:
        scope = "all formal requirements" if requirement_ids is None else ",".join(sorted(requirement_ids))
        print(f"[PASS] PRD semantic quality: {scope}")
        print("SUMMARY: 0 semantic issues")
        return 0

    for issue in issues:
        print(f"[FAIL] {issue.req_id} {issue.field} {issue.code}: {issue.detail}")
    failed_requirements = len({issue.req_id for issue in issues})
    print(f"SUMMARY: {len(issues)} semantic issues in {failed_requirements} requirements")
    return 1


if __name__ == "__main__":
    sys.exit(main())
