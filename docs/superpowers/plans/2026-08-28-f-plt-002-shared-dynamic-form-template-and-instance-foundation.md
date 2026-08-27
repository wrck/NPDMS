# F-PLT-002 Shared Dynamic Form Template and Instance Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to execute this plan. Complete one integrated positive implementation closure first, then run the overall verification campaign. Do not split this Feature into layer-by-layer delivery Tasks, per-step Gate reviews or fragment commits.

**Goal:** Deliver a PLT-owned shared dynamic-form foundation in which authorized administrators configure and publish immutable FormCreate template revisions, authorized users manually select the current enabled revision, and frozen instances render, save and refresh ordinary values plus controlled FileArtifact fields.

**Architecture:** Add three tenant-aware PLT tables and a new REST/UI surface inside `pms-module-platform`. Reuse the repository's existing FormCreate designer, renderer, codec helpers and global component setup; add only a PLT composition layer for `PmsFileArtifact`. Keep template identity, immutable revisions and manual instances in PLT, and keep file Artifact/Version/Reference truth in the existing F-PLT-001 services. Do not create a public module API until a real cross-module caller exists.

**Tech Stack:** Java 25, Spring Boot, MyBatis/XML, Flyway/MySQL, PLATFORM command idempotency/audit, F-PLT-001 with MinIO and optional ClamAV, Vue 3.5.34, TypeScript, Element Plus 2.13.7, `@form-create/designer` 3.4.0, `@form-create/element-ui` 3.2.38, pnpm 9.15.5 and Docker Compose infrastructure.

**Locked inputs:** specification source commit `b2da65a0ae01ad5bef079ad7e027423e4b5d57f5`, NPDMS managed-sync commit `070e95b1`, Feature Ready verdict `NPDMS-FPLT002-FEATURE-READY-20260828-01-R1`, `specs/features/F-PLT-002-shared-dynamic-form-template-and-instance-foundation.md`, `specs/features/F-PLT-002-physical-contract.json`, `specs/features/F-PLT-002-legacy-form-reuse-audit.md`, PRD V1.8 SOL-01/PRE-04/PM-03/PM-11, Phase 1/2/3 SDS and `docs/coding/database-query-interface.md`.

## Execution model and fixed boundary

- Build one integrated path: template metadata and revision design -> preview -> publish -> enable -> manual selection -> frozen instance -> dynamic rendering -> ordinary/FileArtifact input -> save -> refresh. It remains one implementation candidate until every layer is connected.
- Run small compile or type corrections only as implementation feedback. Do not create intermediate PASS, independent review or commit for persistence, backend, frontend or tests separately. Run the candidate-level verification matrix only after the full path exists.
- Do not modify managed specifications in NPDMS. Any new contract need returns to the specification repository before code changes.
- Do not implement WorkBinding matching, project template binding, PRE-04 business/versioning integration, ProjectTask adaptation, domain completion/approval, full SOL-01, or SCH/IMP/ACC/CUT behavior.
- Do not modify or relocate BPM FormCreate code, legacy `pms_eng_form_template` / `pms_eng_form_instance`, legacy requirement-analysis classes, APIs, routes, pages, tables, rows, menus, permissions or behavior. Do not migrate or dual-write legacy data.
- Keep the first version deliberately high-trust and untrimmed: retain all current FormCreate built-ins and repository enhancements, including iframe, arbitrary configured GET/POST API selector, events, functions and `parseFunc`. Do not introduce component/property/URL/script allowlists, sandboxing or approval workflow.
- Future restrictions require a new specification and a new published revision; they cannot rewrite an existing published revision or frozen instance.

## Locked reuse map

