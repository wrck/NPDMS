#!/usr/bin/env python3
"""Replay code facts from multiple divergent branches onto current master.

Rules:
- source commits are ordered by committer time while preserving parent-before-child topology;
- every source commit receives one replay commit, including empty/no-op receipts;
- no PMS/Yudao module is excluded; all textual module files are considered;
- a conflicting file or hunk never rejects other files/hunks from the same commit;
- generated governance files are rebuilt after code replay instead of being copied from stale branches;
- Feature Done is never inferred from code reception alone.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import difflib
import hashlib
import heapq
import json
import os
import re
import shlex
import subprocess
import tempfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


MODULE_PREFIXES = (
    "pms-module-",
    "yudao-module-",
    "yudao-framework/",
    "yudao-server/",
    "yudao-dependencies/",
    "yudao-ui/",
)
CODE_PREFIXES = (
    "scripts/",
    "sql/",
    "docker/",
    "tests/",
)
NON_CODE_PREFIXES = (
    ".github/workflows/",
    ".run/",
    ".superpowers/",
    "docs/",
    "features/",
    "output/",
    "prompts/",
    "specs/",
    "tasks/",
    "需求/",
)
CODE_EXTENSIONS = {
    ".java", ".kt", ".kts", ".groovy", ".scala",
    ".xml", ".sql", ".vue", ".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs",
    ".json", ".json5", ".yaml", ".yml", ".properties", ".toml", ".ini", ".conf",
    ".gradle", ".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd", ".py",
    ".css", ".scss", ".sass", ".less", ".html", ".htm", ".bpmn", ".proto",
    ".graphql", ".gql", ".ftl", ".vm", ".mustache", ".hbs", ".csv",
}
CODE_BASENAMES = {
    "pom.xml", "package.json", "package-lock.json", "pnpm-lock.yaml", "yarn.lock",
    "tsconfig.json", "vite.config.ts", "vite.config.js", "Dockerfile", "compose.yaml",
    "compose.yml", "Makefile", "mvnw", "mvnw.cmd", ".env.example", ".dockerignore",
    ".gitattributes", ".gitignore", "lombok.config",
}


@dataclass(frozen=True)
class CommitMeta:
    sha: str
    timestamp: int
    iso_date: str
    author_name: str
    author_email: str
    subject: str
    parents: tuple[str, ...]
    sources: tuple[str, ...]


@dataclass
class Decision:
    sequence: int
    source_commit: str
    source_date: str
    source_branches: str
    subject: str
    feature: str
    path: str
    decision: str
    detail: str


class GitError(RuntimeError):
    pass


class Replayer:
    def __init__(
        self,
        root: Path,
        master_ref: str,
        sources: dict[str, str],
        cutoff: str,
        report_dir: Path,
    ) -> None:
        self.root = root.resolve()
        self.master_ref = master_ref
        self.sources = sources
        self.cutoff = cutoff
        self.report_dir = report_dir
        self.decisions: list[Decision] = []
        self.commit_rows: list[dict[str, object]] = []
        self.rejects: list[str] = []
        self.migration_map: dict[str, str] = {}
        self.base_master_migrations = set(self.git_lines("ls-tree", "-r", "--name-only", master_ref, "--", "sql/migrations"))
        self.next_migration_version = self._max_migration_version() + 1
        self.source_reachability: dict[str, set[str]] = {}
        self.feature_paths: dict[str, set[str]] = defaultdict(set)
        self.feature_commits: dict[str, set[str]] = defaultdict(set)
        self.feature_conflicts: Counter[str] = Counter()

    def run_git(
        self,
        *args: str,
        check: bool = True,
        data: bytes | None = None,
        env: dict[str, str] | None = None,
    ) -> subprocess.CompletedProcess[bytes]:
        cmd = ["git", *args]
        merged_env = os.environ.copy()
        if env:
            merged_env.update(env)
        proc = subprocess.run(
            cmd,
            cwd=self.root,
            input=data,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=merged_env,
        )
        if check and proc.returncode != 0:
            raise GitError(
                f"command failed ({proc.returncode}): {' '.join(shlex.quote(x) for x in cmd)}\n"
                f"stdout:\n{proc.stdout.decode(errors='replace')}\n"
                f"stderr:\n{proc.stderr.decode(errors='replace')}"
            )
        return proc

    def git_text(self, *args: str, check: bool = True) -> str:
        return self.run_git(*args, check=check).stdout.decode("utf-8", errors="replace").strip()

    def git_lines(self, *args: str, check: bool = True) -> list[str]:
        text = self.git_text(*args, check=check)
        return text.splitlines() if text else []

    def blob(self, ref: str, path: str) -> bytes | None:
        proc = self.run_git("show", f"{ref}:{path}", check=False)
        return proc.stdout if proc.returncode == 0 else None

    def worktree_bytes(self, path: str) -> bytes | None:
        target = self.root / path
        if not target.is_file():
            return None
        return target.read_bytes()

    def write_worktree(self, path: str, content: bytes) -> None:
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content)

    @staticmethod
    def is_binary(content: bytes | None) -> bool:
        if content is None:
            return False
        if b"\0" in content:
            return True
        sample = content[:8192]
        try:
            sample.decode("utf-8")
            return False
        except UnicodeDecodeError:
            return True

    @staticmethod
    def is_code_path(path: str) -> bool:
        normalized = path.replace("\\", "/")
        if normalized.startswith(NON_CODE_PREFIXES):
            return False
        top = normalized.split("/", 1)[0]
        if top.startswith("pms-module-") or top.startswith("yudao-module-"):
            return True
        if normalized.startswith(("yudao-framework/", "yudao-server/", "yudao-dependencies/", "yudao-ui/")):
            return True
        if normalized.startswith(CODE_PREFIXES):
            return True
        p = Path(normalized)
        if p.name in CODE_BASENAMES or p.suffix.lower() in CODE_EXTENSIONS:
            return "/" not in normalized or normalized.startswith(("config/", "bin/"))
        return False

    def _max_migration_version(self) -> int:
        max_version = 0
        migration_dir = self.root / "sql/migrations"
        if migration_dir.is_dir():
            for path in migration_dir.glob("V*.sql"):
                match = re.match(r"V(\d+)__", path.name)
                if match:
                    max_version = max(max_version, int(match.group(1)))
        return max_version

    @staticmethod
    def canonical_sql(content: bytes) -> str:
        text = content.decode("utf-8", errors="replace")
        text = re.sub(r"(?m)^\s*--.*$", "", text)
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        return re.sub(r"\s+", " ", text).strip().lower()

    def find_equivalent_migration(self, content: bytes) -> str | None:
        candidate = self.canonical_sql(content)
        if not candidate:
            return None
        migration_dir = self.root / "sql/migrations"
        if not migration_dir.is_dir():
            return None
        for path in sorted(migration_dir.glob("V*.sql")):
            if self.canonical_sql(path.read_bytes()) == candidate:
                return path.relative_to(self.root).as_posix()
        return None

    @staticmethod
    def created_tables(content: bytes) -> set[str]:
        text = content.decode("utf-8", errors="replace")
        return {
            match.group(1).lower()
            for match in re.finditer(
                r"(?is)\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z0-9_]+)`?",
                text,
            )
        }

    def table_already_owned(self, table: str) -> bool:
        migration_dir = self.root / "sql/migrations"
        pattern = re.compile(
            rf"(?is)\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?{re.escape(table)}`?"
        )
        for path in migration_dir.glob("V*.sql"):
            try:
                if pattern.search(path.read_text(encoding="utf-8", errors="replace")):
                    return True
            except OSError:
                continue
        return False

    def commit_metadata(self, sha: str, selected: set[str]) -> CommitMeta:
        raw = self.git_text("show", "-s", "--format=%ct%x00%cI%x00%an%x00%ae%x00%s%x00%P", sha)
        fields = raw.split("\x00")
        if len(fields) != 6:
            raise GitError(f"unexpected metadata for {sha}: {raw!r}")
        timestamp, iso_date, author_name, author_email, subject, parent_text = fields
        parents = tuple(x for x in parent_text.split() if x)
        source_names = tuple(
            name for name, reachable in self.source_reachability.items() if sha in reachable
        )
        return CommitMeta(
            sha=sha,
            timestamp=int(timestamp),
            iso_date=iso_date,
            author_name=author_name or "unknown",
            author_email=author_email or "unknown@example.invalid",
            subject=subject,
            parents=parents,
            sources=source_names,
        )

    def ordered_commits(self) -> list[CommitMeta]:
        selected: set[str] = set()
        for name, ref in self.sources.items():
            reachable = set(self.git_lines("rev-list", ref))
            self.source_reachability[name] = reachable
            selected.update(
                self.git_lines(
                    "rev-list",
                    f"--since={self.cutoff}",
                    ref,
                    "--not",
                    self.master_ref,
                )
            )

        metadata = {sha: self.commit_metadata(sha, selected) for sha in selected}
        indegree: dict[str, int] = {sha: 0 for sha in selected}
        children: dict[str, list[str]] = defaultdict(list)
        for sha, meta in metadata.items():
            for parent in meta.parents:
                if parent in selected:
                    indegree[sha] += 1
                    children[parent].append(sha)

        heap: list[tuple[int, str]] = []
        for sha, degree in indegree.items():
            if degree == 0:
                heapq.heappush(heap, (metadata[sha].timestamp, sha))

        ordered: list[CommitMeta] = []
        while heap:
            _, sha = heapq.heappop(heap)
            ordered.append(metadata[sha])
            for child in children.get(sha, []):
                indegree[child] -= 1
                if indegree[child] == 0:
                    heapq.heappush(heap, (metadata[child].timestamp, child))

        if len(ordered) != len(selected):
            missing = sorted(selected - {x.sha for x in ordered})
            raise GitError(f"topological ordering failed; missing={missing[:20]}")
        return ordered

    def changed_paths(self, meta: CommitMeta) -> list[tuple[str, str]]:
        if meta.parents:
            args = ("diff-tree", "--no-commit-id", "--name-status", "-r", "--no-renames", meta.parents[0], meta.sha)
        else:
            args = ("diff-tree", "--root", "--no-commit-id", "--name-status", "-r", "--no-renames", meta.sha)
        rows: list[tuple[str, str]] = []
        for line in self.git_lines(*args):
            parts = line.split("\t", 1)
            if len(parts) == 2:
                rows.append((parts[0], parts[1]))
        return rows

    @staticmethod
    def normalized_lines(content: bytes) -> set[str]:
        text = content.decode("utf-8", errors="replace")
        return {re.sub(r"\s+", " ", line).strip() for line in text.splitlines() if line.strip()}

    def merge_text(self, ours: bytes, base: bytes, theirs: bytes) -> bytes | None:
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            ours_path = tmp_path / "ours"
            base_path = tmp_path / "base"
            theirs_path = tmp_path / "theirs"
            ours_path.write_bytes(ours)
            base_path.write_bytes(base)
            theirs_path.write_bytes(theirs)
            proc = subprocess.run(
                ["git", "merge-file", "-p", "--diff3", str(ours_path), str(base_path), str(theirs_path)],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            return proc.stdout if proc.returncode == 0 else None

    def source_patch(self, parent: str | None, sha: str, path: str) -> bytes:
        if parent:
            return self.run_git("diff", "--binary", "--full-index", parent, sha, "--", path, check=False).stdout
        return self.run_git("show", "--binary", "--full-index", "--format=", sha, "--", path, check=False).stdout

    def apply_patch_hunks(self, parent: str | None, meta: CommitMeta, path: str) -> tuple[bool, str]:
        patch = self.source_patch(parent, meta.sha, path)
        if not patch:
            return False, "empty source patch"
        before = self.worktree_bytes(path)
        before_rejects = {p.resolve() for p in self.root.rglob("*.rej")}
        proc = self.run_git(
            "apply", "--reject", "--recount", "--whitespace=nowarn", "-",
            check=False,
            data=patch,
        )
        after = self.worktree_bytes(path)
        after_rejects = {p.resolve() for p in self.root.rglob("*.rej")}
        new_rejects = sorted(after_rejects - before_rejects)
        for reject in new_rejects:
            try:
                content = reject.read_text(encoding="utf-8", errors="replace")
            except OSError:
                content = "<unable to read reject>"
            self.rejects.append(
                f"\n# SOURCE {meta.sha} {meta.iso_date} {'|'.join(meta.sources)}\n"
                f"# PATH {path}\n{content}\n"
            )
            reject.unlink(missing_ok=True)
        detail = proc.stderr.decode("utf-8", errors="replace").strip()[-500:]
        if new_rejects:
            detail = f"rejected_hunks={len(new_rejects)}; {detail}"
        return before != after, detail or f"git-apply rc={proc.returncode}"

    def feature_for(self, path: str, subject: str, source_names: tuple[str, ...]) -> str:
        combined = f"{path} {subject}".lower()
        explicit = re.search(r"\bf-(acc|ast|com|cut|imp|ins|int|proj|sol|plt)-(\d{3})\b", combined)
        if explicit:
            return f"F-{explicit.group(1).upper()}-{explicit.group(2)}"
        if "satisfaction" in combined or "满意度" in subject:
            return "F-ACC-002"
        if any(token in combined for token in ("acceptancereport", "acceptanceactivity", "acceptancescope", "deliverable", "验收报告", "交付件")):
            return "F-ACC-001"
        if "implementationreadiness" in combined or "实施就绪" in subject:
            return "F-IMP-001"
        if "arrivalacceptance" in combined or "到货" in subject:
            return "F-IMP-002"
        if any(token in combined for token in ("deviceops", "device-ops", "collection", "credential")):
            return "F-INT-012"
        if "commerce" in combined or "合同订单" in subject:
            return "F-COM-001"
        if "cutover" in combined or "割接" in subject:
            keyword_map = [
                (("spare", "备件"), "F-CUT-010"),
                (("reminder", "leadtime", "提醒"), "F-CUT-008"),
                (("dashboard", "kpi"), "F-CUT-007"),
                (("closure", "p6", "闭环"), "F-CUT-006"),
                (("approval", "p5", "审批"), "F-CUT-005"),
                (("plan", "p4", "方案"), "F-CUT-004"),
                (("checklist", "p3", "清单"), "F-CUT-003"),
                (("intake", "assessment", "taskv2", "分级"), "F-CUT-002"),
            ]
            for keys, feature in keyword_map:
                if any(key in combined for key in keys):
                    return feature
            return "F-CUT-001"
        if any("f-acc" in name for name in source_names):
            return "F-ACC-001"
        return "UNMAPPED"

    def record(
        self,
        sequence: int,
        meta: CommitMeta,
        path: str,
        decision: str,
        detail: str,
    ) -> None:
        feature = self.feature_for(path, meta.subject, meta.sources)
        row = Decision(
            sequence=sequence,
            source_commit=meta.sha,
            source_date=meta.iso_date,
            source_branches="|".join(meta.sources),
            subject=meta.subject,
            feature=feature,
            path=path,
            decision=decision,
            detail=detail,
        )
        self.decisions.append(row)
        if decision.startswith("APPLIED") or decision.startswith("NOOP"):
            if feature != "UNMAPPED":
                self.feature_paths[feature].add(path)
                self.feature_commits[feature].add(meta.sha)
        if "CONFLICT" in decision or "ADAPT" in decision:
            if feature != "UNMAPPED":
                self.feature_conflicts[feature] += 1

    def apply_migration(self, sequence: int, meta: CommitMeta, status: str, path: str) -> None:
        parent = meta.parents[0] if meta.parents else None
        theirs = self.blob(meta.sha, path)
        if theirs is None:
            self.record(sequence, meta, path, "MIGRATION_DELETE_IGNORED", "Flyway migrations are immutable")
            return
        if self.is_binary(theirs):
            self.record(sequence, meta, path, "BINARY_SKIPPED", "binary migration content")
            return
        equivalent = self.find_equivalent_migration(theirs)
        if equivalent:
            self.migration_map[path] = equivalent
            self.record(sequence, meta, path, "NOOP_MIGRATION_EQUIVALENT", equivalent)
            return

        mapped = self.migration_map.get(path)
        if mapped:
            header = (
                f"-- Chronological code-fact replay update from {meta.sha} ({'|'.join(meta.sources)}).\n"
                f"-- Feature remains governed separately from code reception.\n\n"
            ).encode()
            self.write_worktree(mapped, header + theirs)
            self.record(sequence, meta, mapped, "APPLIED_MIGRATION_UPDATE", f"source={path}")
            return

        if path in self.base_master_migrations or (self.root / path).exists():
            current = self.worktree_bytes(path)
            if current == theirs:
                self.migration_map[path] = path
                self.record(sequence, meta, path, "NOOP_MIGRATION_EXACT", "same active migration")
                return
            tables = sorted(self.created_tables(theirs))
            overlapping = [table for table in tables if self.table_already_owned(table)]
            detail = "active master migration differs"
            if overlapping:
                detail += f"; existing_table_owners={','.join(overlapping)}"
            self.record(sequence, meta, path, "MIGRATION_ADAPT_REQUIRED", detail)
            self.rejects.append(
                f"\n# MIGRATION CANDIDATE {meta.sha} {'|'.join(meta.sources)} {path}\n"
                + theirs.decode("utf-8", errors="replace")
                + "\n"
            )
            return

        original_name = Path(path).name
        match = re.match(r"V(\d+)__(.+)\.sql$", original_name)
        if not match:
            self.write_worktree(path, theirs)
            self.record(sequence, meta, path, "APPLIED_NEW_MIGRATION", "non-standard migration name retained")
            return
        original_version, slug = match.groups()
        target = f"sql/migrations/V{self.next_migration_version}__replay_{original_version}_{slug}.sql"
        self.next_migration_version += 1
        self.migration_map[path] = target
        header = (
            f"-- Chronologically replayed from {meta.sha} ({'|'.join(meta.sources)}), original {path}.\n"
            f"-- Renumbered after current master; Feature status is not promoted by this receipt.\n\n"
        ).encode()
        self.write_worktree(target, header + theirs)
        self.record(sequence, meta, target, "APPLIED_MIGRATION_RENUMBERED", f"source={path}")

    def apply_normal_path(self, sequence: int, meta: CommitMeta, status: str, path: str) -> None:
        parent = meta.parents[0] if meta.parents else None
        base = self.blob(parent, path) if parent else None
        theirs = self.blob(meta.sha, path)
        ours = self.worktree_bytes(path)

        if self.is_binary(base) or self.is_binary(theirs) or self.is_binary(ours):
            self.record(sequence, meta, path, "BINARY_SKIPPED", "non-text module asset")
            return

        if theirs is None:
            if ours is None:
                self.record(sequence, meta, path, "NOOP_DELETE_ABSENT", "already absent")
            elif ours == base:
                (self.root / path).unlink()
                self.record(sequence, meta, path, "APPLIED_DELETE", "current matched source parent")
            else:
                self.record(sequence, meta, path, "DELETE_CONFLICT_KEEP_CURRENT", "current has later/divergent code")
            return

        if ours == theirs:
            self.record(sequence, meta, path, "NOOP_EXACT", "same blob")
            return
        if ours is None:
            self.write_worktree(path, theirs)
            self.record(sequence, meta, path, "APPLIED_NEW_FILE", "missing from current replay tree")
            return
        if ours == base:
            self.write_worktree(path, theirs)
            self.record(sequence, meta, path, "APPLIED_FAST_FORWARD", "current matched source parent")
            return

        ours_lines = self.normalized_lines(ours)
        theirs_lines = self.normalized_lines(theirs)
        if theirs_lines and theirs_lines.issubset(ours_lines):
            self.record(sequence, meta, path, "NOOP_CURRENT_SUPERSET", "all normalized source lines present")
            return
        if ours_lines and ours_lines.issubset(theirs_lines):
            self.write_worktree(path, theirs)
            self.record(sequence, meta, path, "APPLIED_SOURCE_SUPERSET", "source contains all current normalized lines")
            return

        merged = self.merge_text(ours, base or b"", theirs)
        if merged is not None:
            self.write_worktree(path, merged)
            self.record(sequence, meta, path, "APPLIED_THREE_WAY", "clean textual merge")
            return

        changed, detail = self.apply_patch_hunks(parent, meta, path)
        if changed:
            self.record(sequence, meta, path, "APPLIED_PARTIAL_HUNKS", detail)
        else:
            self.record(sequence, meta, path, "CONFLICT_ADAPT_REQUIRED", detail)

    def replay_commit(self, sequence: int, meta: CommitMeta) -> None:
        before = self.git_text("rev-parse", "HEAD")
        paths = self.changed_paths(meta)
        code_paths = [(status, path) for status, path in paths if self.is_code_path(path)]
        start_decisions = len(self.decisions)
        for status, path in code_paths:
            if path.startswith("sql/migrations/") and path.endswith(".sql"):
                self.apply_migration(sequence, meta, status, path)
            else:
                self.apply_normal_path(sequence, meta, status, path)

        self.run_git("add", "-A")
        applied = [
            d for d in self.decisions[start_decisions:]
            if d.decision.startswith("APPLIED")
        ]
        conflicts = [
            d for d in self.decisions[start_decisions:]
            if "CONFLICT" in d.decision or "ADAPT" in d.decision
        ]
        message = (
            f"replay(code-fact): {meta.subject}\n\n"
            f"Source-Commit: {meta.sha}\n"
            f"Source-Date: {meta.iso_date}\n"
            f"Source-Branches: {', '.join(meta.sources)}\n"
            f"Replay-Sequence: {sequence}\n"
            f"Code-Paths-Considered: {len(code_paths)}\n"
            f"Applied-Path-Decisions: {len(applied)}\n"
            f"Conflict-Or-Adapt-Decisions: {len(conflicts)}\n"
            "Policy: no module exclusion; a path/hunk conflict does not reject other code in this commit."
        )
        env = {
            "GIT_AUTHOR_NAME": meta.author_name,
            "GIT_AUTHOR_EMAIL": meta.author_email,
            "GIT_AUTHOR_DATE": meta.iso_date,
            "GIT_COMMITTER_DATE": meta.iso_date,
        }
        self.run_git("commit", "--allow-empty", "--no-verify", "-m", message, env=env)
        after = self.git_text("rev-parse", "HEAD")
        self.commit_rows.append(
            {
                "sequence": sequence,
                "sourceCommit": meta.sha,
                "sourceDate": meta.iso_date,
                "sourceBranches": "|".join(meta.sources),
                "subject": meta.subject,
                "changedPaths": len(paths),
                "codePathsConsidered": len(code_paths),
                "appliedDecisions": len(applied),
                "conflictDecisions": len(conflicts),
                "replayCommit": after,
                "parentBeforeReplay": before,
            }
        )

    def normalize_root_pom_modules(self) -> list[str]:
        pom = self.root / "pom.xml"
        if not pom.exists():
            return []
        text = pom.read_text(encoding="utf-8")
        start = text.find("<modules>")
        end = text.find("</modules>", start)
        if start < 0 or end < 0:
            return []
        section = text[start:end]
        seen: set[str] = set()
        removed: list[str] = []

        def replace(match: re.Match[str]) -> str:
            module = match.group(2).strip()
            if module in seen:
                removed.append(module)
                return ""
            seen.add(module)
            return match.group(0)

        normalized = re.sub(r"(?m)^(\s*)<module>([^<]+)</module>\s*\n?", replace, section)
        if normalized != section:
            pom.write_text(text[:start] + normalized + text[end:], encoding="utf-8")
        return removed

    def update_feature_tasks(self) -> None:
        marker = "## 代码事实按时间逐提交重放回执（2026-09-04）"
        for feature in sorted(self.feature_commits):
            task = self.root / "tasks/features" / f"{feature}.md"
            if not task.exists():
                continue
            text = task.read_text(encoding="utf-8")
            if marker in text:
                continue
            status_pattern = re.compile(r"(?m)^> Feature实施状态：`([^`]+)`")
            status_match = status_pattern.search(text)
            if status_match and "NOT_STARTED" in status_match.group(1):
                text = status_pattern.sub("> Feature实施状态：`IN_PROGRESS`", text, count=1)
            elif not status_match:
                first_newline = text.find("\n")
                insertion = "\n> Feature实施状态：`IN_PROGRESS`\n"
                text = text[:first_newline] + insertion + text[first_newline:]
            commits = sorted(self.feature_commits[feature])
            paths = sorted(self.feature_paths[feature])
            block = [
                "",
                marker,
                "",
                "> 本节仅记录提交代码事实；存在开放Gate时Feature继续保持IN_PROGRESS，代码接收不自动构成Implementation Done。",
                "",
                f"- 来源提交数：`{len(commits)}`",
                f"- 已接收或已确认主干等价路径数：`{len(paths)}`",
                f"- 仍需逐路径适配记录数：`{self.feature_conflicts.get(feature, 0)}`",
                "- 接收范围：三个来源分支中全部模块的文本代码、测试、构建配置与可安全迁移SQL；无模块级排除。",
                "- 完整提交顺序和逐路径裁决：`docs/traceability/code-fact-chronological-replay-2026-09-04.csv`。",
                "",
                "来源提交：",
                "",
            ]
            block.extend(f"- `{sha}`" for sha in commits[:40])
            if len(commits) > 40:
                block.append(f"- 其余 `{len(commits) - 40}` 个提交见CSV。")
            text = text.rstrip() + "\n" + "\n".join(block) + "\n"
            task.write_text(text, encoding="utf-8")

        index = self.root / "specs/features/README.md"
        if index.exists():
            text = index.read_text(encoding="utf-8")
            if marker not in text:
                lines = [
                    "",
                    marker,
                    "",
                    "- 专属分支：`codex/code-fact-chronological-replay-acc-int-cut-20260904`",
                    "- 来源：`codex/f-acc-001-sds`、`prereq-parallel-check-kKiAdn`、`codex/f-cut-001-matrices`。",
                    "- 顺序：提交时间优先、父提交先于子提交；每个来源提交均有一条重放提交记录。",
                    "- 范围：全部PMS/Yudao模块文本代码，不进行模块级排除；文件或hunk冲突只阻断对应片段。",
                    "- 状态：代码事实进入主干候选不自动提升Implementation Done；开放Gate继续保持IN_PROGRESS。",
                    "",
                    "| Feature | 来源提交 | 已接收/等价路径 | 适配记录 |",
                    "|---|---:|---:|---:|",
                ]
                for feature in sorted(self.feature_commits):
                    lines.append(
                        f"| {feature} | {len(self.feature_commits[feature])} | "
                        f"{len(self.feature_paths[feature])} | {self.feature_conflicts.get(feature, 0)} |"
                    )
                index.write_text(text.rstrip() + "\n" + "\n".join(lines) + "\n", encoding="utf-8")

    def write_reports(self, ordered: list[CommitMeta], removed_modules: list[str]) -> None:
        self.report_dir.mkdir(parents=True, exist_ok=True)
        decisions_csv = self.report_dir / "code-fact-chronological-replay-2026-09-04.csv"
        commits_csv = self.report_dir / "code-fact-chronological-commit-order-2026-09-04.csv"
        summary_md = self.report_dir / "code-fact-chronological-replay-2026-09-04.md"
        rejects_patch = self.report_dir / "code-fact-chronological-rejected-hunks-2026-09-04.patch"
        machine_json = self.report_dir / "code-fact-chronological-replay-2026-09-04.json"

        with decisions_csv.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(Decision.__dataclass_fields__.keys()))
            writer.writeheader()
            writer.writerows(d.__dict__ for d in self.decisions)

        with commits_csv.open("w", encoding="utf-8", newline="") as handle:
            fields = list(self.commit_rows[0].keys()) if self.commit_rows else ["sequence"]
            writer = csv.DictWriter(handle, fieldnames=fields)
            writer.writeheader()
            writer.writerows(self.commit_rows)

        rejects_patch.write_text("".join(self.rejects) if self.rejects else "# no rejected hunks\n", encoding="utf-8")

        decision_counts = Counter(d.decision for d in self.decisions)
        branch_commit_counts = Counter()
        for row in self.commit_rows:
            for branch in str(row["sourceBranches"]).split("|"):
                if branch:
                    branch_commit_counts[branch] += 1
        feature_counts: dict[str, Counter[str]] = defaultdict(Counter)
        for decision in self.decisions:
            feature_counts[decision.feature][decision.decision] += 1

        lines = [
            "# 三分支代码事实按时间逐提交重放报告",
            "",
            f"- 基线：`{self.master_ref}` / `{self.git_text('rev-parse', self.master_ref)}`",
            f"- 重放Head：`{self.git_text('rev-parse', 'HEAD')}`",
            f"- 截止时间：`{self.cutoff}`",
            "- 来源：" + "、".join(f"`{name}@{self.git_text('rev-parse', ref)}`" for name, ref in self.sources.items()),
            "- 排序：提交时间优先，同时强制父提交先于子提交。",
            "- 接收：每个来源提交均生成一条重放提交；没有代码净变化时生成空回执提交。",
            "- 模块：不排除任何PMS/Yudao模块；仅不复制旧分支治理文档、运行证据和触发型workflow。",
            "- 冲突：单文件或单hunk冲突不阻断同提交、同模块或同分支的其他代码。",
            "",
            "## 提交统计",
            "",
            f"- 唯一来源提交：`{len(ordered)}`",
            f"- 重放提交：`{len(self.commit_rows)}`",
            f"- 逐路径裁决：`{len(self.decisions)}`",
            f"- 拒绝hunk/迁移候选记录：`{len(self.rejects)}`",
            "",
            "| 来源分支 | 覆盖的唯一来源提交 |",
            "|---|---:|",
        ]
        for branch, count in sorted(branch_commit_counts.items()):
            lines.append(f"| `{branch}` | {count} |")
        lines += ["", "## 路径裁决统计", "", "| 决策 | 数量 |", "|---|---:|"]
        for decision, count in sorted(decision_counts.items()):
            lines.append(f"| `{decision}` | {count} |")
        lines += ["", "## Feature代码事实", "", "| Feature | 来源提交 | 已接收/主干等价路径 | 冲突或适配 |", "|---|---:|---:|---:|"]
        for feature in sorted(self.feature_commits):
            lines.append(
                f"| {feature} | {len(self.feature_commits[feature])} | "
                f"{len(self.feature_paths[feature])} | {self.feature_conflicts.get(feature, 0)} |"
            )
        lines += [
            "",
            "## 构建配置规范化",
            "",
            f"- 根POM重复模块登记移除数：`{len(removed_modules)}`",
        ]
        lines.extend(f"- `{module}`" for module in removed_modules)
        lines += [
            "",
            "## 仍需适配的代码片段",
            "",
            "逐路径记录见CSV；源hunk和不宜直接激活的迁移候选保存在：",
            "",
            "`docs/traceability/code-fact-chronological-rejected-hunks-2026-09-04.patch`",
            "",
            "这些记录不构成模块、提交或分支级拒绝；其他代码已经继续重放。",
        ]
        summary_md.write_text("\n".join(lines) + "\n", encoding="utf-8")

        machine_json.write_text(
            json.dumps(
                {
                    "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                    "master": self.git_text("rev-parse", self.master_ref),
                    "head": self.git_text("rev-parse", "HEAD"),
                    "cutoff": self.cutoff,
                    "sources": {name: self.git_text("rev-parse", ref) for name, ref in self.sources.items()},
                    "commitCount": len(self.commit_rows),
                    "decisionCounts": dict(decision_counts),
                    "featureCommits": {key: sorted(value) for key, value in self.feature_commits.items()},
                    "featurePaths": {key: sorted(value) for key, value in self.feature_paths.items()},
                    "featureConflicts": dict(self.feature_conflicts),
                    "removedDuplicateModules": removed_modules,
                },
                ensure_ascii=False,
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )

    def execute(self) -> None:
        ordered = self.ordered_commits()
        if not ordered:
            raise GitError("no source commits selected")
        for sequence, meta in enumerate(ordered, 1):
            print(f"[{sequence}/{len(ordered)}] {meta.iso_date} {meta.sha[:12]} {meta.subject}", flush=True)
            self.replay_commit(sequence, meta)

        removed_modules = self.normalize_root_pom_modules()
        self.update_feature_tasks()
        self.write_reports(ordered, removed_modules)
        self.run_git("add", "-A", "pom.xml", "tasks/features", "specs/features/README.md", str(self.report_dir))
        self.run_git(
            "commit", "--allow-empty", "--no-verify",
            "-m", "docs(traceability): synchronize chronological code-fact replay",
            "-m", "Update Feature in-progress receipts and write complete commit/path traceability without promoting open gates to Done.",
        )


def parse_sources(values: Iterable[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for value in values:
        if "=" not in value:
            raise argparse.ArgumentTypeError(f"source must be NAME=REF: {value}")
        name, ref = value.split("=", 1)
        if not name or not ref:
            raise argparse.ArgumentTypeError(f"invalid source: {value}")
        result[name] = ref
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--master", default="origin/master")
    parser.add_argument("--source", action="append", required=True)
    parser.add_argument("--cutoff", default="2026-08-21T00:00:00Z")
    parser.add_argument("--report-dir", default="docs/traceability")
    args = parser.parse_args()

    root = Path(args.repo)
    sources = parse_sources(args.source)
    replayer = Replayer(root, args.master, sources, args.cutoff, Path(args.report_dir))
    replayer.execute()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
