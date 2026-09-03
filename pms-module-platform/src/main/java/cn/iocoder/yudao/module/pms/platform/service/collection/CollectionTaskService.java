package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionTaskApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionBatchDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskCreateItem;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionBatchMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionTaskService implements CollectionTaskApi {

    private static final Set<String> COMPLETION_MODES = Set.of("BUSINESS_CONSUMPTION", "CALLBACK_TERMINAL");
    private static final Set<String> PROTOCOLS = Set.of("SSH", "TELNET");
    private static final Set<String> CREDENTIAL_MODES = Set.of("SAVED_CREDENTIAL", "TEMPORARY_SECRET");

    private final CollectionBatchMapper batchMapper;
    private final CollectionTaskMapper taskMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;

    @Override
    public CollectionBatchDTO createBatch(CollectionBatchCreateCommand command) {
        validate(command);
        var result = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "PLT:COLLECTION_BATCH:CREATE", command.actorId(), command.idempotencyKey()),
                command.requestDigest(), CollectionBatchDTO.class,
                () -> persistBatch(command),
                batch -> new PlatformCommandExecutionApi.SuccessFacts(
                        "COLLECTION_BATCH_CREATE", "CollectionBatch", String.valueOf(batch.id()),
                        command.idempotencyKey(), JsonUtils.toJsonString(batch), null, null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw new IllegalStateException("IDEMPOTENCY_CONFLICT");
        }
        if (result.response() == null) {
            throw new IllegalStateException("IDEMPOTENCY_IN_PROGRESS");
        }
        return result.response();
    }

    @Override
    public CollectionTaskDTO getTask(Long tenantId, String platformTaskId) {
        if (tenantId == null || platformTaskId == null || platformTaskId.isBlank()) {
            throw new IllegalArgumentException("采集任务查询条件不完整");
        }
        CollectionTaskDO task = taskMapper.selectByTenantAndPlatformTaskId(tenantId, platformTaskId);
        return task == null ? null : toDTO(task);
    }

    @Transactional(rollbackFor = Exception.class)
    protected CollectionBatchDTO persistBatch(CollectionBatchCreateCommand command) {
        CollectionBatchDO batch = new CollectionBatchDO();
        batch.setTenantId(command.tenantId());
        batch.setBatchNo(UUID.randomUUID().toString());
        batch.setSourceContext(command.sourceContext());
        batch.setSourceObjectType(command.sourceObjectType());
        batch.setSourceObjectId(command.sourceObjectId());
        batch.setIdempotencyKey(command.idempotencyKey());
        batch.setStatus("CREATED");
        batch.setTaskCount(command.tasks().size());
        batch.setSuccessCount(0);
        batch.setFailureCount(0);
        try {
            if (batchMapper.insert(batch) != 1) {
                throw new IllegalStateException("COLLECTION_BATCH_CREATE_FAILED");
            }
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("COLLECTION_BATCH_IDEMPOTENCY_CONFLICT", ex);
        }

        List<CollectionTaskDTO> tasks = new ArrayList<>(command.tasks().size());
        for (CollectionTaskCreateItem item : command.tasks()) {
            CollectionTaskDO task = toTask(command, batch.getId(), item);
            try {
                if (taskMapper.insert(task) != 1) {
                    throw new IllegalStateException("COLLECTION_TASK_CREATE_FAILED");
                }
            } catch (DuplicateKeyException ex) {
                throw new IllegalStateException("COLLECTION_TASK_IDEMPOTENCY_CONFLICT", ex);
            }
            tasks.add(toDTO(task));
        }
        return new CollectionBatchDTO(batch.getId(), batch.getBatchNo(), batch.getSourceContext(),
                batch.getSourceObjectType(), batch.getSourceObjectId(), batch.getIdempotencyKey(),
                batch.getStatus(), batch.getTaskCount(), List.copyOf(tasks));
    }

    private CollectionTaskDO toTask(CollectionBatchCreateCommand command, Long batchId,
                                    CollectionTaskCreateItem item) {
        CollectionTaskDO task = new CollectionTaskDO();
        task.setTenantId(command.tenantId());
        task.setBatchId(batchId);
        task.setPlatformTaskId(UUID.randomUUID().toString());
        task.setSourceContext(command.sourceContext());
        task.setSourceObjectType(command.sourceObjectType());
        task.setSourceObjectId(command.sourceObjectId());
        task.setProjectId(command.projectId());
        task.setDeviceId(item.deviceId());
        task.setDeviceName(item.deviceName());
        task.setHost(item.host());
        task.setPort(item.port());
        task.setProtocol(item.protocol());
        task.setTemplateId(item.templateId());
        task.setTemplateVersion(item.templateVersion());
        task.setTemplateHash(item.templateHash());
        task.setCredentialMode(item.credentialMode());
        task.setCredentialId(item.credentialId());
        task.setGrantSnapshotId(item.grantSnapshotId());
        task.setIdempotencyKey(item.idempotencyKey());
        task.setCompletionMode(command.completionMode());
        task.setStatus("CREATED");
        task.setTechnicalStage("PENDING_DISPATCH");
        task.setConsumerContext(item.consumerContext());
        task.setConsumerObjectType(item.consumerObjectType());
        task.setConsumerObjectId(item.consumerObjectId());
        return task;
    }

    private void validate(CollectionBatchCreateCommand command) {
        if (command == null || command.tenantId() == null || command.actorId() == null
                || command.actorId() <= 0 || blank(command.idempotencyKey()) || command.idempotencyKey().length() > 128
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || blank(command.sourceContext()) || blank(command.sourceObjectType()) || blank(command.sourceObjectId())
                || !COMPLETION_MODES.contains(command.completionMode())
                || command.tasks() == null || command.tasks().isEmpty()) {
            throw new IllegalArgumentException("采集批次创建参数不完整");
        }
        Set<String> taskKeys = new HashSet<>();
        for (CollectionTaskCreateItem task : command.tasks()) {
            validateTask(task);
            if (!taskKeys.add(task.idempotencyKey())) {
                throw new IllegalArgumentException("批次内设备任务幂等键重复");
            }
        }
    }

    private void validateTask(CollectionTaskCreateItem task) {
        if (task == null || blank(task.deviceId()) || blank(task.deviceName()) || blank(task.host())
                || task.port() == null || task.port() < 1 || task.port() > 65535
                || !PROTOCOLS.contains(task.protocol()) || blank(task.templateId())
                || blank(task.templateVersion()) || task.templateHash() == null
                || !task.templateHash().matches("[0-9a-f]{64}")
                || !CREDENTIAL_MODES.contains(task.credentialMode()) || blank(task.idempotencyKey())
                || task.idempotencyKey().length() > 128 || blank(task.consumerContext())
                || blank(task.consumerObjectType()) || blank(task.consumerObjectId())) {
            throw new IllegalArgumentException("设备采集任务参数不完整");
        }
        if ("SAVED_CREDENTIAL".equals(task.credentialMode())
                && (task.credentialId() == null || task.grantSnapshotId() == null)) {
            throw new IllegalArgumentException("已保存凭证任务缺少凭证授权快照");
        }
        if ("TEMPORARY_SECRET".equals(task.credentialMode())
                && (task.credentialId() != null || task.grantSnapshotId() != null)) {
            throw new IllegalArgumentException("临时凭证任务不得引用已保存凭证");
        }
    }

    private CollectionTaskDTO toDTO(CollectionTaskDO task) {
        return new CollectionTaskDTO(task.getId(), task.getBatchId(), task.getPlatformTaskId(),
                task.getSourceContext(), task.getSourceObjectType(), task.getSourceObjectId(), task.getProjectId(),
                task.getDeviceId(), task.getDeviceName(), task.getHost(), task.getPort(), task.getProtocol(),
                task.getTemplateId(), task.getTemplateVersion(), task.getTemplateHash(), task.getCredentialMode(),
                task.getCredentialId(), task.getGrantSnapshotId(), task.getIdempotencyKey(), task.getCompletionMode(),
                task.getStatus(), task.getTechnicalStage(), task.getResultVersion(), task.getFileVersionId(),
                task.getConsumerContext(), task.getConsumerObjectType(), task.getConsumerObjectId());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
