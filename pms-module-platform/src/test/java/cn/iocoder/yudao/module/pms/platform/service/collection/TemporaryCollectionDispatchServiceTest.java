package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchResult;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemporaryCollectionDispatchServiceTest {

    @Mock CollectionTaskMapper taskMapper;
    @Mock DeviceOpsGatewayApi gatewayApi;

    private TemporaryCollectionDispatchService service;
    private CollectionTaskDO task;

    @BeforeEach
    void setUp() {
        service = new TemporaryCollectionDispatchService(taskMapper, gatewayApi);
        task = task();
    }

    @Test
    void activatesOnlyWhenIntegrationGatewayIsAvailable() {
        ConditionalOnBean condition = TemporaryCollectionDispatchService.class.getAnnotation(ConditionalOnBean.class);
        assertArrayEquals(new Class<?>[]{DeviceOpsGatewayApi.class}, condition.value());
    }

    @Test
    void acceptedDispatchClearsSecretAndMovesTaskToDispatched() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenReturn(new DeviceOpsDispatchResult(
                "task-1", "external-1", "ACCEPTED", true, false, "trace-1"));

        service.dispatch(command(secret));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper).updateDispatchState(update.capture());
        assertEquals("DISPATCHED", update.getValue().status());
        assertEquals("ACCEPTED", update.getValue().externalStatus());
    }

    @Test
    void explicitRejectionMovesTaskToFailedWithoutBackgroundReplay() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenReturn(new DeviceOpsDispatchResult(
                "task-1", null, "REJECTED", false, false, "trace-1"));

        assertThrows(IllegalStateException.class, () -> service.dispatch(command(secret)));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper).updateDispatchState(update.capture());
        assertEquals("FAILED", update.getValue().status());
        assertEquals("DISPATCH_FAILED", update.getValue().technicalStage());
    }

    @Test
    void timeoutMovesTaskToReconcilingAndNeverRetriesSecret() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenThrow(new RuntimeException(new HttpTimeoutException("timeout")));

        assertThrows(IllegalStateException.class, () -> service.dispatch(command(secret)));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper).updateDispatchState(update.capture());
        assertEquals("RECONCILING", update.getValue().technicalStage());
    }

    @Test
    void deterministicClientFailureMovesTaskToDispatchFailed() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenThrow(new IllegalArgumentException("invalid endpoint"));

        assertThrows(IllegalStateException.class, () -> service.dispatch(command(secret)));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper).updateDispatchState(update.capture());
        assertEquals("FAILED", update.getValue().status());
        assertEquals("DISPATCH_FAILED", update.getValue().technicalStage());
        assertEquals("CLIENT_DISPATCH_ERROR", update.getValue().failureCategory());
    }

    private TemporaryCollectionDispatchService.TemporaryDispatchCommand command(char[] secret) {
        when(taskMapper.selectByTenantAndPlatformTaskId(0L, "task-1")).thenReturn(task);
        when(taskMapper.updateDispatchState(any())).thenReturn(1);
        return new TemporaryCollectionDispatchService.TemporaryDispatchCommand(
                0L, "task-1", List.of("show version"), "operator", secret, "DEVICE_OPS", "trace-1");
    }

    private CollectionTaskDO task() {
        CollectionTaskDO value = new CollectionTaskDO();
        value.setId(1L);
        value.setTenantId(0L);
        value.setBatchId(2L);
        value.setPlatformTaskId("task-1");
        value.setProjectId("project-1");
        value.setDeviceId("device-1");
        value.setDeviceName("Device");
        value.setHost("10.0.0.1");
        value.setPort(22);
        value.setProtocol("SSH");
        value.setTemplateId("template-1");
        value.setTemplateVersion("v1");
        value.setTemplateHash("a".repeat(64));
        value.setCredentialMode("TEMPORARY_SECRET");
        value.setTemporaryUsername("operator");
        value.setStatus("CREATED");
        value.setTechnicalStage("PENDING_DISPATCH");
        return value;
    }
}
