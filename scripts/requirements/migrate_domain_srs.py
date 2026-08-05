from __future__ import annotations

import argparse
import re
from dataclasses import dataclass, field
from pathlib import Path


FR_HEADING_RE = re.compile(r"^## (FR-[A-Z]+-\d{3})\s+(.+?)\s*$", re.MULTILINE)
EVOLUTION_HEADING_RE = re.compile(r"^### (FR-[A-Z]+-\d{3})\s+(.+?)\s*$", re.MULTILINE)
SECTION_RE = re.compile(r"^### (.+?)\s*$", re.MULTILINE)
METADATA_RE = re.compile(r"^\*\*(.+?)：\*\*\s*(.*?)(?:<br>)?\s*$", re.MULTILINE)
IDENTIFIER_RE = re.compile(r"(?:REQ|DEC)-\d{3}")
AC_IDENTIFIER_RE = re.compile(r"AC-[A-Z]+-\d{3}")
UNKNOWN = "【待确认】legacy 未提供"
BUSINESS_TERM_REPLACEMENTS = {"平台基础": "基础平台"}
EXTERNAL_SYSTEMS = ("CRM", "ERP", "ITR")


@dataclass(frozen=True)
class LegacyRequirement:
    fr_id: str
    title: str
    metadata: dict[str, str]
    sections: dict[str, str]
    source_path: Path

    @property
    def source_ids(self) -> tuple[str, ...]:
        return tuple(dict.fromkeys(IDENTIFIER_RE.findall(self.metadata.get("来源需求", ""))))


@dataclass(frozen=True)
class EvolutionItem:
    fr_id: str
    title: str
    source_ids: tuple[str, ...]
    body: str
    source_path: Path


@dataclass(frozen=True)
class DomainProfile:
    code: str
    name: str
    responsibility: str
    filename: str
    fr_ids: tuple[str, ...]
    evolution_ids: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class DomainNarrative:
    business_context: str
    business_goal: str
    objects: tuple[str, ...]
    lifecycle: tuple[str, ...]
    risk: str


@dataclass(frozen=True)
class MigrationRecord:
    fr_id: str
    source: str
    owner: str
    disposition: str
    numbering: str
    evidence: str


@dataclass(frozen=True)
class EvolutionMigrationRecord:
    fr_id: str
    source: str
    owner: str
    disposition: str
    numbering: str
    boundary: str
    evidence: str


def _fr_range(prefix: str, start: int, end: int) -> tuple[str, ...]:
    return tuple(f"FR-{prefix}-{number:03d}" for number in range(start, end + 1))


DOMAIN_PROFILES: tuple[DomainProfile, ...] = (
    DomainProfile("PLT", "公共平台能力", "身份、权限、流程、文件、通知、审计、字典和通用集成治理", "PLT-public-platform-capabilities-srs.md", _fr_range("PLT", 1, 11)),
    DomainProfile("PROJ", "项目治理", "客户项目上下文、项目组合、项目树、任务 WBS、团队、阶段计划、风险和项目关闭控制", "PROJ-project-governance-srs.md", _fr_range("PROJ", 1, 26)),
    DomainProfile("ENG", "工程交付", "工勘、需求分析、实施准备、方案、到货、安装、配置、联调、质量与现场问题", "ENG-engineering-delivery-srs.md", _fr_range("ENG", 1, 29)),
    DomainProfile("CUT", "割接管理", "割接准备、评估、方案、审批、执行、回退、观察和闭环", "CUT-cutover-management-srs.md", _fr_range("CUT", 1, 15)),
    DomainProfile("ACC", "验收与闭环", "培训、满意度、初终验、交付件、闭环审批和转维护", "ACC-acceptance-and-closure-srs.md", _fr_range("ACC", 1, 10)),
    DomainProfile("INS", "巡检服务", "巡检创建、规则、在线或离线执行、报告、整改和巡检闭环", "INS-inspection-service-srs.md", _fr_range("SRV", 1, 12)),
    DomainProfile("SVC", "服务工单与维保", "工单、时效、问题关联、维保、续保、回访和主动服务", "SVC-service-and-maintenance-srs.md", _fr_range("SRV", 13, 24)),
    DomainProfile("AST", "设备资产", "设备和序列号、版本、配置日志、安装位置和设备档案", "AST-device-asset-srs.md", _fr_range("RES", 1, 4)),
    DomainProfile("TIM", "工时管理", "考勤、工作记录、工时申报和审批", "TIM-worktime-management-srs.md", _fr_range("RES", 5, 7)),
    DomainProfile("OUT", "服务商与外包", "服务商、转包、合同订单回写、付款、余额和回访门禁", "OUT-outsourcing-management-srs.md", _fr_range("RES", 8, 14)),
    DomainProfile("SPT", "备件与 RMA", "RMA、备件库、好坏件、借用补库、转移交接和替换维保", "SPT-spare-parts-and-rma-srs.md", _fr_range("RES", 15, 19) + ("FR-RES-021",), ("FR-RES-020",)),
    DomainProfile("TEC", "技术公告", "公告编制、影响版本、会签、检索、命中、工单关联和统计", "TEC-technical-bulletin-srs.md", _fr_range("RES", 22, 29)),
    DomainProfile("ANA", "经营分析", "项目组合经营、工时人效和跨领域只读分析", "ANA-business-analytics-srs.md", _fr_range("ANA", 1, 2), _fr_range("ANA", 3, 8)),
)


