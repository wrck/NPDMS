package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendMigrationSourceRecordsCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ClaimStagedBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CloseMigrationIssueCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CreateImportBatchCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchClaimResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationIssueFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationMappingPageResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.SourceReconciliationResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MarkStagedReadyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 迁移工具台写路径：迁移证据Owner合同要求调用方持有事务，
 * 控制台作为调用方在此统一开启事务后转调证据层。
 */
@Service
@RequiredArgsConstructor
public class MigrationConsoleService {

    private final PlatformMigrationEvidenceApi evidenceApi;

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchFact createBatch(CreateImportBatchCommand command) {
        return evidenceApi.createImportBatch(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public java.util.List<MigrationSourceRecordFact> appendSourceRecords(AppendMigrationSourceRecordsCommand command) {
        return evidenceApi.appendSourceRecords(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchFact markStagedReady(MarkStagedReadyCommand command) {
        return evidenceApi.markStagedReady(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchClaimResult claim(ClaimStagedBatchCommand command) {
        return evidenceApi.claimStagedBatch(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public SourceReconciliationResult appendMapping(AppendExternalMappingCommand command) {
        return evidenceApi.appendExternalMapping(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationMappingPageResult appendMappings(
            cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingsCommand command) {
        return evidenceApi.appendExternalMappings(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationIssueFact appendIssue(AppendMigrationIssueCommand command) {
        return evidenceApi.appendMigrationIssue(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationBatchFact completeReconciliation(CompleteReconciliationCommand command) {
        return evidenceApi.completeReconciliation(command);
    }

    @Transactional(rollbackFor = Exception.class)
    public MigrationIssueFact closeIssue(CloseMigrationIssueCommand command) {
        return evidenceApi.closeMigrationIssue(command);
    }
}
