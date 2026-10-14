# Initial-migration-only diagnostic

This opt-in runner retains the production candidate and historical V7 measurements.
It is not a throughput optimization or a full four-phase acceptance result.

- Existing four-million generated source is retained. Fresh target:
  `npdms_sync_full_chain_20261009_initialv15r20261014`.
- Preserve all historical receipts. Never rerun the old V15 output directory.
- Start the original Redis container if stopped, without changing its resources,
  ports, image or volumes. Record current MySQL/Redis identity in runtime-epoch.json.
- Copy these scripts to a new external artifact directory, record the fetched
  commit in candidate-head.txt, and verify runner-manifest.json before use.
- First run all 17 Python unit tests and Java InitialMigrationReviewTest.
  No database benchmark is started by either unit test.
- preflight.py verifies exact production/test hashes, retained source, original
  global configuration, no competing JVMs/transactions, and a 19,984,437,248-byte
  capacity budget. It creates one empty target only after these checks.
- launch.py runs one Maven initial-only invocation. The user explicitly approved
  a **1.5x** stop tolerance on 2026-10-14 after requiring equivalent work first.
  initial_comparison.py checks the exact retained V7 task definition, normalizing
  only the three known run-specific source-helper names. Source order, row counts,
  business actions, SQL, mappings and all other fields remain part of the comparison.
  The four completed source limits are their historical times times 1.5; the task
  limit is 6394.131082329 seconds (historical 4262.754054886 remains unchanged).
  Small fluctuations do not stop the run. Invalid comparison or significant
  regression signals only the identified isolated test group and skips verification.
  At native task end, identical actions/counts and acceptable timings authorize the
  Java review gate with CONTINUE_INITIAL_VERIFICATION. The wait is outside native
  task time. No later reconciliation, stable replay or changed-source phase runs.
  Before launch, save the original V7 FULL_CHAIN_DEFINITION as baseline-definition.json
  and original INITIAL_MIGRATION FULL_CHAIN_TASK as baseline-task.json in the external
  artifact directory. These are local evidence inputs, not repository uploads.
- Periodic observation uses /proc CPU/RSS and pressure/cgroup counters. No periodic
  heap attach and no jcmd calls are issued. JFR profile records at JVM start and
  writes its rolling 64 MiB recording on exit. This observer change is a diagnostic
  variable; an improvement alone cannot isolate it from changed host/DB state.
- SQL digests/status are sampled at task/source boundaries, asynchronously. They
  are non-atomic cumulative counters; streaming SELECT includes backpressure.
- Disk guard remains 2 GiB; stopping verifies process start ticks and group identity.
- finalize.py returns FAIL_PERFORMANCE before large correctness scans on regression.
  On success it verifies only initial migration and retained source; no stable replay,
  changed-source or CDC acceptance is claimed. Missing samples/JFR are disclosed.
- Do not drop databases, purge logs, alter resources, or automatically retry from
  these scripts. Those actions require separately scoped authorization.

The previous v15 runner and its failed results remain unchanged in the repository.
