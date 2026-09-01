package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.LegacyCutoverPlanReconciliationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.query.LegacyCutoverPlanTargetQuery;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 只消费PLT冻结来源的pms_cut_plan前向核对；正常生产不查询旧表。 */
public class LegacyCutoverPlanReconciliationService {

    static final String OWNER_CONTEXT = "CUT";
    static final String PURPOSE = "CUTOVER_PLAN_CURRENT_FORWARD";
    static final String SOURCE_SYSTEM = "NPDMS_LEGACY";
    static final String SOURCE_TABLE = "pms_cut_plan";
    private static final int PAGE_SIZE = 500;
    private static final Snowflake ID_GENERATOR = IdUtil.getSnowflake();

    private final PlatformMigrationEvidenceApi migrationApi;
    private final LegacyCutoverTaskMappingPort taskMappingPort;
    private final LegacyCutoverPlanReconciliationMapper reconciliationMapper;
    private final CutoverPlanRevisionMapper planMapper;
    private final CutoverPlanStepMapper stepMapper;
    private final LegacyCutoverPlanRowConverter converter;

    public LegacyCutoverPlanReconciliationService(PlatformMigrationEvidenceApi migrationApi,
                                                   LegacyCutoverTaskMappingPort taskMappingPort,
                                                   LegacyCutoverPlanReconciliationMapper reconciliationMapper,
                                                   CutoverPlanRevisionMapper planMapper,
                                                   CutoverPlanStepMapper stepMapper,
                                                   LegacyCutoverPlanRowConverter converter) {
        this.migrationApi = migrationApi;
        this.taskMappingPort = taskMappingPort;
        this.reconciliationMapper = reconciliationMapper;
        this.planMapper = planMapper;
        this.stepMapper = stepMapper;
        this.converter = converter;
    }

    @Transactional(rollbackFor = Exception.class)
    public LegacyCutoverPlanReconciliationResult reconcileNext(Long tenantId, String correlationId) {
        requirePositive(tenantId, "tenantId");
        requireText(correlationId, 128, "correlationId");
        var claim = migrationApi.claimStagedBatch(new ClaimStagedBatchCommand(tenantId, OWNER_CONTEXT, PURPOSE,
                List.of(SOURCE_SYSTEM), List.of(SOURCE_TABLE), correlationId));
        if (!claim.claimed()) {
            return LegacyCutoverPlanReconciliationResult.empty();
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
            throw new LegacyCutoverPlanMigrationException("PLT来源数量与冻结批次不一致");
        }
        migrationApi.completeReconciliation(new CompleteReconciliationCommand(tenantId, batch.batchId(),
                batch.version(), batch.sourceCount(), mapped, issues, retained,
                LegacyCutoverPlanRowConverter.MAPPING_VERSION, "CUT-PLAN-COMPLETE:" + batch.batchId(), correlationId));
        return new LegacyCutoverPlanReconciliationResult(true, batch.batchId(), mapped, issues, retained);
    }

    private Outcome migrateOne(Long tenantId, Long batchId, MigrationSourceRecordFact source, String correlationId) {
        requireSource(tenantId, batchId, source);
        LegacyCutoverPlanRowConverter.ConvertedLegacyPlan converted;
        try {
            converted = converter.convert(tenantId, source);
        } catch (LegacyCutoverPlanMigrationException exception) {
            appendIssue(tenantId, batchId, source, "SOURCE_DATA_INVALID", correlationId, List.of());
            return Outcome.ISSUE;
        }
        if (converted.deleted()) {
            appendRetained(tenantId, batchId, source, correlationId);
            return Outcome.RETAINED;
        }
        Long targetTaskId = taskMappingPort.resolveTargetTaskId(tenantId, converted.legacyTaskId());
        CutoverTaskDO target = targetTaskId == null ? null : reconciliationMapper.selectQualifiedTargetForUpdate(
                LegacyCutoverPlanTargetQuery.target(tenantId, targetTaskId));
        if (target == null) {
            appendIssue(tenantId, batchId, source, "TARGET_TASK_MAPPING_MISSING", correlationId, List.of());
            return Outcome.ISSUE;
        }
        Long conflictId = reconciliationMapper.selectIdentityConflictForUpdate(
                LegacyCutoverPlanTargetQuery.conflict(tenantId, target.getId(), converted.legacyPlanId()));
        if (conflictId != null && conflictId > 0) {
            appendIssue(tenantId, batchId, source, "TARGET_IDENTITY_CONFLICT", correlationId, List.of(conflictId));
            return Outcome.ISSUE;
        }

        long planId = ID_GENERATOR.nextId();
        CutoverPlanRevisionDO plan = plan(tenantId, target.getId(), planId, converted);
        if (planMapper.insert(plan) != 1) {
            throw new LegacyCutoverPlanMigrationException("旧割接方案revision创建失败");
        }
        List<ExternalTargetMapping> mappings = new ArrayList<>();
        mappings.add(new ExternalTargetMapping("CUT", "CutoverPlanRevision", "cut_plan_revision",
                planId, "PRIMARY", 0));
        int sequence = 1;
        for (LegacyCutoverPlanRowConverter.LegacyStep projection : converted.steps()) {
            long stepId = ID_GENERATOR.nextId();
            if (stepMapper.insert(step(tenantId, planId, stepId, converted, projection)) != 1) {
                throw new LegacyCutoverPlanMigrationException("旧割接方案步骤创建失败");
            }
            mappings.add(new ExternalTargetMapping("CUT", "CutoverPlanStep", "cut_step",
                    stepId, "STEP", sequence++));
        }
        migrationApi.appendExternalMapping(new AppendExternalMappingCommand(tenantId, batchId,
                source.sourceRecordId(), SourceReconciliationType.MAPPED, mappings,
                idempotencyKey("MAP", batchId, source.sourceRecordId()), correlationId));
        return Outcome.MAPPED;
    }

