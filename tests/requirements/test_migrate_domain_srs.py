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
