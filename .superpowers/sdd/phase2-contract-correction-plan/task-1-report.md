# Task 1 implementation report

## Scope

- Corrected the PRD Appendix A.2 count to 55 V1, 48 V2, 103 formal requirements and synchronized the source/snapshot SHA-256.
- Removed EQP-06, RPT-01, RPT-04, the WO project-conversion consumer, and DingTalk clock-in facts from active Phase 2 contracts.
- Aligned the migration summary to 84 objects, 95 source bindings, and 1 excluded source.
- Reopened the Phase 2 gate as `IN_REVIEW / NOT_READY_FOR_PHASE_3`; P3-E09 remains `MODEL_BASELINE_READY`, while AI-MIG-000 remains downstream.
- Added Appendix A.2, 08a, active Requirement whitelist, V3 scope, WO-consumer, and DingTalk clock-in validation.
- Did not decide or modify the historical work-order/time-record API and file boundary.

## Red-green evidence

- Initial focused run: 12 tests, 6 expected failures for missing checks.
- Final focused run: 13 tests, all PASS.
- Full scripts test suite: 198 tests, all PASS.

## Validation

- PRD baseline validator: 49/49 PASS.
- Phase 2 validator: PASS, 103 requirement trace links.
- PRD semantics: PASS, 0 issues.
- Domain generation check: PASS, formal=103, V3=30, OUT_OF_SCOPE=9.
- Phase 2 contract map check: PASS.
- `git diff --check`: PASS.

## Boundaries

- No Excel/XLSX or protected cutover analysis document was read.
- No DDL, API implementation, authorization rule, business state machine, or migration execution was changed.
- The broken `python` launcher was not retried after `pyvenv.cfg` resolution failed; all Python validation used `py -3`.
- A later PowerShell search failed because a backtick in a double-quoted command escaped the terminator; the search was rerun with direct `rg -e` patterns and no backticks.

## Fix round 1/5

- Reworded the 2026-08-13 gate paragraph as historical approval evidence explicitly superseded by the current `IN_REVIEW / NOT_READY_FOR_PHASE_3` status.
- Changed the Phase 2 validator to parse the 103 formal V1/V2 IDs independently from `docs/baseline/prd-v1.7.md`, then require exact equality across PRD, requirement matrix, and explicit contract map.
- Added a regression that replaces one formal ID in both matrix and contract map with a V3 ID while retaining 103 rows; validation now fails on the PRD mismatch.
- Limited WO/DingTalk forbidden checks and active Requirement extraction to positive contract contexts. Historical exclusions, out-of-current-scope statements, `A+B`, and `PENDING` evidence remain allowed.
- Focused Phase 2 tests: 10/10 PASS. Full scripts test suite: 201/201 PASS. Formal validators and generated-contract checks PASS.

## Fix round 2/5

- Replaced free-text exclusion markers with structural recognition: exact first-column `历史排除`/`A+B摘要`, or an explicit disposition/status column carrying a non-active value.
- `适用 Requirement` declarations are always checked; activity disclaimers cannot hide non-formal IDs.
- Added three bypass regressions for DingTalk clock-in, WO consumers, and non-formal Requirement declarations; retained structured historical and pending positive cases.
- Focused Phase 2 tests: 13/13 PASS. Full scripts test suite: 204/204 PASS. Formal validators and generated-contract checks PASS.