    private static CutoverPlanRevisionDO plan(Long tenantId, Long taskId, Long planId,
                                               LegacyCutoverPlanRowConverter.ConvertedLegacyPlan source) {
        CutoverPlanRevisionDO row = new CutoverPlanRevisionDO();
        row.setId(planId);
        row.setTenantId(tenantId);
        row.setCutoverTaskId(taskId);
        row.setRevisionNo(1);
        row.setOriginCode("LEGACY_FORWARD");
        row.setSourceSnapshot(source.sourceSnapshot());
        row.setLegacyPlanId(source.legacyPlanId());
        row.setLegacyStatusRaw(source.legacyStatus());
        row.setLegacySourceVersion(source.legacyVersion());
        row.setLegacyMappingVersion(LegacyCutoverPlanRowConverter.MAPPING_VERSION);
        row.setVersion(0);
        row.setCreator(source.creator());
        row.setCreateTime(source.createTime());
        row.setUpdater(source.updater());
        row.setUpdateTime(source.updateTime());
        row.setDeleted(false);
        return row;
    }

    private static CutoverPlanStepDO step(Long tenantId, Long planId, Long stepId,
                                          LegacyCutoverPlanRowConverter.ConvertedLegacyPlan source,
                                          LegacyCutoverPlanRowConverter.LegacyStep projection) {
        CutoverPlanStepDO row = new CutoverPlanStepDO();
        row.setId(stepId);
        row.setTenantId(tenantId);
        row.setPlanRevisionId(planId);
        row.setSectionCode(projection.sectionCode());
        row.setStepNo(1);
        row.setContent(projection.content());
        row.setVersion(0);
        row.setCreator(source.creator());
        row.setCreateTime(source.createTime());
        row.setUpdater(source.updater());
        row.setUpdateTime(source.updateTime());
        row.setDeleted(false);
        return row;
    }

    private void appendRetained(Long tenantId, Long batchId, MigrationSourceRecordFact source,
                                String correlationId) {
        migrationApi.appendExternalMapping(new AppendExternalMappingCommand(tenantId, batchId,
                source.sourceRecordId(), SourceReconciliationType.RETAINED, List.of(),
                idempotencyKey("RETAIN_LEGACY_DELETED", batchId, source.sourceRecordId()), correlationId));
    }

    private void appendIssue(Long tenantId, Long batchId, MigrationSourceRecordFact source, String issueType,
                             String correlationId, List<Long> candidateTargetIds) {
        String key = idempotencyKey(issueType, batchId, source.sourceRecordId());
        migrationApi.appendMigrationIssue(new AppendMigrationIssueCommand(tenantId, batchId,
                source.sourceRecordId(), key, issueType, source.sourceBusinessKey(), candidateTargetIds,
                source.sourcePayloadJson(), key, correlationId));
    }

    private static String idempotencyKey(String disposition, Long batchId, Long sourceRecordId) {
        return "CUT:PLAN:" + disposition + ":" + batchId + ":" + sourceRecordId + ":"
                + LegacyCutoverPlanRowConverter.MAPPING_VERSION;
    }

    private static void requireBatch(Long tenantId, MigrationBatchFact batch) {
        if (batch == null || !tenantId.equals(batch.tenantId()) || !OWNER_CONTEXT.equals(batch.ownerContextCode())
                || !PURPOSE.equals(batch.purposeCode()) || !SOURCE_SYSTEM.equals(batch.sourceSystem())
                || !SOURCE_TABLE.equals(batch.sourceTable())) {
            throw new LegacyCutoverPlanMigrationException("PLT迁移批次身份不匹配");
        }
    }

    private static void requireSource(Long tenantId, Long batchId, MigrationSourceRecordFact source) {
        if (source == null || !tenantId.equals(source.tenantId()) || !batchId.equals(source.batchId())
                || !SOURCE_SYSTEM.equals(source.sourceSystem()) || !SOURCE_TABLE.equals(source.sourceTable())
                || source.resultType() != null) {
            throw new LegacyCutoverPlanMigrationException("PLT迁移来源身份不匹配");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new LegacyCutoverPlanMigrationException(field + "必须为正整数");
        }
    }

    private static void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > maxLength) {
            throw new LegacyCutoverPlanMigrationException(field + "格式非法");
        }
    }

    private enum Outcome {
        MAPPED, ISSUE, RETAINED
    }
}
