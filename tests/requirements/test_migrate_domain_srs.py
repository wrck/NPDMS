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


def test_parse_and_render_legacy_fr_in_original_volume_format(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(LEGACY_SAMPLE, encoding="utf-8")
    requirements = parse_legacy_fr(source)

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        requirements,
    )

    assert "# TST领域需求规格：示例领域" in rendered
    assert "## 1. 领域目标与边界" in rendered
    assert "## 2. 需求清单" in rendered
    assert "## 3. 详细功能规格" in rendered
    assert "## FR-TST-001 示例功能" in rendered
    assert "# 5. 领域验收门禁" in rendered
    assert "## 文档控制" not in rendered
    assert "#### 基本信息" not in rendered

    expected_sections = (
        "业务目标",
        "前置条件",
        "主流程",
        "分支与异常",
        "状态流转",
        "业务规则",
        "数据要求",
        "权限、通知与审计",
        "输出与后置条件",
        "验收标准",
    )
    positions = [rendered.index(f"### {section}") for section in expected_sections]
    assert positions == sorted(positions)
    assert "**用例编号：** UC-TST-001<br>" in rendered
    assert "**来源需求：** REQ-001,REQ-002<br>" in rendered
    assert "**来源标识：** 已确认" in rendered
    assert "- 示例名称：必填且唯一。" in rendered
    assert "BR-TST-001" in rendered
    assert "DR-TST-001" in rendered
    assert "AC-TST-001" in rendered


def test_original_renderer_preserves_source_text_and_only_normalizes_business_terms(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(
        LEGACY_SAMPLE.replace("**来源标识：** 已确认", "**来源标识：** 【建议】")
        .replace("示例数据必须唯一", "平台基础中的示例数据必须唯一")
        .replace("示例对象处于有效状态", "示例对象处于有效状态，不得通过前端绕过"),
        encoding="utf-8",
    )

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        parse_legacy_fr(source),
    )

    assert "**来源标识：** 【建议】" in rendered
    assert "基础平台中的示例数据必须唯一" in rendered
    assert "平台基础" not in rendered
    assert "不得通过前端绕过" not in rendered
    assert "任何业务操作入口均不得绕过" in rendered
    assert "用户提交示例数据。" in rendered
    assert "系统保存并返回结果。" in rendered


def test_missing_source_marker_stays_explicitly_unknown(tmp_path: Path):
    source = tmp_path / "legacy.md"
    source.write_text(LEGACY_SAMPLE.replace("**来源标识：** 已确认\n", ""), encoding="utf-8")

    rendered = render_srs(
        DomainProfile("TST", "示例领域", "示例责任", "TST-example-srs.md", ("FR-TST-001",)),
        parse_legacy_fr(source),
    )

    assert "**来源标识：** 【待确认】legacy 未提供来源标识" in rendered


def test_domain_profiles_partition_all_formal_requirements_and_keep_receipt_in_imp():
    assert len(DOMAIN_PROFILES) == 13
    assigned = [fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids]
    assert len(assigned) == 145
    assert len(set(assigned)) == 145
    owner = {fr_id: profile.code for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    assert owner["FR-ENG-021"] == "IMP"
    assert owner["FR-ENG-003"] == "AST"
    assert owner["FR-ENG-002"] == "RES"

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


def test_domain_profiles_use_code_plus_chinese_name_filenames():
    expected = {
        "PLT": "PLT-平台公共能力需求规格.md",
        "CUS": "CUS-客户与服务关系需求规格.md",
        "PROJ": "PROJ-项目治理需求规格.md",
        "COM": "COM-合同订单履约需求规格.md",
        "SOL": "SOL-交付准备与方案需求规格.md",
        "IMP": "IMP-现场实施需求规格.md",
        "CUT": "CUT-变更切换与稳定治理需求规格.md",
        "ACC": "ACC-验收与项目闭环需求规格.md",
        "AST": "AST-资产管理需求规格.md",
        "RES": "RES-资源与外包需求规格.md",
        "SRV": "SRV-服务运营需求规格.md",
        "KNO": "KNO-技术知识治理需求规格.md",
        "ANA": "ANA-经营分析需求规格.md",
    }

    assert {profile.code: profile.filename for profile in DOMAIN_PROFILES} == expected


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


def test_all_rendered_domains_keep_original_metadata_and_unique_formal_definitions():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = "\n".join(render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES)

    definitions = re.findall(r"^## (FR-[A-Z]+-\d{3})\s+", rendered, re.MULTILINE)
    assert len(definitions) == 145
    assert len(set(definitions)) == 145
    source_markers = Counter(requirement.metadata.get("来源标识", "【MISSING】") for requirement in formal)
    rendered_markers = Counter(re.findall(r"^\*\*来源标识：\*\* (.+?)$", rendered, re.MULTILINE))
    assert rendered_markers == source_markers
    assert rendered.count("### 权限、通知与审计") == 145
    assert rendered.count("### 验收标准") == 145


def test_evolution_items_appear_once_only_in_v3_scope():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered_by_domain = {profile.code: render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES}
    rendered = "\n".join(rendered_by_domain.values())

    for item in evolution:
        assert rendered.count(item.fr_id) == 1
        assert re.search(rf"^### {item.fr_id}\s+", rendered, re.MULTILINE)
        assert not re.search(rf"^## {item.fr_id}\s+", rendered, re.MULTILINE)
    assert "# 4. V3演进范围" in rendered_by_domain["AST"]
    assert "# 4. V3演进范围" in rendered_by_domain["ANA"]
    assert "# 4. V3演进范围" not in rendered_by_domain["IMP"]


def test_gate_rules_remain_channel_neutral_for_all_formal_requirements():
    root = Path("specs/001-project-delivery-platform")
    formal, evolution = load_legacy_requirements(root)
    rendered = "\n".join(render_srs(profile, formal, evolution) for profile in DOMAIN_PROFILES)

    assert "不得通过前端绕过" not in rendered
    assert rendered.count("任何业务操作入口均不得绕过") == 145
