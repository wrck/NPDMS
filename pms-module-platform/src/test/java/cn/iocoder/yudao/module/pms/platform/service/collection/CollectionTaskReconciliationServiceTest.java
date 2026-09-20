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
    void runningStatusKeepsDispatchedTaskUnchanged() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(0L)).thenReturn(List.of(task("DISPATCHED", "EXECUTING")));
        when(gateway.query("task-1")).thenReturn(snapshot("EXECUTING"));

        service.reconcileDue(0L);

        verify(taskMapper, never()).updateReconciliationState(any());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    @Test
    void runningStatusRestoresReconcilingTaskToDispatched() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(0L)).thenReturn(List.of(task("RECONCILING", "EXECUTING")));
        when(gateway.query("task-1")).thenReturn(snapshot("EXECUTING"));
        when(taskMapper.updateReconciliationState(any())).thenReturn(1);

        service.reconcileDue(0L);

        ArgumentCaptor<CollectionTaskReconciliationUpdate> update =
                ArgumentCaptor.forClass(CollectionTaskReconciliationUpdate.class);
        verify(taskMapper).updateReconciliationState(update.capture());
        assertEquals("DISPATCHED", update.getValue().technicalStage());
        verify(taskMapper, never()).updateReconciliationObservation(any());
    }

    @Test
    void terminalStatusParksTaskWithObservedExternalState() {
        when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        when(taskMapper.selectReconciliationDue(0L)).thenReturn(List.of(task("DISPATCHED", "SUCCEEDED")));
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
        when(taskMapper.selectReconciliationDue(0L)).thenReturn(List.of(task("RECONCILING", "UNKNOWN")));
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
        when(taskMapper.selectReconciliationDue(0L)).thenReturn(List.of(task("DISPATCHED", "EXECUTING")));
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
