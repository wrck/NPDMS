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
- First run the Python unit tests and Java InitialMigrationReviewTest.
  No database benchmark is started by either unit test.
- preflight.py verifies exact production/test hashes, retained source, original
  global configuration, no competing JVMs/transactions, and a 19,984,437,248-byte
  capacity budget. It creates one empty target only after these checks.
- launch.py runs one Maven initial-only invocation. There is no automatic elapsed-time
  or arbitrary multiplier cutoff. The operator first verifies identical task stages,
  source filters, order, row counts and actions, then reviews matching stage progress
  and SQL timings. Small fluctuations are normal. Confirmed significant regression
  is stopped with stop.py and a recorded reason. After the native initial task ends,
  the fixture waits for initial-review.txt before expensive correctness verification.
  Write CONTINUE_INITIAL_VERIFICATION only after the comparison; STOP_PERFORMANCE
  fails before verification. The review wait is after native task timing and must
  not be presented as migration time. It never proceeds to later phases.
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
