# Phase 1 gate snapshot alignment plan

## Context

The specification repository at `M:\AICoding\CodexData\worktrees\09b5\项目交付平台`
is the business and design source of truth. NPDMS uses a locked, manifest-backed local snapshot.
The source repository's current Phase 1 gate is `APPROVED / READY_FOR_PHASE_2`, while the
NPDMS-local unmanaged copy still says `NOT_READY_FOR_PHASE_2`. This plan removes that current-state
contradiction without rewriting historical review evidence.

## Global Constraints

- Source commit is exactly `b7c9d2a8de04391637aef942bc200ff43aec2122`.
- Read source files through Git objects using the existing snapshot tooling.
- Do not modify PRD, SDS business semantics, historical review files, or the local
  `docs/engineering/gates/phase-1/q2-evidence-manifest.json`.
- Do not read Excel files or `需求/割接0807需求分析报告.md`.
- The managed snapshot must include exactly the current Phase 1 `README.md` and `gate-status.md`
  in addition to the existing 109 files.
- The authoritative current Phase 1 status in NPDMS must become `APPROVED / READY_FOR_PHASE_2`;
  historical files may retain their historical `NOT_READY` conclusions.
- Snapshot check mode remains non-writing. Apply uses a repository-level cooperative lock,
  conditionally publishes only from the observed managed state, and rolls back every file on
  any publish error. `atomic and conflict-safe` means batch rollback plus protection against
  changes made before or during the tool's conditional publish protocol; it does not claim a
  filesystem transaction against an unrelated process that ignores the lock and keeps writing
  through a stale open file handle after the file has been renamed.
- Run focused tests, all `scripts/tests`, all three repository validators, and `git diff --check`.
- Make one atomic commit and do not push. Before committing, read the git-commit skill.

## Task 1: Add the current Phase 1 gate to the locked snapshot

**Files:**
- `docs/specification-baseline/allowlist.json`
- `docs/specification-baseline/README.md`
- `docs/specification-baseline/manifest.json`
- `docs/engineering/gates/phase-1/README.md`
- `docs/engineering/gates/phase-1/gate-status.md`
- `scripts/tests/test_specification_baseline.py`
- `scripts/tests/test_repository_baseline_rules.py`
- other existing snapshot tooling only if required by exact-count assumptions

**Requirements:**
1. Add only `docs/engineering/gates/phase-1/README.md` and
   `docs/engineering/gates/phase-1/gate-status.md` to the managed allowlist.
2. Update count assertions and documentation from 109 to 111.
3. Add a regression test proving the managed current gate is `APPROVED` and
   `READY_FOR_PHASE_2`, and that its README does not claim current `NOT_READY_FOR_PHASE_2`.
4. Apply the snapshot from the exact source commit and regenerate the manifest.
5. Confirm the local implementation evidence manifest and historical review files remain unchanged.
6. Verify a second dry run reports all 111 files as KEEP with no ADD/REPLACE/CONFLICT.
7. Run the specified validations, self-review the diff, and commit atomically.
