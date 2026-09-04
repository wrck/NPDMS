#!/usr/bin/env python3
"""Stabilize a chronological multi-branch replay without excluding any module.

The chronological replay is already represented by one receipt commit per source
commit.  This tool only repairs files that fail compilation.  A failing path is
rebuilt from the current master version and then every source patch touching that
path is replayed again in global commit-time order.  Rejected hunks are recorded;
other files and modules are never rolled back as a group.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Source:
    name: str
    ref: str
    order: int


@dataclass(frozen=True)
class CommitFact:
    timestamp: int
    source_order: int
    topo_order: int
    source: str
    sha: str
    parent: str
    subject: str


class GitFailure(RuntimeError):
    pass


class Stabilizer:
    def __init__(self, repo: Path, master: str, sources: list[Source], cutoff: str, report_dir: Path) -> None:
        self.repo = repo
        self.master = master
        self.sources = sources
        self.cutoff = cutoff
        self.report_dir = report_dir
        self.report_dir.mkdir(parents=True, exist_ok=True)
        self.operations: list[dict[str, str]] = []
        self.reject_dir = self.report_dir / "code-fact-chronological-rejected-hunks-2026-09-04"
        self.reject_dir.mkdir(parents=True, exist_ok=True)
        self.commits = self._ordered_commits()

    def run(self, *args: str, check: bool = True, stdin: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
        proc = subprocess.run(
            list(args), cwd=self.repo, input=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        if check and proc.returncode:
            raise GitFailure(
                f"command failed ({proc.returncode}): {' '.join(args)}\n"
                + proc.stderr.decode(errors="replace")[-4000:]
            )
        return proc

    def git(self, *args: str, check: bool = True, stdin: bytes | None = None) -> subprocess.CompletedProcess[bytes]:
        return self.run("git", *args, check=check, stdin=stdin)

    def text(self, *args: str) -> str:
        return self.git(*args).stdout.decode(errors="replace").strip()

    def _ordered_commits(self) -> list[CommitFact]:
        facts: list[CommitFact] = []
        seen: set[str] = set()
        for source in self.sources:
            merge_base = self.text("merge-base", self.master, source.ref)
            raw = self.text(
                "rev-list", "--reverse", "--topo-order", f"--since={self.cutoff}", f"{merge_base}..{source.ref}"
            )
            for topo, sha in enumerate(filter(None, raw.splitlines())):
                if sha in seen:
                    continue
                seen.add(sha)
                meta = self.text("show", "-s", "--format=%ct%x00%P%x00%s", sha).split("\x00", 2)
                parents = meta[1].split()
                parent = parents[0] if parents else self.text("hash-object", "-t", "tree", "/dev/null")
                facts.append(
                    CommitFact(int(meta[0]), source.order, topo, source.name, sha, parent, meta[2])
                )
        facts.sort(key=lambda item: (item.timestamp, item.source_order, item.topo_order, item.sha))
        return facts

    def dedupe_root_modules(self) -> bool:
        pom = self.repo / "pom.xml"
        if not pom.exists():
            return False
        text = pom.read_text(encoding="utf-8")
        pattern = re.compile(r"(?m)^(?P<indent>\s*)<module>(?P<name>[^<]+)</module>\s*$")
        seen: set[str] = set()
        spans: list[tuple[int, int, str]] = []
        for match in pattern.finditer(text):
            name = match.group("name").strip()
            if name in seen:
                start, end = match.span()
                if end < len(text) and text[end] == "\n":
                    end += 1
                spans.append((start, end, name))
            else:
                seen.add(name)
        if not spans:
            return False
        for start, end, name in reversed(spans):
            text = text[:start] + text[end:]
            self.operations.append({"path": "pom.xml", "decision": "REMOVE_DUPLICATE_REACTOR_MODULE", "detail": name})
        pom.write_text(text, encoding="utf-8")
        return True

    @staticmethod
    def parse_error_paths(log_text: str, repo_name: str) -> list[str]:
        paths: list[str] = []
        seen: set[str] = set()
        patterns = [
            re.compile(rf"\[ERROR\]\s+.*?/{re.escape(repo_name)}/([^:\n]+):\[\d+,\d+\]"),
            re.compile(r"\[ERROR\]\s+([^:\n]+\.(?:java|kt|xml|sql|vue|ts|tsx|js)):\[\d+,\d+\]"),
            re.compile(r"\[ERROR\].*?\((/[^)]+/pom\.xml)\)"),
        ]
        for pattern in patterns:
            for match in pattern.finditer(log_text):
                value = match.group(1).replace("\\", "/")
                if value.startswith("/"):
                    marker = f"/{repo_name}/"
                    if marker in value:
                        value = value.split(marker, 1)[1]
                    else:
                        continue
                value = value.lstrip("./")
                if value not in seen:
                    seen.add(value)
                    paths.append(value)
        return paths

    @staticmethod
    def parse_missing_symbols(log_text: str) -> set[str]:
        symbols: set[str] = set()
        lines = log_text.splitlines()
        for index, line in enumerate(lines):
            if "cannot find symbol" not in line:
                continue
            for candidate in lines[index + 1:index + 5]:
                match = re.search(r"symbol:\s+(?:class|interface|variable|method)\s+([A-Za-z_$][A-Za-z0-9_$]*)", candidate)
                if match:
                    symbols.add(match.group(1))
                    break
        return symbols

    def materialize_missing_symbol_sources(self, symbols: Iterable[str]) -> bool:
        changed = False
        for symbol in sorted(set(symbols)):
            regex = rf"(^|[^A-Za-z0-9_$])(class|interface|record|enum)\s+{re.escape(symbol)}([^A-Za-z0-9_$]|$)"
            candidates: list[tuple[int, str, str]] = []
            for source in self.sources:
                proc = self.git("grep", "-l", "-E", regex, source.ref, "--", "*.java", check=False)
                for line in proc.stdout.decode(errors="replace").splitlines():
                    if ":" not in line:
                        continue
                    _, path = line.split(":", 1)
                    candidates.append((source.order, source.name, path))
            for _, source_name, path in sorted(candidates, reverse=True):
                target = self.repo / path
                if target.exists():
                    break
                source_ref = next(source.ref for source in self.sources if source.name == source_name)
                proc = self.git("show", f"{source_ref}:{path}", check=False)
                if proc.returncode:
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(proc.stdout)
                self.operations.append({"path": path, "decision": "MATERIALIZE_MISSING_SYMBOL_SOURCE", "detail": f"{symbol} from {source_name}"})
                changed = True
                break
        return changed

    def commits_touching(self, path: str) -> list[CommitFact]:
        selected: list[CommitFact] = []
        for item in self.commits:
            proc = self.git("diff", "--quiet", item.parent, item.sha, "--", path, check=False)
            if proc.returncode == 1:
                selected.append(item)
        return selected

    def rebuild_path(self, path: str) -> bool:
        target = self.repo / path
        before = target.read_bytes() if target.exists() and target.is_file() else None
        master_blob = self.git("show", f"{self.master}:{path}", check=False)
        if master_blob.returncode == 0:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(master_blob.stdout)
            self.operations.append({"path": path, "decision": "REBUILD_START_FROM_MASTER", "detail": self.master})
        elif target.exists():
            target.unlink()
            self.operations.append({"path": path, "decision": "REBUILD_START_ABSENT", "detail": "not present on master"})

        for item in self.commits_touching(path):
            patch = self.git("diff", "--binary", "--full-index", item.parent, item.sha, "--", path, check=False).stdout
            if not patch:
                continue
            proc = self.git("apply", "--reject", "--recount", "--whitespace=nowarn", "-", check=False, stdin=patch)
            reject = Path(str(target) + ".rej")
            if reject.exists():
                rel = path.replace("/", "__")
                destination = self.reject_dir / f"{item.timestamp}-{item.sha[:12]}-{rel}.rej"
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(reject), destination)
                self.operations.append({"path": path, "decision": "HUNK_REJECTED_RECORDED", "detail": f"{item.source}@{item.sha}: {destination.relative_to(self.repo)}"})
            if proc.returncode == 0:
                self.operations.append({"path": path, "decision": "REAPPLY_COMMIT_PATH", "detail": f"{item.source}@{item.sha}"})
            else:
                self.operations.append({"path": path, "decision": "REAPPLY_PARTIAL", "detail": f"{item.source}@{item.sha}"})

        after = target.read_bytes() if target.exists() and target.is_file() else None
        return before != after

    def write_reports(self, build_history: list[dict[str, object]]) -> None:
        csv_path = self.report_dir / "code-fact-chronological-stabilization-2026-09-04.csv"
        with csv_path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=["path", "decision", "detail"])
            writer.writeheader()
            writer.writerows(self.operations)
        json_path = self.report_dir / "code-fact-chronological-stabilization-2026-09-04.json"
        json_path.write_text(
            json.dumps(
                {
                    "generatedAt": datetime.now(timezone.utc).isoformat(),
                    "master": self.master,
                    "sourceCommitCount": len(self.commits),
                    "operations": self.operations,
                    "buildHistory": build_history,
                },
                ensure_ascii=False,
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
        md_path = self.report_dir / "code-fact-chronological-stabilization-2026-09-04.md"
        lines = [
            "# 三分支时间序代码重放适配记录",
            "",
            f"- 来源提交：`{len(self.commits)}`",
            "- 原则：不排除任何模块；失败只定位到具体文件或hunk。",
            "- 修复方式：从当前master版本起点，按全局提交时间重新应用该文件的所有来源补丁。",
            "",
            "## 构建迭代",
            "",
            "| 阶段 | 轮次 | 退出码 | 错误文件数 |",
            "|---|---:|---:|---:|",
        ]
        for row in build_history:
            lines.append(f"| {row['stage']} | {row['iteration']} | {row['returnCode']} | {len(row['errorPaths'])} |")
        lines += ["", "## 路径操作", "", "| 路径 | 决策 | 说明 |", "|---|---|---|"]
        for row in self.operations:
            detail = row["detail"].replace("|", "\\|")
            lines.append(f"| `{row['path']}` | `{row['decision']}` | {detail} |")
        md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def run_build(repo: Path, stage: str, iteration: int, log_dir: Path, production_only: bool) -> tuple[int, str]:
    log = log_dir / f"maven-{stage}-{iteration}.log"
    command = ["mvn", "-T", "1C", "-Dcheckstyle.skip=true", "-Dspotless.check.skip=true"]
    if production_only:
        command.append("-Dmaven.test.skip=true")
    else:
        command.append("-DskipTests")
    command.append("package")
    with log.open("wb") as handle:
        proc = subprocess.run(command, cwd=repo, stdout=handle, stderr=subprocess.STDOUT)
    return proc.returncode, log.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--master", required=True)
    parser.add_argument("--cutoff", default="2026-08-21T00:00:00Z")
    parser.add_argument("--source", action="append", required=True, help="name=ref")
    parser.add_argument("--report-dir", default="docs/traceability")
    parser.add_argument("--log-dir", default=".integration-logs/stabilization")
    parser.add_argument("--max-iterations", type=int, default=8)
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    sources = []
    for order, value in enumerate(args.source):
        name, ref = value.split("=", 1)
        sources.append(Source(name, ref, order))
    stabilizer = Stabilizer(repo, args.master, sources, args.cutoff, repo / args.report_dir)
    log_dir = repo / args.log_dir
    log_dir.mkdir(parents=True, exist_ok=True)
    history: list[dict[str, object]] = []

    stabilizer.dedupe_root_modules()
    for stage, production_only in (("production", True), ("test-compile", False)):
        previous_signature: tuple[str, ...] | None = None
        for iteration in range(1, args.max_iterations + 1):
            rc, text = run_build(repo, stage, iteration, log_dir, production_only)
            error_paths = stabilizer.parse_error_paths(text, repo.name)
            history.append({"stage": stage, "iteration": iteration, "returnCode": rc, "errorPaths": error_paths})
            if rc == 0:
                break
            symbols = stabilizer.parse_missing_symbols(text)
            progress = stabilizer.materialize_missing_symbol_sources(symbols)
            signature = tuple(error_paths)
            if signature == previous_signature and not progress:
                break
            previous_signature = signature
            for path in error_paths:
                if (repo / path).is_file() or stabilizer.git("cat-file", "-e", f"{args.master}:{path}", check=False).returncode == 0:
                    progress = stabilizer.rebuild_path(path) or progress
            stabilizer.dedupe_root_modules()
            if not progress:
                break
        else:
            rc = 1
        if rc != 0:
            stabilizer.write_reports(history)
            print(f"{stage} build remains red; see {log_dir}", file=sys.stderr)
            return 20 if production_only else 21

    stabilizer.write_reports(history)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
