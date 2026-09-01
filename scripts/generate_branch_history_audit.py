#!/usr/bin/env python3
"""Generate an evidence-only chronological audit of every local branch.

The report deliberately does not infer Feature ownership or Done from Git.
Those adjudications come from Delivery Unit records and Feature authorities.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from validate_delivery_units import load_delivery_units


@dataclass(frozen=True)
class BranchFact:
    name: str
    head: str
    committed_at: str
    behind: int
    ahead: int
    relation: str
    worktree: str
    dirty_count: int | None
    delivery_units: tuple[str, ...]


@dataclass(frozen=True)
class CommitFact:
    committed_epoch: int
    committed_at: str
    commit: str
    author: str
    subject: str
    branches: tuple[str, ...]


@dataclass(frozen=True)
class StashFact:
    selector: str
    commit: str
    committed_at: str
    subject: str
    file_count: int


@dataclass(frozen=True)
class WorktreeFact:
    path: str
    head: str
    branch: str
    dirty_count: int


def git(repository: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repository,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return result.stdout.strip()


def worktree_facts(repository: Path) -> list[WorktreeFact]:
    result: list[WorktreeFact] = []
    current_path = ""
    current_head = ""
    current_branch = "DETACHED"

    def append_current() -> None:
        nonlocal current_path, current_head, current_branch
        if not current_path:
            return
        dirty = git(
            repository,
            "-C",
            current_path,
            "status",
            "--porcelain=v1",
            "--untracked-files=normal",
            check=False,
        )
        result.append(
            WorktreeFact(
                path=current_path.replace("\\", "/"),
                head=current_head,
                branch=current_branch,
                dirty_count=len([entry for entry in dirty.splitlines() if entry]),
            )
        )
        current_path = ""
        current_head = ""
        current_branch = "DETACHED"

    for line in git(repository, "worktree", "list", "--porcelain").splitlines() + [""]:
        if line.startswith("worktree "):
            append_current()
            current_path = line.removeprefix("worktree ")
        elif line.startswith("HEAD "):
            current_head = line.removeprefix("HEAD ")
        elif line.startswith("branch refs/heads/"):
            current_branch = line.removeprefix("branch refs/heads/")
        elif not line:
            append_current()
    return result


def collect(
    repository: Path, master_ref: str
) -> tuple[list[BranchFact], list[WorktreeFact], list[CommitFact], list[StashFact]]:
    raw_branches = git(
        repository,
        "for-each-ref",
        "--sort=refname",
        "--format=%(refname:short)|%(objectname)|%(committerdate:iso-strict)",
        "refs/heads",
    )
    refs = [tuple(line.split("|", 2)) for line in raw_branches.splitlines() if line]
    branch_names = [name for name, _, _ in refs]
    worktrees = worktree_facts(repository)
    worktrees_by_branch = {
        item.branch: (item.path, item.dirty_count)
        for item in worktrees
        if item.branch != "DETACHED"
    }
    units = load_delivery_units(repository / "tasks" / "delivery-units")
    units_by_branch: dict[str, list[str]] = {}
    for unit in units:
        units_by_branch.setdefault(unit.branch, []).append(f"{unit.unit_id}:{unit.status}")

    master_trees: dict[str, str] = {}
    for line in git(repository, "log", master_ref, "--format=%T|%H").splitlines():
        tree, commit = line.split("|", 1)
        master_trees.setdefault(tree, commit)

    commit_membership: dict[str, set[str]] = {}
    branches: list[BranchFact] = []
    for name, head, committed_at in refs:
        counts = git(repository, "rev-list", "--left-right", "--count", f"{master_ref}...{name}")
        behind, ahead = (int(value) for value in counts.split())
        branch_commits = git(repository, "rev-list", f"{master_ref}..{name}").splitlines()
        for commit in branch_commits:
            commit_membership.setdefault(commit, set()).add(name)

        ancestor = subprocess.run(
            ["git", "merge-base", "--is-ancestor", name, master_ref],
            cwd=repository,
            capture_output=True,
        ).returncode == 0
        if name == master_ref:
            relation = "MASTER"
        elif ancestor:
            relation = "IN_MASTER"
        else:
            tree = git(repository, "rev-parse", f"{head}^{{tree}}")
            if tree in master_trees:
                relation = f"TREE_EQUIVALENT:{master_trees[tree][:12]}"
            else:
                cherry = git(repository, "cherry", master_ref, name, check=False).splitlines()
                relation = "PATCH_EQUIVALENT" if cherry and all(row.startswith("-") for row in cherry) else "BRANCH_ONLY"
        worktree, dirty_count = worktrees_by_branch.get(name, ("NONE", None))
        branches.append(
            BranchFact(
                name=name,
                head=head,
                committed_at=committed_at,
                behind=behind,
                ahead=ahead,
                relation=relation,
                worktree=worktree,
                dirty_count=dirty_count,
                delivery_units=tuple(sorted(units_by_branch.get(name, []))),
            )
        )

    commits: list[CommitFact] = []
    for commit, containing_branches in commit_membership.items():
        raw = git(repository, "show", "-s", "--format=%ct|%cI|%H|%an|%s", commit)
        epoch, committed_at, full_commit, author, subject = raw.split("|", 4)
        commits.append(
            CommitFact(
                committed_epoch=int(epoch),
                committed_at=committed_at,
                commit=full_commit,
                author=author,
                subject=subject.replace("|", "\\|"),
                branches=tuple(sorted(containing_branches)),
            )
        )
    commits.sort(key=lambda item: (item.committed_epoch, item.commit))

    stashes: list[StashFact] = []
    raw_stashes = git(repository, "stash", "list", "--format=%gd|%H|%cI|%s", check=False)
    for line in raw_stashes.splitlines():
        selector, commit, committed_at, subject = line.split("|", 3)
        names = git(repository, "diff", "--name-only", f"{commit}^1", commit, check=False)
        stashes.append(
            StashFact(
                selector=selector,
                commit=commit,
                committed_at=committed_at,
                subject=subject.replace("|", "\\|"),
                file_count=len([name for name in names.splitlines() if name]),
            )
        )
    return branches, worktrees, commits, stashes


def render(
    *,
    snapshot_at: str,
    master_ref: str,
    master_input: str,
    branches: list[BranchFact],
    worktrees: list[WorktreeFact],
    commits: list[CommitFact],
    stashes: list[StashFact],
) -> str:
    lines = [
        "# 本地分支完整时间线审计",
        "",
        "> 文档状态：`GENERATED_SNAPSHOT / NON_AUTHORITATIVE_EVIDENCE`<br>",
        f"> 审计截点：`{snapshot_at}`<br>",
        f"> master输入：`{master_ref}@{master_input}`<br>",
        "> 生成器：`scripts/generate_branch_history_audit.py`<br>",
        "> 判读边界：本报告只记录Git、Worktree和stash事实；认领以Delivery Unit为准，Ready/Done以Feature权威文件为准。",
        "",
        "## 分支与Worktree快照",
        "",
        "| 分支 | HEAD | 时间 | behind/ahead | DAG关系 | Worktree | 脏项 | Delivery Unit |",
        "|---|---|---|---:|---|---|---:|---|",
    ]
    for branch in branches:
        dirty = "N/A" if branch.dirty_count is None else str(branch.dirty_count)
        units = "；".join(branch.delivery_units) if branch.delivery_units else "NONE"
        lines.append(
            f"| `{branch.name}` | `{branch.head}` | {branch.committed_at} | "
            f"{branch.behind}/{branch.ahead} | {branch.relation} | `{branch.worktree}` | {dirty} | {units} |"
        )

    lines.extend(
        [
            "",
            "## 全部Worktree快照",
            "",
            "本表独立列出所有Worktree，包括没有本地分支名的detached Worktree。",
            "",
            "| Worktree | 分支状态 | HEAD | 脏项 |",
            "|---|---|---|---:|",
        ]
    )
    for worktree in worktrees:
        lines.append(
            f"| `{worktree.path}` | `{worktree.branch}` | `{worktree.head}` | {worktree.dirty_count} |"
        )

    lines.extend(
        [
            "",
            "## master之外的全部提交时间线",
            "",
            "同一提交被多个分支继承时只列一次，并列出所有包含它的本地分支；分支包含不等于Feature认领。",
            "",
            "| 时间 | 提交 | 作者 | 摘要 | 包含分支 |",
            "|---|---|---|---|---|",
        ]
    )
    for commit in commits:
        branch_text = "、".join(f"`{branch}`" for branch in commit.branches)
        lines.append(
            f"| {commit.committed_at} | `{commit.commit}` | {commit.author} | {commit.subject} | {branch_text} |"
        )
    if not commits:
        lines.append("| - | - | - | master之外无提交 | - |")

    lines.extend(
        [
            "",
            "## stash快照",
            "",
            "| stash | 提交 | 时间 | 文件数 | 摘要 |",
            "|---|---|---|---:|---|",
        ]
    )
    for stash in stashes:
        lines.append(
            f"| `{stash.selector}` | `{stash.commit}` | {stash.committed_at} | {stash.file_count} | {stash.subject} |"
        )
    if not stashes:
        lines.append("| - | - | - | 0 | 无 |")
    lines.extend(
        [
            "",
            "## 使用规则",
            "",
            "- `BRANCH_ONLY`只说明提交尚未进入master，不说明其有效、已认领或可合入。",
            "- `IN_MASTER / PATCH_EQUIVALENT / TREE_EQUIVALENT`分支不得再作为新功能实施基础。",
            "- Worktree脏项和stash不属于提交证据；必须先交接到对应Delivery Unit。",
            "- 截点后任何分支前进都必须重新生成增量快照，不能覆盖本报告的历史结论。",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path)
    parser.add_argument("--master-ref", default="master")
    parser.add_argument("--snapshot-at", required=True)
    parser.add_argument("--master-input")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    repository = (args.repository or Path(__file__).resolve().parents[1]).resolve()
    try:
        datetime.fromisoformat(args.snapshot_at)
        master_input = args.master_input or git(repository, "rev-parse", args.master_ref)
        branches, worktrees, commits, stashes = collect(repository, args.master_ref)
        content = render(
            snapshot_at=args.snapshot_at,
            master_ref=args.master_ref,
            master_input=master_input,
            branches=branches,
            worktrees=worktrees,
            commits=commits,
            stashes=stashes,
        )
        output = args.output if args.output.is_absolute() else repository / args.output
        if args.check:
            if not output.is_file() or output.read_text(encoding="utf-8") != content:
                print(f"FAIL stale branch audit: {output}")
                return 1
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(content, encoding="utf-8", newline="\n")
    except (OSError, UnicodeError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"FAIL {exc}")
        return 1
    print(f"SUMMARY PASS branches={len(branches)} branch_only_commits={len(commits)} stashes={len(stashes)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
