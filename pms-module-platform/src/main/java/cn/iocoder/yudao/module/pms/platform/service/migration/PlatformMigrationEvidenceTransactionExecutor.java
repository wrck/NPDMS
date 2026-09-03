package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.Code.*;

@Service
@RequiredArgsConstructor
public class PlatformMigrationEvidenceTransactionExecutor {

    private static final long SYSTEM_ACTOR = 0L;
    private static final String SYSTEM_ACTOR_TEXT = "0";
    private static final Comparator<ExternalTargetMapping> TARGET_ORDER = Comparator
            .comparing(ExternalTargetMapping::targetRole)
            .thenComparingInt(ExternalTargetMapping::targetSequence)
            .thenComparing(ExternalTargetMapping::targetContext)
            .thenComparing(ExternalTargetMapping::targetObjectType)
            .thenComparing(ExternalTargetMapping::targetId);

    private final MigrationBatchMapper batchMapper;
    private final MigrationSourceRecordMapper sourceMapper;
    private final ExternalKeyMappingMapper mappingMapper;
    private final MigrationIssueMapper issueMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Clock clock = Clock.systemDefaultZone();

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchFact createImportBatch(CreateImportBatchCommand command) {
        return execute("PLT:MIGRATION:BATCH:CREATE", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.ownerContextCode(), command.purposeCode(), command.releaseId(),
                        command.sourceSystem(), command.sourceTable(), command.manifestSchemaVersion(),
                        command.expectedRowCount(), command.contentSha256(), command.exportedAt(),
                        command.previousBatchId(), command.previousIssueId()),
                MigrationBatchFact.class, () -> createBatch(command), command.correlationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationSourceRecordFact appendSourceRecord(AppendMigrationSourceRecordCommand command) {
        MigrationBatchDO batch = requireBatchForUpdate(command.tenantId(), command.batchId());
        requireStatus(batch, MigrationBatchStatus.IMPORTING);
        if (!batch.getSourceSystem().equals(command.sourceSystem())
                || !batch.getSourceTable().equals(command.sourceTable())) {
            throw failure(BATCH_SOURCE_IDENTITY_MISMATCH, "source identity does not belong to batch");
        }
        MigrationSourceIdentityQuery identity = new MigrationSourceIdentityQuery(command.tenantId(), command.batchId(),
                command.sourceSystem(), command.sourceTable(), command.sourcePk());
        MigrationSourceRecordDO existing = sourceMapper.selectByIdentity(identity);
        if (existing != null) {
            return replaySource(existing, command);
        }
        MigrationSourceRecordDO source = new MigrationSourceRecordDO();
        source.setTenantId(command.tenantId());
        source.setBatchId(command.batchId());
        source.setSourceSystem(command.sourceSystem());
        source.setSourceTable(command.sourceTable());
        source.setSourceRecordKey(command.sourcePk());
        source.setSourceBusinessKey(command.sourceBusinessKey());
        source.setSourcePayload(command.sourcePayloadJson());
        source.setSourceChecksum(command.sourceChecksum());
        source.setExtractedAt(command.extractedAt());
        initializeAuditFields(source);
        try {
            sourceMapper.insert(source);
        } catch (DuplicateKeyException ex) {
            return replaySource(sourceMapper.selectByIdentity(identity), command);
        }
        return sourceFact(source, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchFact markStagedReady(MarkStagedReadyCommand command) {
        return execute("PLT:MIGRATION:BATCH:STAGE", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.batchId(), command.expectedBatchVersion(), command.decision(),
                        command.manifestRowCount(), command.manifestSchemaVersion(),
                        command.manifestContentSha256(), command.failureCode()),
                MigrationBatchFact.class, () -> stageBatch(command), command.correlationId());
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public MigrationBatchClaimResult claimStagedBatch(ClaimStagedBatchCommand command) {
        MigrationBatchDO batch = batchMapper.selectNextStagedForUpdate(new MigrationBatchClaimQuery(
                command.tenantId(), command.ownerContextCode(), command.purposeCode(),
                command.sourceSystems(), command.sourceTables()));
        if (batch == null) {
            return MigrationBatchClaimResult.empty();
        }
        if (batchMapper.claim(new MigrationBatchIdQuery(command.tenantId(), batch.getId()), batch.getVersion()) != 1) {
            throw failure(BATCH_STATE_CONFLICT, "staged batch claim conflicted");
        }
        batch.setBatchStatus(MigrationBatchStatus.RECONCILING.name());
        batch.setVersion(batch.getVersion() + 1);
        return new MigrationBatchClaimResult(true, batchFact(batch));
    }

    @Transactional(readOnly = true)
    public MigrationSourceRecordPage pageSourceRecords(MigrationSourceRecordPageQuery query) {
        MigrationBatchDO batch = batchMapper.selectById(query.batchId());
        if (batch == null || !query.tenantId().equals(batch.getTenantId())) {
            throw failure(BATCH_NOT_FOUND, "batch is not visible");
        }
        if (MigrationBatchStatus.IMPORTING.name().equals(batch.getBatchStatus())
                || MigrationBatchStatus.FAILED.name().equals(batch.getBatchStatus())) {
            throw failure(BATCH_STATE_CONFLICT, "batch sources are not frozen");
        }
        List<MigrationSourceRecordFact> records = sourceMapper.selectCursorPage(new MigrationSourceCursorQuery(
                        query.tenantId(), query.batchId(), query.afterSourceRecordId(), query.limit()))
                .stream().map(source -> sourceFact(source, source.getResultType())).toList();
        Long next = records.size() == query.limit() ? records.getLast().sourceRecordId() : null;
        return new MigrationSourceRecordPage(records, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourceReconciliationResult appendExternalMapping(AppendExternalMappingCommand command) {
        return execute("PLT:MIGRATION:RESULT:APPEND", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.batchId(), command.sourceRecordId(),
                        command.resultType(), command.targets()), SourceReconciliationResult.class,
                () -> appendMapping(command), command.correlationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationIssueFact appendMigrationIssue(AppendMigrationIssueCommand command) {
        return execute("PLT:MIGRATION:ISSUE:APPEND", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.batchId(), command.sourceRecordId(), command.issueKey(),
                        command.issueType(), command.rawBusinessKey(), command.candidateTargetIds(),
                        command.rawPayloadJson()), MigrationIssueFact.class,
                () -> appendIssue(command), command.correlationId());
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public MigrationBatchFact completeReconciliation(CompleteReconciliationCommand command) {
        return execute("PLT:MIGRATION:BATCH:COMPLETE", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.batchId(), command.expectedBatchVersion(),
                        command.expectedSourceCount(), command.expectedMappedCount(), command.expectedIssueCount(),
                        command.expectedRetainedCount(), command.ruleVersion()), MigrationBatchFact.class,
                () -> completeBatch(command), command.correlationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationIssueFact closeMigrationIssue(CloseMigrationIssueCommand command) {
        return execute("PLT:MIGRATION:ISSUE:CLOSE", command.tenantId(), command.idempotencyKey(),
                digest(command.tenantId(), command.issueId(), command.resolverUserId(),
                        command.ruleVersion(), command.targetResultJson()), MigrationIssueFact.class,
                () -> closeIssue(command), command.correlationId());
    }

    private MigrationBatchFact createBatch(CreateImportBatchCommand command) {
        MigrationBatchIdentityQuery identity = new MigrationBatchIdentityQuery(command.tenantId(),
                command.ownerContextCode(), command.purposeCode(), command.releaseId(),
                command.sourceSystem(), command.sourceTable());
        if (batchMapper.selectByIdentity(identity) != null) {
            throw failure(BATCH_STATE_CONFLICT, "batch identity already exists under another intent");
        }
        MigrationBatchDO batch = new MigrationBatchDO();
        batch.setTenantId(command.tenantId());
        batch.setOwnerContextCode(command.ownerContextCode());
        batch.setPurposeCode(command.purposeCode());
        batch.setReleaseId(command.releaseId());
        batch.setSourceSystem(command.sourceSystem());
        batch.setSourceTable(command.sourceTable());
        batch.setManifestSchemaVersion(command.manifestSchemaVersion());
        batch.setExpectedRowCount(command.expectedRowCount());
        batch.setContentSha256(command.contentSha256());
        batch.setExportedAt(command.exportedAt());
        batch.setPreviousBatchId(command.previousBatchId());
        batch.setPreviousIssueId(command.previousIssueId());
        batch.setBatchStatus(MigrationBatchStatus.IMPORTING.name());
        batch.setSourceCount(0L);
        batch.setMappedCount(0L);
        batch.setIssueCount(0L);
        batch.setRetainedCount(0L);
        batch.setVersion(0);
        initializeAuditFields(batch);
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException ex) {
            throw failure(BATCH_STATE_CONFLICT, "batch identity already exists under another intent");
        }
        return batchFact(batch);
    }

    private MigrationBatchFact stageBatch(MarkStagedReadyCommand command) {
        MigrationBatchDO batch = requireBatchForUpdate(command.tenantId(), command.batchId());
        requireStatus(batch, MigrationBatchStatus.IMPORTING);
        if (batch.getVersion() != command.expectedBatchVersion()) {
            throw failure(BATCH_STATE_CONFLICT, "batch version is stale");
        }
        MigrationBatchClassificationSummary summary = sourceMapper.selectClassificationSummary(
                new MigrationBatchIdQuery(command.tenantId(), command.batchId()));
        String target;
        String failureCode = null;
        long sourceCount = summary.sourceCount();
        if (command.decision() == ImportStagingDecision.READY) {
            if (command.manifestRowCount() != batch.getExpectedRowCount()
                    || !command.manifestSchemaVersion().equals(batch.getManifestSchemaVersion())
                    || !command.manifestContentSha256().equals(batch.getContentSha256())
                    || sourceCount != command.manifestRowCount()) {
                throw failure(COUNT_MISMATCH, "manifest and frozen sources do not match");
            }
            target = MigrationBatchStatus.STAGED_READY.name();
        } else {
            target = MigrationBatchStatus.FAILED.name();
            failureCode = command.failureCode().name();
        }
        if (batchMapper.transition(new MigrationBatchTransitionUpdate(command.tenantId(), command.batchId(),
                command.expectedBatchVersion(), MigrationBatchStatus.IMPORTING.name(), target,
                sourceCount, failureCode)) != 1) {
            throw failure(BATCH_STATE_CONFLICT, "batch staging conflicted");
        }
        batch.setBatchStatus(target);
        batch.setSourceCount(sourceCount);
        batch.setFailureCode(failureCode);
        batch.setVersion(batch.getVersion() + 1);
        return batchFact(batch);
    }

    private SourceReconciliationResult appendMapping(AppendExternalMappingCommand command) {
        requireReconciliationSource(command.tenantId(), command.batchId(), command.sourceRecordId());
        MigrationSourceOnlyQuery sourceQuery = new MigrationSourceOnlyQuery(command.tenantId(), command.sourceRecordId());
        List<MigrationIssueDO> issues = issueMapper.selectListBySource(sourceQuery);
        List<ExternalKeyMappingDO> existing = mappingMapper.selectListBySource(sourceQuery);
        if (!issues.isEmpty()) {
            throw failure(SOURCE_ALREADY_CLASSIFIED, "source is already classified as ISSUE");
        }
        if (!existing.isEmpty()) {
            return replayMapping(existing, command);
        }
        List<Long> ids = new ArrayList<>();
        if (command.resultType() == SourceReconciliationType.RETAINED) {
            ExternalKeyMappingDO retained = mappingRow(command, null);
            initializeAuditFields(retained);
            mappingMapper.insert(retained);
        } else {
            for (ExternalTargetMapping target : command.targets()) {
                ExternalKeyMappingDO mapping = mappingRow(command, target);
                initializeAuditFields(mapping);
                mappingMapper.insert(mapping);
                ids.add(mapping.getId());
            }
        }
        return new SourceReconciliationResult(command.sourceRecordId(), command.resultType(), ids);
    }

    private MigrationIssueFact appendIssue(AppendMigrationIssueCommand command) {
        requireReconciliationSource(command.tenantId(), command.batchId(), command.sourceRecordId());
        MigrationSourceOnlyQuery sourceQuery = new MigrationSourceOnlyQuery(command.tenantId(), command.sourceRecordId());
        if (!mappingMapper.selectListBySource(sourceQuery).isEmpty()) {
            throw failure(SOURCE_ALREADY_CLASSIFIED, "source is already classified as mapping or retained");
        }
        for (MigrationIssueDO existing : issueMapper.selectListBySource(sourceQuery)) {
            if (existing.getIssueKey().equals(command.issueKey())) {
                if (sameIssue(existing, command)) {
                    return issueFact(existing);
                }
                throw failure(ISSUE_CONFLICT, "issue identity has different immutable facts");
            }
        }
        MigrationIssueDO issue = new MigrationIssueDO();
        issue.setTenantId(command.tenantId());
        issue.setBatchId(command.batchId());
        issue.setSourceRecordId(command.sourceRecordId());
        issue.setIssueKey(command.issueKey());
        issue.setIssueType(command.issueType());
        issue.setRawBusinessKey(command.rawBusinessKey());
        issue.setCandidateTargetIds(JsonUtils.toJsonString(command.candidateTargetIds()));
        issue.setRawPayload(command.rawPayloadJson());
        issue.setIssueStatus(MigrationIssueStatus.OPEN.name());
        issue.setVersion(0);
        initializeAuditFields(issue);
        issueMapper.insert(issue);
        return issueFact(issue);
    }

    private MigrationBatchFact completeBatch(CompleteReconciliationCommand command) {
        MigrationBatchDO batch = requireBatchForUpdate(command.tenantId(), command.batchId());
        requireStatus(batch, MigrationBatchStatus.RECONCILING);
        if (batch.getVersion() != command.expectedBatchVersion()) {
            throw failure(BATCH_STATE_CONFLICT, "claimed batch version is stale");
        }
        MigrationBatchClassificationSummary summary = sourceMapper.selectClassificationSummary(
                new MigrationBatchIdQuery(command.tenantId(), command.batchId()));
        if (summary.unclassifiedCount() != 0 || summary.conflictingCount() != 0
                || summary.sourceCount() != command.expectedSourceCount()
                || summary.mappedCount() != command.expectedMappedCount()
                || summary.issueCount() != command.expectedIssueCount()
                || summary.retainedCount() != command.expectedRetainedCount()) {
            throw failure(COUNT_MISMATCH, "reconciliation classifications or counts do not match");
        }
        MigrationBatchCompletionUpdate update = new MigrationBatchCompletionUpdate(command.tenantId(),
                command.batchId(), command.expectedBatchVersion(), summary.sourceCount(), summary.mappedCount(),
                summary.issueCount(), summary.retainedCount(), command.ruleVersion());
        if (batchMapper.complete(update) != 1) {
            throw failure(BATCH_STATE_CONFLICT, "batch completion conflicted");
        }
        batch.setBatchStatus(MigrationBatchStatus.COMPLETED.name());
        batch.setMappedCount(summary.mappedCount());
        batch.setIssueCount(summary.issueCount());
        batch.setRetainedCount(summary.retainedCount());
        batch.setRuleVersion(command.ruleVersion());
        batch.setVersion(batch.getVersion() + 1);
        return batchFact(batch);
    }

    private MigrationIssueFact closeIssue(CloseMigrationIssueCommand command) {
        MigrationIssueDO discovered = issueMapper.selectById(command.issueId());
        if (discovered == null || !command.tenantId().equals(discovered.getTenantId())) {
            throw failure(ISSUE_NOT_FOUND, "issue is not visible");
        }
        MigrationBatchDO batch = requireBatchForUpdate(command.tenantId(), discovered.getBatchId());
        requireStatus(batch, MigrationBatchStatus.COMPLETED);
        MigrationIssueDO issue = issueMapper.selectByTenantAndIdForUpdate(
                new MigrationIssueIdQuery(command.tenantId(), command.issueId()));
        if (!MigrationIssueStatus.OPEN.name().equals(issue.getIssueStatus())) {
            throw failure(ISSUE_STATE_CONFLICT, "issue is already closed");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (issueMapper.close(new MigrationIssueCloseUpdate(command.tenantId(), command.issueId(),
                issue.getVersion(), command.resolverUserId(), command.ruleVersion(),
                command.targetResultJson(), now)) != 1) {
            throw failure(ISSUE_STATE_CONFLICT, "issue closure conflicted");
        }
        issue.setIssueStatus(MigrationIssueStatus.CLOSED.name());
        issue.setResolverUserId(command.resolverUserId());
        issue.setRuleVersion(command.ruleVersion());
        issue.setTargetResult(command.targetResultJson());
        issue.setResolvedAt(now);
        issue.setVersion(issue.getVersion() + 1);
        return issueFact(issue);
    }

    private MigrationBatchDO requireBatchForUpdate(Long tenantId, Long batchId) {
        MigrationBatchDO batch = batchMapper.selectByTenantAndIdForUpdate(new MigrationBatchIdQuery(tenantId, batchId));
        if (batch == null) {
            throw failure(BATCH_NOT_FOUND, "batch is not visible");
        }
        return batch;
    }

    private MigrationSourceRecordDO requireReconciliationSource(Long tenantId, Long batchId, Long sourceRecordId) {
        MigrationBatchDO batch = requireBatchForUpdate(tenantId, batchId);
        requireStatus(batch, MigrationBatchStatus.RECONCILING);
        MigrationSourceRecordDO source = sourceMapper.selectByBatchAndIdForUpdate(
                new MigrationSourceIdQuery(tenantId, batchId, sourceRecordId));
        if (source == null) {
            throw failure(SOURCE_NOT_FOUND, "source is not visible in the batch");
        }
        return source;
    }

    private void requireStatus(MigrationBatchDO batch, MigrationBatchStatus expected) {
        if (!expected.name().equals(batch.getBatchStatus())) {
            throw failure(BATCH_STATE_CONFLICT, "batch status does not allow this operation");
        }
    }

    private MigrationSourceRecordFact replaySource(MigrationSourceRecordDO source,
                                                    AppendMigrationSourceRecordCommand command) {
        if (source != null && Objects.equals(source.getSourceBusinessKey(), command.sourceBusinessKey())
                && source.getSourcePayload().equals(command.sourcePayloadJson())
                && source.getSourceChecksum().equals(command.sourceChecksum())
                && source.getExtractedAt().equals(command.extractedAt())) {
            return sourceFact(source, null);
        }
        throw failure(SOURCE_RECORD_CONFLICT, "source identity has different immutable facts");
    }

    private SourceReconciliationResult replayMapping(List<ExternalKeyMappingDO> rows,
                                                      AppendExternalMappingCommand command) {
        String existingType = rows.getFirst().getResultType();
        if (!existingType.equals(command.resultType().name())) {
            throw failure(SOURCE_ALREADY_CLASSIFIED, "source already has a different classification");
        }
        if (command.resultType() == SourceReconciliationType.MAPPED) {
            List<ExternalTargetMapping> existingTargets = rows.stream().map(this::targetFact)
                    .sorted(TARGET_ORDER).toList();
            if (!existingTargets.equals(command.targets())) {
                throw failure(MAPPING_CONFLICT, "mapping identity has different targets");
            }
        }
        return new SourceReconciliationResult(command.sourceRecordId(), command.resultType(),
                command.resultType() == SourceReconciliationType.RETAINED ? List.of()
                        : rows.stream().map(ExternalKeyMappingDO::getId).sorted().toList());
    }

    private ExternalKeyMappingDO mappingRow(AppendExternalMappingCommand command, ExternalTargetMapping target) {
        ExternalKeyMappingDO row = new ExternalKeyMappingDO();
        row.setTenantId(command.tenantId());
        row.setBatchId(command.batchId());
        row.setSourceRecordId(command.sourceRecordId());
        row.setResultType(command.resultType().name());
        if (target != null) {
            row.setTargetContext(target.targetContext());
            row.setTargetObjectType(target.targetObjectType());
            row.setTargetTable(target.targetTable());
            row.setTargetId(target.targetId());
            row.setTargetRole(target.targetRole());
            row.setTargetSequence(target.targetSequence());
        }
        return row;
    }

    private boolean sameIssue(MigrationIssueDO issue, AppendMigrationIssueCommand command) {
        return issue.getBatchId().equals(command.batchId())
                && issue.getIssueType().equals(command.issueType())
                && Objects.equals(issue.getRawBusinessKey(), command.rawBusinessKey())
                && issue.getCandidateTargetIds().equals(JsonUtils.toJsonString(command.candidateTargetIds()))
                && Objects.equals(issue.getRawPayload(), command.rawPayloadJson());
    }

    private <T> T execute(String scopeCode, Long tenantId, String key, String digest, Class<T> responseType,
                          java.util.function.Supplier<T> operation, String correlationId) {
        PlatformCommandExecutionApi.ExecutionResult<T> result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(tenantId, scopeCode, SYSTEM_ACTOR, key),
                digest, responseType, operation,
                response -> new PlatformCommandExecutionApi.SuccessFacts(scopeCode,
                        "MigrationEvidence", resourceKey(response), correlationId,
                        JsonUtils.toJsonString(response), null, null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw failure(IDEMPOTENCY_CONFLICT, "idempotency key has a different payload");
        }
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null) {
            throw failure(IDEMPOTENCY_IN_PROGRESS, "idempotency command is in progress");
        }
        return result.response();
    }

    private String resourceKey(Object response) {
        if (response instanceof MigrationBatchFact value) return String.valueOf(value.batchId());
        if (response instanceof MigrationIssueFact value) return String.valueOf(value.issueId());
        if (response instanceof SourceReconciliationResult value) return String.valueOf(value.sourceRecordId());
        throw failure(OWNER_DATA_CORRUPTED, "unsupported command result");
    }

    private MigrationBatchFact batchFact(MigrationBatchDO batch) {
        return new MigrationBatchFact(batch.getId(), batch.getTenantId(), batch.getOwnerContextCode(),
                batch.getPurposeCode(), batch.getReleaseId(), batch.getSourceSystem(), batch.getSourceTable(),
                MigrationBatchStatus.valueOf(batch.getBatchStatus()), batch.getSourceCount(),
                batch.getMappedCount(), batch.getIssueCount(), batch.getRetainedCount(),
                batch.getFailureCode() == null ? null : MigrationImportFailureCode.valueOf(batch.getFailureCode()),
                batch.getVersion(), batch.getCreateTime());
    }

    private MigrationSourceRecordFact sourceFact(MigrationSourceRecordDO source, String resultType) {
        return new MigrationSourceRecordFact(source.getId(), source.getTenantId(), source.getBatchId(),
                source.getSourceSystem(), source.getSourceTable(), source.getSourceRecordKey(),
                source.getSourceBusinessKey(), source.getSourcePayload(), source.getSourceChecksum(),
                source.getExtractedAt(), resultType);
    }

    private MigrationIssueFact issueFact(MigrationIssueDO issue) {
        return new MigrationIssueFact(issue.getId(), issue.getTenantId(), issue.getBatchId(),
                issue.getSourceRecordId(), issue.getIssueKey(), issue.getIssueType(),
                MigrationIssueStatus.valueOf(issue.getIssueStatus()), issue.getResolverUserId(),
                issue.getRuleVersion(), issue.getTargetResult(), issue.getResolvedAt());
    }

    private ExternalTargetMapping targetFact(ExternalKeyMappingDO row) {
        return new ExternalTargetMapping(row.getTargetContext(), row.getTargetObjectType(), row.getTargetTable(),
                row.getTargetId(), row.getTargetRole(), row.getTargetSequence());
    }

    private String digest(Object... values) {
        try {
            byte[] bytes = JsonUtils.toJsonString(Arrays.asList(values)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void initializeAuditFields(cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO row) {
        LocalDateTime now = LocalDateTime.now(clock);
        row.setCreator(SYSTEM_ACTOR_TEXT);
        row.setUpdater(SYSTEM_ACTOR_TEXT);
        row.setCreateTime(now);
        row.setUpdateTime(now);
    }

    private PlatformMigrationEvidenceException failure(PlatformMigrationEvidenceException.Code code,
                                                       String message) {
        return new PlatformMigrationEvidenceException(code, message);
    }
}
