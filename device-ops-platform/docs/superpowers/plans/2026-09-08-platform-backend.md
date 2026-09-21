# Platform Backend Implementation Plan

> **For agentic workers:** Use executing-plans and test-driven-development to implement this approved backend scope task-by-task.

**Goal:** Add namespace-safe management projections, immutable script catalog access, and read-only platform settings without touching frontend or operational data.

**Architecture:** Keep authorization in the web authorizer, transport-independent query contracts in core, and SQL visibility predicates in the JDBC adapter. Collection summaries never load output, credentials, scripts or parser configuration. Script content requires an authorized collection reference matching a registered LOCAL_MANAGED immutable version including hash; never infer project ownership from namespace alone.

**Tech Stack:** Java 25, Spring MVC/Security/JDBC, JUnit 5, H2 in-memory fixtures, Flyway H2/MySQL migrations, Maven from Windows PowerShell.

## Constraints

- Work root: `M:/AICoding/CodexData/worktrees/48b2/NPDP/device-ops-platform`.
- Only Java modules, Java tests, migrations and this requested plan. No other documentation, frontend, data directory, actual database or device operations.
- No commit/push and no repository clean. Main coordinator runs final aggregate verify.
- Existing parser releases, tasks, runtime and execution endpoints remain unchanged.

## Frozen management API

All endpoints require authenticated subject and `device-ops:collections:read` scope. Missing scope or namespace/project grant is 403; missing/inaccessible script content is 404; malformed filters are 400. Existing namespace fallback and project collection claim semantics are shared, not independently reinvented.

- `GET /api/v1/management/collections`: optional `namespace,project,device,status,from,to`; zero-based `page=0,size=20`, maximum 100. ISO-8601 inclusive instants. Response `{items,total,page,size}`. Items `{collectionId,namespace,projectKey,externalRequestId,activityType,status,createdAt,targetCount,scriptSource,scriptKey,scriptVersion,scriptSha256}`. Sort `createdAt DESC, collectionId DESC`; unknown legacy timestamps remain null and sort last.
- `GET /api/v1/management/overview`: same filters, no pagination. `{total,byStatus}` where status keys are collection aggregate status names. Uses identical authorized SQL relation.
- `GET /api/v1/management/scripts`: optional `namespace,project`, `page=0,size=20,max100`. `{items,total,page,size}`. Items `{namespace,scriptKey,version,source,sha256,parserType,collectionId,contentReadable}`. Only versions with provable authorized matching collection association are included. `collectionId` is the authorized representative association to use for content. Multiple associations collapse to one immutable version. No content in list.
- `GET /api/v1/management/scripts/content?collectionId=...`: `{namespace,scriptKey,version,source,sha256,content}` only if collection is visible and matches a LOCAL_MANAGED registered version by namespace/key/version/source/hash. External, execution-only, orphaned, ambiguous/unreliable associations cannot expose content.
- `GET /api/v1/management/settings`: allowlist `{platformName,apiVersion,readOnly,authMode,localDebug,subject,namespaces,projects,allNamespaces,scopes,maxParserInputBytes,capabilities}`. Capabilities are effective booleans `{scheduleEnabled,callbackEnabled,masterDataEnabled,telnetEnabled,credentialStorageAvailable}`. No environment/config serialization and no operational secrets, internal addresses or filesystem paths. Explicit namespace array wildcard provenance is separate from literal fallback `*`.
- Approved follow-up `GET /api/v1/parser-log-types/{logType}/active-release` uses existing `parser:release:read`, returns `{logType,releaseId}` from active binding (null if none; unknown type 404).
- Corrupt/history collections without targets return `status:null`; overview uses `UNKNOWN`, never invents QUEUED. Missing historical creation timestamps remain null independently.

## Task 1: Namespace authorization regression

Files: web `security/ProjectClaimAuthorizer.java`, `schedule/ScheduleController.java`, `SavedConnectionController.java`, `CredentialController.java`; new web test `ManagementNamespaceBoundaryTest.java`.

- [ ] Add direct controller tests rejecting foreign namespace before store/service interaction for list/get/create/replace/delete and schedule list/get/upsert/disable. Preserve owner/project store parameters.
- [ ] Run `mvn -pl device-ops-adapter-web-spring -am -Dtest=ManagementNamespaceBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`; confirm missing namespace denial assertions fail.
- [ ] Inject the existing configured authorizer. Call `claims.requireNamespace(jwt, namespace)` before storage access, alongside existing project checks. Secret-bearing writes check inside their cleanup try/finally.
- [ ] Rerun red tests and existing CollectionBoundaryTest; all pass.

## Task 2: SQL-authorized collection and overview projections

Files: core `port/ManagementQueryPort.java`; JDBC `JdbcManagementQueryAdapter.java`; web `management/ManagementController.java`; server `DeviceOpsWiringConfiguration.java`; authorizer visibility helper; JDBC/web/HTTP tests.

- [ ] Add authorizer tests: wildcard, multiple namespace grants, namespace fallback, absent project grants, numeric and string project values in collections consistent with existing require(). Invalid explicit filters must fail closed.
- [ ] Add H2 query tests with two namespaces/projects, project-less rows, tied creation timestamps, multiple targets/statuses, device filters, from/to, page boundaries, secret sentinel columns. Assert total and overview obey same SQL visibility as list.
- [ ] Observe focused test failures before implementation.
- [ ] Define typed immutable Scope, Filter, Page, Summary and Overview records. Apply predicates before count/order/limit, using bound SQL parameters; no Java post-filtering. Reproduce existing target aggregate status precedence in SQL.
- [ ] Add creation timestamp migration V19 in both dialect locations (do not change V17/V18). Legacy timestamps remain null rather than fabricate historical times. New inserts use database timestamp default; index namespace/project/time/id.
- [ ] Add validated controller read routes and bean wiring. Reject page<0,size outside 1..100, inverted time range and invalid status; explicit namespace/project filters must be authorized.
- [ ] Run focused H2/JUnit tests until green.

## Task 3: Immutable script catalog and content

Files: same management port, JDBC adapter/controller and tests; existing script repository only if demonstrated immutable source bug needs repair.

- [ ] Add tests for two versions, local/external source, unassociated version, unauthorized project association, hash mismatch, and multiple authorized collection references; content must return empty for every unprovable association.
- [ ] Observe red; implement SQL EXISTS association scope and registered-version joins. Require source agreement and hash agreement; content route never returns collection snapshot as a shortcut. List is paginated and immutable metadata only.
- [ ] Rerun immutable script repository regression and new management query tests.

## Task 4: Settings and HTTP security acceptance

Files: management controller and `device-ops-server/src/test/java/com/dp/deviceops/server/ManagementHttpTest.java`.

- [ ] Start a test-only random-port server with explicit `jdbc:h2:mem:` URL and OAuth2 test decoder. No real device execution. Seed only synthetic rows using test JDBC.
- [ ] Assert anonymous 401, absent scope 403, namespace/project forbidden 403, cross-project rows omitted in list/count/overview, bad page/size/time/status 400, scripts source/hash/ownership restrictions, secret absence, settings allowlist and write methods rejected.
- [ ] Observe failures then implement fixed settings record and remaining boundary corrections.
- [ ] Run targeted Maven from PowerShell with explicit JDK25 JAVA_HOME: `mvn -pl device-ops-server -am -Dtest=ManagementHttpTest,ManagementNamespaceBoundaryTest,JdbcManagementQueryAdapterTest,CollectionBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] Run `git diff --check`, inspect only owned paths, report commands/test counts and boundaries. Do not run full clean/verify, do not operate real MySQL, and do not claim production migration execution.