| Audit IDs | Decision | Exact F-PLT-002 target |
|---|---|---|
| BPM-01, BPM-02, BPM-03, BPM-04 | Direct reuse | Import the existing `fc-designer`, `useFormCreateDesigner`, `encodeConf`/`encodeFields`/`decodeFields`/`setConfAndFields`, and rely on existing `setupFormCreate`; do not edit those files. |
| BPM-05, BPM-06, BPM-07, BPM-08 | Copy then enhance | New `DynamicFormTemplateEditor.vue` copies the complete designer configuration and explicit save/reopen/preview experience, but targets a specific PLT DRAFT revision with `If-Match`; next-draft copying is a server command. |
| PMS-02 through PMS-06, PMS-08 | Copy then enhance | New PLT template/revision/instance models and pages retain FormCreate payloads, frozen-revision intent, CAS and list/select interactions while separating revision status from template availability. |
| REQ-02, REQ-03, REQ-04A | Copy then enhance | The new renderer retains Editor, selection/list feedback and responsive layout intent; it does not copy fixed PRE-04 fields, project context or local snapshot fallback. |
| BPM-09, PMS-01, PMS-07, PMS-09 through PMS-12, REQ-01, REQ-04 through REQ-06 | Do not reuse in this Feature | No BPM table/state, `productType`, raw JSON textarea, legacy submit/approve/delete flow, PRE-04 labels, project entry, WorkBinding, old REST or old table truth enters the new implementation. |

The implementation evidence must map every copied target back to these audit IDs and prove the three legacy path groups have zero diff from `070e95b1`.

## Integrated implementation closure

- [ ] **Complete the full F-PLT-002 positive implementation closure**

### 1. Persistence, migrations and deterministic seeds

Create `sql/migrations/V102__fplt002_dynamic_form.sql` with the following exact tables and constraints:

- `plt_dynamic_form_template`: application-assigned `BIGINT` id, tenant, stable `template_code`, mutable name/category/description, `ENABLED|DISABLED`, current published revision pointer, integer CAS version and Yudao audit/logical-delete columns. Enforce `uk(tenant_id,template_code)`, `uk(tenant_id,id)` and the locked availability/name/pointer indexes.
- `plt_dynamic_form_template_revision`: application-assigned id, tenant/template/revision number, `DRAFT|PUBLISHED`, nullable `draft_marker`, source revision, JSON config/rules, exact engine/designer/renderer versions, publication facts, CAS version and audit/logical-delete columns. Enforce the locked revision and single-draft unique keys, tenant-local composite foreign keys and indexes. `draft_marker=1` exists only on DRAFT; publishing clears it to `NULL`.
- `plt_dynamic_form_instance`: preallocated application id, tenant, generated instance code/name, server-owned `PLATFORM/MANUAL_DYNAMIC_FORM/{instanceId}` binding, frozen template/revision/revision number/engine versions, JSON ordinary values, `created_by`, CAS version and audit/logical-delete columns. Enforce the locked instance-code and owner-object unique keys plus the tenant-local revision foreign key.

Use `@TableId(type = IdType.INPUT)` and `IdWorker.getId()` for all new aggregate rows so the manual instance id exists before its server-owned object binding is inserted. Create template first without a current pointer, create revision and instance foreign keys next, then add the template's tenant-local current-revision foreign key; do not weaken the circular ownership with an unenforced cross-tenant pointer. JSON columns start as `{}` and `[]`, not nullable placeholders. Published rows have no update/delete Mapper path.

Use these concrete column types: all ids/tenant/actor references `BIGINT`; code fields `VARCHAR(64)`; names `VARCHAR(128)`; description `VARCHAR(512)`; owner context `VARCHAR(32)`; object type `VARCHAR(64)`; object id `VARCHAR(128)`; state/availability `VARCHAR(16)`; engine code `VARCHAR(64)`; designer/renderer versions `VARCHAR(32)`; revision/CAS numbers `INT`; draft marker `TINYINT NULL`; config/rules/value payloads native `JSON`; publication/audit timestamps `DATETIME`; Yudao creator/updater `VARCHAR(64)` and logical delete `BIT(1)`. Use `utf8mb4_unicode_ci`, no auto-increment and no generated compatibility column.

