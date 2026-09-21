# Platform Management UI Implementation Plan

> For agentic workers: execute inline using the available executing-plans skill and test-driven-development. No commits or pushes are authorized.

**Goal:** Replace overview/scripts/tasks/records/settings placeholders and add a parser console using real authorized APIs.

**Architecture:** Vue 3 / Element Plus pages share the existing AppShell and Axios authentication client. Management modules isolate API contracts, asynchronous request state, and one-shot script handoff; collection and semantic result components remain unchanged. Parser and schedule contracts come directly from Java; management response types are implemented only after coordinator confirmation.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Axios, Vitest, Vue Test Utils, Playwright configuration (no browser execution).

## Ownership and constraints

Root: `M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/`.
Frontend paths below are relative to `device-ops-web/`. Do not change Java, data/, ProjectCollectionView, CollectionTaskPanel, TargetSelector, ParserSidebar, or session-export. Preserve other agents' edits. Use existing industrial Element Plus visual language: restrained cards, compact tables, semantic status tags, explicit action hierarchy; no new design system.

## Task 1 — Contract and asynchronous state foundation

Files: create `src/types/management.ts`, `src/api/parser-management.ts`, `src/management/use-request.ts`, and their focused specs.

- [ ] Read ParserReleaseController, ParseTaskController, ParserRuntimeStatusController, ScheduleController and runtime records.
- [ ] Write API tests that assert encoded identifiers, GET cursor parameters, expectedCurrentReleaseId in PUT body and DELETE query, and absence of unsupported collection cancellation.
- [ ] Run `pnpm --dir device-ops-web test src/management` and observe missing-implementation failures.
- [ ] Implement wrappers using existing deviceOpsApi and typed Java record fields; never add a second authentication client.
- [ ] Write request-state tests for initial loading, failure, 403, retry and out-of-order responses. Implement generation fencing plus AbortController invalidation on unmount. Do not render stale authorized data after errors.
- [ ] Repeat focused tests until green.

## Task 2 — Parser console

Files: create `src/views/management/ParserManagementView.vue`, `src/components/management/RequestState.vue`, parser console specs.

- [ ] Test loading, no log types, denied access, retry and selection races before implementation.
- [ ] Implement log-type listing and detail, release listing and detail; selected type changes clear selected release, validation and semantic result.
- [ ] Test draft JSON parsing, File.size gate before file.text(), malformed JSON, manifest logType mismatch, verificationCases requirement.
- [ ] Implement JSON textarea and file input for exact ReleaseRequest shape; save draft without publication or activation side effects.
- [ ] Test sample validation then publish; show service validation diagnostics. Publish requires explicit action and confirmation.
- [ ] Test activation/clear confirmations and captured expectedCurrentReleaseId from current ACTIVE list; do not refresh the expectation silently after confirmation.
- [ ] Implement activate and revoke with concurrency contract intact. Conflict/error refresh is explicit.
- [ ] Test runtime loading independently from registry; show worker counts, heartbeat, capabilities and waiting reasons.
- [ ] Test offline input UTF-8 byte upper bound, required type/release, submission state polling and SemanticResultPanel rendering. Implement explicit input submission and result refresh; stop polling on unmount.

## Task 3 — Task center and SCHEDULE_DUE

Files: create `src/views/management/TasksManagementView.vue`, `src/components/management/ParseTasksPanel.vue`, `src/components/management/SchedulesPanel.vue`, `src/api/schedule-management.ts`, focused specs.

- [ ] Test three collection/parser/schedule tabs and real API loading independently.
- [ ] Implement parser cursor pagination using limit/afterTaskId only; state filter clearly scoped to current page because server has no state filter.
- [ ] Show state, waitReason, errorCode, timestamps, result link. Confirm cancellation for QUEUED/RUNNING and termination only WAITING according to service contract.
- [ ] Test schedule list namespace/project inputs and unauthorized access; create/update/disable reuse existing routes.
- [ ] Implement schedule request JSON/form with first creation always enabled=false. Explain SCHEDULE_DUE only notifies callback, never runs unattended SSH; runtime dispatcher not enabled must be explicit when capability is available.
- [ ] No collection hard-cancel, rerun or automatic retry action.

## Task 4 — Authorized management data pages

