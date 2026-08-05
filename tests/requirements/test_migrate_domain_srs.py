import re
from collections import Counter
from pathlib import Path

from scripts.requirements.migrate_domain_srs import (
    DOMAIN_PROFILES,
    DomainProfile,
    load_legacy_requirements,
    parse_legacy_fr,
    render_srs,
)


LEGACY_SAMPLE = """# 旧分卷

## FR-TST-001 示例功能

**用例编号：** UC-TST-001<br>
**来源需求：** REQ-001,REQ-002<br>
**所属版本：** V1<br>
**优先级／复杂度：** P0／中<br>
**参与角色：** 项目经理<br>
**业务场景：** 提交示例业务<br>
**来源标识：** 已确认

### 业务目标

完成可观察的示例业务结果。

### 前置条件

- 用户已登录且具有示例权限。
- 依赖条件：REQ-001。
- 示例对象处于有效状态。

### 主流程

1. 用户提交示例数据。
2. 系统保存并返回结果。

### 分支与异常

- 权限不足：拒绝操作。

### 状态流转

无独立状态流转。

### 业务规则

- **BR-TST-001：** 示例数据必须唯一。

### 数据要求

- 示例名称：必填且唯一。
- **DR-TST-001：** 保存示例名称及来源。

### 权限、通知与审计

- 记录操作人和操作时间。

### 输出与后置条件

- 输出：已保存的示例结果。

### 验收标准

- **AC-TST-001：** Given 条件满足，When 用户提交，Then 返回已保存结果。
"""


def test_parse_and_render_legacy_fr_as_strict_twelve_section_unit(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(LEGACY_SAMPLE, encoding="utf-8")

    requirements = parse_legacy_fr(source)
    assert len(requirements) == 1
    assert requirements[0].fr_id == "FR-TST-001"
    assert requirements[0].source_ids == ("REQ-001", "REQ-002")

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        requirements,
    )

    expected_sections = [
        "基本信息",
        "业务目标",
        "触发条件与前置条件",
        "输入",
        "主流程",
        "分支与异常",
        "状态流转（按需）",
        "业务规则",
        "数据要求",
        "权限、通知与业务留痕",
        "输出与后置条件",
        "验收标准",
    ]
    assert "### FR-TST-001 示例功能" in rendered
    positions = [rendered.index(f"#### {section}") for section in expected_sections]
    assert positions == sorted(positions)
    assert "| 用例编号 | UC-TST-001 |" in rendered
    assert "| 来源需求 | REQ-001,REQ-002 |" in rendered
    assert "| 示例名称 | 【待确认】legacy 未提供输入来源 | 是 | 必填且唯一。 | 由 legacy 数据要求迁移 |" in rendered
    assert "| 来源标识 | 已确认 |" in rendered
    assert "| 需求状态 | 【待确认】legacy 未提供需求状态 |" in rendered
    assert "无独立状态流转。\n\n| 当前状态 |" in rendered
    assert "| 不适用 | legacy 状态说明为非结构化文本 |" in rendered
    assert "| 见现有规格 | 执行本功能 |" not in rendered
    assert "BR-TST-001" in rendered
    assert "DR-TST-001" in rendered
    assert "AC-TST-001" in rendered
    assert "NFR-PERF-TST" not in rendered


def test_renderer_preserves_suggestion_and_marks_unknown_data_attributes(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(
        LEGACY_SAMPLE.replace("**来源标识：** 已确认", "**来源标识：** 【建议】")
        .replace(
            "- 示例名称：必填且唯一。",
            "- 示例名称：用于平台基础展示。\n- 联系方式：选填，属于敏感数据。",
        ),
        encoding="utf-8",
    )
    requirement = parse_legacy_fr(source)[0]

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        [requirement],
    )

    assert "| 来源标识 | 【建议】 |" in rendered
    assert "| 需求状态 | 【待确认】legacy 未提供需求状态 |" in rendered
    assert "| 示例名称 | 【待确认】legacy 未提供输入来源 | 【待确认】 | 用于基础平台展示。 |" in rendered
    assert "| 示例名称 | 【待确认】legacy 未提供业务含义 | 【待确认】 | 【待确认】legacy 未提供数据来源 | 用于基础平台展示。 | 【待确认】 |" in rendered
    assert "| 联系方式 | 【待确认】legacy 未提供输入来源 | 否 | 选填，属于敏感数据。 |" in rendered
    assert "| 联系方式 | 【待确认】legacy 未提供业务含义 | 否 | 【待确认】legacy 未提供数据来源 | 选填，属于敏感数据。 | 敏感 |" in rendered
    assert "平台基础" not in rendered
    assert "基础平台" in rendered
    assert "| NFR-PERF-" not in rendered
    assert "【建议】本节不新增通用异常行为" in rendered


def test_missing_source_marker_is_not_upgraded_to_confirmed(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(LEGACY_SAMPLE.replace("**来源标识：** 已确认\n", ""), encoding="utf-8")

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        parse_legacy_fr(source),
    )

    assert "| 来源标识 | 【待确认】legacy 未提供来源标识 |" in rendered


