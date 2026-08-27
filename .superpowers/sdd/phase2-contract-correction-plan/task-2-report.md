# Task 2 implementation report

## Scope and impact

- Requirement/evidence boundary: `AI-MIG-000`, `P3-E09`, PRD V1.7 migration evidence reproducibility.
- Domain impact: domain-entity migration evidence only.
- API, database DDL, migration execution, authorization, state machine and workflow impact: none.

## Implemented

- The generator resolves the frozen implementation commit from explicit `--implementation-commit` or the existing machine contract.
- Migration SQL is listed and read from Git objects at that commit; current HEAD and worktree files are not evidence inputs.
- The validator reads the same pinned Git blobs and no longer requires the implementation HEAD to equal the frozen commit.
- Missing commit and missing `sql/migrations` path fail explicitly.
- The machine contract records `implementationEvidenceMode=PINNED_GIT_COMMIT`.

## Verification

- Red phase: legacy behavior failed HEAD-advance, pinned-source, missing-commit and missing-path scenarios as expected.
- `generate_domain_entity_migration_contract.py --check`: PASS, 84 objects / 95 sources.
- `validate_domain_entity_migration_alignment.py`: PASS, 84 objects.
- Targeted unit suite: 28/28 PASS, including HEAD advance, worktree divergence, incompatible pinned SQL, missing commit and missing path.
- Python compile and `git diff --check`: PASS.

## Fix round 1

- Review finding: callers could persist symbolic, relative or abbreviated Git refs instead of an immutable canonical SHA.
- The generator now resolves the supplied ref once at build entry with `git rev-parse --verify <ref>^{commit}` and uses only the resulting 40-character lowercase SHA in blobs, `implementationCommit` and evidence URIs.
- The validator rejects `HEAD`, branch names, relative refs and abbreviated SHAs in a maintained contract.
- Added regression coverage for `HEAD` resolution, noncanonical contract rejection and a moved ref leaving an already generated contract bound to the original SHA.
- Targeted suite: 31/31 PASS. Full scripts test suite: 212/212 PASS.

## Boundaries and limitations

- No implementation repository writes and no migration execution occurred.
- The implementation repository must remain locally available with the frozen Git object; production migration/cutover evidence remains under downstream `AI-MIG-000`.
- Historical work-order/time-record visibility decision is outside this task.