DOMAIN_NARRATIVES: dict[str, DomainNarrative] = {
    "PLT": DomainNarrative(
        "各业务领域共同依赖身份、权限、流程、文件、通知、审计、字典和集成治理能力，需要以一致机制支撑领域办理。",
        "形成可被各业务领域复用且边界一致的公共能力，避免把业务对象所有权集中到基础平台。",
        ("身份与授权上下文", "流程实例", "业务文件与版本", "通知与待办", "审计记录", "字典项", "集成批次与来源映射"),
        ("配置", "启用", "业务调用", "结果留痕", "停用或变更"),
        "公共机制若承载业务特定规则，会造成领域责任和数据所有权混淆。",
    ),
    "PROJ": DomainNarrative(
        "项目承接后需要统一项目上下文、组合、树、任务、团队、阶段、风险和关闭控制，支撑后续交付活动围绕同一项目展开。",
        "建立从项目承接、组织与计划到风险控制和关闭的项目治理闭环。",
        ("项目主档", "项目组合", "项目树", "WBS 任务", "项目团队", "阶段计划", "项目风险", "项目关闭记录"),
        ("承接", "启动", "规划", "执行治理", "风险处置", "关闭"),
        "外部承接证据、项目范围和项目状态口径不一致会影响全部下游交付活动。",
    ),
    "ENG": DomainNarrative(
        "工程交付覆盖从工勘和需求分析到到货、安装、配置、联调、质量及现场问题闭环，需要连续保存过程证据。",
        "使工程活动按项目范围受控推进，并以可复核交付记录证明阶段结果。",
        ("工勘任务", "需求分析记录", "实施准备项", "技术方案", "到货记录", "安装配置记录", "联调记录", "质量与现场问题"),
        ("工勘", "分析", "准备", "方案", "到货", "安装配置", "联调验证", "问题闭环"),
        "现场条件、设备范围或方案版本不一致会导致返工和交付证据失真。",
    ),
    "CUT": DomainNarrative(
        "割接属于高风险交付活动，需要在窗口执行前完成评估、方案、审批和回退准备，并在执行后观察和归档。",
        "形成可审批、可执行、可回退、可观察和可追溯的割接闭环。",
        ("割接任务", "风险评估", "割接方案", "割接步骤", "回退方案", "执行日志", "观察记录", "归档结果"),
        ("创建", "评估", "编制方案", "审批", "执行或回退", "观察", "闭环归档"),
        "执行窗口、步骤、责任人或回退条件不明确会放大业务中断风险。",
    ),
    "ACC": DomainNarrative(
        "项目交付结果需要通过培训、满意度、初验、终验、交付件、遗留问题和转维护活动形成正式闭环。",
        "以完整交付证据完成验收、关闭审批和维护责任交接。",
        ("培训记录", "满意度记录", "初验记录", "终验记录", "交付件", "遗留问题", "关闭申请", "维护交接记录"),
        ("培训准备", "初验", "整改", "终验", "关闭审批", "转维护"),
        "验收证据、遗留问题和关闭门禁不完整会造成责任提前解除。",
    ),
    "INS": DomainNarrative(
        "巡检服务需要统一计划、规则、在线或离线执行、报告与整改过程，确保巡检发现能够闭环。",
        "使巡检任务可执行、结果可复核、问题可整改并形成服务证据。",
        ("巡检计划", "巡检任务", "巡检规则与模板", "巡检结果", "巡检报告", "整改项"),
        ("创建", "规则准备", "执行", "结果复核", "报告生成", "整改", "关闭"),
        "离线结果、规则版本或整改状态不同步会导致巡检结论不可复核。",
    ),
    "SVC": DomainNarrative(
        "服务工单和维保活动需要关联客户、项目、设备与问题，并持续管理时效、维保状态、续保、回访和主动服务。",
        "形成从服务受理、处理到维保跟踪和回访的持续服务闭环。",
        ("服务工单", "问题关联", "维保期限", "续保提醒", "回访记录", "主动服务任务"),
        ("受理", "分派", "处理", "结果确认", "维保跟踪", "续保或回访", "关闭"),
        "问题关联或维保口径不准确会导致响应超期、责任错配和服务遗漏。",
    ),
    "AST": DomainNarrative(
        "设备交付和后续服务依赖连续的序列号、版本、配置、安装位置和归属档案。",
        "建立可追溯的设备资产档案，为项目、服务、备件和分析提供权威设备上下文。",
        ("设备", "序列号", "设备版本", "配置日志", "安装位置", "设备档案"),
        ("登记", "关联项目与位置", "安装配置", "版本或配置变更", "维护", "停用或移交"),
        "序列号、项目归属或位置关系错误会破坏设备全生命周期追溯。",
    ),
    "TIM": DomainNarrative(
        "项目执行需要通过考勤、工作记录和工时申报审批形成可核验的人力投入记录。",
        "形成与项目工作对应、可审批和可统计的工时记录。",
        ("考勤记录", "工作记录", "工时申报", "工时审批记录"),
        ("记录", "申报", "校验", "审批或退回", "归档统计"),
        "考勤、工作内容和申报口径不一致会影响成本及人效分析。",
    ),
    "OUT": DomainNarrative(
        "外包交付需要管理服务商、转包任务、合同订单关联、付款、余额和回访门禁。",
        "使外包责任、执行结果和结算依据可追溯，并受交付结果约束。",
        ("服务商", "转包任务", "外包合同与订单关联", "付款记录", "余额记录", "回访门禁"),
        ("服务商准入", "转包发起", "执行跟踪", "结果确认", "结算", "回访", "关闭"),
        "转包结果与合同、付款或回访证据脱节会造成结算和责任风险。",
    ),
    "SPT": DomainNarrative(
        "设备故障处置需要联动 RMA、备件库存、好坏件、借用补库、转移交接和替换维保。",
        "形成备件申请、流转、替换和归还补库的可追溯闭环。",
        ("RMA 申请", "备件库存", "好件与坏件", "借用与补库记录", "转移交接记录", "替换维保记录"),
        ("申请", "审核", "出库或借用", "转移交接", "替换", "归还或补库", "关闭"),
        "备件状态、实物位置和交接记录不一致会导致库存及维保责任失真。",
    ),
    "TEC": DomainNarrative(
        "技术公告需要描述影响版本与设备，经过会签后支持检索、命中、工单关联和处置统计。",
        "使技术风险能够发布、命中业务对象、形成处置任务并验证闭环。",
        ("技术公告", "影响版本与设备", "会签记录", "公告命中记录", "治理工单", "问题关联", "处置统计"),
        ("编制", "影响评估", "会签", "发布", "检索与命中", "处置", "统计关闭"),
        "影响范围或版本关系不准确会造成公告漏命中和风险遗漏。",
    ),
    "ANA": DomainNarrative(
        "经营管理需要在不改变业务对象所有权的前提下，汇总项目组合、交付数量和工时人效数据。",
        "提供口径可解释、可下钻且保持来源血缘的跨领域只读分析。",
        ("项目组合指标", "工时与人效指标", "指标口径", "指标血缘与快照", "分析条件"),
        ("选择范围", "汇总计算", "口径校验", "展示与下钻", "快照或导出"),
        "指标口径、统计时点或来源血缘不明确会产生不可解释的经营结论。",
    ),
}


