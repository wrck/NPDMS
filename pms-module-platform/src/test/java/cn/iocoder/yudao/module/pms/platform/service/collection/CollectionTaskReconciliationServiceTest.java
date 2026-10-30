package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationObservationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionTaskReconciliationServiceTest {
    @Test void durablePreSubmissionCancellationTerminatesUnknownTaskWithoutInventingLog() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        var pending = task("RECONCILING", "UNKNOWN");
        pending.setStatus("CREATED"); pending.setExternalTaskId(null);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(pending));
        when(gateway.query("task-1")).thenReturn(new DeviceOpsTaskSnapshot("task-1", null, "CANCELLED",
                "CANCELLED_BEFORE_DISPATCH", null, null, null, null));
        when(taskMapper.updateUndispatchedCancellation(any())).thenReturn(1);
        service.reconcileDue(0L);
        verify(taskMapper).updateUndispatchedCancellation(any());
        verify(gateway, never()).retryResultDelivery(any());
        verify(taskMapper, never()).updateDispatchState(any());
    }

    @Test void cancellationFenceDoesNotRewriteAcceptedOrTerminalTasks() {
        assertTrue(CollectionTaskStateMachine.canCancelBeforeDispatch("CREATED", "RECONCILING", null));
        for (String state : List.of("DISPATCHED", "EXECUTING", "COMPLETED", "FAILED", "CANCELLED", "RESULT_AVAILABLE")) {
            org.junit.jupiter.api.Assertions.assertFalse(CollectionTaskStateMachine.canCancelBeforeDispatch(state, "RECONCILING", null));
        }
        org.junit.jupiter.api.Assertions.assertFalse(CollectionTaskStateMachine.canCancelBeforeDispatch("CREATED", "RECONCILING", "dac-1"));
    }

    @Test
    void dispatchRecoveryCannotReopenTerminalHistoryOrRegressExecutingState() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> CollectionTaskStateMachine.acceptedDispatchStatus("COMPLETED"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> CollectionTaskStateMachine.acceptedDispatchStatus("FAILED"));
        assertEquals("EXECUTING", CollectionTaskStateMachine.acceptedDispatchStatus("EXECUTING"));
    }

    @Mock CollectionTaskMapper taskMapper;
    @Mock DeviceOpsGatewayApi gateway;
    @Mock ObjectProvider<DeviceOpsGatewayApi> gatewayProvider;

    private CollectionTaskReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new CollectionTaskReconciliationService(taskMapper, gatewayProvider);
    }

    @Test
    void skipsWhenGatewayAbsent() {
        when(gatewayProvider.getIfAvailable()).thenReturn(null);

        String summary = service.reconcileDue(0L);

        assertTrue(summary.contains("未装配"));
        verifyNoInteractions(taskMapper);
    }

    @Test
    void acceptedButLocallyUnacknowledgedTaskRecoversExternalIdentityAndDispatchState() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        var task = task("RECONCILING", "UNKNOWN");
        task.setStatus("CREATED"); task.setExternalTaskId(null);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task));
        when(gateway.query("task-1")).thenReturn(snapshot("EXECUTING"));
        when(taskMapper.updateDispatchState(any())).thenReturn(1);
        service.reconcileDue(0L);
        var update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper).updateDispatchState(update.capture());
        assertEquals("DISPATCHED", update.getValue().status());
        assertEquals("dac-1", update.getValue().externalTaskId());
        assertEquals("ACCEPTED", update.getValue().technicalStage());
    }

    @Test
    void runningStatusKeepsDispatchedTaskUnchanged() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task("DISPATCHED", "EXECUTING")));
        when(gateway.query("task-1")).thenReturn(snapshot("EXECUTING"));

        service.reconcileDue(0L);

        verify(taskMapper, never()).updateReconciliationState(any());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    @Test
    void runningStatusRestoresReconcilingTaskToDispatched() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task("RECONCILING", "EXECUTING")));
        when(gateway.query("task-1")).thenReturn(snapshot("EXECUTING"));
        when(taskMapper.updateReconciliationState(any())).thenReturn(1);

        service.reconcileDue(0L);

        ArgumentCaptor<CollectionTaskReconciliationUpdate> update =
                ArgumentCaptor.forClass(CollectionTaskReconciliationUpdate.class);
        verify(taskMapper).updateReconciliationState(update.capture());
        assertEquals("ACCEPTED", update.getValue().technicalStage());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    @Test
    void terminalStatusParksTaskWithObservedExternalState() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task("DISPATCHED", "SUCCEEDED")));
        when(gateway.query("task-1")).thenReturn(snapshot("SUCCEEDED"));
        when(taskMapper.updateReconciliationObservation(any())).thenReturn(1);

        service.reconcileDue(0L);

        ArgumentCaptor<CollectionTaskReconciliationObservationUpdate> update =
                ArgumentCaptor.forClass(CollectionTaskReconciliationObservationUpdate.class);
        verify(taskMapper).updateReconciliationObservation(update.capture());
        assertEquals("SUCCEEDED", update.getValue().externalStatus());
        assertEquals("DISPATCHED", update.getValue().expectedTechnicalStage());
        verify(taskMapper, never()).updateReconciliationState(any());
    }

    @Test
    void unknownExternalStatusLeavesTaskUntouched() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task("RECONCILING", "UNKNOWN")));
        when(gateway.query("task-1")).thenReturn(
                new DeviceOpsTaskSnapshot("task-1", null, "UNKNOWN", "NOT_DISPATCHED",
                        null, null, null, null));

        service.reconcileDue(0L);

        verify(taskMapper, never()).updateReconciliationState(any());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    @Test
    void gatewayFailureSkipsTaskWithoutStateChange() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(any())).thenReturn(List.of(task("DISPATCHED", "EXECUTING")));
        when(gateway.query("task-1")).thenThrow(new IllegalStateException("DAC_QUERY_RESPONSE_INCOMPLETE"));

        service.reconcileDue(0L);

        verify(taskMapper, never()).updateReconciliationState(any());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    private CollectionTaskDO task(String technicalStage, String externalStatus) {
        CollectionTaskDO task = new CollectionTaskDO();
        task.setId(1L);
        task.setTenantId(0L);
        task.setPlatformTaskId("task-1");
        task.setExternalTaskId("dac-1");
        task.setStatus("DISPATCHED");
        task.setTechnicalStage(technicalStage);
        task.setExternalStatus(externalStatus);
        return task;
    }

    private DeviceOpsTaskSnapshot snapshot(String externalStatus) {
        return new DeviceOpsTaskSnapshot("task-1", "dac-1", externalStatus, null,
                null, null, null, null);
    }
}
