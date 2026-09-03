package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionCallbackCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionConsumptionCommand;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionCallbackRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionBatchMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionCallbackRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionResultConsumptionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionBatchProjectionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskCallbackUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskConsumptionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionCallbackServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock
    private CollectionTaskMapper taskMapper;
    @Mock
    private CollectionBatchMapper batchMapper;
    @Mock
    private CollectionCallbackRecordMapper callbackMapper;
    @Mock
    private CollectionResultConsumptionMapper consumptionMapper;
    @Mock
    private PlatformOutboxEventMapper outboxMapper;

    private CollectionCallbackService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        service = new CollectionCallbackService(taskMapper, batchMapper, callbackMapper,
                consumptionMapper, outboxMapper,
                Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void handlesSuccessfulCallbackAtomically() {
        CollectionTaskDO task = task("DISPATCHED", "BUSINESS_CONSUMPTION");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);
        when(callbackMapper.insert(any(CollectionCallbackRecordDO.class))).thenReturn(1);
        when(taskMapper.updateCallbackState(any())).thenReturn(1);
        when(batchMapper.updateProjection(any())).thenReturn(1);
        when(outboxMapper.insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class))).thenReturn(1);

        var result = service.handleCallback(callback("callback-1", 1L, "SUCCEEDED", 10L, null));

        assertEquals("RESULT_AVAILABLE", result.status());
        assertEquals("RESULT_RECEIVED", result.technicalStage());
        assertTrue(!result.duplicate());

        ArgumentCaptor<CollectionTaskCallbackUpdate> taskUpdate =
                ArgumentCaptor.forClass(CollectionTaskCallbackUpdate.class);
        verify(taskMapper).updateCallbackState(taskUpdate.capture());
        assertEquals("RESULT_AVAILABLE", taskUpdate.getValue().status());
        assertEquals(10L, taskUpdate.getValue().fileVersionId());

        ArgumentCaptor<CollectionBatchProjectionUpdate> batchUpdate =
                ArgumentCaptor.forClass(CollectionBatchProjectionUpdate.class);
        verify(batchMapper).updateProjection(batchUpdate.capture());
        assertEquals(1, batchUpdate.getValue().successDelta());
        assertEquals(0, batchUpdate.getValue().failureDelta());
        verify(outboxMapper).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void marksSequenceGapForReconciliationWithoutCallbackSideEffects() {
        CollectionTaskDO task = task("DISPATCHED", "BUSINESS_CONSUMPTION");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);
        when(taskMapper.updateReconciliationState(any())).thenReturn(1);

        var result = service.handleCallback(callback("callback-2", 2L, "SUCCEEDED", 10L, null));

        assertEquals("DISPATCHED", result.status());
        assertEquals("RECONCILING", result.technicalStage());
        ArgumentCaptor<CollectionTaskReconciliationUpdate> update =
                ArgumentCaptor.forClass(CollectionTaskReconciliationUpdate.class);
        verify(taskMapper).updateReconciliationState(update.capture());
        assertEquals("RECONCILING", update.getValue().technicalStage());
        verify(callbackMapper, never()).insert(any(CollectionCallbackRecordDO.class));
        verify(taskMapper, never()).updateCallbackState(any());
        verify(batchMapper, never()).updateProjection(any());
        verify(outboxMapper, never()).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void publishesCompletedForCallbackTerminalSuccess() {
        CollectionTaskDO task = task("DISPATCHED", "CALLBACK_TERMINAL");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);
        when(callbackMapper.insert(any(CollectionCallbackRecordDO.class))).thenReturn(1);
        when(taskMapper.updateCallbackState(any())).thenReturn(1);
        when(batchMapper.updateProjection(any())).thenReturn(1);
        when(outboxMapper.insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class))).thenReturn(1);

        var result = service.handleCallback(callback("callback-1", 1L, "SUCCEEDED", 10L, null));

        assertEquals("COMPLETED", result.status());
        verify(outboxMapper, org.mockito.Mockito.times(2)).insert(
                any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void returnsExistingCallbackWithoutSideEffects() {
        CollectionCallbackRecordDO existing = new CollectionCallbackRecordDO();
        existing.setCallbackId("callback-1");
        existing.setPlatformTaskId("task-1");
        existing.setMappedStatus("FAILED");
        existing.setResultVersion(1L);
        existing.setFileVersionId(10L);
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task("RESULT_AVAILABLE", "BUSINESS_CONSUMPTION"));
        when(callbackMapper.selectByTenantAndCallbackId(TENANT_ID, "callback-1"))
                .thenReturn(existing);

        var result = service.handleCallback(callback("callback-1", 1L, "FAILED", 10L, null));

        assertTrue(result.duplicate());
        verify(taskMapper).selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1");
        verify(taskMapper, never()).updateCallbackState(any());
        verify(batchMapper, never()).updateProjection(any());
        verify(outboxMapper, never()).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void rejectsExternalTaskMismatchWithoutSideEffects() {
        CollectionTaskDO task = task("DISPATCHED", "BUSINESS_CONSUMPTION");
        task.setExternalTaskId("another-task");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.handleCallback(callback("callback-1", 1L,
                        "SUCCEEDED", 10L, null)));

        assertEquals("COLLECTION_EXTERNAL_TASK_MISMATCH", failure.getMessage());
        verify(callbackMapper, never()).insert(any(CollectionCallbackRecordDO.class));
        verify(taskMapper, never()).updateCallbackState(any());
        verify(batchMapper, never()).updateProjection(any());
        verify(outboxMapper, never()).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void mapsSecurityQuarantineWithoutFileVersion() {
        CollectionTaskDO task = task("DISPATCHED", "BUSINESS_CONSUMPTION");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);
        when(callbackMapper.insert(any(CollectionCallbackRecordDO.class))).thenReturn(1);
        when(taskMapper.updateCallbackState(any())).thenReturn(1);
        when(batchMapper.updateProjection(any())).thenReturn(1);
        when(outboxMapper.insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class))).thenReturn(1);

        var result = service.handleCallback(callback("callback-1", 1L,
                "SECURITY_EXCEPTION", null, "quarantine-1"));

        assertEquals("SECURITY_EXCEPTION", result.status());
        assertEquals("RESULT_FILE_QUARANTINED", result.technicalStage());
        assertEquals("quarantine-1", result.quarantineEvidenceId());
    }

    @Test
    void rejectsInvalidResultReference() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.handleCallback(callback("callback-1", 1L,
                        "SUCCEEDED", 10L, "quarantine-1")));

        assertEquals("COLLECTION_RESULT_REFERENCE_INVALID", failure.getMessage());
        verify(callbackMapper, never()).selectByTenantAndCallbackId(any(), any());
    }

    @Test
    void confirmsConsumptionAndPublishesConsumedAndCompleted() {
        CollectionTaskDO task = task("RESULT_AVAILABLE", "BUSINESS_CONSUMPTION");
        task.setResultVersion(2L);
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);
        when(consumptionMapper.insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionResultConsumptionDO.class))).thenReturn(1);
        when(taskMapper.updateConsumptionState(any())).thenReturn(1);
        when(outboxMapper.insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class))).thenReturn(1);

        var result = service.confirmConsumption(new CollectionConsumptionCommand(
                "task-1", "IMP", "IMPLEMENTATION_TASK", "imp-1", 2L, "trace-1"));

        assertEquals("COMPLETED", result.status());
        assertTrue(!result.duplicate());
        ArgumentCaptor<CollectionTaskConsumptionUpdate> update =
                ArgumentCaptor.forClass(CollectionTaskConsumptionUpdate.class);
        verify(taskMapper).updateConsumptionState(update.capture());
        assertEquals(2L, update.getValue().consumedResultVersion());
        verify(outboxMapper, org.mockito.Mockito.times(2)).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    @Test
    void rejectsWrongConsumerWithoutSideEffects() {
        CollectionTaskDO task = task("RESULT_AVAILABLE", "BUSINESS_CONSUMPTION");
        task.setResultVersion(2L);
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(TENANT_ID, "task-1"))
                .thenReturn(task);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.confirmConsumption(new CollectionConsumptionCommand(
                        "task-1", "CUT", "IMPLEMENTATION_TASK", "imp-1", 2L, "trace-1")));

        assertEquals("COLLECTION_CONSUMER_MISMATCH", failure.getMessage());
        verify(consumptionMapper, never()).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionResultConsumptionDO.class));
        verify(taskMapper, never()).updateConsumptionState(any());
        verify(outboxMapper, never()).insert(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO.class));
    }

    private static CollectionTaskDO task(String status, String completionMode) {
        CollectionTaskDO task = new CollectionTaskDO();
        task.setTenantId(TENANT_ID);
        task.setBatchId(7L);
        task.setPlatformTaskId("task-1");
        task.setStatus(status);
        task.setCompletionMode(completionMode);
        task.setExternalTaskId("external-1");
        task.setConsumerContext("IMP");
        task.setConsumerObjectType("IMPLEMENTATION_TASK");
        task.setConsumerObjectId("imp-1");
        return task;
    }

    private static CollectionCallbackCommand callback(String callbackId, Long sequence,
                                                        String status, Long fileVersionId,
                                                        String quarantineEvidenceId) {
        return new CollectionCallbackCommand(8L, callbackId, sequence, "task-1",
                "external-1", status, 1L, fileVersionId, quarantineEvidenceId,
                status.equals("SUCCEEDED") ? null : status,
                LocalDateTime.of(2026, 8, 28, 15, 0),
                LocalDateTime.of(2026, 8, 28, 15, 1), "trace-1");
    }
}
