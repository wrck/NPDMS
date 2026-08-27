# Task 3 implementation report

## Scope and impact

- Requirement/evidence boundary: `AI-MIG-000`, `ACC-02`, PRD 3.4 and Appendix C.
- Domain impact: historical work-order/time data remains immutable migration source or restricted archive evidence only; satisfaction task/questionnaire remains the current core object.
- API, database DDL, migration execution, authorization, state machine and workflow impact: none.

## Implemented

- Removed the erroneous WorkOrder core-object entry from both PRD copies and restored `满意度任务与问卷` with source `ACC-02`.
- Clarified that historical work orders and time records are not current domain objects and have no target table, API or user query entry.
- Synchronized the identical PRD SHA-256 into all registered baseline and traceability records.
- Added PRD validation preventing WorkOrder from returning to Appendix C and requiring the satisfaction object.
- Derived WorkOrder/time forbidden model tokens from `forbiddenV1V2Tables` in the core migration schema contract.
- Added active-contract guards for Chinese/English WorkOrder contexts and forbidden current write models, while preserving structured historical exclusions and future independent-change prose.

## Verification

- Red phase: 9 expected failures across the two missing PRD checks, malicious active WorkOrder contracts and five previously uncovered forbidden tables.
- Targeted suite: 30/30 PASS.
- Full scripts test suite: 223/223 PASS.
- `validate_prd_baseline.py`: 51/51 PASS.
- `validate_sds_phase2.py`: PASS with 103 requirement trace links.
- PRD semantics, PRD domain generation, business naming, core migration contract, domain migration alignment and generator check: PASS.
- `git diff --check`: PASS.

## Boundaries and limitations

- No Requirement, Owner, table, API, migration object or permission was added.
- No Excel/XLSX or cutover analysis report was used.
- Phase 2 remains `IN_REVIEW / NOT_READY_FOR_PHASE_3` pending Task 4 independent review.

## Commit

- Fix round 1: `46421013b92644619870395a5dd7a180a0601533`
