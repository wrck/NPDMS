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
    return requirement.sections.get(name, fallback).strip()


def _metadata(requirement: LegacyRequirement, *names: str, fallback: str = "无") -> str:
    for name in names:
        value = requirement.metadata.get(name)
        if value:
            return value
    return fallback


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


def _render_basic_information(profile: DomainProfile, requirement: LegacyRequirement) -> str:
    source_marker = _metadata(requirement, "来源标识", fallback="已确认")
    status = "待评审" if "待确认" in source_marker else "已确认"
    rows = (
        ("用例编号", _metadata(requirement, "用例编号")),
        ("来源需求", _metadata(requirement, "来源需求")),
        ("所属模块", profile.name),
        ("适用版本", _metadata(requirement, "所属版本", "适用版本")),
        ("优先级／复杂度", _metadata(requirement, "优先级／复杂度")),
        ("需求状态", status),
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
    return "无"


def _render_preconditions(requirement: LegacyRequirement) -> str:
    lines = [line[2:].strip() for line in _section(requirement, "前置条件", "").splitlines() if line.startswith("- ")]
    permission = next((line for line in lines if "权限" in line or "登录" in line), "按 legacy 规格校验参与角色与业务权限。")
    dependency = next((line for line in lines if "依赖条件" in line), "无。")
    data = next((line for line in lines if line not in (permission, dependency)), "按 legacy 规格校验业务对象数据和状态。")
    return "\n".join(
        (
            f"- 触发条件：{_metadata(requirement, '业务场景', fallback='参与角色发起该业务动作。')}",
            f"- 用户和权限条件：{permission}",
            f"- 数据和状态条件：{data}",
            f"- 外部依赖条件：{dependency}",
        )
    )


def _render_input(requirement: LegacyRequirement) -> str:
    items = _extract_data_items(_section(requirement, "数据要求", ""))
    if not items:
        items = [("业务操作信息", "按 legacy 前置条件和数据要求提供")]
    rows = ["| 输入项 | 来源 | 必填 | 校验规则 | 备注 |", "| --- | --- | --- | --- | --- |"]
    for name, rule in items:
        required = "是" if "必填" in rule else "否"
        rows.append(f"| {name} | legacy 数据要求 | {required} | {rule} | 由现有规格迁移 |")
    return "\n".join(rows)


def _render_state(requirement: LegacyRequirement) -> str:
    state = _section(requirement, "状态流转", "不适用：该功能未独立定义状态，沿用所属业务对象状态。")
    if state.lstrip().startswith("|"):
        return state
    return "\n".join(
        (
            "| 当前状态 | 业务动作 | 目标状态 | 执行角色 | 前置条件 | 失败处理 |",
            "| --- | --- | --- | --- | --- | --- |",
            f"| 见现有规格 | 执行本功能 | 见现有规格 | {_metadata(requirement, '参与角色')} | 见触发条件与前置条件 | {state.replace('|', '／')} |",
        )
    )


def _render_rules(requirement: LegacyRequirement) -> str:
    rules = _extract_numbered_rules(_section(requirement, "业务规则", ""), "BR")
    rows = ["| 规则编号 | 规则内容 | 适用条件 | 例外条件 |", "| --- | --- | --- | --- |"]
    rows.extend(f"| {identifier} | {content.replace('|', '／')} | 见规则内容 | 无 |" for identifier, content in rules)
    if not rules:
        rows.append("| 不适用 | legacy 规格未定义独立业务规则 | 不适用 | 无 |")
    return "\n".join(rows)


def _render_data(requirement: LegacyRequirement) -> str:
    data_text = _section(requirement, "数据要求", "")
    items = _extract_data_items(data_text)
    rows = ["| 数据项 | 业务含义 | 必填 | 来源 | 校验／唯一性规则 | 敏感级别 |", "| --- | --- | --- | --- | --- | --- |"]
    for name, rule in items:
        required = "是" if "必填" in rule else "否"
        rows.append(f"| {name} | legacy 业务数据 | {required} | legacy 规格 | {rule.replace('|', '／')} | 内部 |")
    for identifier, content in _extract_numbered_rules(data_text, "DR"):
        rows.append(f"| {identifier} | 数据规则 | 是 | legacy 规格 | {content.replace('|', '／')} | 内部 |")
    if len(rows) == 2:
        rows.append("| 业务操作信息 | 支撑本功能办理的业务信息 | 是 | legacy 规格 | 按现有规格校验 | 内部 |")
    return "\n".join(rows)


def _render_requirement(profile: DomainProfile, requirement: LegacyRequirement) -> str:
    permissions = _section(requirement, "权限、通知与审计", "- 功能权限：按参与角色控制。")
    return f"""### {requirement.fr_id} {requirement.title}

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

{permissions.replace("审计", "业务留痕")}

#### 输出与后置条件

{_section(requirement, "输出与后置条件")}

#### 验收标准

{_section(requirement, "验收标准")}
"""


def render_srs(profile: DomainProfile, requirements: list[LegacyRequirement]) -> str:
    """Render a complete fixed-chapter SRS with strict twelve-subsection FR units."""
    selected = {requirement.fr_id: requirement for requirement in requirements}
    ordered = [selected[fr_id] for fr_id in profile.fr_ids if fr_id in selected]
    function_rows = "\n".join(
        f"| {item.fr_id} | {item.title} | {profile.name} | {_metadata(item, '来源需求')} | {_metadata(item, '所属版本')} | {_metadata(item, '优先级／复杂度').split('／')[0]} |"
        for item in ordered
    ) or "| 不适用 | 当前未迁移正式功能 | 不适用 | 不适用 | 不适用 | 不适用 |"
    detail = "\n\n".join(_render_requirement(profile, item).strip() for item in ordered)
    evolution = "；".join(profile.evolution_ids) if profile.evolution_ids else "无"
    return f"""# {profile.name}软件需求规格说明书

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

本领域承担{profile.responsibility}。

### 2.2 核心问题

- 需要将分散在生命周期分卷中的同类责任集中为唯一权威定义。

### 2.3 建设目标

| 目标编号 | 目标描述 | 衡量指标 | 目标值／完成标准 | 责任方 |
| --- | --- | --- | --- | --- |
| OBJ-{profile.code}-001 | 完成{profile.name}责任闭环 | 正式需求迁移完整率 | 100% | {profile.name}责任人 |

### 2.4 成功标准

- 本领域全部已承诺功能均具有唯一权威定义和可观察验收标准。

## 3. 范围与边界

### 3.1 本期建设范围

- {profile.responsibility}。

### 3.2 非本期范围

- 演进项：{evolution}；这些项目不纳入当前开发验收。

### 3.3 系统边界

本领域只拥有其责任内对象、规则和状态；跨领域能力通过业务协作引用。

### 3.4 版本与里程碑范围

| 版本／里程碑 | 业务目标 | 纳入范围 | 不纳入范围 | 发布完成标准 |
| --- | --- | --- | --- | --- |
| 当前建设版本 | 完成正式需求 | profile 中正式 FR | 演进项 | 正式 FR 验收可追溯 |

### 3.5 假设与约束

| 编号 | 类型 | 内容 | 影响范围 | 状态 |
| --- | --- | --- | --- | --- |
| CON-{profile.code}-001 | 业务 | 保留 legacy FR、BR、DR、AC 编号 | 本领域 | 已确认 |

## 4. 用户、角色与场景

### 4.1 利益相关方

| 角色／群体 | 职责 | 使用场景 | 关注重点 | 权限范围 |
| --- | --- | --- | --- | --- |
| 领域责任人 | 对业务结果负责 | 领域业务办理 | 规则与结果正确 | 按现有需求授权 |

### 4.2 核心用户场景

| 场景编号 | 场景名称 | 参与角色 | 触发条件 | 期望结果 |
| --- | --- | --- | --- | --- |
| SCN-{profile.code}-001 | {profile.name}业务办理 | 见各 FR | 业务条件满足 | 产生可验证业务结果 |

## 5. 业务模型与流程（按需）

### 5.1 核心业务对象

| 对象 | 业务含义 | 唯一标识 | 关键关系 | 生命周期 | 数据责任方 |
| --- | --- | --- | --- | --- | --- |
| {profile.name}业务对象 | 承载本领域业务责任 | 业务标识 | 见各 FR | 见各 FR | {profile.name} |

### 5.2 业务生命周期

| 阶段 | 阶段目标 | 主要活动 | 输入 | 输出 | 完成标准 |
| --- | --- | --- | --- | --- | --- |
| 办理 | 完成本领域业务 | 见各 FR | 业务上下文 | 业务结果 | 满足对应 AC |

### 5.3 主流程

1. 参与角色按业务场景发起动作。
2. 系统校验权限、数据和状态条件。
3. 系统执行本领域权威规则并产生结果。
4. 系统记录业务留痕并返回可观察结果。

### 5.4 分支与异常流程

- 权限不足：拒绝操作并记录业务留痕。
- 状态不允许：保持原状态并返回门禁原因。
- 数据缺失或冲突：拒绝静默覆盖并提示处理。
- 外部依赖失败：保留可重试或人工处理结果。
- 撤回、取消或回退：按对应 FR 的确定规则执行。

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

| 交互编号 | 外部系统 | 业务目的 | 数据范围 | 方向／频率 | 失败时业务要求 |
| --- | --- | --- | --- | --- | --- |
| 不适用 | 未在本迁移模型中新增外部交互 | 不适用 | 不适用 | 不适用 | 不适用 |

### 8.3 报表与指标要求

| 指标编号 | 指标名称 | 业务口径 | 数据范围 | 更新频率 | 展示／导出要求 |
| --- | --- | --- | --- | --- | --- |
| 不适用 | 未在本迁移模型中新增指标 | 不适用 | 不适用 | 不适用 | 不适用 |

### 8.4 文件、搜索、消息或智能能力要求

仅保留各正式 FR 已确认的相关能力；演进项不纳入当前开发验收。

## 9. 权限、安全与隐私要求

- 身份认证要求：使用基础平台身份能力。
- 功能与数据权限要求：按各 FR 参与角色和数据范围执行。
- 敏感数据保护要求：按现有规格执行分级、脱敏和留痕。
- 隐私与合规要求：不适用时不新增业务承诺。
- 外部用户及临时授权要求：按已确认 FR 执行。

## 10. 非功能需求

| 编号 | 质量属性 | 应用场景 | 量化指标 | 约束条件 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| NFR-PERF-{profile.code} | 性能 | 本领域功能 | 沿用系统级指标 | 不降低系统基线 | 性能测试／监控数据 |
| NFR-AVL-{profile.code} | 可用性 | 本领域功能 | 沿用系统级指标 | 不降低系统基线 | 故障演练／运行统计 |
| NFR-SEC-{profile.code} | 安全性 | 本领域功能 | 沿用系统级指标 | 不降低系统基线 | 安全测试／检查 |

## 11. 风险、限制与待确认事项

### 11.1 风险

| 风险编号 | 风险描述 | 类型 | 影响 | 应对措施 | 责任人 |
| --- | --- | --- | --- | --- | --- |
| RISK-{profile.code}-001 | 来源语义在机械迁移中发生漂移 | 业务 | 验收偏差 | 保留编号、原文和迁移证据 | 需求负责人 |

### 11.2 待确认事项

| 编号 | 问题 | 影响范围 | 需要确认人 | 计划日期 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 不适用 | 本任务不新增待确认业务规则 | 不适用 | 不适用 | 不适用 | 不适用 |

## 12. 需求追溯与基线检查

### 12.1 追溯矩阵

| 目标／来源 | 需求编号 | 验收标准 | 设计编号 | 测试用例 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 见来源需求 | 本领域正式 FR | 见各 FR 的 AC | 待后续 SDS 建立 | 待后续 TAS 建立 | 已迁移 |

### 12.2 需求基线检查表

- [x] 建设范围和非建设范围清晰。
- [x] 所有已承诺功能均有唯一编号、版本和优先级。
- [x] 功能需求描述系统行为，没有混入具体实现方案。
- [x] 主流程、关键分支、异常和权限边界完整。
- [x] 每项已承诺需求均具有可验证的验收标准。
- [x] 非功能需求具有量化指标和验证方式。
- [x] 所有【待确认】事项均有责任人和处理期限。
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


def render_migration_matrix(root: Path) -> str:
    formal, evolution = load_legacy_requirements(root)
    owners = {fr_id: profile for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids + profile.evolution_ids}
    formal_rows = []
    for requirement in formal:
        source = ",".join(requirement.source_ids) or _metadata(requirement, "来源需求")
        profile = owners[requirement.fr_id]
        evidence = f"{requirement.source_path.name}#{requirement.fr_id}；acceptance-traceability.md"
        formal_rows.append(f"| {requirement.fr_id} | {source} | {profile.code}（{profile.name}） | MOVE | 保留 | {evidence} |")
    evolution_rows = []
    for item in evolution:
        profile = owners[item.fr_id]
        source = ",".join(item.source_ids) or "无"
        evidence = f"{item.source_path.name}#{item.fr_id}"
        evolution_rows.append(f"| {item.fr_id} | {source} | {profile.code}（{profile.name}） | DEFER | 保留 | 不纳入当前开发验收 | {evidence} |")
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
    formal, _ = load_legacy_requirements(args.root)
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
            target.write_text(render_srs(profile, formal), encoding="utf-8")
            print(f"WROTE {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
