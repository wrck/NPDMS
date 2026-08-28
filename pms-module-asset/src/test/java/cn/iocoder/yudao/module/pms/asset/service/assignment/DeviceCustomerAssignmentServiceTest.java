package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceCustomerCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceCustomerAssignmentResult;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCustomerAssignmentServiceTest {

    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private DeviceAssignmentMapper assignmentMapper;

    private DeviceCustomerAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCustomerAssignmentService(customerQueryApi, commandExecutionApi, assignmentMapper);
        lenient().doAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.NEW,
                invocation.<Supplier<DeviceCustomerAssignmentResult>>getArgument(3).get()))
                .when(commandExecutionApi).execute(any(), anyString(),
                        eq(DeviceCustomerAssignmentResult.class), any(), any());
    }

    @Test
    void shouldRejectMissingCustomerWithoutAssignmentChanges() {
        when(customerQueryApi.getCustomer(300L)).thenReturn(null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 300L)));

        assertEquals("CUSTOMER_NOT_FOUND", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldRejectCrossTenantCustomerWithoutAssignmentChanges() {
        when(customerQueryApi.getCustomer(300L)).thenReturn(customer(2L, "ENABLED"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 300L)));

        assertEquals("CUSTOMER_NOT_FOUND", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldRejectDisabledCustomerWithoutAssignmentChanges() {
        when(customerQueryApi.getCustomer(300L)).thenReturn(customer(1L, "DISABLED"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 300L)));

        assertEquals("CUSTOMER_NOT_REFERENCEABLE", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldCloseOldRelationshipInsertNewAndCasCustomerProjection() {
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 11, 0);
        when(customerQueryApi.getCustomer(301L)).thenReturn(customer(1L, "ENABLED"));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device(100L, 300L, 0L));
        when(assignmentMapper.selectCurrentCustomer(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(relationship(21L, 300L, 0L));
        when(assignmentMapper.closeCurrentCustomer(1L, 8L, effectiveAt, 1L)).thenReturn(1);
        when(assignmentMapper.updateDeviceCustomerIfMatch(
                new DeviceCustomerAssignmentUpdate(1L, 8L, 301L, 0L, 1L))).thenReturn(1);

        DeviceCustomerAssignmentResult result = service.assign(command(0L, 301L, effectiveAt));

        assertEquals(1L, result.assignmentVersion());
        assertEquals(301L, result.customerId());
        verify(assignmentMapper).insertCustomerRelationship(any(DeviceCustomerRelationshipDO.class));
        verify(assignmentMapper).closeCurrentCustomer(1L, 8L, effectiveAt, 1L);
        verify(assignmentMapper).updateDeviceCustomerIfMatch(
                new DeviceCustomerAssignmentUpdate(1L, 8L, 301L, 0L, 1L));
    }

    @Test
    void shouldReturnReplayResultWithoutAssignmentChanges() {
        DeviceCustomerAssignmentResult first = new DeviceCustomerAssignmentResult(
                8L, 301L, 1L, "op-customer-8", false);
        when(customerQueryApi.getCustomer(301L)).thenReturn(customer(1L, "ENABLED"));
        when(commandExecutionApi.execute(any(), anyString(), eq(DeviceCustomerAssignmentResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, first));

        DeviceCustomerAssignmentResult result = service.assign(command(0L, 301L));

        assertTrue(result.replayed());
        assertEquals("op-customer-8", result.operationId());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldRejectIdempotencyDigestConflictWithoutAssignmentChanges() {
        when(customerQueryApi.getCustomer(301L)).thenReturn(customer(1L, "ENABLED"));
        when(commandExecutionApi.execute(any(), anyString(), eq(DeviceCustomerAssignmentResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 301L)));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldRejectStaleCustomerAssignmentVersionBeforeWritingRelationship() {
        when(customerQueryApi.getCustomer(301L)).thenReturn(customer(1L, "ENABLED"));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device(100L, 300L, 2L));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(1L, 301L)));

        assertEquals("VERSION_CONFLICT", failure.getMessage());
        verify(assignmentMapper, never()).closeCurrentCustomer(any(), any(), any(), any());
        verify(assignmentMapper, never()).insertCustomerRelationship(any(DeviceCustomerRelationshipDO.class));
        verify(assignmentMapper, never()).updateDeviceCustomerIfMatch(any(DeviceCustomerAssignmentUpdate.class));
    }

    private AssignDeviceCustomerCommand command(Long expectedVersion, Long customerId) {
        return command(expectedVersion, customerId, LocalDateTime.of(2026, 8, 27, 11, 0));
    }

    private AssignDeviceCustomerCommand command(Long expectedVersion, Long customerId, LocalDateTime effectiveAt) {
        return new AssignDeviceCustomerCommand(
                1L, 8L, customerId, "DIRECT", expectedVersion, "客户调整", "idem-customer-8",
                "b".repeat(64), 9L, "corr-customer-8", effectiveAt);
    }

    private CustomerSummaryDTO customer(Long tenantId, String lifecycleStatus) {
        return new CustomerSummaryDTO(
                300L, tenantId, "CUS-300", "客户300", "客户300",
                lifecycleStatus, "LOCAL", 1L, LocalDateTime.of(2026, 8, 27, 10, 0));
    }

    private DeviceDO device(Long projectId, Long customerId, Long customerAssignmentVersion) {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        device.setProjectId(projectId);
        device.setCustomerId(customerId);
        device.setCustomerAssignmentVersion(customerAssignmentVersion);
        return device;
    }

    private DeviceCustomerRelationshipDO relationship(Long id, Long customerId, Long assignmentVersion) {
        DeviceCustomerRelationshipDO relationship = new DeviceCustomerRelationshipDO();
        relationship.setId(id);
        relationship.setTenantId(1L);
        relationship.setDeviceSn("SN-8");
        relationship.setCustomerId(customerId);
        relationship.setAssignmentVersion(assignmentVersion);
        relationship.setRelationshipType("DIRECT");
        return relationship;
    }
}