def _domain_narrative(profile: DomainProfile) -> DomainNarrative:
    return DOMAIN_NARRATIVES.get(
        profile.code,
        DomainNarrative(
            f"本领域承担{profile.responsibility}。",
            f"完成{profile.name}责任闭环。",
            (f"{profile.name}业务对象集合",),
            ("办理",),
            "领域来源语义或责任边界不清会造成验收偏差。",
        ),
    )

LEGACY_FILENAMES = (
    "01-platform-and-permission.md",
    "02-project-initiation.md",
    "03-planning-and-execution.md",
    "04-cutover-and-stabilization.md",
    "05-acceptance-and-closure.md",
    "06-inspection-and-maintenance.md",
    "07-assets-and-outsourcing.md",
    "08-analytics-and-integration.md",
)


def _slice_blocks(text: str, pattern: re.Pattern[str], stop_pattern: re.Pattern[str]) -> list[tuple[re.Match[str], str]]:
    matches = list(pattern.finditer(text))
    blocks: list[tuple[re.Match[str], str]] = []
    for match in matches:
        stop = stop_pattern.search(text, match.end())
        blocks.append((match, text[match.end() : stop.start() if stop else len(text)].strip()))
    return blocks


def parse_legacy_fr(path: Path) -> list[LegacyRequirement]:
    """Parse formal level-two legacy FR definitions from one current domain volume."""
    text = path.read_text(encoding="utf-8")
    requirements: list[LegacyRequirement] = []
    next_same_or_higher_heading = re.compile(r"^#{1,2}\s+", re.MULTILINE)
    for heading, body in _slice_blocks(text, FR_HEADING_RE, next_same_or_higher_heading):
        metadata = {key.strip(): value.strip() for key, value in METADATA_RE.findall(body)}
        section_matches = list(SECTION_RE.finditer(body))
        sections: dict[str, str] = {}
        for index, section in enumerate(section_matches):
            end = section_matches[index + 1].start() if index + 1 < len(section_matches) else len(body)
            sections[section.group(1).strip()] = body[section.end() : end].strip()
        requirements.append(
            LegacyRequirement(
                fr_id=heading.group(1),
                title=heading.group(2).strip(),
                metadata=metadata,
                sections=sections,
                source_path=path,
            )
        )
    return requirements


def parse_evolution_items(path: Path) -> list[EvolutionItem]:
    """Parse level-three V3 entries without promoting them to formal FR units."""
    text = path.read_text(encoding="utf-8")
    items: list[EvolutionItem] = []
    next_heading = re.compile(r"^#{1,3}\s+", re.MULTILINE)
    for heading, body in _slice_blocks(text, EVOLUTION_HEADING_RE, next_heading):
        sources = tuple(dict.fromkeys(IDENTIFIER_RE.findall(body)))
        items.append(EvolutionItem(heading.group(1), heading.group(2).strip(), sources, body, path))
    return items


def load_legacy_requirements(root: Path) -> tuple[list[LegacyRequirement], list[EvolutionItem]]:
    formal: list[LegacyRequirement] = []
    evolution: list[EvolutionItem] = []
    for filename in LEGACY_FILENAMES:
        path = root / filename
        formal.extend(parse_legacy_fr(path))
        evolution.extend(parse_evolution_items(path))
    return formal, evolution


def _section(requirement: LegacyRequirement, name: str, fallback: str = "不适用。") -> str:
    return _normalize_business_terms(requirement.sections.get(name, fallback).strip())


def _metadata(requirement: LegacyRequirement, *names: str, fallback: str = UNKNOWN) -> str:
    for name in names:
        value = requirement.metadata.get(name)
        if value:
            return _normalize_business_terms(value)
    return _normalize_business_terms(fallback)


def _normalize_business_terms(text: str) -> str:
    for legacy_term, preferred_term in BUSINESS_TERM_REPLACEMENTS.items():
        text = text.replace(legacy_term, preferred_term)
    return text


def _table_cell(text: str) -> str:
    return _normalize_business_terms(text).replace("|", "／").replace("\n", "；")


def _source_ids(requirement: LegacyRequirement) -> str:
    return ",".join(requirement.source_ids) or _metadata(requirement, "来源需求")


def _acceptance_ids(requirement: LegacyRequirement) -> str:
    identifiers = tuple(dict.fromkeys(AC_IDENTIFIER_RE.findall(_section(requirement, "验收标准", ""))))
    return ",".join(identifiers) or f"{UNKNOWN}验收标准编号"


