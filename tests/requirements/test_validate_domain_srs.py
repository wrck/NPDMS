from pathlib import Path

from scripts.requirements.migrate_domain_srs import DOMAIN_PROFILES, render_migration_matrix
from scripts.requirements.validate_domain_srs import validate_tree


LEGACY_ROOT = Path("specs/001-project-delivery-platform")


def _write_valid_matrix(root: Path) -> str:
    appendices = root / "appendices"
    appendices.mkdir(parents=True, exist_ok=True)
    matrix = render_migration_matrix(LEGACY_ROOT)
    (appendices / "requirement-migration.md").write_text(matrix, encoding="utf-8")
    return matrix


def test_allow_missing_targets_reports_only_missing_domain_documents(tmp_path: Path):
    root = tmp_path / "spec"
    _write_valid_matrix(root)

    errors = validate_tree(root, allow_missing_targets=True, legacy_root=LEGACY_ROOT)

    assert len(errors) == 13
    assert all(error.startswith("MISSING_TARGET:") for error in errors)


def test_validator_detects_duplicate_definitions_dangling_refs_and_placeholders(tmp_path: Path):
    root = tmp_path / "spec"
    _write_valid_matrix(root)
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

    errors = validate_tree(root, allow_missing_targets=True, legacy_root=LEGACY_ROOT)

    assert any("DUPLICATE FR-TST-001" in error for error in errors)
    assert any("DUPLICATE BR-TST-001" in error for error in errors)
    assert any("DANGLING FR-TST-999" in error for error in errors)
    assert any("PLACEHOLDER" in error for error in errors)


def test_validator_rejects_per_fr_source_owner_and_evidence_tampering(tmp_path: Path):
    root = tmp_path / "spec"
    matrix = _write_valid_matrix(root)
    mutations = (
        ("| FR-PLT-001 | REQ-001 |", "| FR-PLT-001 | REQ-002 |"),
        ("| PLT（平台公共能力） | MOVE |", "| PROJ（项目治理） | MOVE |"),
        (
            "01-platform-and-permission.md#FR-PLT-001；acceptance-traceability.md",
            "wrong-evidence.md#FR-PLT-001",
        ),
    )

    for original, replacement in mutations:
        assert original in matrix
        (root / "appendices" / "requirement-migration.md").write_text(
            matrix.replace(original, replacement, 1),
            encoding="utf-8",
        )
        errors = validate_tree(root, allow_missing_targets=True, legacy_root=LEGACY_ROOT)
        assert any("FORMAL_MATRIX_MISMATCH FR-PLT-001" in error for error in errors)


def test_validator_rejects_extra_domain_markdown_and_scans_it_for_duplicates(tmp_path: Path):
    root = tmp_path / "spec"
    _write_valid_matrix(root)
    domains = root / "domains"
    domains.mkdir(parents=True)
    duplicate = """# 示例
### FR-TST-001 示例
| 规则编号 | 规则内容 | 适用条件 | 例外条件 |
| --- | --- | --- | --- |
| BR-TST-001 | 规则 | 条件 | 无 |
"""
    (domains / DOMAIN_PROFILES[0].filename).write_text(duplicate, encoding="utf-8")
    (domains / "unexpected-authoritative-srs.md").write_text(duplicate, encoding="utf-8")

    errors = validate_tree(root, allow_missing_targets=True, legacy_root=LEGACY_ROOT)

    assert any("EXTRA_TARGET: domains/unexpected-authoritative-srs.md" in error for error in errors)
    assert any("DUPLICATE FR-TST-001" in error for error in errors)
    assert any("DUPLICATE BR-TST-001" in error for error in errors)


def test_validator_enforces_basic_platform_term_and_allows_products_only_in_technical_selection(tmp_path: Path):
    root = tmp_path / "spec"
    _write_valid_matrix(root)
    domains = root / "domains"
    domains.mkdir(parents=True)
    text = """# 示例
业务正文使用平台基础和 RuoYi。

## 技术选型

Yudao
"""
    (domains / DOMAIN_PROFILES[0].filename).write_text(text, encoding="utf-8")

    errors = validate_tree(root, allow_missing_targets=True, legacy_root=LEGACY_ROOT)

    assert any("NONSTANDARD_TERM 平台基础" in error for error in errors)
    assert any("RESTRICTED_TERM RuoYi" in error for error in errors)
    assert not any("RESTRICTED_TERM Yudao" in error for error in errors)
