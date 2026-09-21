# Shared Output and Evidence Implementation Plan

> **For agentic workers:** Execute approved design task-by-task using test-driven development; independent owners must not overwrite concurrent files. No automatic Git commits.

**Goal:** Share the collection output experience, recover authoritative request/input evidence and make all connection controls readable and reachable.

**Architecture:** Existing CollectionTaskPanel owns four tabs and semantic output. An independently authorized evidence port exposes historical frozen inputs and new versioned safe submission snapshots. Additive V20 supports H2/MySQL.

**Tech Stack:** Java25/Spring/JDBC/Flyway, Vue3/TypeScript/Element Plus, Vitest, Playwright, PowerShell.

---

## Ownership and steps

- [x] Confirm user approval and document design in `../specs/2026-09-08-shared-output-evidence-design.md`.
- [ ] Backend owner: `2026-09-08-collection-evidence-backend.md`; tests first for independent evidence API, safe captured submission/winner semantics, historical facts/input and V20.
- [ ] Shared panel owner: `2026-09-08-collection-output-evidence-ui.md`; tests first for four tabs, one semantic instance, default expansion, true input download, request provenance and no credential persistence.
- [ ] Layout owner: `2026-09-08-workbench-output-layout.md`; remove sidebar result responsibilities, fix single-scroll connection layout and scoped management pre styles without touching panel-owned files.
- [ ] Freeze evidence DTO before integrating frontend; distinguish unknown historical values from explicit null/default request choices.
- [ ] Independently review authorization, secret omission, transaction/rollback/idempotency behavior, multi-target facts and race isolation.
- [ ] Run frontend `pnpm.cmd test`, `pnpm.cmd ts:check`, `pnpm.cmd lint`, `pnpm.cmd build` and browser mock regressions. Record actual warnings, not zero-warning claims.
- [ ] Run focused Maven tests then complete `mvn.cmd verify`; before any clean, stop only confirmed test processes occupying targets. Do not clean away running48181 launcher working files.
- [ ] Verify current dist index and referenced assets exist identically in server JAR.
- [ ] Before real V20 deployment, verify48181 PID and exact worktree JAR, stop that instance, back up original data/device-ops.mv.db and original DPAPI into a new private outside-Git directory, compare hashes. Never generate a new credential key.
- [ ] Start48181 using original persistent database, DPAPI-restored child-only key, local mode/Telnet settings. Validate migration/health and unchanged historical result coordinates.
- [ ] Main-agent browser: real history four tabs, download input, request facts, output dark background/height and connection controls at desktop and narrow sizes. No connection tests, remote commands, release changes or reparsing.
- [ ] Record verified outcomes and tool limitations; inspect git diff; leave user-requested branch service running without commit/push.