Create `sql/migrations/V103__fplt002_dynamic_form_seed.sql` using template ids `992202010001..3`, revision ids `992202020001..3`, dictionary id `992202030001` and menu ids `198800..198805`. It must:

- add `DYNAMIC_FORM_ATTACHMENT` to `pms_file_category` without changing F-PLT-001 states or storage; MinIO and optional ClamAV remain inherited infrastructure;
- add visible menu `198800 / dynamic-form-template / pms/platform/dynamic-form/template/index` under the existing `19271` “文档表单” group with `pms:dynamic-form-template:query`, children `198801 manage` and `198802 publish`; add visible menu `198803 / dynamic-form-instance / pms/platform/dynamic-form/instance/index` with `pms:dynamic-form-instance:query`, children `198804 create` and `198805 update`. These are exactly the six locked permission resources. Do not write `system_role_menu` or create a role;
- insert `PLT_EXAMPLE_GENERAL_FORM` as an enabled/current-published example containing representative text, rich text, select, boolean, number, layout, ordinary upload and `PmsFileArtifact` rules; `PLT_EXAMPLE_DISABLED_FORM` as a disabled/current-published example excluded from selection; and `PLT_EXAMPLE_DRAFT_FORM` as a draft-only example excluded from selection. Use `creator='seed'` and exact engine versions. Menus/dictionaries may use deterministic upsert; sample templates/revisions use insert-if-absent and never rewrite an existing PUBLISHED payload or reset a user's later pointer/availability on rerun. Do not seed PRE-04, WorkBinding or another domain's values.

Add `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/DynamicFormMigrationContractTest.java` to assert table/constraint/index names, V102-before-V103 ordering, exactly the six permissions, absence of role grants and the three selection-state examples.

### 2. PLT persistence and schema model

Add these tenant-owned data objects:

- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormTemplateDO.java`
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormTemplateRevisionDO.java`
- `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/dal/dataobject/dynamicform/DynamicFormInstanceDO.java`

Add `DynamicFormTemplateMapper.java`, `DynamicFormTemplateRevisionMapper.java`, `DynamicFormInstanceMapper.java` under `dal/mysql/dynamicform/`, matching XML files under `src/main/resources/mapper/dynamicform/`, and the following single-purpose records under `dal/mysql/dynamicform/query/`:

- `DynamicFormTemplatePageQuery`, `DynamicFormTemplateRowQuery`, `DynamicFormTemplateLockQuery`, `DynamicFormTemplateVersionUpdate`;
- `DynamicFormRevisionListQuery`, `DynamicFormRevisionRowQuery`, `DynamicFormRevisionLockQuery`, `DynamicFormDraftCreateQuery`, `DynamicFormRevisionPublishUpdate`;
- `DynamicFormInstancePageQuery`, `DynamicFormInstanceRowQuery`, `DynamicFormInstanceLockQuery`, `DynamicFormInstanceValueUpdate`.

Page summaries and stable ordering, revision lists, lock reads, tenant-composite identity checks and CAS updates belong in XML. Simple primary/stable-unique reads may use `LambdaQueryWrapperX`. No Mapper method accepts a long positional argument list, `Map` or generic query object; no SQL annotation, `${}`, `.last(...)`, Service SQL or cross-module table read is allowed. Empty tenant/permission scope must return no rows.

Add `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormSchemaService.java`. It owns only the locked structural model:

- parse config as one JSON object and rules as one JSON array;
- recursively walk each rule object and every nested `children` array in document order;
- classify every nonblank `field` as a value field, require uniqueness across the whole revision, and classify only exact `type=PmsFileArtifact` as controlled file input;
- reject `/` in a controlled file field key and require `FORM_FIELD_ATTACHMENT/{fieldKey}` to fit the existing F-PLT-001 `purpose_code VARCHAR(64)`/REST limit (fixed prefix 22 characters, therefore controlled `fieldKey` maximum 42 characters); retain unknown rule/component objects unchanged and never inspect or strip URL, event, function, iframe or `parseFunc` content;
- require `FORM_CREATE_ELEMENT_PLUS`, designer `3.4.0` and renderer `3.2.38` at DRAFT save/publish and return ordered ordinary/file field-key sets for commands, queries and the File Provider.

