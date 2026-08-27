package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAssignmentReconciliationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceProjectAssignmentResult;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceProjectAssignmentServiceTest {

    @Mock private ProjectDeviceAssignmentGuardApi projectGuardApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private DeviceAssignmentMapper assignmentMapper;

    private DeviceProjectAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new DeviceProjectAssignmentService(projectGuardApi, commandExecutionApi, assignmentMapper);
        lenient().doAnswer(invocation -> new PlatformCommandExecutionApi.ExecutionResult<>(
                PlatformCommandExecutionApi.Decision.NEW,
                invocation.<Supplier<DeviceProjectAssignmentResult>>getArgument(3).get()))
                .when(commandExecutionApi).execute(any(), anyString(),
                        eq(DeviceProjectAssignmentResult.class), any(), any());
    }

    @Test
    void shouldRejectProjectGuardWithoutAssignmentChanges() {
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(new ProjectDeviceAssignmentGuardResult(
                        200L, 1L, 300L, 100L, 7L, false, "PROJECT_MANAGE_FORBIDDEN"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 200L)));

        assertEquals("PROJECT_MANAGE_FORBIDDEN", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldCloseOldRelationshipInsertNewAndCasProjectProjection() {
        LocalDateTime effectiveAt = LocalDateTime.of(2026, 8, 27, 10, 0);
        DeviceDO device = device(100L, 0L, 300L);
        DeviceProjectRelationshipDO current = relationship(11L, 100L, 0L);
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 300L));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device);
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(current);
        when(assignmentMapper.closeCurrentProject(1L, 8L, effectiveAt, 1L)).thenReturn(1);
        when(assignmentMapper.updateDeviceProjectIfMatch(
                new DeviceProjectAssignmentUpdate(1L, 8L, 200L, 0L, 1L))).thenReturn(1);

        DeviceProjectAssignmentResult result = service.assign(command(0L, 200L, effectiveAt));

        assertEquals(1L, result.assignmentVersion());
        assertEquals(200L, result.projectId());
        verify(assignmentMapper).insertProjectRelationship(any(DeviceProjectRelationshipDO.class));
        verify(assignmentMapper).closeCurrentProject(1L, 8L, effectiveAt, 1L);
        verify(assignmentMapper).updateDeviceProjectIfMatch(
                new DeviceProjectAssignmentUpdate(1L, 8L, 200L, 0L, 1L));
    }

    @Test
    void shouldFreezeOldProjectIdInDeviceAssignedEvent() {
        AtomicReference<PlatformCommandExecutionApi.SuccessFacts> facts = new AtomicReference<>();
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 300L));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device(100L, 0L, 300L));
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(relationship(11L, 100L, 0L));
        when(assignmentMapper.closeCurrentProject(any(), any(), any(), any())).thenReturn(1);
        when(assignmentMapper.updateDeviceProjectIfMatch(any(DeviceProjectAssignmentUpdate.class))).thenReturn(1);
        when(commandExecutionApi.execute(any(), anyString(), eq(DeviceProjectAssignmentResult.class), any(), any()))
                .thenAnswer(invocation -> {
                    DeviceProjectAssignmentResult result = invocation
                            .<Supplier<DeviceProjectAssignmentResult>>getArgument(3).get();
                    facts.set(invocation
                            .<Function<DeviceProjectAssignmentResult, PlatformCommandExecutionApi.SuccessFacts>>getArgument(4)
                            .apply(result));
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });

        service.assign(command(0L, 200L));

        assertEquals("DeviceAssigned", facts.get().eventType());
        assertTrue(facts.get().eventPayload().contains("\"oldProjectId\":100"));
        assertTrue(facts.get().eventPayload().contains("\"newProjectId\":200"));
    }

    @Test
    void shouldRejectStaleAssignmentVersionBeforeWritingRelationship() {
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 300L));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device(100L, 2L, 300L));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(1L, 200L)));

        assertEquals("VERSION_CONFLICT", failure.getMessage());
        verify(assignmentMapper, never()).closeCurrentProject(any(), any(), any(), any());
        verify(assignmentMapper, never()).insertProjectRelationship(any(DeviceProjectRelationshipDO.class));
        verify(assignmentMapper, never()).updateDeviceProjectIfMatch(any(DeviceProjectAssignmentUpdate.class));
    }

    @Test
    void shouldReturnReplayResultWithoutAssignmentChanges() {
        DeviceProjectAssignmentResult first = new DeviceProjectAssignmentResult(8L, 100L, 200L, 1L, "op-8", false);
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 300L));
        when(commandExecutionApi.execute(any(), anyString(), eq(DeviceProjectAssignmentResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, first));

        DeviceProjectAssignmentResult result = service.assign(command(0L, 200L));

        assertTrue(result.replayed());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldRejectIdempotencyDigestConflictWithoutAssignmentChanges() {
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 300L));
        when(commandExecutionApi.execute(any(), anyString(), eq(DeviceProjectAssignmentResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.assign(command(0L, 200L)));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.getMessage());
        verifyNoInteractions(assignmentMapper);
    }

    @Test
    void shouldCreatePendingReconciliationWithoutOverwritingCustomer() {
        when(projectGuardApi.validate(new ProjectDeviceAssignmentGuardQuery(1L, 200L, 9L)))
                .thenReturn(accepted(200L, 301L));
        when(assignmentMapper.selectDeviceForUpdate(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(device(100L, 0L, 300L));
        when(assignmentMapper.selectCurrentProject(new DeviceAssignmentLockQuery(1L, 8L)))
                .thenReturn(relationship(11L, 100L, 0L));
        when(assignmentMapper.closeCurrentProject(any(), any(), any(), any())).thenReturn(1);
        when(assignmentMapper.updateDeviceProjectIfMatch(any(DeviceProjectAssignmentUpdate.class))).thenReturn(1);

        service.assign(command(0L, 200L));

        verify(assignmentMapper).insertReconciliation(any(DeviceAssignmentReconciliationDO.class));
        verify(assignmentMapper).updateDeviceProjectIfMatch(
                new DeviceProjectAssignmentUpdate(1L, 8L, 200L, 0L, 1L));
    }

    private AssignDeviceProjectCommand command(Long expectedVersion, Long projectId) {
        return command(expectedVersion, projectId, LocalDateTime.of(2026, 8, 27, 10, 0));
    }

    private AssignDeviceProjectCommand command(Long expectedVersion, Long projectId, LocalDateTime effectiveAt) {
        return new AssignDeviceProjectCommand(
                1L, 8L, projectId, expectedVersion, "项目调整", "idem-8",
                "a".repeat(64), 9L, "corr-8", effectiveAt);
    }

    private ProjectDeviceAssignmentGuardResult accepted(Long projectId, Long customerId) {
        return new ProjectDeviceAssignmentGuardResult(projectId, 1L, customerId, 100L, 7L, true, null);
    }

    private DeviceDO device(Long projectId, Long assignmentVersion, Long customerId) {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        device.setProjectId(projectId);
        device.setProjectAssignmentVersion(assignmentVersion);
        device.setCustomerId(customerId);
        return device;
    }

    private DeviceProjectRelationshipDO relationship(Long id, Long projectId, Long assignmentVersion) {
        DeviceProjectRelationshipDO relationship = new DeviceProjectRelationshipDO();
        relationship.setId(id);
        relationship.setTenantId(1L);
        relationship.setDeviceSn("SN-8");
        relationship.setProjectId(projectId);
        relationship.setAssignmentVersion(assignmentVersion);
        relationship.setRelationshipType("DIRECT");
        return relationship;
    }
}