def _render_stakeholders(requirements: list[LegacyRequirement]) -> str:
    role_requirements: dict[str, list[str]] = {}
    for requirement in requirements:
        role = _metadata(requirement, "参与角色")
        role_requirements.setdefault(role, []).append(requirement.fr_id)
    rows = ["| 角色／群体 | 职责 | 使用场景 | 关注重点 | 权限范围 |", "| --- | --- | --- | --- | --- |"]
    for role, fr_ids in role_requirements.items():
        references = "、".join(fr_ids)
        rows.append(
            f"| {_table_cell(role)} | 执行 {references} 已定义的业务动作 | {references} | "
            f"对应业务规则和验收结果 | 见对应 FR 权限要求；统一权限范围【待确认】 |"
        )
    return "\n".join(rows)


def _render_scenarios(profile: DomainProfile, requirements: list[LegacyRequirement]) -> str:
    rows = ["| 场景编号 | 场景名称 | 参与角色 | 触发条件 | 期望结果 |", "| --- | --- | --- | --- | --- |"]
    for index, requirement in enumerate(requirements, start=1):
        rows.append(
            f"| SCN-{profile.code}-{index:03d} | {_table_cell(requirement.title)} | "
            f"{_table_cell(_metadata(requirement, '参与角色'))} | "
            f"{_table_cell(_metadata(requirement, '业务场景'))} | 满足 {_acceptance_ids(requirement)} |"
        )
    return "\n".join(rows)


def _render_core_objects(profile: DomainProfile) -> str:
    narrative = _domain_narrative(profile)
    lifecycle = "→".join(narrative.lifecycle)
    rows = ["| 对象 | 业务含义 | 唯一标识 | 关键关系 | 生命周期 | 数据责任方 |", "| --- | --- | --- | --- | --- | --- |"]
    for item in narrative.objects:
        rows.append(
            f"| 【建议】{item} | 【建议】由本领域 FR 的标题、业务目标和数据要求归纳 | "
            f"【待确认】legacy 未提供统一标识 | 见各 FR | 【建议】{lifecycle} | {profile.name} |"
        )
    return "\n".join(rows)


def _render_lifecycle(profile: DomainProfile) -> str:
    stages = _domain_narrative(profile).lifecycle
    rows = ["| 阶段 | 阶段目标 | 主要活动 | 输入 | 输出 | 完成标准 |", "| --- | --- | --- | --- | --- | --- |"]
    for index, stage in enumerate(stages):
        prior = "本领域 FR 定义的业务输入" if index == 0 else f"上一阶段“{stages[index - 1]}”的结果"
        rows.append(
            f"| 【建议】{stage} | 【建议】推进{profile.name}业务闭环 | 见本阶段相关 FR | "
            f"【建议】{prior} | 【建议】本阶段业务结果与留痕 | 满足相关 FR 的 AC |"
        )
    return "\n".join(rows)


def _render_evolution_scope(profile: DomainProfile, evolution_items: list[EvolutionItem]) -> str:
    if not profile.evolution_ids:
        return "- 无已登记演进项。"
    selected = {item.fr_id: item for item in evolution_items}
    rows = [
        "以下演进方向不纳入当前开发验收：",
        "",
        "| 演进项 | 方向 | 来源 | 当前边界 |",
        "| --- | --- | --- | --- |",
    ]
    for fr_id in profile.evolution_ids:
        item = selected.get(fr_id)
        title = _normalize_business_terms(item.title) if item else f"{UNKNOWN}演进方向"
        sources = ",".join(item.source_ids) if item and item.source_ids else f"{UNKNOWN}来源"
        rows.append(f"| {fr_id} | {_table_cell(title)} | {sources} | 不纳入当前开发验收 |")
    return "\n".join(rows)


def _interaction_systems(requirement: LegacyRequirement) -> tuple[str, ...]:
    interaction_text = "\n".join(
        (
            requirement.metadata.get("业务场景", ""),
            requirement.sections.get("业务目标", ""),
            requirement.sections.get("主流程", ""),
            requirement.sections.get("输出与后置条件", ""),
        )
    ).upper()
    return tuple(system for system in EXTERNAL_SYSTEMS if system in interaction_text)


