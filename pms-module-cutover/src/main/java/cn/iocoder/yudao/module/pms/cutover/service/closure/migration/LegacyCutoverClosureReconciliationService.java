package cn.iocoder.yudao.module.pms.cutover.service.closure.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ClaimStagedBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPage;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.SourceReconciliationType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Classifies frozen pms_cut_execution rows; production code never reads the legacy table. */
public class LegacyCutoverClosureReconciliationService {

    static final String OWNER_CONTEXT = "CUT";
    static final String PURPOSE = "CUTOVER_CLOSURE_CURRENT_FORWARD";
    static final String SOURCE_SYSTEM = "NPDMS_LEGACY";
    static final String SOURCE_TABLE = "pms_cut_execution";
    private static final int PAGE_SIZE = 500;

    private final PlatformMigrationEvidenceApi migrationApi;
    private final LegacyCutoverClosureRowClassifier classifier;

    public LegacyCutoverClosureReconciliationService(PlatformMigrationEvidenceApi migrationApi,
                                                       LegacyCutoverClosureRowClassifier classifier) {
        this.migrationApi = migrationApi;
        this.classifier = classifier;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result reconcileNext(Long tenantId, String correlationId) {
        requirePositive(tenantId, "tenantId");
        requireText(correlationId, 128, "correlationId");
        var claim = migrationApi.claimStagedBatch(new ClaimStagedBatchCommand(tenantId, OWNER_CONTEXT, PURPOSE,
                List.of(SOURCE_SYSTEM), List.of(SOURCE_TABLE), correlationId));
        if (!claim.claimed()) return Result.empty();
        MigrationBatchFact batch = claim.batch();
        requireBatch(tenantId, batch);

        long retained = 0;
        long issues = 0;
        Long cursor = null;
        do {
            MigrationSourceRecordPage page = migrationApi.pageSourceRecords(
                    new MigrationSourceRecordPageQuery(tenantId, batch.batchId(), cursor, PAGE_SIZE));
            for (MigrationSourceRecordFact source : page.records()) {
                requireSource(tenantId, batch.batchId(), source);
                if (classifier.classify(tenantId, source)
                        == LegacyCutoverClosureRowClassifier.Disposition.RETAINED) {
                    appendRetained(tenantId, batch.batchId(), source, correlationId);
                    retained++;
                } else {
                    appendIssue(tenantId, batch.batchId(), source, correlationId);
                    issues++;
                }
            }
            cursor = page.nextAfterSourceRecordId();
        } while (cursor != null);

        if (batch.sourceCount() != retained + issues) {
            throw new IllegalStateException("PLT来源数量与冻结批次不一致");
        }
        migrationApi.completeReconciliation(new CompleteReconciliationCommand(tenantId, batch.batchId(),
                batch.version(), batch.sourceCount(), 0, issues, retained,
                LegacyCutoverClosureRowClassifier.RULE_VERSION,
                "CUT:CLOSURE:COMPLETE:" + batch.batchId(), correlationId));
        return new Result(true, batch.batchId(), issues, retained);
    }

    private void appendRetained(Long tenantId, Long batchId, MigrationSourceRecordFact source,
                                String correlationId) {
        migrationApi.appendExternalMapping(new AppendExternalMappingCommand(tenantId, batchId,
                source.sourceRecordId(), SourceReconciliationType.RETAINED, List.of(),
                key("RETAIN", batchId, source.sourceRecordId()), correlationId));
    }

    private void appendIssue(Long tenantId, Long batchId, MigrationSourceRecordFact source,
                             String correlationId) {
        String key = key("FCUT006_SOURCE_RECORD_INVALID", batchId, source.sourceRecordId());
        migrationApi.appendMigrationIssue(new AppendMigrationIssueCommand(tenantId, batchId,
                source.sourceRecordId(), key, "FCUT006_SOURCE_RECORD_INVALID", source.sourceBusinessKey(),
                List.of(), source.sourcePayloadJson(), key, correlationId));
    }

    private static String key(String disposition, Long batchId, Long sourceRecordId) {
        return "CUT:CLOSURE:" + disposition + ":" + batchId + ":" + sourceRecordId;
    }

    private static void requireBatch(Long tenantId, MigrationBatchFact batch) {
        if (batch == null || !tenantId.equals(batch.tenantId()) || !OWNER_CONTEXT.equals(batch.ownerContextCode())
                || !PURPOSE.equals(batch.purposeCode()) || !SOURCE_SYSTEM.equals(batch.sourceSystem())
                || !SOURCE_TABLE.equals(batch.sourceTable())) {
            throw new IllegalStateException("PLT迁移批次身份不匹配");
        }
    }

    private static void requireSource(Long tenantId, Long batchId, MigrationSourceRecordFact source) {
        if (source == null || !tenantId.equals(source.tenantId()) || !batchId.equals(source.batchId())
                || !SOURCE_SYSTEM.equals(source.sourceSystem()) || !SOURCE_TABLE.equals(source.sourceTable())
                || source.resultType() != null) {
            throw new IllegalStateException("PLT迁移来源身份不匹配");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + "必须为正整数");
    }

    private static void requireText(String value, int max, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + "格式非法");
        }
    }

    public record Result(boolean claimed, Long batchId, long issues, long retained) {
        public static Result empty() { return new Result(false, null, 0, 0); }
    }
}
