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
