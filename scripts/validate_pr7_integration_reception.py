#!/usr/bin/env python3
"""Validate and derive the terminal replay ledger for PR #7.

The original replay JSON/CSV are immutable evidence.  This program produces a
second, deterministic "effective" layer that records the integration decision
for every source commit/path without rewriting the raw replay observations.

A decision named RESOLVED_FINAL_TREE_SELECTED deliberately means that the
integrated owner implementation was selected over a source hunk; it does not
claim byte-for-byte or semantic equivalence.  Build, test, migration and review
gates remain mandatory evidence for that selection.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_POLICY = ROOT / "docs/traceability/code-fact-chronological-resolution-policy-2026-09-04.json"
DEFAULT_JSON = ROOT / "docs/traceability/code-fact-chronological-replay-2026-09-04.json"
DEFAULT_CSV = ROOT / "docs/traceability/code-fact-chronological-replay-2026-09-04.csv"
DEFAULT_EFFECTIVE_JSON = ROOT / "docs/traceability/code-fact-chronological-effective-replay-2026-09-04.json"
DEFAULT_EFFECTIVE_CSV = ROOT / "docs/traceability/code-fact-chronological-effective-replay-2026-09-04.csv"
DEFAULT_EFFECTIVE_MD = ROOT / "docs/traceability/code-fact-chronological-effective-replay-2026-09-04.md"

TERMINAL_RAW_DECISIONS = {
    "APPLIED_CODE_CHANGE",
    "APPLIED_ADD_ADD",
    "EXCLUDED_SOURCE_METADATA",
}


class ValidationError(RuntimeError):
    pass


@dataclass(frozen=True)
class EffectiveRow:
    replay_order: int
    source_commit: str
    source_committer_date: str
    source_branches: str
    receipt_commit: str
    feature: str
    path: str
    raw_decision: str
    effective_decision: str
    resolution_reason: str
    subject: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "replayOrder": self.replay_order,
            "sourceCommit": self.source_commit,
            "sourceCommitterDate": self.source_committer_date,
            "sourceBranches": self.source_branches,
            "receiptCommit": self.receipt_commit,
            "feature": self.feature,
            "path": self.path,
            "rawDecision": self.raw_decision,
            "effectiveDecision": self.effective_decision,
            "resolutionReason": self.resolution_reason,
            "subject": self.subject,
        }


def fail(message: str) -> None:
    raise ValidationError(message)


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read JSON {path}: {exc}")


def read_csv(path: Path) -> list[dict[str, str]]:
    try:
        with path.open("r", encoding="utf-8", newline="") as handle:
            return list(csv.DictReader(handle))
    except OSError as exc:
        fail(f"cannot read CSV {path}: {exc}")


def git_blob(path: Path) -> str:
    result = subprocess.run(
        ["git", "hash-object", path.as_posix()],
        cwd=ROOT,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode:
        fail(f"git hash-object failed for {path}: {result.stderr.strip()}")
    return result.stdout.strip()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def assert_raw_evidence(policy: dict[str, Any], raw_json: Path, raw_csv: Path) -> None:
    evidence = policy["rawEvidence"]
    expected = {
        Path(evidence["json"]["path"]): evidence["json"]["gitBlob"],
        Path(evidence["csv"]["path"]): evidence["csv"]["gitBlob"],
    }
    actual_paths = {raw_json.relative_to(ROOT), raw_csv.relative_to(ROOT)}
    if actual_paths != set(expected):
        fail(f"raw evidence paths differ from policy: actual={sorted(map(str, actual_paths))}")
    for relative, expected_blob in expected.items():
        absolute = ROOT / relative
        if not absolute.is_file():
            fail(f"missing immutable raw evidence: {relative}")
        actual_blob = git_blob(relative)
        if actual_blob != expected_blob:
            fail(
                f"raw evidence drift for {relative}: expected git blob {expected_blob}, got {actual_blob}"
            )


def raw_counts(ledger: dict[str, Any], csv_rows: list[dict[str, str]]) -> dict[str, Any]:
    records = ledger["records"]
    pending = 0
    decision_counts: Counter[str] = Counter()
    for record in records:
        for decision in record.get("decisions", []):
            name = decision.get("decision", "")
            decision_counts[name] += 1
            if name == "CONFLICTING_HUNKS_PENDING":
                pending += 1

    unmapped = [row for row in csv_rows if row.get("feature") == "UNMAPPED"]
    unmapped_commits = {row["sourceCommit"] for row in unmapped}
    unmapped_paths = {row["path"] for row in unmapped if row.get("path")}
    return {
        "records": len(records),
        "pendingDecisions": pending,
        "decisionCounts": dict(sorted(decision_counts.items())),
        "unmappedCommits": len(unmapped_commits),
        "unmappedPaths": len(unmapped_paths),
    }


def assert_source_inventory(
    ledger: dict[str, Any], csv_rows: list[dict[str, str]], policy: dict[str, Any]
) -> dict[str, Any]:
    expected = policy["expected"]
    records = ledger["records"]
    source_commits = [record["sourceCommit"] for record in records]
    if len(source_commits) != len(set(source_commits)):
        duplicates = sorted(commit for commit, count in Counter(source_commits).items() if count > 1)
        fail(f"raw ledger contains duplicate source commits: {duplicates[:10]}")

    actual_total = ledger["totalUniqueSourceCommits"]
    if actual_total != expected["totalUniqueSourceCommits"] or len(records) != actual_total:
        fail(
            "source inventory mismatch: "
            f"ledger={actual_total}, records={len(records)}, expected={expected['totalUniqueSourceCommits']}"
        )
    actual_branch_counts = ledger["branchPrimaryCommitCounts"]
    if actual_branch_counts != expected["branchPrimaryCommitCounts"]:
        fail(
            f"branch inventory mismatch: actual={actual_branch_counts}, "
            f"expected={expected['branchPrimaryCommitCounts']}"
        )
    orders = [int(record["replayOrder"]) for record in records]
    if orders != list(range(1, actual_total + 1)):
        fail("replayOrder is not the exact contiguous range 1..572")

    counts = raw_counts(ledger, csv_rows)
    for key in ("pendingDecisions", "unmappedCommits", "unmappedPaths"):
        expected_key = f"raw{key[0].upper()}{key[1:]}"
        expected_value = expected.get(expected_key)
        if expected_value is not None and counts[key] != expected_value:
            fail(f"{key} mismatch: actual={counts[key]}, expected={expected_value}")
    print(
        "[INFO] raw replay counts: "
        f"pending={counts['pendingDecisions']} "
        f"unmappedCommits={counts['unmappedCommits']} "
        f"unmappedPaths={counts['unmappedPaths']}"
    )
    return counts


def match_rule(value: str, pattern: str | None) -> bool:
    return pattern is None or re.search(pattern, value, flags=re.IGNORECASE) is not None


def derive_feature(
    current: str,
    source_branches: str,
    path: str,
    subject: str,
    rules: list[dict[str, Any]],
) -> tuple[str, str]:
    if current and current != "UNMAPPED":
        return current, "raw feature mapping retained"
    haystack = f"{path}\n{subject}"
    for rule in rules:
        if not match_rule(source_branches, rule.get("branchRegex")):
            continue
        if not match_rule(haystack, rule.get("pathOrSubjectRegex")):
            continue
        return rule["feature"], rule["reason"]
    fail(
        f"no feature rule for sourceBranches={source_branches!r}, path={path!r}, subject={subject!r}"
    )


def resolve_decision(
    raw_decision: str,
    path: str,
    rules: list[dict[str, Any]],
) -> tuple[str, str]:
    if raw_decision != "CONFLICTING_HUNKS_PENDING":
        return raw_decision, "raw non-pending decision retained"
    for rule in rules:
        if rule.get("rawDecision") and rule["rawDecision"] != raw_decision:
            continue
        if not match_rule(path, rule.get("pathRegex")):
            continue
        return rule["effectiveDecision"], rule["reason"]
    fail(f"no terminal resolution rule for decision={raw_decision!r}, path={path!r}")


def normalize_csv_rows(csv_rows: list[dict[str, str]]) -> dict[tuple[str, str], dict[str, str]]:
    result: dict[tuple[str, str], dict[str, str]] = {}
    for row in csv_rows:
        key = (row["sourceCommit"], row.get("path", ""))
        existing = result.get(key)
        if existing is not None:
            existing_feature = existing.get("feature", "UNMAPPED")
            incoming_feature = row.get("feature", "UNMAPPED")
            if existing_feature != incoming_feature:
                fail(
                    f"conflicting raw CSV feature mappings for {key}: "
                    f"{existing_feature!r} vs {incoming_feature!r}"
                )
            continue
        result[key] = row
    return result


def derive_effective_rows(
    ledger: dict[str, Any],
    csv_rows: list[dict[str, str]],
    policy: dict[str, Any],
) -> list[EffectiveRow]:
    by_key = normalize_csv_rows(csv_rows)
    feature_rules = policy["featureRules"]
    resolution_rules = policy["resolutionRules"]
    effective: list[EffectiveRow] = []
    seen_keys: set[tuple[str, str]] = set()

    for record in ledger["records"]:
        commit = record["sourceCommit"]
        decisions = record.get("decisions", [])
        if not decisions:
            path = ""
            csv_row = by_key.get((commit, path), {})
            current_feature = csv_row.get("feature", "UNMAPPED")
            feature, feature_reason = derive_feature(
                current_feature,
                ",".join(record["sourceBranches"]),
                path,
                record["subject"],
                feature_rules,
            )
            effective.append(
                EffectiveRow(
                    replay_order=int(record["replayOrder"]),
                    source_commit=commit,
                    source_committer_date=record["sourceCommitterDate"],
                    source_branches=",".join(record["sourceBranches"]),
                    receipt_commit=record.get("receiptCommit", ""),
                    feature=feature,
                    path=path,
                    raw_decision="NO_EFFECTIVE_PATH_DECISION",
                    effective_decision="RESOLVED_NO_EFFECTIVE_CODE_DELTA",
                    resolution_reason=f"commit produced no retained path decision; {feature_reason}",
                    subject=record["subject"],
                )
            )
            continue

        for decision in decisions:
            path = decision["path"]
            key = (commit, path)
            csv_row = by_key.get(key, {})
            current_feature = csv_row.get("feature", "UNMAPPED")
            feature, feature_reason = derive_feature(
                current_feature,
                ",".join(record["sourceBranches"]),
                path,
                record["subject"],
                feature_rules,
            )
            terminal, decision_reason = resolve_decision(
                decision["decision"], path, resolution_rules
            )
            effective.append(
                EffectiveRow(
                    replay_order=int(record["replayOrder"]),
                    source_commit=commit,
                    source_committer_date=record["sourceCommitterDate"],
                    source_branches=",".join(record["sourceBranches"]),
                    receipt_commit=record.get("receiptCommit", ""),
                    feature=feature,
                    path=path,
                    raw_decision=decision["decision"],
                    effective_decision=terminal,
                    resolution_reason=f"{decision_reason}; {feature_reason}",
                    subject=record["subject"],
                )
            )
            seen_keys.add(key)

    # The CSV is a projection of the JSON.  Any extra row would otherwise become
    # invisible in the effective ledger.
    unexpected = sorted(set(by_key) - seen_keys)
    unexpected = [key for key in unexpected if key[1] or by_key[key].get("decision")]
    if unexpected:
        fail(f"raw CSV contains rows not represented by raw JSON decisions: {unexpected[:10]}")

    return sorted(effective, key=lambda row: (row.replay_order, row.path, row.raw_decision))


def assert_repository_state(policy: dict[str, Any]) -> dict[str, Any]:
    assertions = policy["repositoryAssertions"]
    for relative in assertions["absentPaths"]:
        if (ROOT / relative).exists():
            fail(f"excluded path is active in final tree: {relative}")

    v203 = ROOT / assertions["v203"]["path"]
    sql = v203.read_text(encoding="utf-8")
    missing_tables = [
        name for name in assertions["v203"]["requiredTables"]
        if f"CREATE TABLE `{name}`" not in sql
    ]
    forbidden = [token for token in assertions["v203"]["forbiddenTokens"] if token in sql]
    missing_tokens = [token for token in assertions["v203"]["requiredTokens"] if token not in sql]
    if missing_tables or forbidden or missing_tokens:
        fail(
            "V203 boundary mismatch: "
            f"missingTables={missing_tables}, forbidden={forbidden}, missingTokens={missing_tokens}"
        )

    migrations = ROOT / "sql/migrations"
    by_version: defaultdict[int, list[str]] = defaultdict(list)
    for path in migrations.glob("V*__*.sql"):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        if not match:
            fail(f"invalid migration filename: {path.name}")
        by_version[int(match.group(1))].append(path.name)
    duplicates = {version: names for version, names in by_version.items() if len(names) > 1}
    if duplicates:
        fail(f"duplicate active Flyway versions: {duplicates}")

    for item in assertions["occurrenceCounts"]:
        text = (ROOT / item["path"]).read_text(encoding="utf-8")
        actual = text.count(item["needle"])
        if actual != item["count"]:
            fail(
                f"occurrence guard failed for {item['path']}: "
                f"{item['needle']!r} expected={item['count']} actual={actual}"
            )

    for scan in assertions["forbiddenScans"]:
        hits: list[str] = []
        regex = re.compile(scan["regex"])
        for relative in scan["roots"]:
            root = ROOT / relative
            if not root.exists():
                continue
            files: Iterable[Path] = [root] if root.is_file() else root.rglob("*")
            for path in files:
                if not path.is_file() or path.suffix not in scan["suffixes"]:
                    continue
                text = path.read_text(encoding="utf-8", errors="replace")
                if regex.search(text):
                    hits.append(path.relative_to(ROOT).as_posix())
        if hits:
            fail(f"forbidden ownership surface {scan['name']} found in {hits[:20]}")

    return {
        "activeMigrationCount": len(by_version),
        "highestMigrationVersion": max(by_version) if by_version else None,
    }


def summarize_effective(rows: list[EffectiveRow], expected: dict[str, Any]) -> dict[str, Any]:
    source_commits = {row.source_commit for row in rows}
    pending_rows = [
        row for row in rows
        if row.effective_decision == "CONFLICTING_HUNKS_PENDING"
        or row.effective_decision.startswith("UNRESOLVED")
    ]
    unmapped_rows = [row for row in rows if row.feature == "UNMAPPED"]
    summary = {
        "sourceCommits": len(source_commits),
        "rows": len(rows),
        "pendingDecisions": len(pending_rows),
        "unmappedCommits": len({row.source_commit for row in unmapped_rows}),
        "unmappedPaths": len({row.path for row in unmapped_rows if row.path}),
        "featureCounts": dict(sorted(Counter(row.feature for row in rows).items())),
        "effectiveDecisionCounts": dict(
            sorted(Counter(row.effective_decision for row in rows).items())
        ),
    }
    checks = {
        "sourceCommits": expected["totalUniqueSourceCommits"],
        "pendingDecisions": expected["effectivePendingDecisions"],
        "unmappedCommits": expected["effectiveUnmappedCommits"],
        "unmappedPaths": expected["effectiveUnmappedPaths"],
    }
    for key, value in checks.items():
        if summary[key] != value:
            fail(f"effective {key} mismatch: actual={summary[key]}, expected={value}")
    return summary


def render_json(
    policy: dict[str, Any],
    raw: dict[str, Any],
    raw_summary: dict[str, Any],
    repo_summary: dict[str, Any],
    effective_summary: dict[str, Any],
    rows: list[EffectiveRow],
    raw_json: Path,
    raw_csv: Path,
) -> str:
    document = {
        "schemaVersion": 1,
        "purpose": "PR7_TERMINAL_CODE_FACT_REPLAY_DECISIONS",
        "semantics": {
            "rawEvidenceIsImmutable": True,
            "finalTreeSelectionClaimsEquivalence": False,
            "mandatoryIndependentGates": [
                "BACKEND_BUILD_AND_TEST",
                "FRONTEND_TYPECHECK_AND_PRODUCTION_BUILD",
                "MIGRATION_BOUNDARY_AND_EXECUTION",
                "REQUIREMENT_TRACEABILITY_DRIFT",
                "HUMAN_OWNER_REVIEW",
            ],
        },
        "rawEvidence": {
            "base": raw["ledger"]["base"],
            "master": raw["ledger"]["master"],
            "json": {
                "path": raw_json.relative_to(ROOT).as_posix(),
                "gitBlob": git_blob(raw_json.relative_to(ROOT)),
                "sha256": sha256(raw_json),
            },
            "csv": {
                "path": raw_csv.relative_to(ROOT).as_posix(),
                "gitBlob": git_blob(raw_csv.relative_to(ROOT)),
                "sha256": sha256(raw_csv),
            },
            "summary": raw_summary,
        },
        "resolutionPolicy": {
            "path": DEFAULT_POLICY.relative_to(ROOT).as_posix(),
            "schemaVersion": policy["schemaVersion"],
        },
        "repositorySummary": repo_summary,
        "effectiveSummary": effective_summary,
        "records": [row.as_dict() for row in rows],
    }
    return json.dumps(document, ensure_ascii=False, indent=2, sort_keys=False) + "\n"


def render_csv(rows: list[EffectiveRow]) -> str:
    output = io.StringIO(newline="")
    fieldnames = [
        "replayOrder",
        "sourceCommit",
        "sourceCommitterDate",
        "sourceBranches",
        "receiptCommit",
        "feature",
        "path",
        "rawDecision",
        "effectiveDecision",
        "resolutionReason",
        "subject",
    ]
    writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    for row in rows:
        writer.writerow(row.as_dict())
    return output.getvalue()


def render_markdown(
    raw_summary: dict[str, Any],
    repo_summary: dict[str, Any],
    effective_summary: dict[str, Any],
) -> str:
    decision_lines = "\n".join(
        f"| `{name}` | {count} |"
        for name, count in effective_summary["effectiveDecisionCounts"].items()
    )
    feature_lines = "\n".join(
        f"| `{name}` | {count} |"
        for name, count in effective_summary["featureCounts"].items()
    )
    return f"""# PR #7 代码事实重放终态台账

