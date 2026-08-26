package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionTerminationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionStorageReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadTerminateCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_STATE_INVALID;

@Service
public class FileUploadCompensationService {

    private final FileUploadSessionMapper sessionMapper;
    private final FileVersionMapper versionMapper;
    private final FileStorageReceiptApi storageReceiptApi;
    private final TransactionTemplate transactions;

    public FileUploadCompensationService(FileUploadSessionMapper sessionMapper,
                                         FileVersionMapper versionMapper,
                                         FileStorageReceiptApi storageReceiptApi,
                                         PlatformTransactionManager transactionManager) {
        this.sessionMapper = sessionMapper;
        this.versionMapper = versionMapper;
        this.storageReceiptApi = storageReceiptApi;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void terminate(FileUploadTerminateCommand command) {
        validate(command);
        String operationId = transactions.execute(status -> terminateSession(command));
        FileStorageReceipt receipt = storageReceiptApi.inspect(operationId);
        if (receipt == null) {
            return;
        }
        Boolean safeToDelete = transactions.execute(status -> verifyNoCommittedVersion(command, receipt));
        if (!Boolean.TRUE.equals(safeToDelete)) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        storageReceiptApi.delete(operationId);
    }

    private String terminateSession(FileUploadTerminateCommand command) {
        FileUploadSessionDO session = lockSession(command);
        if ("FAILED_FINAL".equals(session.getStatusCode())) {
            return session.getStorageOperationId();
        }
        if (sessionMapper.terminateIfRetryable(new FileUploadSessionTerminationUpdate(
                command.tenantId(), command.sessionId(), session.getVersion(),
                command.actorUserId(), command.reasonCode().trim())) != 1) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        return session.getStorageOperationId();
    }

    private boolean verifyNoCommittedVersion(FileUploadTerminateCommand command, FileStorageReceipt receipt) {
        FileUploadSessionDO session = lockSession(command);
        if (!"FAILED_FINAL".equals(session.getStatusCode())
                || !session.getStorageOperationId().equals(receipt.storageOperationId())) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        return versionMapper.selectByInfraFileIdForUpdate(new FileVersionStorageReferenceQuery(
                command.tenantId(), receipt.infraFileId())) == null;
    }

    private FileUploadSessionDO lockSession(FileUploadTerminateCommand command) {
        FileUploadSessionDO session = sessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(command.tenantId(), command.sessionId()));
        if (session == null) {
            throw exception(FILE_UPLOAD_SESSION_NOT_FOUND);
        }
        return session;
    }

    private void validate(FileUploadTerminateCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.sessionId() == null || command.sessionId() <= 0
                || command.reasonCode() == null || command.reasonCode().isBlank()
                || command.reasonCode().trim().length() > 64) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }
}
