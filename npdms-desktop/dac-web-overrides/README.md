# DAC web: explicit unavailable catalogue repair

Status: the missing import is repaired and the web source builds. The actual command catalogue remains **BLOCKED_BY_SPEC**. This is not a reconstructed catalogue or a packaged/running DAC service.

## Evidence and bounded contract

The fixed baseline is `d4f6918d67c3f5b32559d7eba6deb6e9a847858a`.

- `device-ops-platform/device-ops-web/src/components/ScriptArtifactEditor.vue:5-6` imports a module absent from that Git tree: `@/data/common-commands`.
- Its existing handler at lines 34-37 and template at lines 133-146 require `CommonCommand` with string fields `id`, `label`, `category`, and `command`, plus an iterable `commonCommands` export. Selection replaces `ScriptDraft.content` and fills `ScriptDraft.key` only when the existing key is blank. It does not execute a device command.
- No authoritative catalogue, entry ordering, labels, categories, command text, vendor applicability, or identifier set was found in tracked sources/docs. Commands in API examples, parsing fixtures, and test drafts are not authority to invent this catalogue.
- `src/components/workbench-output-layout.spec.ts` requires the existing Element Plus shortcut-button structure; it does not define entries. `src/components/workbench-phase-one.spec.ts` checks explicit one-time script handoff, hashing, and no automatic execution.
- `device-ops-platform/docs/platform-management.md`, section “脚本版本”, requires explicit load, in-memory handoff, operator review, and no automatic device execution. The repair preserves that path.

## Patch

- `src/data/common-commands.ts`: exports the four-field interface, a frozen empty array, and a clear unavailable message. There are no fabricated device operations, disabled fake entries, or fallback network requests.
- `src/components/ScriptArtifactEditor.vue`: retains all original handlers/watchers/input paths and renders this message when the catalogue is empty: `常用命令目录缺失，暂不可用。请手动输入并核对命令。` The existing button loop remains available for a future authoritative catalogue.
- `src/components/common-commands-unavailable.spec.ts`: verifies empty presets, visible explanation, unchanged input/identity, exact SHA-256 of manual text, and collapsed-state preservation.

The full component override differs from the pinned source only by the notice import, the conditional empty-state paragraph/list, and its small scoped style. Manual entry, key/version/source/policy, hash calculation, and the explicit handoff workflow remain intact. Preset convenience is explicitly unavailable; full original preset feature parity is not claimed.

## Offline reproduction

From `npdms-desktop`, using the exact clean baseline and an already installed DAC-web dependency directory:

```sh
node --test test/dac-web-overrides.test.cjs
mkdir -p build
node scripts/prepare-dac-web.cjs \
  --source /path/to/exact-baseline-repository \
  --stage "$PWD/build/dac-web-stage" \
  --dependencies /path/to/existing/dac-web/node_modules
cd build/dac-web-stage
node node_modules/vitest/vitest.mjs run
node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
node node_modules/vite/bin/vite.js build
```

The stage parent must already exist, and the stage itself must not exist. Choose a new stage path for another run. No install, fetch, Java, Electron, GUI, service start, or device execution is performed by this workflow. Use the established dependencies and a shell without unrelated `VITE_*` overrides.

The preparation script verifies the exact Git revision and clean tracked baseline, reads immutable tracked Git blobs rather than copying the worktree, excludes environment files/private-key formats/package-manager configuration, pins source/overlay hashes, and refuses to overwrite an existing directory or stage inside the baseline/overrides. Its generated source manifest records file hashes and the unresolved catalogue status. It may link only an explicitly supplied existing dependency directory. No baseline source, private `.env`, or untracked file is copied or modified.

## Verified 2026-11-06

- Node staging/hash/path tests: **5/5 passed**.
- DAC web Vitest: **27 files / 131 tests passed**, comprising 127 existing tests and 4 new tests. The two previously import-blocked suites are included.
- `vue-tsc --noEmit`: **passed**.
- Vite static build: **passed**, 27 files, 1,700,004 bytes. Non-failing warnings: two upstream VueUse `PURE` annotation warnings and the main chunk exceeding 500 KB.
- Node 24.22.1; existing dependencies: Vite 7.3.6, Vitest 3.2.7, vue-tsc 3.3.8, TypeScript 5.9.2, Vue 3.5.40, Element Plus 2.14.3, jsdom 27.4.0.
- Original editor SHA-256: `8e65a9eee20ed8048b9ddc074d5a4a33e52761d65e449047e859413ff6a0161f`.
- Source-tree manifest aggregate SHA-256: `7e39ff8e13f10f5d8b2590ffb5841513742cc296dac368894e767b29c68ab3dd`.
- Static output aggregate SHA-256: `ff513b1a85afae0f6528e9297a13fca59098b1ac2fcdd2879fe1635174be41d0`. This is SHA-256 of compact JSON for the sorted `files` array (`path`, `bytes`, `sha256`) in `build/dac-web-static-manifest-20261106.json`.
- Local logs: `build/dac-web-tests-20261106.log`, `build/dac-web-typecheck-20261106.log`, and `build/dac-web-build-20261106.log`.
- The original baseline's tracked worktree remains clean.

Validation outputs reside under the ignored `build/` directory and are not committed as a runtime payload. The static build has not been embedded into a DAC JAR or installed into a running service. Java/Maven/Gradle, browser, GUI, real-device and integrated runtime acceptance were not run here. The earlier Java SSH concurrency failure and missing service JAR are not resolved by this web patch.

## Open question

An authoritative source/approved specification must supply the original preset entries and their complete metadata before a catalogue can be restored. Adding guessed command examples or converting parser fixtures into selectable device operations would exceed this repair. Record that future decision in the project's authorized specification workflow; this reconstruction does not modify the original baseline's decision log.