> 本文件由 `scripts/validate_pr7_integration_reception.py` 自动生成。
> 原始 JSON/CSV 保持不可变；本文件只记录本次整合对每个来源 commit/path 的终态选择。
> `RESOLVED_FINAL_TREE_SELECTED` 表示选择整合后的 Owner 实现，并**不**声称与被拒 hunk
> 字节或语义等价；后端、前端、迁移、追溯与人工 Review Gate 仍必须独立通过。

## 汇总

| 指标 | 原始层 | 终态层 |
|---|---:|---:|
| 唯一来源提交 | {effective_summary['sourceCommits']} | {effective_summary['sourceCommits']} |
| 待裁决冲突 | {raw_summary['pendingDecisions']} | {effective_summary['pendingDecisions']} |
| 未映射提交 | {raw_summary['unmappedCommits']} | {effective_summary['unmappedCommits']} |
| 未映射路径 | {raw_summary['unmappedPaths']} | {effective_summary['unmappedPaths']} |
| 有效决策行 | - | {effective_summary['rows']} |
| 活动迁移数 | - | {repo_summary['activeMigrationCount']} |
| 最高活动迁移版本 | - | V{repo_summary['highestMigrationVersion']} |

## 终态决策分布

| 决策 | 行数 |
|---|---:|
{decision_lines}

