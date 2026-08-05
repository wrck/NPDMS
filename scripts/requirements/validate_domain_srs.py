from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

try:
    from scripts.requirements.migrate_domain_srs import (
        DOMAIN_PROFILES,
        LEGACY_FILENAMES,
        build_migration_records,
    )
except ModuleNotFoundError:  # direct script execution
    from migrate_domain_srs import DOMAIN_PROFILES, LEGACY_FILENAMES, build_migration_records


ID_PATTERN = re.compile(r"\b(?:FR|BR|DR|AC)-[A-Z]+-\d{3}\b")
REQ_PATTERN = re.compile(r"\bREQ-\d{3}\b")
DEFINITION_PATTERNS = {
    "FR": re.compile(r"^### (FR-[A-Z]+-\d{3})\s+", re.MULTILINE),
    "BR": re.compile(r"(?:^\|\s*|^[-*]\s+\*\*)(BR-[A-Z]+-\d{3})(?:\s*\||：)", re.MULTILINE),
    "DR": re.compile(r"(?:^\|\s*|^[-*]\s+\*\*)(DR-[A-Z]+-\d{3})(?:\s*\||：)", re.MULTILINE),
    "AC": re.compile(r"^[-*]\s+\*\*(AC-[A-Z]+-\d{3})：", re.MULTILINE),
}
PLACEHOLDERS = ("〈填写〉", "FR-XXX-", "BR-XXX-", "AC-XXX-", "草稿／评审中／已基线")
RESTRICTED_TERMS = ("Yudao", "RuoYi", "若依", "master-jdk25")
NONSTANDARD_BUSINESS_TERMS = ("平台基础",)


def _domain_paths(root: Path) -> tuple[list[Path], list[str], bool]:
    errors: list[str] = []
    expected_paths = {root / "domains" / profile.filename for profile in DOMAIN_PROFILES}
    for profile in DOMAIN_PROFILES:
        path = root / "domains" / profile.filename
        if not path.exists():
            errors.append(f"MISSING_TARGET: domains/{profile.filename}")
    domain_dir = root / "domains"
    actual_paths = set(domain_dir.rglob("*.md")) if domain_dir.exists() else set()
    for path in sorted(actual_paths - expected_paths):
        errors.append(f"EXTRA_TARGET: {path.relative_to(root).as_posix()}")
    expected_complete = all(path.exists() for path in expected_paths)
    return sorted(actual_paths), errors, expected_complete


def _resolve_legacy_root(root: Path, legacy_root: Path | None) -> Path | None:
    candidates = [legacy_root] if legacy_root is not None else [root, root / "legacy"]
    for candidate in candidates:
        if candidate is not None and all((candidate / filename).exists() for filename in LEGACY_FILENAMES):
            return candidate
    return None


def _parse_matrix_rows(section: str, column_count: int) -> tuple[dict[str, tuple[str, ...]], list[str]]:
    rows: dict[str, tuple[str, ...]] = {}
    errors: list[str] = []
    for line in section.splitlines():
        if not re.match(r"^\|\s*FR-[A-Z]+-\d{3}\s*\|", line):
            continue
        cells = tuple(cell.strip() for cell in line.strip().strip("|").split("|"))
        if len(cells) != column_count:
            errors.append(f"MATRIX_COLUMN_COUNT {cells[0]}: expected={column_count} actual={len(cells)}")
            continue
        if cells[0] in rows:
            errors.append(f"DUPLICATE_MATRIX_ROW {cells[0]}")
        rows[cells[0]] = cells[1:]
    return rows, errors


def _validate_matrix(root: Path, legacy_root: Path | None) -> list[str]:
    path = root / "appendices" / "requirement-migration.md"
    if not path.exists():
        return ["MISSING_APPENDIX: appendices/requirement-migration.md"]
    text = path.read_text(encoding="utf-8")
    formal_section = text.split("## 2. 演进项", 1)[0]
    evolution_section = text.split("## 2. 演进项", 1)[1] if "## 2. 演进项" in text else ""
    formal_rows, errors = _parse_matrix_rows(formal_section, 6)
    evolution_rows, evolution_errors = _parse_matrix_rows(evolution_section, 7)
    errors.extend(evolution_errors)
    if legacy_root is None:
        errors.append("MISSING_LEGACY_BASELINE: cannot validate per-FR migration rows")
    else:
        expected_formal_records, expected_evolution_records = build_migration_records(legacy_root)
        expected_formal = {
            record.fr_id: (
                record.source,
                record.owner,
                record.disposition,
                record.numbering,
                record.evidence,
            )
            for record in expected_formal_records
        }
        expected_evolution = {
            record.fr_id: (
                record.source,
                record.owner,
                record.disposition,
                record.numbering,
                record.boundary,
                record.evidence,
            )
            for record in expected_evolution_records
        }
        for fr_id in sorted(set(expected_formal) | set(formal_rows)):
            if formal_rows.get(fr_id) != expected_formal.get(fr_id):
                errors.append(
                    f"FORMAL_MATRIX_MISMATCH {fr_id}: "
                    f"expected={expected_formal.get(fr_id)} actual={formal_rows.get(fr_id)}"
                )
        for fr_id in sorted(set(expected_evolution) | set(evolution_rows)):
            if evolution_rows.get(fr_id) != expected_evolution.get(fr_id):
                errors.append(
                    f"EVOLUTION_MATRIX_MISMATCH {fr_id}: "
                    f"expected={expected_evolution.get(fr_id)} actual={evolution_rows.get(fr_id)}"
                )
    if len(formal_rows) != 145:
        errors.append(f"FORMAL_MATRIX_COUNT: expected=145 actual={len(formal_rows)}")
    if len(evolution_rows) != 7:
        errors.append(f"EVOLUTION_MATRIX_COUNT: expected=7 actual={len(evolution_rows)}")
    req_ids = set(REQ_PATTERN.findall(text))
    expected_reqs = {f"REQ-{number:03d}" for number in range(1, 149)}
    if req_ids != expected_reqs:
        missing = sorted(expected_reqs - req_ids)
        extra = sorted(req_ids - expected_reqs)
        errors.append(f"REQ_COVERAGE: expected=148 actual={len(req_ids)} missing={','.join(missing)} extra={','.join(extra)}")
    return errors


