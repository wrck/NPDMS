# F-SOL-003 Requirement Analysis Versioning Implementation Plan

> 状态：`CANCELED_BY_USER / 2026-08-28`。本计划及其既有实施评审不得继续驱动开发；保留文件仅作历史证据。须先完成共享动态表单与模板配置基础能力，随后基于新的锁定规格重新生成完整 Technical Plan。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to execute this plan. This plan follows one positive implementation closure: connect the complete database, module, API and UI path first, then run one overall verification and acceptance pass. Do not split it into per-layer delivery Tasks, per-step tests, or fragment commits.

**Goal:** Deliver PRE-04 as the SOL-owned, project-scoped requirement-analysis truth with one editable draft, one current effective immutable completed version, historical comparison, exact FileArtifact references and a stable completed-version fact API for future SCH-01 consumers.

**Architecture:** Extend the existing `sol_preparation` aggregate for the PRE-04 root and add a dedicated section table. Obtain the frozen project template only through PROJ's public WorkBinding fact API, keep file objects and references in PLT, and expose completed requirement-analysis facts from `pms-module-engineering-api`. Reuse the existing `/api/v1/pms/preparations` controller family and project workspace instead of introducing a parallel aggregate or general form designer.

**Tech Stack:** Java 25, Spring Boot, MyBatis/XML, Flyway/MySQL, existing PLATFORM command idempotency/audit and FileArtifact APIs, Vue 3/TypeScript/Element Plus, WangEditor, pnpm 9.15.5, Docker Compose infrastructure.

**Locked inputs:** `specs/features/F-SOL-003-requirement-analysis-versioning.md`, `specs/features/F-SOL-003-physical-contract.json`, PRD V1.8 PRE-04, Phase 1/2/3 baseline SDS, `docs/coding/database-query-interface.md`, Feature Ready verdict `NPDMS-FSOL003-FEATURE-READY-20260827-01-R2`.

## Execution model

- Build one integrated positive closure. The implementation remains one candidate until persistence, WorkBinding, FileArtifact, SOL commands/queries/facts and the responsive UI are connected end to end.
- Do not declare intermediate PASS, run a layer-by-layer acceptance campaign, or commit partial business slices. Small compile or type corrections during development are implementation work, not Gate evidence.
- After the complete path exists, execute the single overall verification matrix below. Only a fully passing candidate is committed and submitted for independent Implementation Done review.
- Keep PRE-03, SCH-01 behavior, SOL-01 general forms, PLT-01 unified work items, legacy V1.7 migration, Deployment, SIT, UAT and Release out of this plan.

## Integrated implementation closure

- [ ] **Complete the full PRE-04 positive implementation closure**

### Persistence and frozen catalog

Create `sql/migrations/V99__fsol003_requirement_analysis.sql` to extend `sol_preparation` only with the locked PRE-04 root facts (`source_preparation_id`, `draft_marker`, `effective_marker`, `content_version`, `completed_by`, `completed_at`) and to create `sol_requirement_analysis_section`. Preserve PRE-02 columns, status meanings and current-row rules. Enforce the independent `draft_marker` and `effective_marker` uniqueness, immutable completed history, project/business-version uniqueness and tenant-local section foreign key described by the physical contract. Add `sql/migrations/V100__fsol003_requirement_analysis_seed.sql` for the specified query/manage permissions, PRE-04 catalog version 1 and 11 core labels, plus representative no-extension, multi-field-type and disabled-dictionary-rejection WorkBinding data required by the project seed policy; use dedicated identifiers and do not invent authoritative business values.

Extend `PreparationDO`, `PreparationMapper` and `PreparationMapper.xml`, add `RequirementAnalysisSectionDO`, its scenario Query objects, Mapper and XML under the existing preparation package, and add the locked `PMS-SOL-*` codes to engineering `ErrorCodeConstants`. Mapper methods must accept a single scenario Query except for primary/stable composite unique lookups. Locking, ordered history, current draft/effective lookups, dedicated CAS and compare reads belong in XML; no SQL annotations, `${}`, `.last(...)`, generic maps, general CRUD exposure, or update/delete path for completed content.

