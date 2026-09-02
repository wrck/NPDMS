package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionException;

import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.Code.*;

@Service
@RequiredArgsConstructor
public class PlatformMigrationEvidenceApiImpl implements PlatformMigrationEvidenceApi {

    private final PlatformMigrationEvidenceTransactionExecutor executor;

    @Override
    public MigrationBatchFact createImportBatch(CreateImportBatchCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.createImportBatch(command));
    }

    @Override
    public MigrationSourceRecordFact appendSourceRecord(AppendMigrationSourceRecordCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.appendSourceRecord(command));
    }

    @Override
    public MigrationBatchFact markStagedReady(MarkStagedReadyCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.markStagedReady(command));
    }

    @Override
    public MigrationBatchClaimResult claimStagedBatch(ClaimStagedBatchCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.claimStagedBatch(command));
    }

    @Override
    public MigrationSourceRecordPage pageSourceRecords(MigrationSourceRecordPageQuery query) {
        return invoke(query == null ? null : query.tenantId(), () -> executor.pageSourceRecords(query));
    }

    @Override
    public SourceReconciliationResult appendExternalMapping(AppendExternalMappingCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.appendExternalMapping(command));
    }

    @Override
    public MigrationIssueFact appendMigrationIssue(AppendMigrationIssueCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.appendMigrationIssue(command));
    }

    @Override
    public MigrationBatchFact completeReconciliation(CompleteReconciliationCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.completeReconciliation(command));
    }

    @Override
    public MigrationIssueFact closeMigrationIssue(CloseMigrationIssueCommand command) {
        return invoke(command == null ? null : command.tenantId(), () -> executor.closeMigrationIssue(command));
    }

    private <T> T invoke(Long tenantId, Supplier<T> operation) {
        if (tenantId == null) {
            throw new PlatformMigrationEvidenceException(INVALID_REQUEST, "request must not be null");
        }
        Long runtimeTenantId;
        try {
            runtimeTenantId = TenantContextHolder.getRequiredTenantId();
        } catch (RuntimeException ex) {
            throw new PlatformMigrationEvidenceException(TENANT_CONTEXT_MISMATCH,
                    "trusted tenant context is required", ex);
        }
        if (!tenantId.equals(runtimeTenantId)) {
            throw new PlatformMigrationEvidenceException(TENANT_CONTEXT_MISMATCH,
                    "explicit tenant does not match trusted context");
        }
        try {
            return operation.get();
        } catch (PlatformMigrationEvidenceException ex) {
            throw ex;
        } catch (IllegalTransactionStateException ex) {
            throw new PlatformMigrationEvidenceException(CALLER_TRANSACTION_REQUIRED,
                    "caller-owned transaction is required", ex);
        } catch (DataAccessException | TransactionException ex) {
            throw new PlatformMigrationEvidenceException(PROVIDER_UNAVAILABLE,
                    "migration evidence provider is unavailable", ex);
        }
    }
}
