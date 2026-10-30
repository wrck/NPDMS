package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionCallbackApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionLogIngestionApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionLogQuarantineMapper;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionLogQuarantineDO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** INT-12: immutable terminal evidence and PLT callback facts commit in the same transaction. */
@Service
@RequiredArgsConstructor
public class CollectionLogIngestionService implements CollectionLogIngestionApi {
    public static final int MAX_LOG_BYTES = 20 * 1024 * 1024;
    private final CollectionTaskMapper taskMapper;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final FileContentPolicyService contentPolicy;
    private final CollectionLogStorageService storage;
    private final CollectionCallbackApi callbacks;
    private final CollectionLogQuarantineMapper quarantines;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionCallbackResultDTO ingest(Command command, InputStream log) {
        if (command == null || !Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())
                || command.sizeBytes() < 1 || command.sizeBytes() > MAX_LOG_BYTES || log == null) {
            throw new IllegalArgumentException("COLLECTION_LOG_INVALID");
        }
        var task = taskMapper.selectByTenantAndPlatformTaskIdForUpdate(command.tenantId(), command.platformTaskId());
        if (task == null || !Objects.equals(task.getExternalTaskId(), command.externalTaskId())
                || !Set.of("DISPATCHED", "EXECUTING", "CALLBACK_PROCESSING").contains(task.getStatus())) {
            throw new IllegalStateException("COLLECTION_LOG_TASK_BINDING_INVALID");
        }
        byte[] bytes;
        try {
            bytes = log.readNBytes((int) command.sizeBytes() + 1);
        } catch (IOException failure) {
            throw new IllegalStateException("COLLECTION_LOG_READ_FAILED");
        }
        String name = "collection-" + task.getId() + "-" + command.resultVersion() + ".txt";
        var policy = new FileBusinessObjectPolicyFact(true, 0L, "IMMUTABLE", "SINGLE",
                Set.of("COLLECTION_LOG"), Set.of("text/plain"), (long) MAX_LOG_BYTES, "INTERNAL");
        ValidatedFileContent content;
        try {
            content = contentPolicy.validateBounded(new BoundedFileContentValidationCommand(bytes,
                    name, command.sizeBytes(), "text/plain", command.sha256(), policy));
        } catch (ServiceException failure) {
            if (!Objects.equals(failure.getCode(), cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants
                    .FILE_SECURITY_SCAN_REJECTED.getCode())) throw failure;
            return quarantine(command, bytes, name);
        }
        String operation = storageOperation(command);
        var receipt = storage.store(operation, content.content(), name, content.mediaType());
        if (receipt.sizeBytes() != command.sizeBytes()) throw new IllegalStateException("COLLECTION_LOG_STORAGE_CONFLICT");

        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setTenantId(command.tenantId());
        artifact.setName(name);
        artifact.setOwnerContext("PLT");
        artifact.setCategoryCode("COLLECTION_LOG");
        artifact.setLifecycleStatusCode("ACTIVE");
        artifact.setVersion(0);
        artifact.setCreator(task.getCreator());
        if (artifactMapper.insert(artifact) != 1) throw new IllegalStateException("COLLECTION_LOG_ARTIFACT_FAILED");
        FileVersionDO version = new FileVersionDO();
        version.setTenantId(command.tenantId());
        version.setArtifactId(artifact.getId());
        version.setVersionNo(1);
        version.setInfraFileId(receipt.infraFileId());
        version.setAvailabilityVersion(0);
        version.setSha256(content.sha256());
        version.setSizeBytes(content.sizeBytes());
        version.setDeclaredMediaType("text/plain");
        version.setDetectedMediaType(content.mediaType());
        version.setScanStatusCode(content.scanStatusCode());
        version.setScanProviderCode(content.scanProviderCode());
        version.setScanProviderVersion(content.scanProviderVersion());
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedAt(LocalDateTime.now());
        version.setCreatedBy(Long.valueOf(task.getCreator()));
        if (versionMapper.insert(version) != 1) throw new IllegalStateException("COLLECTION_LOG_VERSION_FAILED");
        FileReferenceDO reference = new FileReferenceDO();
        reference.setTenantId(command.tenantId());
        reference.setOwnerContext("PLT");
        reference.setObjectType("CollectionTask");
        reference.setObjectId(command.platformTaskId());
        reference.setPurposeCode("COLLECTION_LOG");
        reference.setReferenceKey("result-" + command.resultVersion());
        reference.setArtifactId(artifact.getId());
        reference.setFileVersionNo(1);
        reference.setSensitivityCode("INTERNAL");
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(0L);
        reference.setVersion(0);
        reference.setCreator(task.getCreator());
        reference.setUpdater(task.getCreator());
        if (referenceMapper.insert(reference) != 1) throw new IllegalStateException("COLLECTION_LOG_REFERENCE_FAILED");
        return callbacks.handleCallback(new CollectionCallbackCommand(command.receiptId(), command.callbackId(),
                command.sequence(), command.platformTaskId(), command.externalTaskId(), command.externalStatus(),
                command.resultVersion(), version.getId(), null, command.failureCategory(), null, null, command.traceId()));
    }

    private CollectionCallbackResultDTO quarantine(Command command, byte[] bytes, String name) {
        String operation = storageOperation(command);
        storage.store(operation, bytes, name, "text/plain");
        var evidence = new CollectionLogQuarantineDO();
        evidence.setTenantId(command.tenantId());
        evidence.setCallbackId(command.callbackId());
        evidence.setPlatformTaskId(command.platformTaskId());
        evidence.setStorageOperationId(operation);
        evidence.setSha256(command.sha256());
        evidence.setSizeBytes(command.sizeBytes());
        evidence.setReasonCode("FILE_SECURITY_SCAN_REJECTED");
        evidence.setQuarantinedAt(LocalDateTime.now());
        if (quarantines.insert(evidence) != 1 || evidence.getId() == null) {
            throw new IllegalStateException("COLLECTION_LOG_QUARANTINE_FAILED");
        }
        return callbacks.handleCallback(new CollectionCallbackCommand(command.receiptId(), command.callbackId(),
                command.sequence(), command.platformTaskId(), command.externalTaskId(), command.externalStatus(),
                command.resultVersion(), null, String.valueOf(evidence.getId()), "FILE_SECURITY_SCAN_REJECTED",
                null, null, command.traceId()));
    }

    static String storageOperation(Command command) {
        try {
            String identity = command.tenantId() + ":" + command.callbackId() + ":" + command.sha256();
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