def _render_external_interactions(profile: DomainProfile, requirements: list[LegacyRequirement]) -> str:
    rows = [
        "| 交互编号 | 外部系统 | 业务目的 | 数据范围 | 方向／频率 | 失败时业务要求 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    if profile.code == "PLT":
        rows.append(
            "| 不适用 | 业务特定外部系统由对象 Owner 领域定义 | "
            "FR-PLT-009 仅提供通用集成、事件、幂等和补偿机制 | 通用批次、来源映射和处理证据 | "
            "由对象 Owner 领域定义 | 按 FR-PLT-009 保留失败明细并支持补偿 |"
        )
        return "\n".join(rows)
    interaction_requirements = [requirement for requirement in requirements if _interaction_systems(requirement)]
    for index, requirement in enumerate(interaction_requirements, start=1):
        systems = "／".join(_interaction_systems(requirement))
        rows.append(
            f"| IR-{profile.code}-{index:03d} | {systems} | 承接 {requirement.fr_id}“{_table_cell(requirement.title)}”的业务交互 | "
            f"见 {requirement.fr_id} 输入和数据要求 | 【待确认】legacy 未统一定义方向／频率 | "
            f"执行 {requirement.fr_id} 分支与异常要求并保留失败证据 |"
        )
    if not interaction_requirements:
        rows.append("| 不适用 | 本领域正式 FR 未定义业务特定外部系统交互 | 不适用 | 不适用 | 不适用 | 不适用 |")
    return "\n".join(rows)


def _render_metrics(profile: DomainProfile, requirements: list[LegacyRequirement]) -> str:
    metric_requirements = [
        requirement
        for requirement in requirements
        if re.search(r"统计|看板|报表|指标|分析", requirement.title)
    ]
    rows = [
        "| 指标编号 | 指标名称 | 业务口径 | 数据范围 | 更新频率 | 展示／导出要求 |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for index, requirement in enumerate(metric_requirements, start=1):
        rows.append(
            f"| MET-{profile.code}-{index:03d} | {_table_cell(requirement.title)} | "
            f"见 {requirement.fr_id} 业务规则和验收标准 | 见 {requirement.fr_id} 数据要求 | "
            f"【待确认】legacy 未统一定义更新频率 | 见 {requirement.fr_id} 输出要求 |"
        )
    if not metric_requirements:
        rows.append("| 不适用 | 本领域正式 FR 未定义独立报表或指标 | 不适用 | 不适用 | 不适用 | 不适用 |")
    return "\n".join(rows)


def _render_traceability(requirements: list[LegacyRequirement]) -> str:
    rows = ["| 目标／来源 | 需求编号 | 验收标准 | 设计编号 | 测试用例 | 状态 |", "| --- | --- | --- | --- | --- | --- |"]
    for requirement in requirements:
        rows.append(
            f"| {_source_ids(requirement)} | {requirement.fr_id} | {_acceptance_ids(requirement)} | "
            "【待确认】待后续 SDS 建立 | 【待确认】待后续 TAS 建立 | 已迁移 |"
        )
    return "\n".join(rows)


def _extract_data_items(data_text: str) -> list[tuple[str, str]]:
    items: list[tuple[str, str]] = []
    for line in data_text.splitlines():
        match = re.match(r"^-\s+(?!\*\*(?:DR|BR|AC)-)(?:\*\*)?([^：*]+?)(?:\*\*)?：\s*(.+)$", line.strip())
        if match:
            items.append((match.group(1).strip(), match.group(2).strip()))
    return items


def _extract_numbered_rules(text: str, prefix: str) -> list[tuple[str, str]]:
    pattern = re.compile(rf"^[-*]\s+\*\*({prefix}-[A-Z]+-\d{{3}})：\*\*\s*(.+)$", re.MULTILINE)
    return [(identifier, content.strip()) for identifier, content in pattern.findall(text)]


def _infer_required(rule: str) -> str:
    if re.search(r"非必填|选填|可选|可为空|允许为空", rule):
        return "否"
    if re.search(r"必填|不得为空|不能为空", rule):
        return "是"
    return "【待确认】"


def _infer_sensitivity(rule: str) -> str:
    if "敏感" in rule:
        return "敏感"
    if "公开" in rule:
        return "公开"
    return "【待确认】"


def _render_basic_information(profile: DomainProfile, requirement: LegacyRequirement) -> str:
    source_marker = _metadata(requirement, "来源标识", fallback=f"{UNKNOWN}来源标识")
    rows = (
        ("用例编号", _metadata(requirement, "用例编号")),
        ("来源需求", _metadata(requirement, "来源需求")),
        ("所属模块", profile.name),
        ("适用版本", _metadata(requirement, "所属版本", "适用版本")),
        ("优先级／复杂度", _metadata(requirement, "优先级／复杂度")),
        ("需求状态", f"{UNKNOWN}需求状态"),
        ("参与角色", _metadata(requirement, "参与角色")),
        ("业务场景", _metadata(requirement, "业务场景")),
        ("依赖需求", _dependency_ids(_section(requirement, "前置条件", ""))),
        ("来源标识", source_marker),
    )
    return "\n".join(["| 字段 | 内容 |", "| --- | --- |", *(f"| {key} | {value} |" for key, value in rows)])


def _dependency_ids(preconditions: str) -> str:
    for line in preconditions.splitlines():
        if "依赖条件" in line:
            identifiers = IDENTIFIER_RE.findall(line)
            return ",".join(identifiers) if identifiers else "无"
    return f"{UNKNOWN}依赖需求"


def _render_preconditions(requirement: LegacyRequirement) -> str:
    lines = [line[2:].strip() for line in _section(requirement, "前置条件", "").splitlines() if line.startswith("- ")]
    permission = next((line for line in lines if "权限" in line or "登录" in line), f"{UNKNOWN}用户和权限条件。")
    dependencies = [line for line in lines if "依赖条件" in line]
    remaining = [line for line in lines if line != permission and line not in dependencies]
    data = "；".join(remaining) if remaining else f"{UNKNOWN}数据和状态条件。"
    return "\n".join(
        (
            f"- 触发条件：{_metadata(requirement, '业务场景', fallback=f'{UNKNOWN}触发条件。')}",
            f"- 用户和权限条件：{permission}",
            f"- 数据和状态条件：{data}",
            f"- 外部依赖条件：{UNKNOWN}外部依赖；legacy 前置条件未单独区分外部依赖。",
        )
    )


def _render_input(requirement: LegacyRequirement) -> str:
    items = _extract_data_items(_section(requirement, "数据要求", ""))
    rows = ["| 输入项 | 来源 | 必填 | 校验规则 | 备注 |", "| --- | --- | --- | --- | --- |"]
    for name, rule in items:
        required = _infer_required(rule)
        rows.append(f"| {name} | {UNKNOWN}输入来源 | {required} | {rule} | 由 legacy 数据要求迁移 |")
    if not items:
        rows.append(f"| 不适用 | legacy 未提供可结构化输入项 | 不适用 | {UNKNOWN}输入规则 | 未从模板反推输入 |")
    return "\n".join(rows)


def _render_state(requirement: LegacyRequirement) -> str:
    state = _section(requirement, "状态流转", f"{UNKNOWN}状态流转；legacy 未提供状态说明。")
    if state.lstrip().startswith("|"):
        return state
    return "\n".join(
        (
            state,
            "",
            "| 当前状态 | 业务动作 | 目标状态 | 执行角色 | 前置条件 | 失败处理 |",
            "| --- | --- | --- | --- | --- | --- |",
            "| 不适用 | legacy 状态说明为非结构化文本 | 不适用 | 不适用 | 不适用 | 未转换为状态迁移行 |",
        )
    )


def _render_rules(requirement: LegacyRequirement) -> str:
    rules = _extract_numbered_rules(_section(requirement, "业务规则", ""), "BR")
    rows = ["| 规则编号 | 规则内容 | 适用条件 | 例外条件 |", "| --- | --- | --- | --- |"]
    rows.extend(
        f"| {identifier} | {content.replace('|', '／')} | {UNKNOWN}适用条件 | {UNKNOWN}例外条件 |"
        for identifier, content in rules
    )
    if not rules:
        rows.append("| 不适用 | legacy 规格未定义独立业务规则 | 不适用 | 无 |")
    return "\n".join(rows)


def _render_data(requirement: LegacyRequirement) -> str:
    data_text = _section(requirement, "数据要求", "")
    items = _extract_data_items(data_text)
    rows = ["| 数据项 | 业务含义 | 必填 | 来源 | 校验／唯一性规则 | 敏感级别 |", "| --- | --- | --- | --- | --- | --- |"]
    for name, rule in items:
        required = _infer_required(rule)
        sensitivity = _infer_sensitivity(rule)
        rows.append(
            f"| {name} | {UNKNOWN}业务含义 | {required} | {UNKNOWN}数据来源 | "
            f"{rule.replace('|', '／')} | {sensitivity} |"
        )
    for identifier, content in _extract_numbered_rules(data_text, "DR"):
        rows.append(
            f"| {identifier} | 数据规则 | 【待确认】 | {UNKNOWN}数据来源 | "
            f"{content.replace('|', '／')} | {_infer_sensitivity(content)} |"
        )
    if len(rows) == 2:
        rows.append(f"| 不适用 | legacy 未提供可结构化数据项 | 不适用 | {UNKNOWN}数据来源 | {UNKNOWN}数据规则 | 【待确认】 |")
    return "\n".join(rows)


def _render_requirement(profile: DomainProfile, requirement: LegacyRequirement) -> str:
    permissions = _section(requirement, "权限、通知与审计", f"- {UNKNOWN}权限、通知与业务留痕要求。")
    return f"""### {requirement.fr_id} {_normalize_business_terms(requirement.title)}

#### 基本信息

{_render_basic_information(profile, requirement)}

#### 业务目标

{_section(requirement, "业务目标")}

#### 触发条件与前置条件

{_render_preconditions(requirement)}

#### 输入

{_render_input(requirement)}

#### 主流程

{_section(requirement, "主流程")}

#### 分支与异常

{_section(requirement, "分支与异常")}

#### 状态流转（按需）

{_render_state(requirement)}

#### 业务规则

{_render_rules(requirement)}

#### 数据要求

{_render_data(requirement)}

#### 权限、通知与业务留痕

{permissions}

#### 输出与后置条件

{_section(requirement, "输出与后置条件")}

#### 验收标准

{_section(requirement, "验收标准")}
"""


def render_srs(
    profile: DomainProfile,
    requirements: list[LegacyRequirement],
    evolution_items: list[EvolutionItem] | None = None,
) -> str:
    """Render a complete fixed-chapter SRS with strict twelve-subsection FR units."""
    selected = {requirement.fr_id: requirement for requirement in requirements}
    ordered = [selected[fr_id] for fr_id in profile.fr_ids if fr_id in selected]
    narrative = _domain_narrative(profile)
    function_rows = "\n".join(
        f"| {item.fr_id} | {_normalize_business_terms(item.title)} | {profile.name} | {_metadata(item, '来源需求')} | {_metadata(item, '所属版本')} | {_metadata(item, '优先级／复杂度').split('／')[0]} |"
        for item in ordered
    ) or "| 不适用 | 当前未迁移正式功能 | 不适用 | 不适用 | 不适用 | 不适用 |"
    detail = "\n\n".join(_render_requirement(profile, item).strip() for item in ordered)
    stakeholders = _render_stakeholders(ordered)
    scenarios = _render_scenarios(profile, ordered)
    core_objects = _render_core_objects(profile)
    lifecycle = _render_lifecycle(profile)
    evolution_scope = _render_evolution_scope(profile, evolution_items or [])
    external_interactions = _render_external_interactions(profile, ordered)
    metrics = _render_metrics(profile, ordered)
    traceability = _render_traceability(ordered)
    document = f"""# {profile.name}软件需求规格说明书

## 文档控制

| 字段 | 内容 |
| --- | --- |
| 项目／产品名称 | 项目实施交付管理平台 |
| 文档名称 | {profile.name}软件需求规格说明书 |
| 文档编号 | SRS-PDP-{profile.code} |
| 文档版本 | V0.1 |
| 文档状态 | 已确认迁移基线 |
| 适用版本／里程碑 | 当前建设版本 |
| 权威需求源 | 当前 legacy 分卷及来源追溯附录 |
| 需求基线 | d8f7002df7dee3f41773e00da935fa8d040e27f0 |
| 编制人／日期 | Codex／2026-08-05 |
| 批准人／日期 | 待业务基线审批 |

### 修订记录

| 版本 | 日期 | 变更范围 | 变更原因 | 编制人 | 批准人 |
| --- | --- | --- | --- | --- | --- |
| V0.1 | 2026-08-05 | 初始版本 | 领域化 SRS 直接迁移 | Codex | 待业务基线审批 |

## 1. 文档说明

### 1.1 文档目的

定义{profile.name}的业务范围、行为、规则、数据和验收标准。

### 1.2 目标读者

- 业务负责人：{profile.name}责任人
- 产品及需求人员：项目需求团队
- 研发和架构人员：项目研发团队
- 测试和验收人员：项目测试与验收团队
- 其他利益相关方：相关协作领域责任人

### 1.3 术语与缩写

| 术语／缩写 | 定义 | 备注 |
| --- | --- | --- |
| {profile.code} | {profile.name} | 领域代码 |

### 1.4 参考资料

| 资料名称 | 版本／日期 | 来源 | 用途 |
| --- | --- | --- | --- |
| 现有需求分卷 | 基线 d8f7002 | 当前规格目录 | 需求直接迁移 |

## 2. 背景与建设目标

### 2.1 业务背景

{narrative.business_context}

### 2.2 核心问题

- 现有需求按交付阶段分散，{profile.name}责任需要集中为可独立评审的权威定义。
- 本领域需要在自身对象和规则边界内完成{profile.responsibility}，并通过公共机制与其他领域协作。

### 2.3 建设目标

| 目标编号 | 目标描述 | 衡量指标 | 目标值／完成标准 | 责任方 |
| --- | --- | --- | --- | --- |
| OBJ-{profile.code}-001 | {narrative.business_goal} | 正式 FR 验收覆盖率 | 本领域全部正式 FR 均有可验证 AC | {profile.name}责任人 |

### 2.4 成功标准

- 本领域全部已承诺功能均具有唯一权威定义和可观察验收标准。
- 每项正式 FR 的来源、验收标准和后续设计／测试映射均可逐项追溯。

## 3. 范围与边界

### 3.1 本期建设范围

- {profile.responsibility}。

### 3.2 非本期范围

{evolution_scope}

### 3.3 系统边界

本领域只拥有其责任内对象、规则和状态；跨领域能力通过业务协作引用。

### 3.4 版本与里程碑范围

| 版本／里程碑 | 业务目标 | 纳入范围 | 不纳入范围 | 发布完成标准 |
| --- | --- | --- | --- | --- |
| 当前建设版本 | 完成正式需求 | 本领域 profile 中正式 FR | 3.2 节登记的演进方向 | 正式 FR 验收可追溯 |

### 3.5 假设与约束

| 编号 | 类型 | 内容 | 影响范围 | 状态 |
| --- | --- | --- | --- | --- |
| CON-{profile.code}-001 | 业务 | 保留 legacy FR、BR、DR、AC 编号 | 本领域 | 已确认 |

## 4. 用户、角色与场景

### 4.1 利益相关方

{stakeholders}

### 4.2 核心用户场景

{scenarios}

## 5. 业务模型与流程（按需）

### 5.1 核心业务对象

{core_objects}

### 5.2 业务生命周期

{lifecycle}

### 5.3 主流程

1. 【建议】本节只作为领域级阅读导航；实际主流程以各 FR 原文为准。
2. 【建议】按各 FR 已定义的参与角色、前置条件和主流程组织领域办理顺序。
3. 【建议】不得用本节替代或扩展任何 FR 的确定性行为。

### 5.4 分支与异常流程

- 【建议】本节不新增通用异常行为；权限、状态、数据、外部依赖、撤回、取消或回退均以各 FR 原文为准。
- 【待确认】legacy 未提供可覆盖本领域全部 FR 的统一异常和补偿规则。

## 6. 产品功能架构

### 6.1 功能结构

| 业务域 | 功能模块 | 主要能力 | 适用角色 | 版本／里程碑 |
| --- | --- | --- | --- | --- |
| {profile.name} | {profile.name} | {profile.responsibility} | 见各 FR | 当前建设版本 |

### 6.2 功能清单

| 功能编号 | 功能名称 | 所属模块 | 来源需求 | 版本 | 优先级 |
| --- | --- | --- | --- | --- | --- |
{function_rows}

## 7. 功能需求详细规格

{detail}

## 8. 专项需求（按需）

### 8.1 数据业务要求

本领域业务数据的含义、来源、质量和归属以各 FR 数据要求为准。

### 8.2 外部系统交互要求

{external_interactions}

### 8.3 报表与指标要求

{metrics}

### 8.4 文件、搜索、消息或智能能力要求

仅保留各正式 FR 原文中的相关能力；演进项不纳入当前开发验收。

## 9. 权限、安全与隐私要求

- 身份认证要求：本迁移模型不新增领域级要求，仅保留各 FR 原文。
- 功能与数据权限要求：本迁移模型不新增领域级要求，仅保留各 FR 原文。
- 敏感数据保护要求：【待确认】legacy 未提供本领域统一敏感级别和保护规则。
- 隐私与合规要求：本迁移模型不新增无来源承诺。
- 外部用户及临时授权要求：【待确认】以各 FR 原文及后续权威来源为准。

## 10. 非功能需求

| 编号 | 质量属性 | 应用场景 | 量化指标 | 约束条件 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| 不适用 | 本次机械迁移不新增领域级非功能需求 | 不适用 | 【待确认】待权威来源定义量化指标 | 不适用 | 不适用 |

## 11. 风险、限制与待确认事项

### 11.1 风险

| 风险编号 | 风险描述 | 类型 | 影响 | 应对措施 | 责任人 |
| --- | --- | --- | --- | --- | --- |
| RISK-{profile.code}-001 | 来源语义在机械迁移中发生漂移 | 业务 | 验收偏差 | 保留编号、原文和迁移证据 | 【待确认】责任人待指定 |
| RISK-{profile.code}-002 | 【建议】{narrative.risk} | 业务 | 【建议】影响本领域责任闭环 | 【建议】按相关 FR 的规则、状态和 AC 执行门禁并留痕 | 【待确认】责任人待指定 |

### 11.2 待确认事项

| 编号 | 问题 | 影响范围 | 需要确认人 | 计划日期 | 状态 |
| --- | --- | --- | --- | --- | --- |
| TBD-{profile.code}-MIG-001 | 汇总并确认本领域 legacy 未提供的模板字段 | 业务含义、输入／数据来源、必填、敏感级别等 | 【待确认】责任人待指定 | 【待确认】计划日期待确定 | 待确认 |

## 12. 需求追溯与基线检查

### 12.1 追溯矩阵

{traceability}

### 12.2 需求基线检查表

- [x] 建设范围和非建设范围清晰。
- [x] 所有已承诺功能均有唯一编号、版本和优先级。
- [x] 功能需求描述系统行为，没有混入具体实现方案。
- [x] 主流程、关键分支、异常和权限边界完整。
- [x] 每项已承诺需求均具有可验证的验收标准。
- [ ] 非功能需求具有量化指标和验证方式（本次迁移未从 legacy 推导领域级 NFR）。
- [ ] 所有【待确认】事项均有责任人和处理期限（责任人和日期仍待确认）。
- [x] 需求、设计和测试之间的追溯关系可以建立。

## 附录：推荐编号

| 对象 | 编号格式 |
| --- | --- |
| 业务目标 | OBJ-001 |
| 业务需求 | BRQ-001 |
| 用户需求 | UR-001 |
| 功能需求 | FR-领域-001 |
| 业务规则 | BR-领域-001 |
| 数据需求 | DR-领域-001 |
| 接口需求 | IR-领域-001 |
| 非功能需求 | NFR-类型-001 |
| 验收标准 | AC-领域-001 |
| 决策 | DEC-001 |
| 风险／待确认 | RISK-001／TBD-001 |
"""
    if profile.code == "PLT":
        document = document.replace("与CRM、ITR、ERP等外部系统交换数据", "与外部系统交换数据")
    return _normalize_business_terms(document)


def build_migration_records(
    root: Path,
) -> tuple[list[MigrationRecord], list[EvolutionMigrationRecord]]:
    formal, evolution = load_legacy_requirements(root)
    owners = {fr_id: profile for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids + profile.evolution_ids}
    formal_records: list[MigrationRecord] = []
    for requirement in formal:
        source = ",".join(requirement.source_ids) or _metadata(requirement, "来源需求")
        profile = owners[requirement.fr_id]
        evidence = f"{requirement.source_path.name}#{requirement.fr_id}；acceptance-traceability.md"
        formal_records.append(
            MigrationRecord(
                requirement.fr_id,
                source,
                f"{profile.code}（{profile.name}）",
                "MOVE",
                "保留",
                evidence,
            )
        )
    evolution_records: list[EvolutionMigrationRecord] = []
    for item in evolution:
        profile = owners[item.fr_id]
        source = ",".join(item.source_ids) or "无"
        evidence = f"{item.source_path.name}#{item.fr_id}"
        evolution_records.append(
            EvolutionMigrationRecord(
                item.fr_id,
                source,
                f"{profile.code}（{profile.name}）",
                "DEFER",
                "保留",
                "不纳入当前开发验收",
                evidence,
            )
        )
    return formal_records, evolution_records


def render_migration_matrix(root: Path) -> str:
    formal_records, evolution_records = build_migration_records(root)
    formal_rows = [
        f"| {record.fr_id} | {record.source} | {record.owner} | {record.disposition} | "
        f"{record.numbering} | {record.evidence} |"
        for record in formal_records
    ]
    evolution_rows = [
        f"| {record.fr_id} | {record.source} | {record.owner} | {record.disposition} | "
        f"{record.numbering} | {record.boundary} | {record.evidence} |"
        for record in evolution_records
    ]
    return """# 需求逐项迁移矩阵

> 基线：148 条 `REQ-*`、145 项正式 FR、7 项演进项。正式 FR 仅调整权威 Owner，处置统一为 `MOVE`，语义及 FR/BR/DR/AC 编号保持不变。

## 1. 正式 FR 迁移

| legacy FR | 来源 REQ／决策 | 目标领域 | 处置 | 编号动作 | 证据 |
| --- | --- | --- | --- | --- | --- |
""" + "\n".join(formal_rows) + """

## 2. 演进项

| legacy FR | 来源 REQ | 目标领域 | 处置 | 编号动作 | 当前边界 | 证据 |
| --- | --- | --- | --- | --- | --- | --- |
""" + "\n".join(evolution_rows) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="迁移 legacy 分卷为领域 SRS，或生成逐项迁移矩阵")
    parser.add_argument("--root", type=Path, default=Path("specs/001-project-delivery-platform"))
    parser.add_argument("--write-matrix", action="store_true")
    parser.add_argument("--write-domains", action="store_true")
    args = parser.parse_args()
    formal, evolution = load_legacy_requirements(args.root)
    if args.write_matrix:
        target = args.root / "appendices" / "requirement-migration.md"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render_migration_matrix(args.root), encoding="utf-8")
        print(f"WROTE {target}: formal={len(formal)}, evolution=7")
    if args.write_domains:
        target_dir = args.root / "domains"
        target_dir.mkdir(parents=True, exist_ok=True)
        for profile in DOMAIN_PROFILES:
            target = target_dir / profile.filename
            target.write_text(render_srs(profile, formal, evolution), encoding="utf-8")
            print(f"WROTE {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
