from pathlib import Path

from scripts.requirements.migrate_domain_srs import DOMAIN_PROFILES
from scripts.requirements.validate_domain_srs import validate_tree


def test_allow_missing_targets_reports_only_missing_domain_documents(tmp_path: Path):
    root = tmp_path / "spec"
    appendices = root / "appendices"
    appendices.mkdir(parents=True)
    formal_ids = [fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids]
    evolution_ids = [fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids]
    formal_rows = [
        f"| {fr_id} | REQ-{min(index, 142):03d} | TST | MOVE | 保留 | legacy |"
        for index, fr_id in enumerate(formal_ids, 1)
    ]
    evolution_sources = (124, 143, 144, 145, 146, 147, 148)
    evolution_rows = [
        f"| {fr_id} | REQ-{source:03d} | TST | DEFER | 保留 | 不纳入当前开发验收 | legacy |"
        for fr_id, source in zip(evolution_ids, evolution_sources, strict=True)
    ]
    (appendices / "requirement-migration.md").write_text(
        "# 迁移矩阵\n\n## 1. 正式 FR 迁移\n\n"
        + "\n".join(formal_rows)
        + "\n\n## 2. 演进项\n\n"
        + "\n".join(evolution_rows),
        encoding="utf-8",
    )

    errors = validate_tree(root, allow_missing_targets=True)

    assert len(errors) == 13
    assert all(error.startswith("MISSING_TARGET:") for error in errors)


def test_validator_detects_duplicate_definitions_dangling_refs_and_placeholders(tmp_path: Path):
    root = tmp_path / "spec"
    domains = root / "domains"
    domains.mkdir(parents=True)
    text = """# 示例
### FR-TST-001 示例
- **BR-TST-001：** 规则。
- **AC-TST-001：** 验收。
引用 FR-TST-999。
〈填写〉
"""
    (domains / DOMAIN_PROFILES[0].filename).write_text(text, encoding="utf-8")
    (domains / DOMAIN_PROFILES[1].filename).write_text(text, encoding="utf-8")

    errors = validate_tree(root, allow_missing_targets=True)

    assert any("DUPLICATE FR-TST-001" in error for error in errors)
    assert any("DUPLICATE BR-TST-001" in error for error in errors)
    assert any("DANGLING FR-TST-999" in error for error in errors)
    assert any("PLACEHOLDER" in error for error in errors)
