package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.migration.LegacyCutoverReconciliationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.migration.query.LegacyCutoverReconciliationQuery;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ClaimStagedBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPage;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.SourceReconciliationType;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** pms_cut_task到cut_task的受控只读投影核对；不注册自动Job。 */
@Service
public class LegacyCutoverReconciliationService {

    static final String OWNER_CONTEXT = "CUT";
    static final String PURPOSE = "CUTOVER_TASK_CURRENT_FORWARD";
    static final String SOURCE_SYSTEM = "NPDMS_LEGACY";
    static final String SOURCE_TABLE = "pms_cut_task";
    private static final int PAGE_SIZE = 500;
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();

    private final PlatformMigrationEvidenceApi migrationApi;
    private final ProjectOrganizationFactApi projectApi;
    private final LegacyCutoverReconciliationMapper reconciliationMapper;
    private final CutoverTaskMapper targetMapper;
    private final LegacyCutoverRowConverter converter;

    public LegacyCutoverReconciliationService(PlatformMigrationEvidenceApi migrationApi,
                                               ProjectOrganizationFactApi projectApi,
                                               LegacyCutoverReconciliationMapper reconciliationMapper,
                                               CutoverTaskMapper targetMapper,
                                               LegacyCutoverRowConverter converter) {
        this.migrationApi = migrationApi;
        this.projectApi = projectApi;
        this.reconciliationMapper = reconciliationMapper;
        this.targetMapper = targetMapper;
        this.converter = converter;
    }

    @Transactional(rollbackFor = Exception.class)
    public LegacyCutoverReconciliationResult reconcileNext(Long tenantId, String correlationId) {
        requirePositive(tenantId, "tenantId");
        requireText(correlationId, 128, "correlationId");
        var claim = migrationApi.claimStagedBatch(new ClaimStagedBatchCommand(tenantId, OWNER_CONTEXT, PURPOSE,
                List.of(SOURCE_SYSTEM), List.of(SOURCE_TABLE), correlationId));
        if (!claim.claimed()) {
            return LegacyCutoverReconciliationResult.empty();
        }
        MigrationBatchFact batch = claim.batch();
        requireBatch(tenantId, batch);
        long mapped = 0;
        long issues = 0;
        long retained = 0;
        Long cursor = null;
        do {
            MigrationSourceRecordPage page = migrationApi.pageSourceRecords(
                    new MigrationSourceRecordPageQuery(tenantId, batch.batchId(), cursor, PAGE_SIZE));
            for (MigrationSourceRecordFact source : page.records()) {
                Outcome outcome = migrateOne(tenantId, batch.batchId(), source, correlationId);
                mapped += outcome == Outcome.MAPPED ? 1 : 0;
                issues += outcome == Outcome.ISSUE ? 1 : 0;
                retained += outcome == Outcome.RETAINED ? 1 : 0;
            }
            cursor = page.nextAfterSourceRecordId();
        } while (cursor != null);
        if (mapped + issues + retained != batch.sourceCount()) {
            throw new LegacyCutoverMigrationException("PLT来源数量与冻结批次不一致");
        }
        migrationApi.completeReconciliation(new CompleteReconciliationCommand(tenantId, batch.batchId(),
                batch.version(), batch.sourceCount(), mapped, issues, retained,
                LegacyCutoverRowConverter.MAPPING_VERSION,
                "CUT-COMPLETE:" + batch.batchId(), correlationId));
        return new LegacyCutoverReconciliationResult(true, batch.batchId(), mapped, issues, retained);
    }

