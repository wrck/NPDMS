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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

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
        ConditionalOnProperty condition = TemporaryCollectionDispatchService.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("pms.integration.device-ops", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
    }

    @Test
    void acceptedDispatchClearsSecretAndMovesTaskToDispatched() {
        task.setSourceContext("IMP");
        task.setSourceObjectType("Configuration");
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenReturn(new DeviceOpsDispatchResult(
                "task-1", "external-1", "ACCEPTED", true, false, "trace-1"));

        service.dispatch(command(secret));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper, org.mockito.Mockito.times(2)).updateDispatchState(update.capture());
        assertEquals("DISPATCHING", update.getAllValues().getFirst().technicalStage());
        assertEquals("DISPATCHED", update.getValue().status());
        assertEquals("ACCEPTED", update.getValue().externalStatus());
        var sent = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand.class);
        verify(gatewayApi).dispatch(sent.capture());
        assertEquals("IMP", sent.getValue().sourceContext());
        assertEquals("Configuration", sent.getValue().sourceObjectType());
    }

    @Test
    void explicitRejectionMovesTaskToFailedWithoutBackgroundReplay() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenReturn(new DeviceOpsDispatchResult(
                "task-1", null, "REJECTED", false, false, "trace-1"));

        assertThrows(IllegalStateException.class, () -> service.dispatch(command(secret)));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper, org.mockito.Mockito.times(2)).updateDispatchState(update.capture());
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
        verify(taskMapper, org.mockito.Mockito.times(2)).updateDispatchState(update.capture());
        assertEquals("RECONCILING", update.getValue().technicalStage());
    }

    @Test
    void deterministicClientFailureMovesTaskToDispatchFailed() {
        char[] secret = "temporary-secret".toCharArray();
        when(gatewayApi.dispatch(any())).thenThrow(new IllegalArgumentException("invalid endpoint"));

        assertThrows(IllegalStateException.class, () -> service.dispatch(command(secret)));

        assertArrayEquals(new char[16], secret);
        ArgumentCaptor<CollectionTaskDispatchUpdate> update = ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);
        verify(taskMapper, org.mockito.Mockito.times(2)).updateDispatchState(update.capture());
        assertEquals("FAILED", update.getValue().status());
        assertEquals("DISPATCH_FAILED", update.getValue().technicalStage());
        assertEquals("CLIENT_DISPATCH_ERROR", update.getValue().failureCategory());
    }

    @Test
    void concurrentDispatchClaimCannotSendTwice() {
        char[] secret = "temporary-secret".toCharArray();
        var command = command(secret);
        when(taskMapper.updateDispatchState(any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.dispatch(command));
        org.mockito.Mockito.verifyNoInteractions(gatewayApi);
        assertArrayEquals(new char[16], secret);
    }

    @Test void revokedSavedGrantNeverReachesDacAfterTaskWasPrepared() {
        var saved=org.mockito.Mockito.mock(CollectionConnectionService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"savedConnections",saved);
        task.setCredentialMode("SAVED_CREDENTIAL");
        when(taskMapper.selectByTenantAndPlatformTaskId(0L,"task-1")).thenReturn(task);
        when(taskMapper.updateDispatchState(any())).thenReturn(1);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {
            assertThrows(IllegalStateException.class,()->service.dispatchSaved(new cn.iocoder.yudao.module.pms.platform.api.collection.CollectionDispatchApi.SavedCommand(0L,"task-1",List.of("show run"),"operator","saved-1",4L,"trace")));
            org.mockito.Mockito.verifyNoInteractions(gatewayApi);
            var update=ArgumentCaptor.forClass(CollectionTaskDispatchUpdate.class);verify(taskMapper).updateDispatchState(update.capture());
            assertEquals("FAILED",update.getValue().status());assertEquals("CREDENTIAL_AUTHORIZATION_REVOKED",update.getValue().failureCategory());
        } finally {cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
    }
    @Test void pendingTaskCanBeCancelledOnlyWithDurableProviderProof() {
        when(taskMapper.selectByTenantAndPlatformTaskId(0L,"task-1")).thenReturn(task);
        when(gatewayApi.query("task-1")).thenReturn(new cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsTaskSnapshot("task-1",null,"CANCELLED","CANCELLED_BEFORE_DISPATCH",null,null,null,null));
        when(taskMapper.updateUndispatchedCancellation(any())).thenReturn(1);
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.setTenantId(0L);
        try {service.cancel(0L,"task-1");verify(taskMapper).updateUndispatchedCancellation(any());}
        finally {cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();}
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
