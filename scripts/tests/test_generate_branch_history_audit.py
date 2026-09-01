from __future__ import annotations

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from generate_branch_history_audit import BranchFact, CommitFact, StashFact, WorktreeFact, render


class BranchHistoryAuditTest(unittest.TestCase):
    def test_render_keeps_git_evidence_separate_from_authority(self) -> None:
        content = render(
            snapshot_at="2026-09-01T16:24:32+08:00",
            master_ref="master",
            master_input="a" * 40,
            branches=[
                BranchFact(
                    name="master",
                    head="a" * 40,
                    committed_at="2026-09-01T15:32:29+08:00",
                    behind=0,
                    ahead=0,
                    relation="MASTER",
                    worktree="M:/repo",
                    dirty_count=0,
                    delivery_units=(),
                )
            ],
            worktrees=[
                WorktreeFact(
                    path="M:/detached",
                    head="d" * 40,
                    branch="DETACHED",
                    dirty_count=2,
                )
            ],
            commits=[
                CommitFact(
                    committed_epoch=1,
                    committed_at="2026-09-01T16:00:00+08:00",
                    commit="b" * 40,
                    author="worker",
                    subject="implementation candidate",
                    branches=("codex/example",),
                )
            ],
            stashes=[
                StashFact(
                    selector="stash@{0}",
                    commit="c" * 40,
                    committed_at="2026-09-01T16:10:00+08:00",
                    subject="WIP",
                    file_count=1,
                )
            ],
        )

        self.assertIn("NON_AUTHORITATIVE_EVIDENCE", content)
        self.assertIn("分支包含不等于Feature认领", content)
        self.assertIn("`codex/example`", content)
        self.assertIn("包括没有本地分支名的detached Worktree", content)
        self.assertIn("`M:/detached` | `DETACHED`", content)
        self.assertIn("stash@{0}", content)


if __name__ == "__main__":
    unittest.main()