    private Outcome migrateOne(Long tenantId, Long batchId, MigrationSourceRecordFact source, String correlationId) {
        requireSource(tenantId, batchId, source);
        Long legacyTaskId;
        try {
            legacyTaskId = parsePositiveId(source.sourcePk());
        } catch (LegacyCutoverMigrationException exception) {
            appendIssue(tenantId, batchId, source, "SOURCE_DATA_INVALID", correlationId);
            return Outcome.ISSUE;
        }
        CutTaskDO legacy = reconciliationMapper.selectSourceForUpdate(
                LegacyCutoverReconciliationQuery.source(tenantId, legacyTaskId));
        if (legacy == null) {
            appendIssue(tenantId, batchId, source, "SOURCE_DATA_INVALID", correlationId);
            return Outcome.ISSUE;
        }
        if (Boolean.TRUE.equals(legacy.getDeleted())) {
            appendRetained(tenantId, batchId, source, correlationId);
            return Outcome.RETAINED;
        }
        CutoverTaskDO target;
        try {
            target = converter.convert(ID_GENERATOR.nextId(), tenantId, legacy);
        } catch (LegacyCutoverMigrationException exception) {
            appendIssue(tenantId, batchId, source, "SOURCE_DATA_INVALID", correlationId);
            return Outcome.ISSUE;
        }
        ProjectOrganizationFact inspected = projectApi.inspect(new ProjectOrganizationFactQuery(legacy.getProjectId()));
        if (inspected == null || !legacy.getProjectId().equals(inspected.projectId())) {
            appendIssue(tenantId, batchId, source, "OWNER_FACT_MISMATCH", correlationId);
            return Outcome.ISSUE;
        }
        ProjectOrganizationFact locked = projectApi.lockAndRevalidate(
                new ProjectOrganizationFactRevalidationQuery(inspected.projectId(), inspected.projectVersion()));
        if (!inspected.equals(locked)) {
            appendIssue(tenantId, batchId, source, "OWNER_FACT_MISMATCH", correlationId);
            return Outcome.ISSUE;
        }
        long conflicts = reconciliationMapper.countTargetIdentityConflicts(
                LegacyCutoverReconciliationQuery.target(tenantId, target.getLegacyTaskId(),
                        target.getProjectId(), target.getTaskNo()));
        if (conflicts != 0) {
            appendIssue(tenantId, batchId, source, "TARGET_IDENTITY_CONFLICT", correlationId);
            return Outcome.ISSUE;
        }
        if (targetMapper.insert(target) != 1) {
            throw new LegacyCutoverMigrationException("旧割接任务只读投影创建失败");
        }
        migrationApi.appendExternalMapping(new AppendExternalMappingCommand(tenantId, batchId,
                source.sourceRecordId(), SourceReconciliationType.MAPPED,
                List.of(new ExternalTargetMapping("CUT", "CutoverTask", "cut_task", target.getId(),
                        "PRIMARY", 0)), "CUT-MAP:" + batchId + ":" + source.sourceRecordId() + ":"
                        + LegacyCutoverRowConverter.MAPPING_VERSION, correlationId));
        return Outcome.MAPPED;
    }

    private void appendRetained(Long tenantId, Long batchId, MigrationSourceRecordFact source,
                                String correlationId) {
        migrationApi.appendExternalMapping(new AppendExternalMappingCommand(tenantId, batchId,
                source.sourceRecordId(), SourceReconciliationType.RETAINED, List.of(),
                idempotencyKey("RETAIN_LEGACY_DELETED", batchId, source.sourceRecordId()), correlationId));
    }

    private void appendIssue(Long tenantId, Long batchId, MigrationSourceRecordFact source,
                             String issueType, String correlationId) {
        String key = idempotencyKey(issueType, batchId, source.sourceRecordId());
        migrationApi.appendMigrationIssue(new AppendMigrationIssueCommand(tenantId, batchId,
                source.sourceRecordId(), key, issueType, source.sourceBusinessKey(), List.of(),
                source.sourcePayloadJson(), key, correlationId));
    }

    private static String idempotencyKey(String disposition, Long batchId, Long sourceRecordId) {
        return "CUT:" + disposition + ":" + batchId + ":" + sourceRecordId + ":"
                + LegacyCutoverRowConverter.MAPPING_VERSION;
    }

    private static void requireBatch(Long tenantId, MigrationBatchFact batch) {
        if (batch == null || !tenantId.equals(batch.tenantId()) || !OWNER_CONTEXT.equals(batch.ownerContextCode())
                || !PURPOSE.equals(batch.purposeCode()) || !SOURCE_SYSTEM.equals(batch.sourceSystem())
                || !SOURCE_TABLE.equals(batch.sourceTable())) {
            throw new LegacyCutoverMigrationException("PLT迁移批次身份不匹配");
        }
    }

    private static void requireSource(Long tenantId, Long batchId, MigrationSourceRecordFact source) {
        if (source == null || !tenantId.equals(source.tenantId()) || !batchId.equals(source.batchId())
                || !SOURCE_SYSTEM.equals(source.sourceSystem()) || !SOURCE_TABLE.equals(source.sourceTable())
                || source.resultType() != null) {
            throw new LegacyCutoverMigrationException("PLT迁移来源身份不匹配");
        }
    }

    private static Long parsePositiveId(String sourcePk) {
        try {
            Long value = Long.valueOf(sourcePk);
            requirePositive(value, "sourcePk");
            return value;
        } catch (NumberFormatException exception) {
            throw new LegacyCutoverMigrationException("sourcePk不是有效旧任务ID");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new LegacyCutoverMigrationException(field + "必须为正整数");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new LegacyCutoverMigrationException(field + "格式非法");
        }
    }

    private enum Outcome {
        MAPPED,
        ISSUE,
        RETAINED
    }
}