Implement a fixed `RequirementAnalysisCatalog`/validator for the 11 V1 core sections and the three mandatory sections. Add `RequirementAnalysisWorkBindingSchema` and minimally modify PROJ `TemplatePublishValidator`, `ProjectTemplateServiceImpl`, the public `ProjectWorkBindingFactApi` query/revalidation/fact DTOs and its implementation/Mapper/XML. Replace the current PRE-02-only lookup assumption with a controlled exact target tuple while preserving PRE-02 behavior:
`BUSINESS_OBJECT/SOL/REQUIREMENT_ANALYSIS/PRE_04_REQUIREMENT_ANALYSIS`. Return and lock/revalidate the raw frozen `catalogCode=PRE_04_REQUIREMENT_ANALYSIS`, `catalogVersion=1`, `extensionItems[]` and template revision facts. Validate extension item codes, closed field types and required flags; for select fields use SYSTEM `DictDataApi` at template publication to freeze enabled `dictionaryType` plus code-sorted `code/label optionSnapshot`. Do not create or infer `dictionaryVersion`, and do not let SOL read PROJ or SYSTEM tables. Empty or multiple exact bindings fail closed. Do not add PRE-04 to `ProjectManualCreationApplicationService.initializePreparationIfConfigured`; unlike PRE-02 auto-initialization, the first PRE-04 draft is created only by its authorized HTTP command.

### PLT existing-version attachment boundary

Modify `pms-module-platform/pms-module-platform-api/.../FileArtifactApi.java` and add explicit command/result DTOs for:

```java
List<FileArtifactVersionFact> attachExistingVersions(
        AttachExistingFileVersionsCommand command);
```

Implement it in `FileArtifactApiImpl`, `FileReferenceMapper` and `FileReferenceMapper.xml` as one caller-owned transaction. The command creates no Artifact or FileVersion and reads no file body. It receives every source stable business key, exact `artifactId/versionNo/fileFactVersion/scopeVersion`, and every target `SOL/REQUIREMENT_ANALYSIS_SECTION/{newSectionId}/SECTION_ATTACHMENT/{newSlotKey}` key.

The implementation has exactly two lock phases. First, sort the complete source `READ` and target `REFERENCE` Provider requests by `ownerContext/objectType/objectId/purposeCode/referenceKey/action`, then lock and revalidate every business Provider. Second, after all Provider calls are finished, acquire all PLT Artifact, Version and Reference locks in their specified stable order and compare the frozen facts. No Provider callback is allowed after the first PLT lock. Under the target Reference lock: insert when absent, replay when it already points to the same artifact/version, and conflict when it points elsewhere. A failure rolls back all new References and the caller's new SOL root/sections.

Event ownership is fixed inside PLT. Extract the existing `PlatformCommandExecutionApiImpl` Outbox persistence logic into the PLT-internal `PlatformTransactionalOutboxWriter`, backed by `PlatformOutboxEventMapper`; both the existing command executor and the new attach implementation reuse it with Spring's surrounding transaction (`REQUIRED`, never `REQUIRES_NEW`). For each target Reference actually inserted, the attach implementation uses PLT-internal `FileEventFactory.referenceAttached` and immediately writes exactly one `FileReferenceAttached` through that writer. A target already attached to the same version is a replay and writes no event. SOL never creates PLT events, receives no internal event object, and does not start a nested `PlatformCommandExecutionApi` execution or a second idempotency record. Therefore N actual inserts produce N events, while any batch failure rolls back all new References, their events, and the outer SOL command's success idempotency/audit facts together. The public return type remains `List<FileArtifactVersionFact>`.

### SOL aggregate, commands, queries and public facts

Add requirement-analysis domain/rule objects, command/query services, explicit request/response VOs and stable error codes under the existing preparation packages. Modify `PreparationController` so the locked PRE-04 operations remain under `/api/v1/pms/preparations` and dispatch by `type=PRE_04` without changing PRE-02 semantics:

- query current effective version plus the manager-visible draft; create the first draft; read one version and ordered sections;
- PATCH one draft section with field-presence semantics and `If-Match`, including rich text normalization, frozen typed-value validation and exact attachment slots;
- complete a draft using `Idempotency-Key` and `If-Match`; create the next draft from the current effective completed version with a stable `Idempotency-Key`;
- return immutable completed history using the locked `(businessVersion,id)` cursor and compare two allowed same-project versions without persisting a diff.

