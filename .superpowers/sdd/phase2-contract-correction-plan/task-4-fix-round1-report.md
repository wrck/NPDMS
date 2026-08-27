# Task 4 fix round 1 report

## Scope

- Tightened non-active row recognition so generic `PENDING` and `PENDING_*` values cannot hide active V1/V2 contracts.
- Recomputed and checked PRD scope totals: V1 55, V2 48, formal 103, V3 30, OUT_OF_SCOPE 9.
- Reclassified file retention/DR and cache capacity/TTL values as `DEFERRED_TO_PHASE_3` and prohibited unexplained `IN_REVIEW` in current Phase 2 BASELINE design chapters.
- Kept the Phase 2 gate at `IN_REVIEW / NOT_READY_FOR_PHASE_3`; did not modify `independent-review.md`.

## Verification

- Red phase: eight expected failures across the three Required groups.
- Targeted: `29/29` passed.
- Full suite: `230/230` passed.
- PRD baseline: `51/51` passed.
- Phase 2, PRD semantic/domain, domain migration, naming validators: passed.
- Requirement matrix and Phase 2 contract-map generators: no drift.
- `git diff --check`: passed.

## Remaining gate

Fresh-context re-review must confirm there are no Critical or Required findings before any GO or Phase 3 readiness is recorded.