Files: create `src/api/management.ts`, `src/views/management/OverviewManagementView.vue`, `RecordsManagementView.vue`, `ScriptsManagementView.vue`, `SettingsManagementView.vue`, collection list/detail components and focused tests.

Frozen endpoints: GET management/collections, overview, scripts, scripts/content, settings. Scope is device-ops:collections:read. Collections query namespace/project/device/status/from/to/page/size; page zero-based, size 20 and max100; response items/total/page/size. Overview same filters and total/byStatus. Exact remaining DTO fields must be read from coordinator contract or implemented Java, not invented.

- [ ] Write exact DTO API tests after contract arrives; implement typed wrappers with cancellation.
- [ ] Test collection filters resetting page, stable pagination, 403, empty, retry and response races.
- [ ] Implement collection summary table reused by tasks and records; link details using namespace/project/collection ID only.
- [ ] Detail route loads existing collection API and semantic results, reuses CollectionTaskPanel and SemanticResultPanel without edits, never invents missing historical script/timestamps.
- [ ] Overview renders only authorized total/byStatus from server, not inferred totals or mock metrics.
- [ ] Scripts metadata response never reads contents automatically. Content request proves collectionId ownership and is only offered when server supports safe LOCAL_MANAGED content.
- [ ] Test read then explicit load; add `src/management/script-handoff.ts` with offerScript and consumeScript (one-shot module memory, no storage, no URL content). Coordinate workbench consumer with owning agent.
- [ ] Settings renders only whitelisted masked identity/capability fields, no token dump or generic config JSON, no write actions.

## Task 5 — Navigation and cross-platform acceptance harness

Files: modify `src/router/index.ts`, `src/components/AppShell.vue`, `playwright.config.ts`; create management e2e and route specs.

- [ ] Test management routes are not placeholders and parser appears in navigation.
- [ ] Replace five route components, add parser route and record detail deep link; retain existing auth guard and collection routes.
- [ ] Test Playwright config contains no fixed Windows Chrome path or unconditional pnpm.cmd. Set executablePath only from PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH; choose pnpm.cmd on Windows and pnpm elsewhere, allow PLAYWRIGHT_WEB_SERVER_COMMAND and PLAYWRIGHT_BASE_URL overrides.
- [ ] Add mocked management e2e for navigation, forbidden/retry, script explicit handoff, parser confirmation and record deep link. Do not execute browser; main agent owns GUI acceptance.

## Execution checkpoint — 2026-09-08

Implemented all six management views, shared collection/parser/schedule panels, exact management/parser/schedule APIs, scoped UI hints, request generation fencing, one-shot revocable memory script handoff, real record query restoration, router navigation, configurable Playwright harness and three mocked e2e journeys.

Contract corrections during execution: release state never implies activation; newly coordinated GET active-release binding supplies compare-and-set expectations. ParseTaskState has no TERMINATED. Collection status is CONNECTING/EXECUTING/PARSING rather than RUNNING and null is unknown. Offline input is explicit adapter-compatible JSON with 8 MiB default / settings maxParserInputBytes; full draft budget is independently 32 MiB. User retries of unchanged offline input reuse an explicit idempotency key. Settings allNamespaces is explicit, never inferred from a literal star in identity fallback.

Verified: 11 focused test files / 22 tests passed including existing AppShell; final vue-tsc passed; targeted ESLint only owned management files passed; git diff --check for frontend passed. No browser execution, Java edits, data mutations, commits or pushes. Main agent owns full frontend build/quality gate and GUI/e2e execution. The checkboxes below retain the original intended granularity; not every originally proposed edge-case assertion has a dedicated component test, and browser acceptance remains outstanding.

## Verification and report

- [ ] Run targeted new API/component specs and existing AppShell regression using `pnpm --dir device-ops-web test ...`.
- [ ] Run `pnpm --dir device-ops-web ts:check`; distinguish pre-existing/other-agent errors, do not edit their files.
- [ ] Optionally run focused ESLint on owned files without --fix; never whole-repository lint --fix.
- [ ] Inspect git diff for owned paths only and ensure no Java/data/browser/commit operations occurred.
- [ ] Report implemented flows, test command outcomes, exact absolute paths, contract/coordination gaps and browser acceptance not performed.
