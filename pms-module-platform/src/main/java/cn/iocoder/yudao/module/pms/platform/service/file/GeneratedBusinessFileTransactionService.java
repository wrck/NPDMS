package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionCompletionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionStorageBindingUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionValidationUpdate;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFilePersistence;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFileReservation;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_STORAGE_RECEIPT_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_STATE_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.PLATFORM_COMMAND_KEY_CONFLICT;

@Service
public class GeneratedBusinessFileTransactionService {

    private final FileUploadSessionMapper sessionMapper;
    private final FileStorageReceiptApi storageReceiptApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Duration sessionTtl;

    public GeneratedBusinessFileTransactionService(
            FileUploadSessionMapper sessionMapper,
            FileStorageReceiptApi storageReceiptApi,
            PlatformCommandExecutionApi commandExecutionApi,
            @Value("${pms.file.upload.session-ttl:PT15M}") Duration sessionTtl) {
        this.sessionMapper = sessionMapper;
        this.storageReceiptApi = storageReceiptApi;
        this.commandExecutionApi = commandExecutionApi;
        this.sessionTtl = sessionTtl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public GeneratedBusinessFileReservation reserve(GeneratedBusinessFileCommand command,
                                                     ValidatedFileContent content,
                                                     String requestDigest) {
        Long sessionId = IdWorker.getId();
        Long artifactId = IdWorker.getId();
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "PLT:FILE:GENERATED_RESERVE", command.actorUserId(), command.operationId()),
                requestDigest, GeneratedBusinessFileReservation.class,
                () -> createSession(command, content, sessionId, artifactId),
                reservation -> new PlatformCommandExecutionApi.SuccessFacts(
                        "FILE_GENERATED_SESSION_RESERVED", "FileUploadSession",
                        String.valueOf(reservation.sessionId()), command.operationId(),
                        JsonUtils.toJsonString(Map.of(
                                "sessionId", reservation.sessionId(),
                                "artifactId", reservation.artifactId(),
                                "resultId", command.resultId())),
                        null, null, List.of()));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PLATFORM_COMMAND_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw exception(PLATFORM_COMMAND_IN_PROGRESS);
        }
        return execution.response();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public FileStorageReceipt store(GeneratedBusinessFileCommand command,
                                    GeneratedBusinessFileReservation reservation,
                                    ValidatedFileContent content) {
        FileUploadSessionDO session = lockSession(command.tenantId(), reservation.sessionId());
        requireSession(command, reservation, content, session);
        FileStorageReceipt receipt = storageReceiptApi.inspect(session.getStorageOperationId());
        if (receipt == null) {
            receipt = storageReceiptApi.store(new FileStorageStoreCommand(
                    session.getStorageOperationId(), content.content(), command.fileName(), content.mediaType()));
        }
        requireReceipt(session, content, receipt);
        if (session.getRegisteredInfraFileId() == null) {
            if (sessionMapper.bindStorageReceiptIfInitialized(new FileUploadSessionStorageBindingUpdate(
                    command.tenantId(), session.getId(), session.getVersion(), content.sha256(),
                    receipt.infraFileId())) != 1) {
                throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
            }
        } else if (!session.getRegisteredInfraFileId().equals(receipt.infraFileId())
                || !content.sha256().equals(session.getActualSha256())) {
            throw exception(FILE_STORAGE_RECEIPT_CONFLICT);
        }
        return receipt;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void completeSession(Long tenantId, Long sessionId,
                                GeneratedBusinessFilePersistence persistence,
                                FileStorageReceipt receipt) {
        FileUploadSessionDO session = lockSession(tenantId, sessionId);
        if ("COMPLETED".equals(session.getStatusCode())) {
            if (!persistence.fact().artifactId().equals(session.getArtifactId())
                    || !persistence.referenceId().equals(session.getReferenceId())
                    || !receipt.infraFileId().equals(session.getRegisteredInfraFileId())) {
                throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
            }
            return;
        }
        if (!"INITIALIZED".equals(session.getStatusCode())
                || sessionMapper.beginValidationIfInitialized(new FileUploadSessionValidationUpdate(
                tenantId, sessionId, session.getVersion())) != 1
                || sessionMapper.completeIfValidating(new FileUploadSessionCompletionUpdate(
                tenantId, sessionId, session.getVersion() + 1, persistence.fact().artifactId(),
                persistence.referenceId(), persistence.fact().sha256(), persistence.fact().versionNo(),
                receipt.infraFileId(), LocalDateTime.now())) != 1) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
    }

    private GeneratedBusinessFileReservation createSession(GeneratedBusinessFileCommand command,
                                                             ValidatedFileContent content,
                                                             Long sessionId, Long artifactId) {
        FileUploadSessionDO row = new FileUploadSessionDO();
        row.setId(sessionId);
        row.setModeCode("CREATE_ARTIFACT");
        row.setOwnerContext(command.ownerContext());
        row.setObjectType(command.objectType());
        row.setObjectId(String.valueOf(command.resultId()));
        row.setPurposeCode(command.purposeCode());
        row.setReferenceKey("satisfaction-result-" + command.resultId());
        row.setFileName(command.fileName());
        row.setCategoryCode(command.purposeCode());
        row.setDeclaredSizeBytes(content.sizeBytes());
        row.setDeclaredMediaType(content.mediaType());
        row.setStorageOperationId(String.valueOf(sessionId));
        row.setStatusCode("INITIALIZED");
        row.setScopeVersion(command.scopeVersion());
        row.setExpiresAt(LocalDateTime.now().plus(sessionTtl));
        row.setVersion(0);
        row.setArtifactId(artifactId);
        row.setClientSha256(content.sha256());
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        row.setTenantId(command.tenantId());
        if (sessionMapper.insert(row) != 1) {
            throw new IllegalStateException("GENERATED_FILE_SESSION_CREATE_FAILED");
        }
        return new GeneratedBusinessFileReservation(sessionId, artifactId, String.valueOf(sessionId));
    }

    private FileUploadSessionDO lockSession(Long tenantId, Long sessionId) {
        FileUploadSessionDO session = sessionMapper.selectForUpdate(new FileUploadSessionLockQuery(tenantId, sessionId));
        if (session == null) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        return session;
    }

    private void requireSession(GeneratedBusinessFileCommand command,
                                GeneratedBusinessFileReservation reservation,
                                ValidatedFileContent content, FileUploadSessionDO session) {
        if (!"INITIALIZED".equals(session.getStatusCode())
                || !reservation.artifactId().equals(session.getArtifactId())
                || !reservation.storageOperationId().equals(session.getStorageOperationId())
                || !command.ownerContext().equals(session.getOwnerContext())
                || !command.objectType().equals(session.getObjectType())
                || !String.valueOf(command.resultId()).equals(session.getObjectId())
                || !command.purposeCode().equals(session.getPurposeCode())
                || !command.scopeVersion().equals(session.getScopeVersion())
                || !content.sha256().equals(session.getClientSha256())) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
    }

    private void requireReceipt(FileUploadSessionDO session, ValidatedFileContent content,
                                FileStorageReceipt receipt) {
        if (receipt == null || receipt.infraFileId() == null || receipt.infraFileId() <= 0
                || !session.getStorageOperationId().equals(receipt.storageOperationId())
                || !session.getFileName().equals(receipt.name())
                || !content.mediaType().equals(receipt.mediaType())
                || content.sizeBytes() != receipt.sizeBytes()) {
            throw exception(FILE_STORAGE_RECEIPT_CONFLICT);
        }
    }
}