Add the locked dynamic-form errors in `pms-module-platform/pms-module-platform-api/src/main/java/cn/iocoder/yudao/module/pms/platform/enums/ErrorCodeConstants.java` as `1_010_003_000..010` in the physical-contract order: template not found/code conflict/disabled/current revision changed/draft exists/revision not draft/schema invalid/field duplicate/instance not found/unknown instance field/file field requires File API. Add `DYNAMIC_FORM_VERSION_CONFLICT` as `1_010_003_011` for the contract's generic `VERSION_CONFLICT`. Reuse the existing global `FORBIDDEN`, `PLATFORM_COMMAND_KEY_CONFLICT` and `PLATFORM_COMMAND_IN_PROGRESS` codes rather than duplicating them.

### 3. Commands, transactions, authorization and audit

Add `DynamicFormCommandService.java`, `DynamicFormQueryService.java`, `DynamicFormActionProjection.java`, `DynamicFormCommands.java` and `DynamicFormViews.java` under `service/dynamicform/`. `DynamicFormCommands` contains the actor plus create-template, patch-metadata, create-revision, patch-revision, publish-revision, set-availability, create-instance and patch-instance records; `DynamicFormViews` contains service-layer template/revision/selection/instance views so services do not depend on controller VOs. Add explicit direct dependencies on the existing SYSTEM permission API, web/security, validation and transaction facilities in `pms-module-platform/pom.xml`; do not depend on another PMS module's `-biz` code.

`DynamicFormCommandService` must execute these exact transactions:

1. **Create template:** normalize and validate request, claim `PLT:DYNAMIC_FORM:TEMPLATE_CREATE`, insert a DISABLED template and revision 1 DRAFT with `{}`/`[]` and pinned engine versions, then persist controlled success audit/idempotency in the same transaction.
2. **PATCH template metadata:** lock by tenant/id, require manage permission and `If-Match`, apply only presence-tracked `templateName/categoryCode/description`, CAS the template, then write one safe success audit. Explicit `description:null` clears it; absence leaves it unchanged.
3. **Create next draft:** claim `PLT:DYNAMIC_FORM:REVISION_CREATE`, lock template then current draft/current published rows, require the expected template version and no draft, copy the immutable current published payload to `revisionNo+1`, then persist success facts. No published revision means this command is invalid; initial creation already owns revision 1.
4. **PATCH revision:** lock template then explicit revision, require manage permission, DRAFT and revision `If-Match`; structurally validate and replace the complete config/rules/engine tuple, CAS the DRAFT and audit only ids, engine versions and ordered field keys.
5. **Publish revision:** claim `PLT:DYNAMIC_FORM:REVISION_PUBLISH`, lock template then explicit DRAFT, require publish permission and revision `If-Match`, repeat all structural/field/engine validation, CAS DRAFT to immutable PUBLISHED while clearing `draft_marker`, switch the template current pointer and increment template version, then write success idempotency/audit. Failure keeps the old pointer and DRAFT unchanged.
6. **Enable/disable template:** claim separate target-state-aware idempotency scopes, lock template, require publish permission and template `If-Match`; enable only with a current PUBLISHED revision, CAS availability, and never create a revision.
7. **Create manual instance:** claim `PLT:DYNAMIC_FORM:INSTANCE_CREATE`, lock template then requested revision, require create permission, expected template version, ENABLED and exact current PUBLISHED identity; preallocate the instance id, bind `PLATFORM/MANUAL_DYNAMIC_FORM/{id}`, generate `DFI-{id}`, freeze revision/engine facts, initialize `{}`, and persist success facts. Never substitute a newer revision.
8. **PATCH instance:** lock tenant/id, require update permission, creator identity and instance `If-Match`; require a nonempty JSON object, reject unknown and controlled-file keys, merge only present ordinary keys while preserving `null/false/0/""/[]`, CAS once and audit only ordered submitted keys and before/after version.