Initialization must require the current manager, `ACTIVE+S1`, exact frozen WorkBinding and no existing draft, and must atomically create business version 1 with all frozen sections. Editing is restricted to the current draft and current project manager. Completion locks and revalidates PROJ scope/manager/project, the SOL root/effective pointer/sections/providers, and then every PLT attachment before any business CAS; it validates the three core mandatory sections and all frozen required extensions, changes the draft to immutable `COMPLETED`, and atomically switches the effective marker. Creating a revision inserts the new root/sections, copies frozen definitions and normalized values, invokes `attachExistingVersions` for new server-generated slot keys, and commits all SOL/PLT facts together. Existing completed rows and their FileReferences remain unchanged.

Use PLATFORM command execution semantics unchanged: completed same-key/same-payload replays the original result, same-key/different-payload conflicts, and `Decision.IN_PROGRESS` maps to stable `PMS-PLATFORM-COMMAND-IN-PROGRESS` with no successful side effect. PATCH relies on aggregate CAS and does not generate a new idempotency key to hide conflicts. Success and rejection auditing records controlled summaries, versions, exact attachment metadata and actor facts, never raw rich text, file bodies, URLs, MinIO keys or Provider exceptions.

Implement `RequirementAnalysisFilePolicyProvider` with the exact stable key and closed action matrix. Draft sections authorize manager `UPLOAD/REFERENCE/REPLACE/DETACH/READ/DOWNLOAD/PREVIEW`; completed sections authorize only `READ/DOWNLOAD/PREVIEW`. Functional permission, `ProjectStageScope`, current manager/member facts, object state and version all remain server authoritative. Query `allowedActions` is conservatively empty when scope or Provider facts are unknown; command locking remains final authorization.

Create in `pms-module-engineering-api` and implement in engineering:

```java
RequirementAnalysisFact inspect(RequirementAnalysisFactQuery query);

RequirementAnalysisFact lockAndRevalidate(
        RequirementAnalysisFactRevalidationQuery query);
```

`inspect` is pure read and returns only an explicit `COMPLETED` version: identity/business/content/project/template versions, completion facts, current-effective relation, ordered normalized section facts and exact attachment vectors. `lockAndRevalidate` follows `PROJ project/scope -> SOL completed root, sections and current pointer -> PLT exact files`, compares the entire expected vector, and holds locks in the caller transaction. An explicit historical completed version remains valid after losing the effective marker while reporting the latest current-effective relation. Do not create SCH references, prefill, input-difference state, events or Outbox in SOL.

### Responsive project workspace

Create `yudao-ui/yudao-ui-admin-vue3/src/api/pms/engineering/requirement-analysis/index.ts` and focused requirement-analysis components under `views/pms/project/project-master-detail/components/`, then integrate a “需求分析” entry into the existing master-detail and responsive project detail. Reuse WangEditor safe rendering and `PmsFileArtifact`; never embed durable file URLs in rich text.

The workspace must distinguish current effective completed content, an editable draft and immutable history. It renders the 11 core sections and frozen extension controls, exact attachments, server blockers, allowed actions and version metadata; supports manager create/edit/complete/revise, authorized member read, stable history pagination and section/attachment comparison. Desktop and 320/768/1024 layouts must keep actions and version identity visible. All action visibility uses server `allowedActions`; the client does not infer roles. PATCH sends only genuinely changed fields with `If-Match`. Create/complete/revise preserve one Idempotency-Key for the complete user intent across response-unknown retries and release it only after success or an explicit new intent.

## Overall verification and acceptance

- [ ] **After the integrated closure is complete, run one overall test and acceptance pass, then submit one implementation candidate**

Run the following as a single candidate-level verification campaign. A failure returns the same integrated candidate to implementation; it does not create a separately passed Task or partial commit.

