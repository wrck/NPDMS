# V15 isolated full-chain acceptance runner

Candidate production commit: `ec70009342a64a35eeed0df57080135d71f0c93f`.
These scripts are an environment-specific acceptance harness for the generic synchronization implementation. They do not add business-specific production behavior.

Fetch this directory from its exact Git commit into `/workspace/npdms-sync-reusev154m-verification-20261013` outside the candidate checkout. Keep the candidate checkout at the production commit above. Verify SHA-256 values in `runner-manifest.json`. Before execution, record current Docker inspect results for the existing MySQL and Redis containers in `runtime-epoch.json` as `mysql` and `redis` objects. No credentials belong in this directory.

Run `python3 preflight.py`, then only after PASS run `python3 launch.py` once. Observe the process receipts and run `python3 finalize.py` after the test terminates. Do not rerun over partial evidence or reset the databases automatically. The runner requires the existing protected source baseline and V7 baseline evidence referenced in the scripts.

Coverage: initial migration, post-link reconciliation replay, unchanged replay, and changed-source full scan. The changed phase is not CDC or true incremental acceptance. The existing 4 million row fixture is reused without regeneration. Binlog remains enabled.

Capacity approval for this isolated run: stop below 2 GiB, require historical observed growth plus that guard and 512 MiB additional reserve at launch. Source baseline is preserved.

Local runner contract tests and syntax checks are not database acceptance. Publishing this runner does not mean these four database phases have run or passed.