## Feature 映射分布

| Feature | 行数 |
|---|---:|
{feature_lines}

## Gate 解释

- 原始层保存 cherry-pick、冲突和 metadata 排除事实，不做回写。
- 终态层把每个来源 commit/path 映射到明确 Feature 与终态决策。
- 架构 Owner、successor migration 和明确排除项由专项规则裁决。
- 其余冲突 hunk 选择最终整合树；其正确性由编译、测试、迁移和人工审查证明。
- 本台账通过不等于 PR 可合并；所有独立 Gate 必须同时为绿。
"""


def compare_or_write(path: Path, content: str, check: bool) -> None:
    if check:
        if not path.is_file():
            fail(f"missing generated effective replay output: {path.relative_to(ROOT)}")
        actual = path.read_text(encoding="utf-8")
        if actual != content:
            fail(f"generated effective replay drift: {path.relative_to(ROOT)}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--raw-json", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--raw-csv", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--effective-json", type=Path, default=DEFAULT_EFFECTIVE_JSON)
    parser.add_argument("--effective-csv", type=Path, default=DEFAULT_EFFECTIVE_CSV)
    parser.add_argument("--effective-md", type=Path, default=DEFAULT_EFFECTIVE_MD)
    parser.add_argument("--check", action="store_true")
    parser.add_argument(
        "--summary-output",
        type=Path,
        help="optional machine-readable validation summary for CI artifacts",
    )
    args = parser.parse_args()

    try:
        policy = read_json(args.policy)
        raw = read_json(args.raw_json)
        csv_rows = read_csv(args.raw_csv)
        assert_raw_evidence(policy, args.raw_json, args.raw_csv)
        raw_summary = assert_source_inventory(raw["ledger"], csv_rows, policy)
        rows = derive_effective_rows(raw["ledger"], csv_rows, policy)
        repo_summary = assert_repository_state(policy)
        effective_summary = summarize_effective(rows, policy["expected"])
        effective_json = render_json(
            policy,
            raw,
            raw_summary,
            repo_summary,
            effective_summary,
            rows,
            args.raw_json,
            args.raw_csv,
        )
        effective_csv = render_csv(rows)
        effective_md = render_markdown(raw_summary, repo_summary, effective_summary)

        compare_or_write(args.effective_json, effective_json, args.check)
        compare_or_write(args.effective_csv, effective_csv, args.check)
        compare_or_write(args.effective_md, effective_md, args.check)

        summary = {
            "status": "PASS",
            "raw": raw_summary,
            "effective": effective_summary,
            "repository": repo_summary,
            "outputs": {
                "json": args.effective_json.as_posix(),
                "csv": args.effective_csv.as_posix(),
                "markdown": args.effective_md.as_posix(),
            },
        }
        if args.summary_output:
            args.summary_output.parent.mkdir(parents=True, exist_ok=True)
            args.summary_output.write_text(
                json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
                newline="\n",
            )
        print(
            "[PASS] PR #7 replay resolution: "
            f"sources={effective_summary['sourceCommits']} "
            f"pending={effective_summary['pendingDecisions']} "
            f"unmappedCommits={effective_summary['unmappedCommits']} "
            f"unmappedPaths={effective_summary['unmappedPaths']}"
        )
        return 0
    except ValidationError as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
