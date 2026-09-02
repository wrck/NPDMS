package cn.iocoder.yudao.module.pms.platform.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;

/** PLT迁移证据Owner公开合同。 */
public interface PlatformMigrationEvidenceApi {

    MigrationBatchFact createImportBatch(CreateImportBatchCommand command);

    MigrationSourceRecordFact appendSourceRecord(AppendMigrationSourceRecordCommand command);

    MigrationBatchFact markStagedReady(MarkStagedReadyCommand command);

    MigrationBatchClaimResult claimStagedBatch(ClaimStagedBatchCommand command);

    MigrationSourceRecordPage pageSourceRecords(MigrationSourceRecordPageQuery query);

    SourceReconciliationResult appendExternalMapping(AppendExternalMappingCommand command);

    MigrationIssueFact appendMigrationIssue(AppendMigrationIssueCommand command);

    MigrationBatchFact completeReconciliation(CompleteReconciliationCommand command);

    MigrationIssueFact closeMigrationIssue(CloseMigrationIssueCommand command);
}