1. Run focused unit/component and module tests for PROJ WorkBinding, PLT existing-version attachment, SOL persistence/domain/API/file policy, and the requirement-analysis Vue components; then run the affected backend Reactor build, frontend `corepack pnpm ts:check`, targeted lint and production/local build.
2. Start the repository-authoritative Docker Compose infrastructure and validate a clean MySQL migration from V1 through V100, Flyway info/validate, MySQL constraints and the host-run JDK 25 application. MinIO remains the file object store; optional ClamAV behavior is inherited from F-PLT-001 and is not reimplemented here.
3. Through public APIs execute one positive lifecycle: publish a legal PRE-04 WorkBinding, create an ACTIVE S1 project, initialize a draft, save all fixed/extension field types, attach and replace exact files, complete V1, create V2 by reusing immutable FileVersions into new slots, edit V2, complete V2, retain immutable V1, query history/compare, and inspect/lock the selected completed fact.
4. In the same real-MySQL campaign verify mandatory/typed/dictionary validation, empty/multiple/changed WorkBinding, file unavailable/version/scope changes, Provider failure, unauthorized member/non-manager/cross-project/cross-tenant access, stale CAS, same-key replay/different-payload conflict/IN_PROGRESS, concurrent create/complete single-winner behavior, two-phase Provider-before-PLT lock order, rollback with zero partial SOL/Reference/success-audit/outbox facts, effective/draft uniqueness, historical immutability and fact-vector revalidation. Assert specifically that N newly inserted target References produce exactly N `FileReferenceAttached` events, replaying an already identical target adds zero events and any failed batch leaves zero events.
5. In a real browser execute the public UI lifecycle at 320/768/1024/1440 widths, including response-unknown idempotent retry, attachment reuse/replace/detach, completion/revision, member read-only view, history/compare, permission-negative responses, refresh persistence, network status, console errors and page errors. Capture versioned screenshots and HTTP evidence; browser success requires zero unexpected console/page errors and durable state after refresh.
6. Run managed-spec baseline validation, repository baseline rules, migration/architecture boundary checks and `git diff --check`. Confirm the candidate contains only F-SOL-003 and the minimal public PROJ/PLT extensions authorized by the locked contract.

When all evidence passes, update the single F-SOL-003 checkpoint (maximum 300 Chinese characters), create one explicitly staged implementation-candidate commit, and send the independent reviewer the exact Feature/Task scope, commit, files, risks, rollback and evidence. Only reviewer GO permits the Implementation Done status/traceability forward-write. This plan does not authorize Deployment, SIT, UAT or Release.

## Plan self-review

- **Specification coverage:** The integrated closure covers BR/AC-FSOL003-001 through 013: frozen catalog, draft editing, dual current axes, exact files, completion, history/compare, SCH-facing facts, authorization, idempotency, locking, audit, responsive UI and seeded runtime entry.
- **Ownership and dependency direction:** PROJ owns project/template/participant facts, PLT owns Artifact/Version/Reference and constructs/persists `FileReferenceAttached`, SOL owns PRE-04, SYSTEM owns dictionaries, and future SCH consumes only engineering-api. No module reads another Context's table or depends on another module's biz Service/Mapper/DO; the Outbox writer remains a PLT-internal implementation detail.
- **Locking:** Command paths preserve `PROJ -> SOL business Providers -> PLT`. The attach-existing command completes all sorted source/target Provider locks before any sorted PLT lock and never re-enters a Provider.
- **State and type consistency:** `DRAFT/COMPLETED`, independent draft/effective markers, immutable historical content, Long identifiers, explicit business/content/project/template/file/scope versions and structured vectors match the machine contract; no decision-free hashes are introduced.
- **Scope:** No PRE-03, SCH-01 flow, general form designer, legacy double-write/migration, file body/object-store truth, Deployment, SIT, UAT or Release work is included.
- **Execution granularity:** Implementation is one positive closure and verification is one overall campaign, as explicitly required; there are exactly two executable checklist items and no layer-by-layer acceptance Gates.

## Technical Plan Gate

Current status: `PASS / GO`. Independent verdict: `NPDMS-FSOL003-TECHPLAN-20260827-01-R1`; approved plan commit: `fa7d4b469f08d4fa9027bd03e751b3c88f37129d`. Proceed with one complete F-SOL-003 Implementation closure; this does not approve Implementation Done, Deployment, SIT, UAT or Release.
