# Collection Evidence Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans to implement task-by-task. User has already approved execution. No commits.

**Goal:** Authorized immutable collection input and safe submission evidence, including honest legacy reconstruction.

**Architecture:** Independent CollectionEvidenceQueryPort, JDBC projection with exact binary namespace/project predicates, and GET-only HTTP adapter. Capture allowlisted actual submission before connection/semantic resolution; carry snapshot through submit command and immutable task into the existing atomic insert. Nullable V20 column preserves V19 history.

**Tech Stack:** Java, Spring MVC/Security/JDBC, Jackson, Flyway, JUnit, H2 and disposable Docker MySQL.

## Constraints

Only Java modules, SQL migrations, Java tests and this requested plan. Do not modify frontend, V19, data/, process on 48181 or devices. Do not clean, commit, start persistent services or overwrite unrelated working changes. Targeted Maven only.

Implementation limits: the two collection POST routes accept at most 4 MiB raw request bytes (413 above this bound); parser ingestion limits are unchanged. Capture uses streaming allowlists, not a whole request tree. Duplicate JSON names and malformed containers return sanitized 400. The owned replay byte array is wiped in request cleanup; JVM/Jackson internal temporary allocations are not claimed to be fully erasable. Allowed username/name/key/etc. strings are authorized user text, not arbitrary secret detection. Unknown JSON property names are represented by fixed `.*` omitted paths, never copied. `telnetPrompts` is omitted as free text and listed in omittedFields. Authorization/Cookie headers are never captured.

Snapshot envelope is `{schemaVersion:1,request:{method,path,idempotencyKey},body:{...}}`; omitted paths are stored alongside the envelope in the column and projected as submission.omittedFields. A null original semanticParsing remains null, omitted remains absent, and enabled=false remains false. The actual request model has no mode field. Same-transaction idempotency checks resolve an uncommitted local winner before attempting a fresh transaction read for a cross-transaction winner.

## Frozen HTTP contract

GET `/api/v1/collections/{collectionId}/evidence?namespace=...` and `/api/v1/projects/{projectKey}/collections/{collectionId}/evidence?namespace=...`; collections:read scope, SQL authorization, Cache-Control:no-store.

```java
record Evidence(Metadata metadata, Input input, Submission submission, ExecutionFacts executionFacts) {}
record Metadata(String collectionId, String namespace, String projectKey, String externalRequestId, String activityType, Instant createdAt) {}
record Input(String source, String key, String version, String policy, String parserType, String sha256, String contentStatus, String content) {}
record Submission(String provenance, Map<String,Object> snapshot, List<String> omittedFields) {}
record ExecutionFacts(List<Target> targets, SemanticParsing semanticParsing) {}
record Target(long targetId, String deviceKey, String protocol, String host, int port, String username, String hostKeyFingerprint, String status) {}
record SemanticParsing(String logType, String releaseId, String inputFormat, String resultConsumerId) {}
```

`createdAt` nullable; `contentStatus` AVAILABLE/UNAVAILABLE, not guessed from current versions. submission provenance CAPTURED_SUBMISSION/RECONSTRUCTED_FACTS. Legacy snapshot null. Sensitive arbitrary extensions, parserConfig, callback omitted with field paths. Script content appears only in input. Historical LOCAL_MANAGED/ADHOC_INLINE/EXTERNAL_DELIVERED and EXECUTION_ONLY readable without widening management catalog.

## Task 1 — Database evidence projection

Files: core port `CollectionEvidenceQueryPort.java`; JDBC `JdbcCollectionEvidenceQueryAdapter.java`; tests `JdbcCollectionEvidenceQueryAdapterTest.java`; migration `db/{migration,mysql-migration}/V20__add_collection_submission_snapshot.sql`.

- [x] Write failing projection test using migrated H2 and historical task fixtures. Assert task script content, null legacy timestamp, all sources/policies, legacy provenance, unauthorized namespace/project empty, exact case IDs/grants, wildcard only with allNamespaces.
- [x] Run `mvn -pl device-ops-adapter-persistence-jdbc -am -Dtest=JdbcCollectionEvidenceQueryAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test`; observe missing port/projection feature.
- [x] Add DTO records above and nullable `submission_snapshot_json` large text column in V20 only.
- [x] Implement SELECT from collection itself, never script catalog. Use SQL predicate `(column=:value AND CAST(column AS VARBINARY)=CAST(:value AS VARBINARY))`, BINARY on MySQL. Namespace grants only bypassed by allNamespaces; project null or exact granted or explicit project wildcard. Read targets/parser facts only after authorized parent lookup.
- [x] Re-run targeted projection tests green.

## Task 2 — Capture and atomic persistence

Files: web `CollectionRequestBodyAdvice.java`, new safe capture helper; controllers; `CollectionSubmissionCoordinator.java`; core `SubmitCollectionService.java`, `CollectionTask.java`; JDBC `CollectionJdbcRepository.java`; tests in web and JDBC.

- [x] Write failing secret-sentinel/actual-body tests: omit password/privateKey/passphrase at every nesting level; omit callbackUrl/extensions/parserConfig; keep route, idempotency key, requested connection selectors/timeouts/semantic selection; no script content duplication; absent input fields remain absent.
- [x] Run focused web tests red.
- [x] Capture via request-body advice before typed conversion/resolution using explicit field whitelist, storing only safe JSON request attribute. Controllers pass snapshot explicitly into coordinator before resolution.
- [x] Extend command/task with nullable safe snapshot and compatible constructors. Existing task immutable insert binds snapshot in same INSERT, never replay UPDATE. Preserve loaded winner snapshot.
- [x] Write and run rollback/concurrent winner tests red; implement atomic persistence; green tests verify no leaked loser snapshot or orphan snapshot on rollback.

## Task 3 — HTTP and wiring

Files: new `CollectionEvidenceController.java`; existing server `DeviceOpsWiringConfiguration.java`; Java HTTP tests.

- [x] Write failing route/read-scope/no-store/auth tests.
- [x] Implement separate controller delegating `authorizer.visibleScope(jwt)` and namespace/project path filter to evidence query. Return 404 for invisible/missing database parent; do not use broad ordinary details as authorization source.
- [x] Wire JDBC adapter with current database dialect detection while preserving existing working changes.
- [x] Run focused web/server tests green, confirm ordinary details never serialize script body.

## Task 4 — Migration and regression

- [x] Test Flyway V19 to V20 on H2 with a legacy row and existing checksum untouched; assert nullable new column and history retained.
- [x] Extend existing disposable Docker MySQL integration tests for V19→V20, case-insensitive database collation exact grants, snapshot concurrency and rollback; use existing opt-in conventions, never application data.
- [x] Run focused Maven module suites (no clean), collect red/green commands, skipped tests/environment blockers.
- [x] Review diff against starting dirty inventory, report frozen contract, limitations and exact modified paths to coordinator. No commit.