def _validate_authoritative_documents(paths: list[Path], expected_complete: bool) -> list[str]:
    definitions: dict[str, list[str]] = {}
    occurrences: Counter[str] = Counter()
    errors: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        business_text = _without_technical_selection(text)
        for identifier in ID_PATTERN.findall(text):
            occurrences[identifier] += 1
        for kind, pattern in DEFINITION_PATTERNS.items():
            for identifier in pattern.findall(text):
                definitions.setdefault(identifier, []).append(path.as_posix())
        for placeholder in PLACEHOLDERS:
            if placeholder in text:
                errors.append(f"PLACEHOLDER {placeholder}: {path.as_posix()}")
        for term in RESTRICTED_TERMS:
            if re.search(re.escape(term), business_text, re.IGNORECASE):
                errors.append(f"RESTRICTED_TERM {term}: {path.as_posix()}")
        for term in NONSTANDARD_BUSINESS_TERMS:
            if term in business_text:
                errors.append(f"NONSTANDARD_TERM {term}: {path.as_posix()}")
    for identifier, locations in sorted(definitions.items()):
        if len(locations) > 1:
            errors.append(f"DUPLICATE {identifier}: {';'.join(locations)}")
    expected_formal = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    expected_evolution = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids}
    defined_formal = {identifier for identifier in definitions if identifier.startswith("FR-")}
    if expected_complete and defined_formal != expected_formal:
        missing = sorted(expected_formal - defined_formal)
        extra = sorted(defined_formal - expected_formal)
        errors.append(
            f"FORMAL_TARGET_COUNT: expected=145 actual={len(defined_formal)} "
            f"missing={','.join(missing)} extra={','.join(extra)}"
        )
    if expected_complete:
        for identifier in sorted(expected_evolution):
            if occurrences[identifier] != 1:
                errors.append(f"EVOLUTION_TARGET_COUNT {identifier}: expected=1 actual={occurrences[identifier]}")
    defined = set(definitions) | expected_evolution
    for identifier in sorted(occurrences):
        if identifier not in defined:
            errors.append(f"DANGLING {identifier}")
    return errors


def _without_technical_selection(text: str) -> str:
    headings = list(re.finditer(r"^##\s+(.+?)\s*$", text, re.MULTILINE))
    retained = [text[: headings[0].start()] if headings else text]
    for index, heading in enumerate(headings):
        end = headings[index + 1].start() if index + 1 < len(headings) else len(text)
        if "技术选型" not in heading.group(1):
            retained.append(text[heading.start() : end])
    return "\n".join(retained)


def validate_tree(
    root: Path,
    allow_missing_targets: bool = False,
    legacy_root: Path | None = None,
) -> list[str]:
    paths, missing, expected_complete = _domain_paths(root)
    resolved_legacy_root = _resolve_legacy_root(root, legacy_root)
    matrix_errors = _validate_matrix(root, resolved_legacy_root)
    if not paths and allow_missing_targets:
        return missing + matrix_errors
    errors = missing + matrix_errors + _validate_authoritative_documents(paths, expected_complete)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="校验领域化 SRS 迁移完整性和唯一性")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--allow-missing-targets", action="store_true")
    parser.add_argument("--legacy-root", type=Path)
    args = parser.parse_args()
    errors = validate_tree(
        args.root,
        allow_missing_targets=args.allow_missing_targets,
        legacy_root=args.legacy_root,
    )
    non_allowed = [error for error in errors if not (args.allow_missing_targets and error.startswith("MISSING_TARGET:"))]
    for error in errors:
        prefix = "INFO" if args.allow_missing_targets and error.startswith("MISSING_TARGET:") else "ERROR"
        print(f"{prefix}: {error}")
    if non_allowed:
        return 1
    if errors:
        print("PASS: migration model is valid; target domain documents are pending")
    else:
        print("PASS: domain-oriented SRS migration model is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
