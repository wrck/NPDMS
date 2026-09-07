#!/usr/bin/env python3
"""Project PR7 source-path evidence without inventing code acceptance or Feature Done.

The previous replay suffix mixed source filenames with current-tree acceptance.
This projection replaces those suffixes; Feature Task remains the status authority.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INVENTORY_REF = "757ec7c8eb4df9b18658191176ae5e029f0fbcc5"
OWNER_SELECTION_PARENT = "d68309a371b97de2c3dc802a0c219ecd6ae87ed1"
RAW_PATH = "docs/traceability/code-fact-chronological-replay-2026-09-04.csv"
MARKER = "## 三分支按提交时间代码事实重放（2026-09-04）"
SUPPORT_CONTRACT = "specs/features/F-CUT-002-customer-service-level-fact-contract.json"
FIELDS = ("feature", "owner", "requirement", "sourcePath", "sourceCommits", "pathStatus",
          "currentPath", "semanticStatus", "coverageClaim")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True, encoding="utf-8")


def classify(root: Path, path: str, excluded: set[str]) -> tuple[str, str]:
    if (root / path).is_file():
        return "PRESENT_NOT_ACCEPTANCE_PROOF", path
    if path in excluded:
        return "EXCLUDED_BY_OWNER_SELECTION", ""
    if path.startswith(".spec-repo"):
        return "EXCLUDED_EXTERNAL_SPEC_SNAPSHOT", ""
    if path.startswith("sql/migrations/") and "__" in path:
        suffix = Path(path).name.split("__", 1)[1]
        matches = sorted((root / "sql/migrations").glob("V*__" + suffix))
        if len(matches) == 1:
            return "SUCCESSOR_CANDIDATE_REQUIRES_REVIEW", matches[0].relative_to(root).as_posix()
    return "ABSENT_REQUIRES_REVIEW", ""


def build_rows(root: Path = ROOT) -> list[dict[str, str]]:
    raw = list(csv.DictReader(io.StringIO((root / RAW_PATH).read_text(encoding="utf-8"))))
    commits = defaultdict(set)
    for row in raw:
        commits[row["path"]].add(row["sourceCommit"])
    excluded = set(git(root, "diff", "--diff-filter=D", "--name-only",
                       OWNER_SELECTION_PARENT, INVENTORY_REF).splitlines())
    rows = []
    tasks = git(root, "ls-tree", "-r", "--name-only", INVENTORY_REF, "tasks/features").splitlines()
    for task in tasks:
        text = git(root, "show", f"{INVENTORY_REF}:{task}")
        if MARKER not in text:
            continue
        paths = re.findall(r"(?m)^- `([^`]+)`$", text.split(MARKER, 1)[1])
        requirement = re.search(r"(?m)^> Requirement：`([^`]+)`", text)
        for path in paths:
            status, current = classify(root, path, excluded)
            rows.append(dict(zip(FIELDS, (
                Path(task).stem, "SEE_FEATURE_SPEC", requirement.group(1) if requirement else "SEE_FEATURE_SPEC",
                path, ";".join(sorted(commits[path])), status, current,
                "NOT_ESTABLISHED_BY_PATH_PROJECTION", "NONE_SOURCE_INVENTORY_ONLY"))))
    contract = json.loads((root / SUPPORT_CONTRACT).read_text(encoding="utf-8"))
    for path in sorted(commits):
        if path.startswith("pms-module-customer/") and "/api/servicelevel/" in path:
            status, current = classify(root, path, excluded)
            rows.append(dict(zip(FIELDS, (
                contract["consumerFeatureId"], contract["owner"], contract["supportingRequirement"], path,
                ";".join(sorted(commits[path])), status, current,
                "SUPPORT_CONTRACT_ONLY", contract["requirementCoverage"]))))
    return sorted(rows, key=lambda row: (row["feature"], row["sourcePath"]))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args()
    rows = build_rows()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    summary = {
        "sourceInventoryRef": INVENTORY_REF,
        "checkedCommit": git(ROOT, "rev-parse", "HEAD").strip(),
        "workingTreeDirty": bool(git(ROOT, "status", "--porcelain").strip()),
        "rawCsvSha256": hashlib.sha256((ROOT / RAW_PATH).read_bytes()).hexdigest(),
        "rowCount": len(rows),
        "pathStatusCounts": dict(sorted(Counter(row["pathStatus"] for row in rows).items())),
        "semanticAcceptanceEstablished": False,
        "warning": "Path presence/successor candidates do not establish semantic replay closure or Feature Done.",
    }
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
