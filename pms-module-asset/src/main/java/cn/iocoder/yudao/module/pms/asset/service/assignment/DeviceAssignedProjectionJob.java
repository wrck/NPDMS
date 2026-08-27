package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.DeviceAssignedPayload;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.RebuildDeviceAncestorProjectionCommand;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectAncestorQueryApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceAssignedProjectionJob implements JobHandler {

    static final int BATCH_SIZE = 50;
    static final long MAX_RETRY_DELAY_MINUTES = 60;

    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final ProjectAncestorQueryApi ancestorQueryApi;
    private final DeviceMapper deviceMapper;
    private final DeviceAncestorProjectionService projectionService;

    @Override
    @TenantJob
    public String execute(String param) {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery("DeviceAssigned", dueAt, BATCH_SIZE));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                deliver(message);
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException ex) {
                LocalDateTime nextRetryTime = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(
                        message.eventId(), message.retryCount(), nextRetryTime);
                retried++;
                log.warn("[execute][设备归属事件({})投影失败，计划于({})重试]",
                        message.eventId(), nextRetryTime, ex);
            }
        }
        return String.format("设备祖先投影成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private void deliver(PlatformOutboxMessageDTO message) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (message == null || message.eventId() == null || message.eventId().isBlank()
                || !"DeviceAssigned".equals(message.eventType())
                || !Objects.equals(message.tenantId(), tenantId)) {
            throw new IllegalArgumentException("设备归属事件元数据不完整");
        }
        DeviceAssignedPayload payload = JsonUtils.parseObject(
                message.payload(), DeviceAssignedPayload.class);
        validatePayload(payload);
        DeviceDO device = deviceMapper.selectByTenantAndId(tenantId, payload.deviceId());
        if (device == null || device.getSn() == null || device.getSn().isBlank()) {
            throw new IllegalStateException("DEVICE_NOT_FOUND");
        }
        ProjectAncestorResult ancestors = ancestorQueryApi.getAncestors(
                new ProjectAncestorQuery(tenantId, payload.newProjectId(), null));
        projectionService.rebuild(new RebuildDeviceAncestorProjectionCommand(
                tenantId, device.getSn(), payload.newProjectId(), ancestors.ancestorProjectIds(),
                ancestors.treeVersion(), payload.assignmentVersion(), message.eventId(), payload.operationId()));
    }

    private static void validatePayload(DeviceAssignedPayload payload) {
        if (payload == null || payload.deviceId() == null || payload.newProjectId() == null
                || payload.assignmentVersion() == null || payload.effectiveAt() == null
                || payload.operationId() == null || payload.operationId().isBlank()) {
            throw new IllegalArgumentException("设备归属事件冻结载荷不完整");
        }
    }

    private static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
    }
}
