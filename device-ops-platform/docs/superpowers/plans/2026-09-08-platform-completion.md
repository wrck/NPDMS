# Platform Completion Implementation Plan

> **For agentic workers:** Execute the approved design task-by-task with the available executing-plans workflow. Independent backend, workbench and management UI files have separate owners; do not overwrite concurrent work.

**Goal:** Replace management placeholders with authorized operational flows while correcting the existing collection workbench.

**Architecture:** Reuse Java core ports/JDBC/Spring controllers and Vue 3 components. Database-filtered summaries feed management pages; existing parser endpoints remain authoritative. No production operations or automatic Git commits.

**Tech Stack:** JDK 25, Spring Boot, Maven, H2/MySQL/Flyway, Vue 3, TypeScript, Element Plus, Vitest, Playwright, PowerShell.

---

## File ownership and executable subplans

- Backend: Java modules, additive migrations, Java tests; detailed red/green steps in `2026-09-08-platform-backend.md`.
- Workbench: `ProjectCollectionView.vue`, `CollectionTaskPanel.vue`, `TargetSelector.vue`, `ParserSidebar.vue`, `utils/session-export.ts` and dedicated tests; steps in `2026-09-08-platform-workbench.md`.
- Management UI: new management API/types/components/views/tests, router, AppShell and Playwright portability; steps in `2026-09-08-platform-management-ui.md`.
- Integration owner: cross-boundary contract review, build/browser validation, README/API and final acceptance evidence. Existing runtime data excluded.

## Tasks

- [x] Read existing functionality, historical plans and acceptance evidence; distinguish placeholders from existing backend capability.
- [x] Obtain design approval and save `../specs/2026-09-08-platform-completion-design.md`.
- [ ] Backend: reproduce authorization gaps; implement scoped collections pagination/statistics, script metadata/content proof and whitelisted settings; run focused tests.
- [ ] Workbench: reproduce execution mode, resume, export and async race cases; fix and run focused component tests.
- [ ] Management UI: implement parser existing contracts first, then frozen management API; replace five placeholders and add parser console with loading/empty/error/permission paths.
- [ ] Integrate safe memory-only script handoff; no automatic execution or credential persistence.
- [ ] Review contract agreement, authorization filters, immutable versions, async request ownership and dangerous-action confirmations.
- [ ] Run frontend quality gate from `device-ops-web` in PowerShell: `pnpm.cmd test`, `pnpm.cmd ts:check`, `pnpm.cmd lint`, `pnpm.cmd build`. Expected each exit 0; report existing warnings separately.
- [ ] Run `mvn.cmd clean verify` from platform root only after parallel focused builds finish. Expected exit 0; record actual tests/skips and Docker availability.
- [ ] Verify `jar tf device-ops-server/target/device-ops-server.jar` includes `BOOT-INF/classes/static/index.html` and the current frontend asset references.
- [ ] Browser acceptance: start isolated local test environment, inspect/click/filter/open history and parser management with controlled data, never connect real devices or use runtime data. Verify visual layout separately from component assertions. Document any unavailable real IdP/device/production callback verification.
- [ ] Run `git diff --check`, inspect untracked files and publish implementation/verification summary without committing or pushing.

## Final execution checkpoint

Implementation tasks completed; final backend clean verify passed 401 tests with zero skips, frontend passed 63 tests/type/lint/build and four mock browser journeys. JAR static index/assets match current dist. Follow-up regressions fixed nested alert semantics, management SPA deep-link fallback and read-only history controls. User-required Element Plus design unification is implemented through management.css and ManagementStatus.

The isolated real-JAR browser read checks succeeded for overview, history, script content and settings. IAB screenshots failed and ordinary clicks timed out/unexpectedly navigated, so full visual and release-lifecycle GUI acceptance is blocked by tooling and is explicitly not marked passed. See `../../platform-management-acceptance.md`. No commit, push or production operation performed.

## Acceptance requirements

Namespace/project restrictions apply before pagination and aggregation. Summary lists never include output/script/credentials. Content reads require proven authorized LOCAL_MANAGED collection references. Zero-based pages have bounded sizes and stable time/id ordering. Old missing timestamps remain unknown. Parser activation retains concurrency precondition; cancellations cannot be confused with collection stop-viewing. Schedule means due notification, not automated collection. Settings expose only a field whitelist. Every new screen has loading, empty, failure retry and permission feedback. No historical parser release or live activity is modified by development.
