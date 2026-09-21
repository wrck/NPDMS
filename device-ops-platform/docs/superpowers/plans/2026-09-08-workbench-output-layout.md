# Workbench Output Layout Implementation Plan

> **For agentic workers:** Execute inline with test-driven-development. The user has approved the design and requested implementation in this session; no commits or browser work.

**Goal:** 收拢解析配置与结果职责，并修正三列工作台的滚动、紧凑布局与管理样式隔离。

**Architecture:** ProjectCollectionView 仅向 ParserSidebar 传递脚本和解析选择；CollectionTaskPanel 自主管理事实和语义结果，保留 start/stopPolling 接口。桌面以工作台专用类限定高度，连接卡 header 不参与滚动，body 为唯一连接滚动容器；1200px 以下恢复文档流。解析配置自然高度，脚本元数据按可用列宽换行。

**Tech Stack:** Vue 3, Element Plus, scoped CSS, Vitest, Vue Test Utils, vue-tsc.

---

## Ownership

Root: `M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/`

Modify only:
- `device-ops-web/src/views/ProjectCollectionView.vue`: remove result forwarding; scope shell layout.
- `device-ops-web/src/components/ParserSidebar.vue`: configuration only, preserve strategy/version/compatibility/error retry.
- `device-ops-web/src/components/ScriptArtifactEditor.vue`: wrapping metadata, safe artifact details, token-based command buttons.
- `device-ops-web/src/styles/main.css`: desktop connection scroll, compact configuration, existing 1200px breakpoint.
- `device-ops-web/src/styles/management.css`: exclude task-panel pre from management code styling.

Create `device-ops-web/src/components/workbench-output-layout.spec.ts` only for independent tests. Do not edit phase-one tests, panels, records, APIs, Java, data, services or device state. Browser verification belongs to the main agent. Do not change global status-rail colors.

## Task 1: Independent failing regression tests

- [ ] Mount the real ParserSidebar with Element Plus and mocked parser-options API; assert there is no SemanticResultPanel, while strategy/compatibility/retry remain.
- [ ] Mount ProjectCollectionView with child stubs; expose sentinel getters for result state on the task stub, assert no result attrs reach ParserSidebar and no getter is accessed. Preserve submission via exposed start.
- [ ] Add CSS contract checks for a fixed connection header, body-only scrolling, natural-height parser card, adaptive script metadata, no clipped artifact ancestors, and management/task pre isolation. CSS contracts are not substitutes for browser geometry verification.
- [ ] Run `pnpm --dir M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/device-ops-web test src/components/workbench-output-layout.spec.ts`; confirm failures are missing behavior, not setup errors.

## Task 2: Responsibilities

- [ ] Delete ProjectCollectionView computed latestParsedFacts/latestSemanticResults/latestCollectionDetails, unused computed/CollectionSemanticResult imports, and all three ParserSidebar result bindings.
- [ ] Delete ParserSidebar result props and SemanticResultPanel import/rendering. Keep collapsed prop, models, configuration loading, errors and retry unchanged.
- [ ] Run the independent tests after the change.

## Task 3: Layout and style isolation

- [ ] Add `project-collection` class to AppShell. In ProjectCollectionView scoped desktop CSS use `.project-collection :deep(.app-shell__content)` as flex column with `overflow: visible`, `.workflow-grid` as `flex: 1 1 0; min-height: 0`, and min-height-zero columns. Ancestor workspace bounds the viewport; no redundant content scrollbar.
- [ ] Replace connection card outer `overflow: auto` with `overflow: visible`. At desktop only set the card to height 100%, header `flex: 0 0 auto`, body `flex: 1 1 auto; min-height: 0; overflow-y: auto`. Reset body overflow and height below 1200px through desktop-only placement.
- [ ] Remove parser `height: 100%`; use natural-height configuration with maximum desktop height and only body scrolling when necessary. Remove stale parser-facts CSS.
- [ ] Keep command card nonshrinking and allow visible overflow. Use metadata `repeat(auto-fit, minmax(min(100%, 13rem), 1fr))`; constrain artifact popover to available editor width. Use Element Plus button controls and theme tokens for command shortcuts. At mobile use static artifact details.
- [ ] Change `.management-page pre` to `.management-page pre:not(.task-panel pre)` so management code remains styled without leaking into task output. Do not touch global status-rail.
- [ ] Run independent layout tests and existing parser/phase-one regressions without editing them.

## Task 4: Validation and handoff

- [ ] Run `pnpm --dir M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/device-ops-web test src/components/workbench-output-layout.spec.ts src/components/parser-sidebar.spec.ts src/components/workbench-phase-one.spec.ts`.
- [ ] Run `pnpm --dir M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform/device-ops-web ts:check`.
- [ ] Inspect diff limited to owned files. Report directed test counts and type-check outcome, with any concurrent-file failures distinguished.
- [ ] Handoff browser checks at desktop 1200/1440+, medium and mobile widths: connection bottom fields/buttons reachable, header fixed, no duplicate card scrollbars, metadata wraps, popovers visible, facts exclusively in task panel.