Use the existing `PlatformCommandExecutionApi` for the six idempotent command families. Build each request digest from an explicit normalized `LinkedHashMap` of the target id, expected version and normalized payload; do not accept a client digest. Map `Decision.CONFLICT` and `Decision.IN_PROGRESS` to their distinct existing errors. Use `TransactionTemplate` around the command executor and record a stable rejected audit only after rollback, following the existing application-service pattern. PATCH commands participate in one transaction and call `OperationAuditApi` for success; they do not invent an idempotency record.

Every success detail must include `operationId`, actor, aggregate ids, before/after status or versions and ordered changed field keys where applicable. Never copy complete config/rules, event/function source, rich text, form values, API/iframe content, file body, MinIO key, permanent URL or Provider exception into audit. No dynamic-form Outbox event is produced.

`DynamicFormActionProjection` uses `PermissionApi` and current row facts to return only currently executable actions: `PATCH_TEMPLATE`, `CREATE_REVISION`, `PATCH_REVISION`, `PUBLISH_REVISION`, `ENABLE`, `DISABLE`, `CREATE_INSTANCE`, `PATCH_INSTANCE`. A query/permission failure produces an empty action set. Controllers still enforce function permissions and every command repeats authorization after the lock.

### 4. FileArtifact composition without a second file truth

Add `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/service/dynamicform/DynamicFormFilePolicyProvider.java` implementing the existing `FileBusinessObjectPolicyProvider` for exactly `PLATFORM/DYNAMIC_FORM_INSTANCE`.