def test_domain_profiles_are_complete_and_partition_formal_requirements():
    assert len(DOMAIN_PROFILES) == 13
    assigned = [fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids]
    assert len(assigned) == 145
    assert len(set(assigned)) == 145
    evolution = [fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids]
    assert sorted(evolution) == [
        "FR-ANA-003",
        "FR-ANA-004",
        "FR-ANA-005",
        "FR-ANA-006",
        "FR-ANA-007",
        "FR-ANA-008",
        "FR-RES-020",
    ]


def test_current_legacy_baseline_matches_confirmed_counts_and_profiles():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    assigned = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    evolution_assigned = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids}

    assert len(formal) == 145
    assert {requirement.fr_id for requirement in formal} == assigned
    assert all(len(requirement.sections) == 10 for requirement in formal)
    assert len(evolution) == 7
    assert {item.fr_id for item in evolution} == evolution_assigned
    sources = {source for requirement in formal for source in requirement.source_ids} | {
        source for item in evolution for source in item.source_ids
    }
    assert {source for source in sources if source.startswith("REQ-")} == {
        f"REQ-{number:03d}" for number in range(1, 149)
    }


def test_all_rendered_domains_preserve_source_markers_and_add_no_unmarked_defaults():
    root = Path("specs/001-project-delivery-platform")
    formal, _ = load_legacy_requirements(root)
    rendered = "\n".join(render_srs(profile, formal) for profile in DOMAIN_PROFILES)
    source_markers = Counter(requirement.metadata.get("来源标识", "【MISSING】") for requirement in formal)
    rendered_markers = Counter(re.findall(r"^\| 来源标识 \| (.+?) \|$", rendered, re.MULTILINE))

    assert rendered_markers == source_markers
    assert "平台基础" not in rendered
    assert not re.search(r"NFR-(?:PERF|AVL|SEC)-[A-Z]+", rendered)
    assert "| 内部 |" not in rendered
    assert rendered.count("| 需求状态 | 【待确认】legacy 未提供需求状态 |") == 145


def test_domain_static_sections_are_source_driven_and_trace_every_formal_fr():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = {profile.code: render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES}

    trace_rows = re.findall(
        r"^\| (?:REQ|DEC)-\d{3}(?:,(?:REQ|DEC)-\d{3})* \| (FR-[A-Z]+-\d{3}) \| AC-[A-Z]+-\d{3}",
        "\n".join(rendered.values()),
        re.MULTILINE,
    )
    assert len(trace_rows) == 145
    assert set(trace_rows) == {requirement.fr_id for requirement in formal}
    assert "| 项目经理 | 执行 FR-PROJ-" in rendered["PROJ"]
    assert "| 【建议】项目主档 |" in rendered["PROJ"]
    assert "| 【建议】承接 |" in rendered["PROJ"]
    assert "| MET-ANA-001 | 项目组合经营看板 |" in rendered["ANA"]


def test_business_specific_external_interactions_stay_with_owner_domains():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = {profile.code: render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES}

    assert not re.search(r"\b(?:CRM|ERP|ITR)\b", rendered["PLT"])
    assert "FR-PLT-009 仅提供通用集成、事件、幂等和补偿机制" in rendered["PLT"]
    assert "| IR-PROJ-" in rendered["PROJ"] and "CRM" in rendered["PROJ"] and "ERP" in rendered["PROJ"]
    assert "| IR-ENG-" in rendered["ENG"] and "CRM" in rendered["ENG"]
    assert "| IR-CUT-" in rendered["CUT"] and "ITR" in rendered["CUT"]
    assert "| IR-SVC-" in rendered["SVC"] and "ITR" in rendered["SVC"]
    assert "| IR-SPT-" in rendered["SPT"] and "ITR" in rendered["SPT"]
    assert "| IR-TEC-" in rendered["TEC"] and "ITR" in rendered["TEC"]


def test_evolution_items_appear_once_in_non_current_scope_table():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = "\n".join(render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES)

    for item in evolution:
        assert rendered.count(item.fr_id) == 1
        assert not re.search(rf"^### {item.fr_id}\s+", rendered, re.MULTILINE)
        row = next(line for line in rendered.splitlines() if line.startswith(f"| {item.fr_id} |"))
        assert f"| {item.title} |" in row
        assert f"| {item.metadata['来源']} | V3 | P3 |" in row
        for field in ("演进目标", "数据前提", "控制边界", "人工责任", "当前承诺"):
            assert item.metadata[field] in row
        assert "不纳入当前开发验收" in row


