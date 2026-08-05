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
    assert "| 示例名称 | legacy 数据要求 | 是 | 必填且唯一。 | 由现有规格迁移 |" in rendered
    assert "BR-TST-001" in rendered
    assert "DR-TST-001" in rendered
    assert "AC-TST-001" in rendered


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