- Parse only `FORM_FIELD_ATTACHMENT/{fieldKey}` and UUID `referenceKey`; load the current-tenant instance and its frozen revision, then use `DynamicFormSchemaService` to require an exact controlled file field.
- For `UPLOAD/REFERENCE/REPLACE/DETACH`, require `pms:dynamic-form-instance:update` and `created_by=current actor`; for `READ/DOWNLOAD/PREVIEW`, require `pms:dynamic-form-instance:query`. The F-PLT-001 endpoint independently enforces the matching `pms:file:*` permission.
- Return the immutable frozen `template_revision_id` as `scopeVersion`, `referenceMutability=MUTABLE`, `cardinality=MULTIPLE`, category `DYNAMIC_FORM_ATTACHMENT`, 50MB, `INTERNAL`, and exactly `application/pdf`, `image/jpeg`, `image/png`, `text/plain`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/vnd.ms-powerpoint`, `application/vnd.openxmlformats-officedocument.presentationml.presentation`. Ordinary instance PATCH must not stale valid file references. `lockAndRevalidate` locks the instance, compares the expected frozen revision id and rechecks permission/creator/schema before returning the same facts; unknown identity, permission or schema fails closed.
- Implement both single-reference and reference-set inspection/revalidation so an instance detail can read every controlled field in one batch. The Provider obtains its instance lock before F-PLT-001 obtains Artifact -> Version -> Reference locks and never calls another Context.

`DynamicFormQueryService` must load the frozen revision and discover all controlled field purposes. With one or more controlled fields it calls `FileArtifactApi.inspectReferenceSets` exactly once for the whole instance, requires one result per purpose, and accepts each authorized purpose's explicit empty `activeFacts` array. With no controlled fields it makes no FileArtifact call and returns an empty controlled-fact map, because the existing collection API intentionally requires at least one collection key. It maps ACTIVE facts by `fieldKey` and returns only `artifactId/versionNo/referenceKey/fileFactVersion/scopeVersion/status`; it never stores those facts in `value_json` or exposes storage keys/URLs. Provider unavailability fails the detail read closed rather than returning forged empty evidence.

The dedicated `PmsFileArtifact` component keeps using existing upload/add-version/detach/read/access-ticket commands and stable `slotKey` intent UUIDs. A file success followed by ordinary-value PATCH failure remains visible from current FileReference truth and is retried with the original slot/intent; no attachment snapshot or PENDING state is added to the instance.

### 5. REST contract and response projection

Add these controllers and VO packages under `pms-module-platform/src/main/java/cn/iocoder/yudao/module/pms/platform/controller/admin/dynamicform/`:

- `DynamicFormTemplateController.java` with `DynamicFormTemplatePageReqVO`, `DynamicFormTemplateCreateReqVO`, presence-aware `DynamicFormTemplatePatchReqVO`, `DynamicFormTemplateRespVO`, `DynamicFormRevisionPatchReqVO` and `DynamicFormRevisionRespVO`;
- `DynamicFormInstanceController.java` with `DynamicFormInstancePageReqVO`, `DynamicFormInstanceCreateReqVO`, `DynamicFormInstancePatchReqVO`, `DynamicFormInstanceRespVO` and controlled `DynamicFormFileFactRespVO`.

Expose exactly the locked `/api/v1/pms` endpoints for template page/create/detail/metadata PATCH, revision create/detail/PATCH/publish, enable/disable, selection, instance page/create/detail/PATCH. Use `TenantContextHolder` and `SecurityFrameworkUtils` for tenant/actor; reject forbidden client fields by not defining them. `If-Match` is a required integer header on every locked CAS endpoint and `Idempotency-Key` is required only on the locked idempotent endpoints. Selection GET requires instance-query permission and projects `CREATE_INSTANCE` only when create permission is current. Template/instance pages use the locked stable ordering.

Controller contract tests must assert method/path/header/permission annotations, forbidden request fields, CommonResult/PageResult shapes and the distinction between absent and explicit-null metadata fields. Do not add a new dynamic-form contract to `pms-module-platform-api` in this Feature.

### 6. Complete FormCreate configuration and manual-instance UI

Add `yudao-ui/yudao-ui-admin-vue3/src/api/pms/platform/dynamic-form/index.ts` with exact request/response types for all REST endpoints, structured `allowedActions`, numeric CAS versions and controlled file facts. The client never sends tenant, actor, owner binding, generated code, revision status or publication facts.

Add these new PLT UI targets under `src/views/pms/platform/dynamic-form/`:

- `template/index.vue`: responsive template list, metadata create/edit, dual revision/availability labels, version history and server-action-driven controls;
- `template/DynamicFormTemplateEditor.vue`: full-screen designer/preview for an explicit revision, the complete copied BPM `designerConfig`, `useFormCreateDesigner`, PLT controlled-field registration, DRAFT save with `If-Match`, response-unknown reload and PUBLISHED read-only preview;
- `instance/index.vue`: responsive instance list plus enabled-template selection/preview; creation always sends the selected `templateRevisionId` and `expectedTemplateVersion`;
- `instance/DynamicFormInstanceForm.vue`: render the frozen revision with FormCreate, inject server file facts into controlled fields, collect only genuinely changed ordinary keys, validate through the renderer, PATCH with `If-Match`, and restore the same values after refresh;
- `components/PmsFileArtifactField.vue`, `components/usePmsFileArtifactDesignerRule.ts`, `components/registerDynamicFormComponents.ts`, `components/dynamicFormCodec.ts` and `components/dynamicFormRuntime.ts`.

`dynamicFormCodec.ts` must reuse the existing encode/decode helpers while adapting their string-array representation to the REST JSON-array contract. `dynamicFormRuntime.ts` deep-clones the frozen rules, recursively injects instance id/field key/current facts/read-write actions into exact `PmsFileArtifact` rules, and excludes those keys from ordinary PATCH without altering the persisted revision.

`PmsFileArtifactField.vue` composes the existing `PmsFileUploader`, `PmsFileReferenceList` and version/access flows; it owns no second file value and retains the same slot/idempotency key when a response is unknown. `registerDynamicFormComponents.ts` registers the PLT component only when the new pages load; do not edit `src/plugins/formCreate/index.ts` or the shared `useFormCreateDesigner.ts`.

Keep one stable client idempotency key per complete create-template, create-revision, publish, availability and create-instance intent. A response-unknown retry reuses it; a successful response or an explicit payload/target change starts a new intent. The UI uses only returned `allowedActions`, shows the high-trust browser-code warning, distinguishes ordinary upload from controlled FileArtifact evidence, and states that disabling a template does not change existing instances.

At 320px the template designer may display a clear desktop-width recommendation, but template lists and instance filling must remain operable without page-level horizontal overflow. At 768/1024/1440 the editor, preview and action/version identity remain visible. Add only the two new menu-driven page components; do not edit old BPM/engineering/requirement/project-detail pages.

### 7. Focused automated coverage created with the implementation

Add backend tests under `pms-module-platform/src/test/java/cn/iocoder/yudao/module/pms/platform/dynamicform/`:

- `DynamicFormSchemaServiceTest`: nested field discovery, duplicate/blank keys, slash in controlled keys, exact engine versions, unknown-component retention and unchanged function/API/iframe content;
- `DynamicFormCommandServiceTest`: every state/permission/version/idempotency/audit branch and safe detail boundaries;
- `DynamicFormQueryServiceTest`: stable rows/actions, frozen revision rendering, one batch FileArtifact call for all controlled purposes, explicit empty ACTIVE facts per purpose, zero calls when the schema has no controlled field, and Provider failure;
- `DynamicFormFilePolicyProviderTest`: exact namespace/field/action/creator/permission/scope-version decisions and instance-before-file lock behavior;
- `DynamicFormControllerContractTest`: the REST contract described above;
- `DynamicFormApplicationMySqlIntegrationTest`: the real Mappers, services, `PlatformCommandExecutionApiImpl`, `OperationAuditApiImpl`, FileArtifact API/Provider registry and MySQL transactions in one Spring context.

The MySQL application test must prove: unique template code and unique draft; immutable published revision; pointer/availability behavior; enabled-selection drift rejection; frozen existing instance after a new publish; ordinary false/zero/null/empty semantics; CAS single winner for concurrent publish and instance PATCH; same-key completed replay, different-payload conflict and pre-existing IN_PROGRESS; tenant and creator isolation; exact safe `plt_operation_audit.detail_snapshot`; one-batch file facts; and rollback leaving zero partial template/revision/instance, successful idempotency, success audit or pointer changes.

Add frontend runtime tests beside the new components: `dynamicFormCodec.runtime.spec.ts`, `DynamicFormTemplateEditor.runtime.spec.ts`, `DynamicFormInstanceForm.runtime.spec.ts`, `PmsFileArtifactField.runtime.spec.ts` and `dynamicFormPages.spec.ts`. Use the Vue client renderer for real component branches, not static text matching. Cover complete designer menus/config, save/reopen/preview, recursive file-rule injection, false/zero/null/empty-array preservation, genuine partial PATCH, stable unknown-response intents, allowedActions and all four responsive branches.

## Overall verification and acceptance

- [ ] **After the integrated closure is complete, run one overall campaign, commit one candidate and request one Implementation Done review**

Run the following only against the complete candidate. A failure returns the same candidate to implementation and does not create a passed sub-Task or partial Gate.

1. Run the focused dynamic-form backend and Vue runtime tests, all affected PLATFORM/FileArtifact tests, `corepack pnpm ts:check`, targeted ESLint/Stylelint/Prettier checks and `corepack pnpm build:local`; then run the JDK 25 full Maven Reactor test/build required by repository rules.
2. Start only the repository-authoritative Docker Compose infrastructure. Recreate an isolated MySQL schema and migrate V1 -> V103; record Flyway migrate/info/validate, constraints/indexes/seeds and the host-run backend/frontend build identities. Use real MinIO for browser file storage and test both default `scanStatus=SKIPPED` and configured optional ClamAV `PASSED` without changing the inherited file state machine.
3. Through public REST execute the positive loop and the MySQL matrix: create -> design -> reopen -> preview -> publish -> enable -> select -> create frozen instance -> save ordinary values -> upload/replace/detach controlled files -> refresh -> publish a newer revision -> prove the old instance remains frozen -> disable/re-enable selection. Verify permissions, cross-tenant isolation, stale CAS, selection drift, idempotent retry, IN_PROGRESS, rollback and safe audit facts.
4. In a real browser repeat the public loop at 320/768/1024/1440. Include every current built-in/enhanced control, rich text, ordinary upload, `PmsFileArtifact`, iframe, API GET/POST, linkage, validation, event/function/`parseFunc`, response-unknown retries, false/zero/null/empty-array values, revision history, disable/re-enable and immutable old-instance rendering. An intentionally unauthorized target API/CORS/CSP/iframe failure must remain a browser/target failure and must not create a PLT or other-domain success fact.
5. Use an authorized read-only user, a non-creator update attempt and a second tenant to verify UI action projection plus server rejection with zero success side effects. Capture HTTP status, final refreshed state, unexpected console/page/request error counts and versioned screenshots in `docs/engineering/evidence/f-plt-002-browser-evidence.json` and `docs/engineering/evidence/f-plt-002-browser/`; expected negative requests are labeled and zero unexpected errors is required.
6. In the same browser/application run, open and exercise the existing BPM form list/editor, legacy PMS form-template/form-instance pages/APIs and legacy requirement-analysis/project entry under their existing permissions. Record `git diff --exit-code 070e95b1 --` for the audited legacy backend/frontend/menu paths and verify their row counts/responses are unchanged by new PLT commands.
7. Run managed-spec baseline validation, repository baseline rules, architecture/module-boundary and migration checks plus `git diff --check`. Confirm no WorkBinding/PRE-04 consumer code, public dynamic-form module API, legacy edit, role grant, Deployment, SIT, UAT or Release claim entered the candidate.

After all evidence passes, update the single F-PLT-002 checkpoint (maximum 300 Chinese characters), explicitly stage only this Feature's implementation/evidence files, create one implementation-candidate commit and submit that exact commit for independent Implementation Done review. Reviewer GO alone permits status/traceability forward-write.

## Plan self-review

- **Specification coverage:** The integrated path covers BR/AC-FPLT002-001 through 012: template identity, immutable revision, independent availability, complete FormCreate, manual selection, frozen instance, ordinary/FileArtifact values, authorization/actions, idempotency/CAS/audit, migration, responsive browser acceptance and legacy invariance.
- **Ownership:** PLT owns template/revision/instance truth; F-PLT-001 owns Artifact/Version/Reference and MinIO/scan facts; SYSTEM supplies permission facts. No module reads another Context's table, and no empty public API is created.
- **State and locking:** Commands preserve `Template -> current DRAFT/PUBLISHED Revision -> Instance -> File Provider -> Artifact -> Version -> Reference`. Published revision fields have no write path; template availability never changes historical revision or instance facts.
- **Reuse:** The plan directly reuses stable FormCreate infrastructure, copies only audited page-level behavior into new PLT targets, and leaves BPM, legacy PMS forms and requirement analysis untouched.
- **High-trust boundary:** The complete current designer surface stays available. The server validates structure/identity/version only and never evaluates, proxies, strips or silently rewrites administrator-authored browser configuration.
- **Execution granularity:** There are exactly two executable checklist items: one complete implementation closure and one overall validation/submission campaign. No micro-feature is treated as independently delivered.

## Technical Plan Gate

Current status: `PENDING_INDEPENDENT_REVIEW`. No product implementation is authorized until an independent GO is recorded against the committed version of this plan.
