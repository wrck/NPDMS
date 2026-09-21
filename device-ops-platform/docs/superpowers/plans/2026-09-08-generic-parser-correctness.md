# Generic Parser Correctness Implementation Plan

> For agentic workers: execute the independent component tasks with test-driven development, then integrate and verify the frozen releases. User has authorized implementation; do not commit or request intermediate approval.

**Goal:** Correctly preserve and structure the three device command logs without changing historical release output.

**Architecture:** New 1.4.0 engine selects enhanced component behavior; legacy constructors and 1.3.0 plans retain frozen algorithms. Reuse GenericContent schema and mixed-segment traversal, keeping evidence and explicit semantic projections separate.

**Tech Stack:** Java 25, Maven, JUnit 5, Jackson, existing Vue/TypeScript renderers.

## File ownership

All Java paths below are under `device-ops-parser-semantic/src/main/java/com/dp/deviceops/parser/semantic/`; tests mirror them in `src/test/java/`.

- Security/version worker: `internal/SensitiveValueRedactor.java`, `SensitiveEvidenceSanitizer.java`, `DelimitedSectionParser.java`, `NestedEvidenceExpander.java`, `plan/ParserPlanCompiler.java`; related tests and runtime supported-engine registration.
- KV/table worker: `internal/KeyValueStructureParser.java`, `TableStructureParser.java`, optional focused enhanced implementations; related tests.
- Coordinator: `internal/StructuralSegmenter.java`, `GenericStructureParser.java`, `RecordStructureParser.java`, `ConfigStanzaStructureParser.java`, `ListStructureParser.java`, `StructureBudget.java`, `SemanticEngine.java`; record/budget integration tests.
- Fact worker: `internal/FactExtractor.java`, recursive structure traversal helper if needed; related tests.
- Release integration: `parser-releases/device-command-output-1.4.0/`, golden tests, documentation and server defaults only where newly-created deployments require the new version.

## Task 1 — Versioned safety and valid nested boundaries

- [x] Add tests for an enhanced sanitizer removing synthetic unquoted multiword secrets and PEM bodies while preserving line count/indentation; verify legacy sanitizer remains unchanged.
- [x] Add a nested section regression with `show session statistic`, pure-star decoration and `Total: 4`, asserting the total remains in the session section rather than an invalid star section.
- [x] Implement `boolean enhanced` constructor overloads; no-argument/default constructors delegate to false. Filter non-alphanumeric nested titles before recording boundaries only on the enhanced path.
- [x] Register engine 1.4.0 and compatible runtime capability without editing existing release JSON.
- [x] Run `mvn -pl device-ops-parser-semantic -am test -Dtest=SensitiveEvidenceSanitizerTest,NestedEvidenceExpanderTest,ParserPlanCompilerTest -Dsurefire.failIfNoSpecifiedTests=false`, first red then green.

## Task 2 — KV and table formats

- [x] Add enhanced-mode tests for `State:up`, `Input: 10  Output: 20`, duplicate keys, parent value/children, continuation, and time/MAC/IPv6/URL/event negative examples.
- [x] Add table regressions for the real five-column mixed dash decoration, one data row, indentation, Tab, plus borders, a table followed immediately by `Total: 1`, and route summary statistics preceding the header.
- [x] Implement enhanced overloads preserving default legacy behavior. Table recognition must return the first unconsumed line on structural change and use consistent header/data boundaries rather than every dash run. KV must expose a common enhanced line-recognition helper for segmentation/records.
- [x] Run focused tests red then green; retain original tests unchanged and passing.

## Task 3 — Complete mixed sections, records and budgets

- [x] Add FORCE/TEXT multi-paragraph tests asserting every nonblank input line is preserved in output section rawLines or explicit limited omissions.
- [x] Add two `Interface` records beginning with prose then MTU, and assert record child sections and fields remain reachable. Add a golden assertion for all 23 real record bodies.
- [x] Add legal configuration preamble regression and whitespace-only forced list item regression.
- [x] Add independent table/node budget tests; record children count against the node budget; recursive depth is bounded and produces LIMITED/fallback, not stack overflow.
- [x] Implement enhanced segmentation loops, recursively parse record contents with bounded depth, preserve default constructors, use a recursive status check and source-line omission accounting.
- [x] Run focused mixed/record/budget tests red then green, then the full semantic module.

## Task 4 — Semantic structure reuse

- [x] Add a KEY_VALUE(State) regression for repeated Name/State records, expecting both states with correct evidence.
- [x] Add a TABLE regression with headers `Name / Name / column1`, expecting all three values and distinct final keys.
- [x] Add bounded candidate construction tests using synthetic object-regex content and a small maxFacts limit.
- [x] Implement enhanced-only recursive structure traversal and unique field names; enforce candidate limits before map/list allocation growth.
- [x] Run focused fact tests red then green; no 1.3 golden changes permitted.

## Task 5 — New release and real-log acceptance

- [x] Copy the immutable release layout into new `parser-releases/device-command-output-1.4.0/`; set engine/release/rule versions to 1.4.0. Keep schema 1.1.0 and the same source evidence.
- [x] Add semantic assertions before generating new golden results: 50 nested units, 12 empty; session statistics has child entries; 23 interface records are not empty shells; temperature table has 5 columns with intact values; route summary statistics are not headers; short CPU/ARP/MAC tables preserve values.
- [x] Build and run CLI on the two new release verification inputs. Inspect structured JSON fields and source coverage programmatically; do not blindly accept generated golden output.
- [x] Write reviewed new golden files, run each case three times and compare bytes. Run all existing release goldens without modifications.
- [x] Run `mvn test`, `mvn -DskipTests package`, and frontend package-defined type-check/build commands; report exact failures or skipped environment integrations.
- [x] Update module README, new release README and `docs/reviews/2026-09-08-generic-log-parsing-delivery.md` with behavior, acceptance and compatibility boundaries. The root README has unrelated concurrent edits and is left untouched by this task. Verify `git diff --check` and final change list; no commit or push.

## Coordination constraints

Use independent agents only for disjoint file ownership. Shared constructor contract is `ExistingClass(...existingArgs, boolean enhanced)` with old constructors preserving false. Main coordinator owns SemanticEngine and integration. Test failures from another concurrently unfinished task must be distinguished from the worker's own failures. All runtime output stays in target; never modify user data or old versioned golden artifacts.
