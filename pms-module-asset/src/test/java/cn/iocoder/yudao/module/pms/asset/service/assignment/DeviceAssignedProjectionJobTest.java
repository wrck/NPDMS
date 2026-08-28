package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.RebuildDeviceAncestorProjectionCommand;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAssignedProjectionJobTest {

    @Mock private PlatformOutboxDeliveryApi outboxDeliveryApi;
    @Mock private ProjectAncestorQueryApi ancestorQueryApi;
    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceAncestorProjectionService projectionService;

    private DeviceAssignedProjectionJob job;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        job = new DeviceAssignedProjectionJob(
                outboxDeliveryApi, ancestorQueryApi, deviceMapper, projectionService);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldQueryCurrentAncestorsAndRebuildProjectionIdempotentlyByEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 12, 0);
        PlatformOutboxMessageDTO message = new PlatformOutboxMessageDTO(
                "event-8", "DeviceAssigned",
                "{\"deviceId\":8,\"oldProjectId\":100,\"newProjectId\":200,"
                        + "\"assignmentVersion\":1,\"effectiveAt\":\"2026-08-27T12:00:00\","
                        + "\"operationId\":\"op-8\"}",
                0, 1L, occurredAt);
        when(outboxDeliveryApi.claimDue(any(PlatformOutboxClaimQuery.class))).thenReturn(List.of(message));
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(ancestorQueryApi.getAncestors(new ProjectAncestorQuery(1L, 200L, null)))
                .thenReturn(new ProjectAncestorResult(200L, 100L, 7L, List.of(100L, 150L)));
        when(projectionService.rebuild(any())).thenReturn(true);

        String result = job.execute(null);

        assertEquals("设备祖先投影成功 1 条，待重试 0 条", result);
        verify(projectionService).rebuild(new RebuildDeviceAncestorProjectionCommand(
                1L, "SN-8", 200L, List.of(100L, 150L), 7L, 1L, "event-8", "op-8"));
        verify(outboxDeliveryApi).markDelivered("event-8", 0);
    }

    @Test
    void shouldScheduleRetryWithoutMarkingDeliveredWhenProjectionFails() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 27, 12, 0);
        PlatformOutboxMessageDTO message = new PlatformOutboxMessageDTO(
                "event-9", "DeviceAssigned",
                "{\"deviceId\":8,\"oldProjectId\":100,\"newProjectId\":200,"
                        + "\"assignmentVersion\":1,\"effectiveAt\":\"2026-08-27T12:00:00\","
                        + "\"operationId\":\"op-9\"}",
                2, 1L, occurredAt);
        when(outboxDeliveryApi.claimDue(any(PlatformOutboxClaimQuery.class))).thenReturn(List.of(message));
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(ancestorQueryApi.getAncestors(new ProjectAncestorQuery(1L, 200L, null)))
                .thenThrow(new IllegalStateException("TREE_VERSION_UNAVAILABLE"));

        String result = job.execute(null);

        assertEquals("设备祖先投影成功 0 条，待重试 1 条", result);
        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("event-9"),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(outboxDeliveryApi, never()).markDelivered("event-9", 2);
    }

    @Test
    void shouldScheduleRetryForCrossTenantMessage() {
        PlatformOutboxMessageDTO message = new PlatformOutboxMessageDTO(
                "event-cross", "DeviceAssigned", "{}", 0, 2L,
                LocalDateTime.of(2026, 8, 27, 12, 0));
        when(outboxDeliveryApi.claimDue(any(PlatformOutboxClaimQuery.class))).thenReturn(List.of(message));

        String result = job.execute(null);

        assertEquals("设备祖先投影成功 0 条，待重试 1 条", result);
        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("event-cross"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(deviceMapper, never()).selectByTenantAndId(any(), any());
    }

    @Test
    void shouldScheduleRetryWhenFrozenPayloadIsIncomplete() {
        PlatformOutboxMessageDTO message = new PlatformOutboxMessageDTO(
                "event-incomplete", "DeviceAssigned",
                "{\"deviceId\":8,\"newProjectId\":200}", 0, 1L,
                LocalDateTime.of(2026, 8, 27, 12, 0));
        when(outboxDeliveryApi.claimDue(any(PlatformOutboxClaimQuery.class))).thenReturn(List.of(message));

        String result = job.execute(null);

        assertEquals("设备祖先投影成功 0 条，待重试 1 条", result);
        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("event-incomplete"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(deviceMapper, never()).selectByTenantAndId(any(), any());
    }

    @Test
    void shouldScheduleRetryWhenDeviceDoesNotExist() {
        PlatformOutboxMessageDTO message = new PlatformOutboxMessageDTO(
                "event-missing-device", "DeviceAssigned",
                "{\"deviceId\":8,\"oldProjectId\":100,\"newProjectId\":200,"
                        + "\"assignmentVersion\":1,\"effectiveAt\":\"2026-08-27T12:00:00\","
                        + "\"operationId\":\"op-missing\"}",
                0, 1L, LocalDateTime.of(2026, 8, 27, 12, 0));
        when(outboxDeliveryApi.claimDue(any(PlatformOutboxClaimQuery.class))).thenReturn(List.of(message));
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(null);

        String result = job.execute(null);

        assertEquals("设备祖先投影成功 0 条，待重试 1 条", result);
        verify(outboxDeliveryApi).scheduleRetry(
                org.mockito.ArgumentMatchers.eq("event-missing-device"),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(ancestorQueryApi, never()).getAncestors(any());
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setTenantId(1L);
        device.setSn("SN-8");
        return device;
    }
}