def test_real_basic_information_preserves_legacy_metadata_and_dependencies():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    owner = {fr_id: profile for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    rendered = {profile.code: render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES}

    for requirement in formal:
        document = rendered[owner[requirement.fr_id].code]
        start = document.index(f"### {requirement.fr_id} ")
        next_requirement = re.search(r"^### FR-[A-Z]+-\d{3}\s+", document[start + 4 :], re.MULTILINE)
        end = start + 4 + next_requirement.start() if next_requirement else document.index("\n## 8.", start)
        block = document[start:end]
        for legacy_name, target_name in (
            ("来源需求", "来源需求"),
            ("所属版本", "适用版本"),
            ("优先级／复杂度", "优先级／复杂度"),
            ("参与角色", "参与角色"),
            ("业务场景", "业务场景"),
        ):
            expected_value = requirement.metadata[legacy_name].replace("平台基础", "基础平台")
            if owner[requirement.fr_id].code == "PLT":
                expected_value = expected_value.replace("与CRM、ITR、ERP等外部系统交换数据", "与外部系统交换数据")
            assert f"| {target_name} | {expected_value} |" in block
        dependency_line = next(
            (line for line in requirement.sections["前置条件"].splitlines() if "依赖条件" in line),
            "",
        )
        dependency_ids = re.findall(r"(?:REQ|DEC)-\d{3}", dependency_line)
        dependency_text = dependency_line.split("：", 1)[1].strip().rstrip("。")
        non_identifiers = re.sub(r"(?:REQ|DEC)-\d{3}", "", dependency_text)
        expected_dependencies = (
            ",".join(dependency_ids)
            if dependency_ids and not re.sub(r"[,，、;；\s]", "", non_identifiers)
            else dependency_text
        )
        assert f"| 依赖需求 | {expected_dependencies} |" in block


def test_named_none_and_missing_dependencies_keep_distinct_meanings(tmp_path: Path):
    cases = (
        ("- 依赖条件：REQ-001。", "REQ-001"),
        ("- 依赖条件：企业身份源。", "企业身份源"),
        ("- 依赖条件：无。", "无"),
        ("", "【待确认】legacy 未提供依赖需求"),
    )
    for index, (dependency_line, expected) in enumerate(cases):
        source = tmp_path / f"legacy-{index}.md"
        replacement = dependency_line + ("\n" if dependency_line else "")
        source.write_text(
            LEGACY_SAMPLE.replace("- 依赖条件：REQ-001。\n", replacement),
            encoding="utf-8",
        )
        rendered = render_srs(
            DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
            parse_legacy_fr(source),
        )
        assert f"| 依赖需求 | {expected} |" in rendered
        assert f"- 外部依赖条件：{expected}" in rendered


def test_real_named_dependencies_are_preserved_verbatim():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    profile = next(profile for profile in DOMAIN_PROFILES if profile.code == "PLT")
    rendered = render_srs(profile, formal, evolution)

    for fr_id, dependency in (
        ("FR-PLT-001", "企业身份源"),
        ("FR-PLT-009", "主数据编码及外部系统接口"),
        ("FR-PLT-010", "基础设施容量"),
    ):
        start = rendered.index(f"### {fr_id} ")
        end = rendered.find("\n### FR-", start + 5)
        block = rendered[start : end if end != -1 else len(rendered)]
        assert f"| 依赖需求 | {dependency} |" in block
        assert f"- 外部依赖条件：{dependency}" in block


def test_domain_narratives_are_explicit_suggestions_and_documents_are_under_review():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    for profile in DOMAIN_PROFILES:
        rendered = render_srs(profile, formal, evolution)
        assert "| 文档状态 | 评审中 |" in rendered
        assert "| 批准人／日期 | 【待确认】 |" in rendered
        assert "已确认迁移基线" not in rendered
        assert re.search(r"^### 2\.1 业务背景\n\n【建议】", rendered, re.MULTILINE)
        core_problem = rendered.split("### 2.2 核心问题", 1)[1].split("### 2.3 建设目标", 1)[0]
        assert all(line.startswith("- 【建议】") for line in core_problem.splitlines() if line.startswith("- "))
        objective_row = next(line for line in rendered.splitlines() if line.startswith(f"| OBJ-{profile.code}-001 |"))
        assert "| 【建议】" in objective_row


def test_gate_rules_are_channel_neutral_for_all_formal_requirements():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = "\n".join(render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES)

    assert "不得通过前端绕过" not in rendered
    assert rendered.count("任何业务操作入口均不得绕过") == 145


def test_platform_integration_keeps_generic_mechanism_and_project_owner_keeps_objects():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = {profile.code: render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES}
    plt_start = rendered["PLT"].index("### FR-PLT-009 ")
    plt_end = rendered["PLT"].index("### FR-PLT-010 ", plt_start)
    plt_integration = rendered["PLT"][plt_start:plt_end]

    for term in ("项目范围", "合同", "订单行", "设备归属"):
        assert term not in plt_integration
    assert "目标对象 Owner" in plt_integration
    assert "同步批次" in plt_integration
    assert "幂等" in plt_integration
    assert "失败记录" in plt_integration
    assert "项目、合同、订单及订单行业务交互" in rendered["PROJ"]
    assert "FR-PROJ-008、FR-PROJ-011" in rendered["PROJ"]
    assert "通用传输机制追溯 FR-PLT-009" in rendered["PROJ"]
