from __future__ import annotations

import argparse
import re
from collections import Counter
from pathlib import Path

try:
    from scripts.requirements.migrate_domain_srs import DOMAIN_PROFILES
except ModuleNotFoundError:  # direct script execution
    from migrate_domain_srs import DOMAIN_PROFILES


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


def _domain_paths(root: Path) -> tuple[list[Path], list[str]]:
    paths: list[Path] = []
    errors: list[str] = []
    for profile in DOMAIN_PROFILES:
        path = root / "domains" / profile.filename
        if path.exists():
            paths.append(path)
        else:
            errors.append(f"MISSING_TARGET: domains/{profile.filename}")
    return paths, errors


def _validate_matrix(root: Path) -> list[str]:
    path = root / "appendices" / "requirement-migration.md"
    if not path.exists():
        return ["MISSING_APPENDIX: appendices/requirement-migration.md"]
    text = path.read_text(encoding="utf-8")
    formal_section = text.split("## 2. 演进项", 1)[0]
    evolution_section = text.split("## 2. 演进项", 1)[1] if "## 2. 演进项" in text else ""
    formal_rows = re.findall(r"^\|\s*(FR-[A-Z]+-\d{3})\s*\|.*?\|\s*MOVE\s*\|\s*保留\s*\|", formal_section, re.MULTILINE)
    evolution_rows = re.findall(r"^\|\s*(FR-[A-Z]+-\d{3})\s*\|.*?\|\s*DEFER\s*\|\s*保留\s*\|\s*不纳入当前开发验收\s*\|", evolution_section, re.MULTILINE)
    errors: list[str] = []
    expected_formal = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    expected_evolution = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids}
    if len(formal_rows) != 145 or set(formal_rows) != expected_formal:
        errors.append(f"FORMAL_MATRIX_COUNT: expected=145 actual={len(formal_rows)} unique={len(set(formal_rows))}")
    if len(evolution_rows) != 7 or set(evolution_rows) != expected_evolution:
        errors.append(f"EVOLUTION_MATRIX_COUNT: expected=7 actual={len(evolution_rows)} unique={len(set(evolution_rows))}")
    req_ids = set(REQ_PATTERN.findall(text))
    expected_reqs = {f"REQ-{number:03d}" for number in range(1, 149)}
    if req_ids != expected_reqs:
        missing = sorted(expected_reqs - req_ids)
        extra = sorted(req_ids - expected_reqs)
        errors.append(f"REQ_COVERAGE: expected=148 actual={len(req_ids)} missing={','.join(missing)} extra={','.join(extra)}")
    return errors


def _validate_authoritative_documents(paths: list[Path]) -> list[str]:
    definitions: dict[str, list[str]] = {}
    occurrences: Counter[str] = Counter()
    errors: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for identifier in ID_PATTERN.findall(text):
            occurrences[identifier] += 1
        for kind, pattern in DEFINITION_PATTERNS.items():
            for identifier in pattern.findall(text):
                definitions.setdefault(identifier, []).append(path.as_posix())
        for placeholder in PLACEHOLDERS:
            if placeholder in text:
                errors.append(f"PLACEHOLDER {placeholder}: {path.as_posix()}")
        for term in RESTRICTED_TERMS:
            if re.search(re.escape(term), text, re.IGNORECASE):
                errors.append(f"RESTRICTED_TERM {term}: {path.as_posix()}")
    for identifier, locations in sorted(definitions.items()):
        if len(locations) > 1:
            errors.append(f"DUPLICATE {identifier}: {';'.join(locations)}")
    expected_formal = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.fr_ids}
    expected_evolution = {fr_id for profile in DOMAIN_PROFILES for fr_id in profile.evolution_ids}
    defined_formal = {identifier for identifier in definitions if identifier.startswith("FR-")}
    if len(paths) == len(DOMAIN_PROFILES) and defined_formal != expected_formal:
        missing = sorted(expected_formal - defined_formal)
        extra = sorted(defined_formal - expected_formal)
        errors.append(
            f"FORMAL_TARGET_COUNT: expected=145 actual={len(defined_formal)} "
            f"missing={','.join(missing)} extra={','.join(extra)}"
        )
    if len(paths) == len(DOMAIN_PROFILES):
        for identifier in sorted(expected_evolution):
            if occurrences[identifier] != 1:
                errors.append(f"EVOLUTION_TARGET_COUNT {identifier}: expected=1 actual={occurrences[identifier]}")
    defined = set(definitions) | expected_evolution
    for identifier in sorted(occurrences):
        if identifier not in defined:
            errors.append(f"DANGLING {identifier}")
    return errors


def validate_tree(root: Path, allow_missing_targets: bool = False) -> list[str]:
    paths, missing = _domain_paths(root)
    matrix_errors = _validate_matrix(root)
    if not paths and allow_missing_targets:
        return missing + matrix_errors
    errors = missing + matrix_errors + _validate_authoritative_documents(paths)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="校验领域化 SRS 迁移完整性和唯一性")
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--allow-missing-targets", action="store_true")
    args = parser.parse_args()
    errors = validate_tree(args.root, allow_missing_targets=args.allow_missing_targets)
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
