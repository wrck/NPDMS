package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.hutool.core.util.IdUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionCallbackApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackResultDTO;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionResultDTO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionCallbackRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionResultConsumptionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionBatchMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionCallbackRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionResultConsumptionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionBatchProjectionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskCallbackUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskConsumptionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.ExistingCollectionConsumptionQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CollectionCallbackService implements CollectionCallbackApi {

    private static final Set<String> CALLBACK_FINAL_STATUSES = Set.of(
            "RESULT_AVAILABLE", "COMPLETED", "FAILED", "CANCELLED", "SECURITY_EXCEPTION");

    private final CollectionTaskMapper taskMapper;
    private final CollectionBatchMapper batchMapper;
    private final CollectionCallbackRecordMapper callbackMapper;
    private final CollectionResultConsumptionMapper consumptionMapper;
    private final PlatformOutboxEventMapper outboxMapper;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionCallbackResultDTO handleCallback(CollectionCallbackCommand command) {
        validateCallback(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CollectionTaskDO task = requiredTaskForUpdate(tenantId, command.platformTaskId());
        CollectionCallbackRecordDO existing = callbackMapper.selectByTenantAndCallbackId(
                tenantId, command.callbackId());
        if (existing != null) {
            return callbackResult(existing, true);
        }

        if (!validateTaskBinding(tenantId, task, command)) {
            return new CollectionCallbackResultDTO(command.callbackId(), command.platformTaskId(),
                    task.getStatus(), "RECONCILING", task.getResultVersion(), task.getFileVersionId(),
                    task.getQuarantineEvidenceId(), false);
        }
        CallbackMapping mapping = map(command.externalStatus(), task.getCompletionMode(),
                command.quarantineEvidenceId());

        CollectionCallbackRecordDO record = callbackRecord(tenantId, command, mapping);
        try {
            if (callbackMapper.insert(record) != 1) {
                throw new IllegalStateException("COLLECTION_CALLBACK_RECORD_CREATE_FAILED");
            }
        } catch (DuplicateKeyException ex) {
            CollectionCallbackRecordDO duplicate = callbackMapper.selectByTenantAndCallbackId(
                    tenantId, command.callbackId());
            if (duplicate == null) {
                throw ex;
            }
            return callbackResult(duplicate, true);
        }

        if (taskMapper.updateCallbackState(new CollectionTaskCallbackUpdate(
                tenantId, command.platformTaskId(), task.getStatus(),
                task.getLastCallbackSequence(), mapping.status(), mapping.technicalStage(),
                command.externalStatus(), command.resultVersion(), command.fileVersionId(),
                command.quarantineEvidenceId(), command.failureCategory(), command.sequence())) != 1) {
            throw new IllegalStateException("COLLECTION_CALLBACK_STATE_CONFLICT");
        }

        int successDelta = mapping.success() ? 1 : 0;
        int failureDelta = mapping.success() ? 0 : 1;
        if (batchMapper.updateProjection(new CollectionBatchProjectionUpdate(
                tenantId, task.getBatchId(), successDelta, failureDelta)) != 1) {
            throw new IllegalStateException("COLLECTION_BATCH_PROJECTION_UPDATE_FAILED");
        }

        insertOutbox(tenantId, mapping.eventType(), command.platformTaskId(),
                callbackPayload(command, mapping));
        if (mapping.completed()) {
            insertOutbox(tenantId, "CollectionCompleted", command.platformTaskId(), Map.of(
                    "platformTaskId", command.platformTaskId(),
                    "resultVersion", command.resultVersion(),
                    "completionMode", task.getCompletionMode()));
        }

        return new CollectionCallbackResultDTO(command.callbackId(), command.platformTaskId(),
                mapping.status(), mapping.technicalStage(), command.resultVersion(),
                command.fileVersionId(), command.quarantineEvidenceId(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollectionConsumptionResultDTO confirmConsumption(CollectionConsumptionCommand command) {
        validateConsumption(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CollectionTaskDO task = requiredTaskForUpdate(tenantId, command.platformTaskId());
        ExistingCollectionConsumptionQuery query = consumptionQuery(tenantId, command);
        CollectionResultConsumptionDO existing = consumptionMapper.selectExisting(query);
        if (existing != null) {
            return new CollectionConsumptionResultDTO(command.platformTaskId(),
                    command.resultVersion(), "COMPLETED", true);
        }

        if (!"BUSINESS_CONSUMPTION".equals(task.getCompletionMode())
                || !"RESULT_AVAILABLE".equals(task.getStatus())) {
            throw new IllegalStateException("COLLECTION_RESULT_NOT_AVAILABLE");
        }
        if (!Objects.equals(task.getConsumerContext(), command.consumerContext())
                || !Objects.equals(task.getConsumerObjectType(), command.consumerObjectType())
                || !Objects.equals(task.getConsumerObjectId(), command.consumerObjectId())) {
            throw new IllegalStateException("COLLECTION_CONSUMER_MISMATCH");
        }
        if (!Objects.equals(task.getResultVersion(), command.resultVersion())) {
            throw new IllegalStateException("COLLECTION_RESULT_VERSION_MISMATCH");
        }

        CollectionResultConsumptionDO consumption = new CollectionResultConsumptionDO();
        consumption.setTenantId(tenantId);
        consumption.setPlatformTaskId(command.platformTaskId());
        consumption.setConsumerContext(command.consumerContext());
        consumption.setConsumerObjectType(command.consumerObjectType());
        consumption.setConsumerObjectId(command.consumerObjectId());
        consumption.setResultVersion(command.resultVersion());
        consumption.setConsumptionResult("CONSUMED");
        consumption.setConsumedAt(LocalDateTime.now(clock));
        consumption.setTraceId(command.traceId());
        try {
            if (consumptionMapper.insert(consumption) != 1) {
                throw new IllegalStateException("COLLECTION_CONSUMPTION_CREATE_FAILED");
            }
        } catch (DuplicateKeyException ex) {
            CollectionResultConsumptionDO duplicate = consumptionMapper.selectExisting(query);
            if (duplicate == null) {
                throw ex;
            }
            return new CollectionConsumptionResultDTO(command.platformTaskId(),
                    command.resultVersion(), "COMPLETED", true);
        }

        if (taskMapper.updateConsumptionState(new CollectionTaskConsumptionUpdate(
                tenantId, command.platformTaskId(), command.resultVersion(),
                "COMPLETED", command.resultVersion())) != 1) {
            throw new IllegalStateException("COLLECTION_CONSUMPTION_STATE_CONFLICT");
        }

        insertOutbox(tenantId, "CollectionResultConsumed", command.platformTaskId(), Map.of(
                "platformTaskId", command.platformTaskId(),
                "consumerContext", command.consumerContext(),
                "consumerObjectType", command.consumerObjectType(),
                "consumerObjectId", command.consumerObjectId(),
                "resultVersion", command.resultVersion(),
                "traceId", command.traceId() == null ? "" : command.traceId()));
        insertOutbox(tenantId, "CollectionCompleted", command.platformTaskId(), Map.of(
                "platformTaskId", command.platformTaskId(),
                "resultVersion", command.resultVersion(),
                "completionMode", task.getCompletionMode()));

        return new CollectionConsumptionResultDTO(command.platformTaskId(),
                command.resultVersion(), "COMPLETED", false);
    }

    private CollectionTaskDO requiredTaskForUpdate(Long tenantId, String platformTaskId) {
        CollectionTaskDO task = taskMapper.selectByTenantAndPlatformTaskIdForUpdate(
                tenantId, platformTaskId);
        if (task == null) {
            throw new IllegalStateException("COLLECTION_TASK_NOT_FOUND");
        }
        return task;
    }

    private boolean validateTaskBinding(Long tenantId, CollectionTaskDO task,
                                        CollectionCallbackCommand command) {
        if (CALLBACK_FINAL_STATUSES.contains(task.getStatus())) {
            throw new IllegalStateException("COLLECTION_TASK_TERMINAL");
        }
        if (!Objects.equals(task.getExternalTaskId(), command.externalTaskId())) {
            throw new IllegalStateException("COLLECTION_EXTERNAL_TASK_MISMATCH");
        }
        long expectedSequence = task.getLastCallbackSequence() == null
                ? 1L : task.getLastCallbackSequence() + 1L;
        if (command.sequence() < expectedSequence) {
            throw new IllegalStateException("COLLECTION_CALLBACK_SEQUENCE_STALE");
        }
        if (command.sequence() > expectedSequence) {
            if (taskMapper.updateReconciliationState(new CollectionTaskReconciliationUpdate(
                    tenantId, task.getPlatformTaskId(), task.getStatus(),
                    task.getLastCallbackSequence(), "RECONCILING")) != 1) {
                throw new IllegalStateException("COLLECTION_CALLBACK_STATE_CONFLICT");
            }
            return false;
        }
        return true;
    }

    private static void validateCallback(CollectionCallbackCommand command) {
        if (command == null || command.receiptId() == null
                || blank(command.callbackId()) || command.sequence() == null || command.sequence() <= 0
                || blank(command.platformTaskId()) || blank(command.externalTaskId())
                || blank(command.externalStatus()) || command.resultVersion() == null
                || command.resultVersion() <= 0) {
            throw new IllegalArgumentException("COLLECTION_CALLBACK_INVALID");
        }
        boolean hasFile = command.fileVersionId() != null && command.fileVersionId() > 0;
        boolean hasQuarantine = !blank(command.quarantineEvidenceId());
        if (hasFile == hasQuarantine) {
            throw new IllegalArgumentException("COLLECTION_RESULT_REFERENCE_INVALID");
        }
        if (hasQuarantine && !"SECURITY_EXCEPTION".equals(command.externalStatus())) {
            throw new IllegalArgumentException("COLLECTION_QUARANTINE_STATUS_INVALID");
        }
    }

    private static void validateConsumption(CollectionConsumptionCommand command) {
        if (command == null || blank(command.platformTaskId())
                || blank(command.consumerContext()) || blank(command.consumerObjectType())
                || blank(command.consumerObjectId()) || command.resultVersion() == null
                || command.resultVersion() <= 0) {
            throw new IllegalArgumentException("COLLECTION_CONSUMPTION_INVALID");
        }
    }

    private static CallbackMapping map(String externalStatus, String completionMode,
                                       String quarantineEvidenceId) {
        return switch (externalStatus) {
            case "SUCCEEDED", "SUCCESS", "PARTIAL_SUCCESS" -> {
                boolean callbackTerminal = "CALLBACK_TERMINAL".equals(completionMode);
                yield new CallbackMapping(callbackTerminal ? "COMPLETED" : "RESULT_AVAILABLE",
                        "RESULT_RECEIVED", "CollectionResultAvailable", true, callbackTerminal);
            }
            case "FAILED" -> new CallbackMapping("FAILED", "RESULT_RECEIVED",
                    "CollectionFailed", false, false);
            case "TIMED_OUT" -> new CallbackMapping("FAILED", "TIMED_OUT",
                    "CollectionFailed", false, false);
            case "CANCELLED" -> new CallbackMapping("CANCELLED", "RESULT_RECEIVED",
                    "CollectionCancelled", false, false);
            case "SECURITY_EXCEPTION" -> new CallbackMapping("SECURITY_EXCEPTION",
                    blank(quarantineEvidenceId) ? "RESULT_RECEIVED" : "RESULT_FILE_QUARANTINED",
                    "CollectionSecurityFailed", false, false);
            default -> throw new IllegalArgumentException("COLLECTION_EXTERNAL_STATUS_UNSUPPORTED");
        };
    }

    private static ExistingCollectionConsumptionQuery consumptionQuery(
            Long tenantId, CollectionConsumptionCommand command) {
        return new ExistingCollectionConsumptionQuery(tenantId, command.platformTaskId(),
                command.consumerContext(), command.consumerObjectType(), command.consumerObjectId(),
                command.resultVersion());
    }

    private static Map<String, Object> callbackPayload(CollectionCallbackCommand command,
                                                       CallbackMapping mapping) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("platformTaskId", command.platformTaskId());
        payload.put("externalStatus", command.externalStatus());
        payload.put("status", mapping.status());
        payload.put("resultVersion", command.resultVersion());
        payload.put("fileVersionId", command.fileVersionId());
        payload.put("quarantineEvidenceId", command.quarantineEvidenceId());
        payload.put("traceId", command.traceId());
        return payload;
    }

    private CollectionCallbackRecordDO callbackRecord(Long tenantId,
                                                        CollectionCallbackCommand command,
                                                        CallbackMapping mapping) {
        CollectionCallbackRecordDO record = new CollectionCallbackRecordDO();
        record.setTenantId(tenantId);
        record.setPlatformTaskId(command.platformTaskId());
        record.setCallbackId(command.callbackId());
        record.setReceiptId(command.receiptId());
        record.setSequenceNo(command.sequence());
        record.setExternalTaskId(command.externalTaskId());
        record.setExternalStatus(command.externalStatus());
        record.setMappedStatus(mapping.status());
        record.setResultVersion(command.resultVersion());
        record.setFileVersionId(command.fileVersionId());
        record.setQuarantineEvidenceId(command.quarantineEvidenceId());
        record.setFailureCategory(command.failureCategory());
        record.setProcessingResult("PROCESSED");
        record.setStartedAt(command.startedAt());
        record.setCompletedAt(command.completedAt());
        record.setTraceId(command.traceId());
        return record;
    }

    private void insertOutbox(Long tenantId, String eventType, String platformTaskId,
                              Map<String, ?> payload) {
        PlatformOutboxEventDO event = new PlatformOutboxEventDO();
        event.setTenantId(tenantId);
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setEventType(eventType);
        event.setAggregateType("CollectionTask");
        event.setAggregateKey(platformTaskId);
        event.setPayload(JsonUtils.toJsonString(payload));
        event.setStatus("PENDING");
        event.setOccurredAt(LocalDateTime.now(clock));
        event.setRetryCount(0);
        if (outboxMapper.insert(event) != 1) {
            throw new IllegalStateException("COLLECTION_OUTBOX_CREATE_FAILED");
        }
    }

    private static CollectionCallbackResultDTO callbackResult(CollectionCallbackRecordDO record,
                                                                boolean duplicate) {
        return new CollectionCallbackResultDTO(record.getCallbackId(), record.getPlatformTaskId(),
                record.getMappedStatus(), null, record.getResultVersion(), record.getFileVersionId(),
                record.getQuarantineEvidenceId(), duplicate);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record CallbackMapping(
            String status,
            String technicalStage,
            String eventType,
            boolean success,
            boolean completed) {
    }
}
