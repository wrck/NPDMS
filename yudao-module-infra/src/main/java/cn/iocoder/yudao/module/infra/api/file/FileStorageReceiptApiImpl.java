package cn.iocoder.yudao.module.infra.api.file;

import cn.hutool.core.lang.Assert;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageAccessReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.query.FileStorageOperationLookupQuery;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import cn.iocoder.yudao.module.infra.service.file.FileConfigService;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_STORAGE_COMPENSATION_FAILED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_STORAGE_CONTENT_TOO_LARGE;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_STORAGE_RECEIPT_CONFLICT;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_STORAGE_RECEIPT_REPLAY_MISMATCH;

@Service
@Validated
@Slf4j
public class FileStorageReceiptApiImpl implements FileStorageReceiptApi {

    static final int MAX_CONTENT_BYTES = 50 * 1024 * 1024;
    static final String STORAGE_DIRECTORY = "pms-storage-receipts";

    private static final Pattern STORAGE_OPERATION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Override
    @SneakyThrows
    public FileStorageReceipt store(FileStorageStoreCommand command) {
        String operationId = validateStorageOperationId(command.storageOperationId());
        byte[] content = command.validatedContent();
        if (content.length > MAX_CONTENT_BYTES) {
            throw exception(FILE_STORAGE_CONTENT_TOO_LARGE);
        }
        String name = FilePathUtils.validateFileName(command.name());
        String mediaType = command.mediaType().trim();
        String storagePath = buildStoragePath(operationId);

        List<FileDO> existingFiles = selectByStoragePath(storagePath, operationId);
        if (existingFiles.size() == 1) {
            FileDO existingFile = existingFiles.getFirst();
            if (!Objects.equals(existingFile.getName(), name)
                    || !Objects.equals(existingFile.getType(), mediaType)
                    || !Objects.equals(existingFile.getSize(), (long) content.length)) {
                throw exception(FILE_STORAGE_RECEIPT_REPLAY_MISMATCH);
            }
            return toReceipt(operationId, existingFile);
        }

        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, storagePath, mediaType);
        FileDO file = new FileDO().setConfigId(client.getId())
                .setName(name).setPath(storagePath).setUrl(url)
                .setType(mediaType).setSize((long) content.length);
        try {
            fileMapper.insert(file);
        } catch (RuntimeException persistenceFailure) {
            compensateUnregisteredObject(client, storagePath, operationId, persistenceFailure);
            throw persistenceFailure;
        }
        return toReceipt(operationId, file);
    }

    @Override
    public FileStorageAccessReceipt presignGet(Long infraFileId, Integer expirationSeconds) {
        FileDO file = fileMapper.selectById(infraFileId);
        if (file == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        FilePathUtils.validatePath(file.getPath());
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        String url = client.presignGetUrl(file.getPath(), expirationSeconds);
        return new FileStorageAccessReceipt(url, LocalDateTime.now().plusSeconds(expirationSeconds));
    }

    @Override
    @SneakyThrows
    public void delete(String storageOperationId) {
        String operationId = validateStorageOperationId(storageOperationId);
        String storagePath = buildStoragePath(operationId);
        List<FileDO> existingFiles = selectByStoragePath(storagePath, operationId);
        if (existingFiles.isEmpty()) {
            return;
        }
        FileDO file = existingFiles.getFirst();
        FilePathUtils.validatePath(file.getPath());
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        client.delete(file.getPath());
        fileMapper.deleteById(file.getId());
    }

    static String buildStoragePath(String storageOperationId) {
        return STORAGE_DIRECTORY + "/" + storageOperationId;
    }

    private List<FileDO> selectByStoragePath(String storagePath, String operationId) {
        List<FileDO> files = fileMapper.selectListByStorageOperation(
                new FileStorageOperationLookupQuery(storagePath));
        if (files.size() > 1) {
            log.error("file_storage_reconciliation_required operationId={} path={} recordCount={}",
                    operationId, storagePath, files.size());
            throw exception(FILE_STORAGE_RECEIPT_CONFLICT);
        }
        return files;
    }

    private String validateStorageOperationId(String storageOperationId) {
        String normalized = storageOperationId == null ? null : storageOperationId.trim();
        if (normalized == null || !STORAGE_OPERATION_ID_PATTERN.matcher(normalized).matches()) {
            throw exception(FILE_STORAGE_RECEIPT_CONFLICT);
        }
        return normalized;
    }

    private FileStorageReceipt toReceipt(String operationId, FileDO file) {
        return new FileStorageReceipt(operationId, file.getId(), file.getName(),
                file.getType(), file.getSize());
    }

    private void compensateUnregisteredObject(FileClient client, String storagePath,
                                              String operationId, RuntimeException persistenceFailure) {
        try {
            client.delete(storagePath);
        } catch (Exception compensationFailure) {
            log.error("file_storage_compensation_failed operationId={} path={}",
                    operationId, storagePath, compensationFailure);
            persistenceFailure.addSuppressed(compensationFailure);
            throw exception(FILE_STORAGE_COMPENSATION_FAILED);
        }
    }

}
