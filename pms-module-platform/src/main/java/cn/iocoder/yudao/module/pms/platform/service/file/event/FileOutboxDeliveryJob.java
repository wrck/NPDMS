package cn.iocoder.yudao.module.pms.platform.service.file.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileOutboxDeliveryJob implements JobHandler {

    static final Set<String> FILE_EVENT_TYPES = Set.of(FileEventFactory.VERSION_COMMITTED,
            FileEventFactory.REFERENCE_ATTACHED, FileEventFactory.REFERENCE_DETACHED,
            FileEventFactory.FILE_ARCHIVED);
    static final Set<String> SUCCESSFUL_SCAN_STATUSES = Set.of("PASSED", "SKIPPED");
    static final int BATCH_SIZE = 50;
    static final long MAX_RETRY_DELAY_MINUTES = 60;

    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return deliverDueEvents();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = deliverDueEvents());
        return result[0];
    }

    private String deliverDueEvents() {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(dueAt, BATCH_SIZE, FILE_EVENT_TYPES));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                eventPublisher.publishEvent(toMessage(message));
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException exception) {
                LocalDateTime nextRetryTime = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(message.eventId(), message.retryCount(), nextRetryTime);
                retried++;
                log.warn("[execute][文件事件({})投递失败，计划于({})重试]",
                        message.eventId(), nextRetryTime, exception);
            }
        }
        return String.format("文件事件投递成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private Object toMessage(PlatformOutboxMessageDTO message) {
        if (message == null || message.eventId() == null || message.eventId().isBlank()
                || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("文件事件元数据不完整");
        }
        return switch (message.eventType()) {
            case FileEventFactory.VERSION_COMMITTED -> validateVersionCommitted(message);
            case FileEventFactory.REFERENCE_ATTACHED -> validateReferenceAttached(message);
            case FileEventFactory.REFERENCE_DETACHED -> validateReferenceDetached(message);
            case FileEventFactory.FILE_ARCHIVED -> validateArchived(message);
            default -> throw new IllegalArgumentException("未知文件事件类型");
        };
    }

    private FileVersionCommittedMessage validateVersionCommitted(PlatformOutboxMessageDTO message) {
        FileVersionCommittedMessage payload = JsonUtils.parseObject(message.payload(), FileVersionCommittedMessage.class);
        requireCommon(message, payload == null ? null : payload.eventId(), payload == null ? null : payload.tenantId());
        if (payload.artifactId() == null || payload.versionNo() == null || payload.versionNo() <= 0
                || isBlank(payload.sha256()) || !SUCCESSFUL_SCAN_STATUSES.contains(payload.scanStatus())
                || payload.occurredAt() == null || isBlank(payload.operationId())) {
            throw new IllegalArgumentException("文件版本提交事件载荷不完整");
        }
        return payload;
    }

    private FileReferenceAttachedMessage validateReferenceAttached(PlatformOutboxMessageDTO message) {
        FileReferenceAttachedMessage payload = JsonUtils.parseObject(message.payload(), FileReferenceAttachedMessage.class);
        requireCommon(message, payload == null ? null : payload.eventId(), payload == null ? null : payload.tenantId());
        requireReference(payload.referenceId(), payload.artifactId(), payload.versionNo(), payload.ownerContext(),
                payload.objectType(), payload.objectId(), payload.purposeCode(), payload.occurredAt(), payload.operationId());
        return payload;
    }

    private FileReferenceDetachedMessage validateReferenceDetached(PlatformOutboxMessageDTO message) {
        FileReferenceDetachedMessage payload = JsonUtils.parseObject(message.payload(), FileReferenceDetachedMessage.class);
        requireCommon(message, payload == null ? null : payload.eventId(), payload == null ? null : payload.tenantId());
        requireReference(payload.referenceId(), payload.artifactId(), payload.versionNo(), payload.ownerContext(),
                payload.objectType(), payload.objectId(), payload.purposeCode(), payload.occurredAt(), payload.operationId());
        return payload;
    }

    private FileArchivedMessage validateArchived(PlatformOutboxMessageDTO message) {
        FileArchivedMessage payload = JsonUtils.parseObject(message.payload(), FileArchivedMessage.class);
        requireCommon(message, payload == null ? null : payload.eventId(), payload == null ? null : payload.tenantId());
        requireReference(payload.referenceId(), payload.artifactId(), payload.versionNo(), payload.ownerContext(),
                payload.objectType(), payload.objectId(), payload.purposeCode(), payload.occurredAt(), payload.operationId());
        if (isBlank(payload.archiveBatchId()) || isBlank(payload.businessDecisionRef())) {
            throw new IllegalArgumentException("文件归档事件载荷不完整");
        }
        return payload;
    }

    private void requireCommon(PlatformOutboxMessageDTO message, String payloadEventId, Long payloadTenantId) {
        if (!Objects.equals(message.eventId(), payloadEventId)
                || !Objects.equals(message.tenantId(), payloadTenantId)) {
            throw new IllegalArgumentException("文件事件身份不一致");
        }
    }

    private void requireReference(Long referenceId, Long artifactId, Integer versionNo, String ownerContext,
                                  String objectType, String objectId, String purposeCode,
                                  LocalDateTime occurredAt, String operationId) {
        if (referenceId == null || artifactId == null || versionNo == null || versionNo <= 0
                || isBlank(ownerContext) || isBlank(objectType) || isBlank(objectId) || isBlank(purposeCode)
                || occurredAt == null || isBlank(operationId)) {
            throw new IllegalArgumentException("文件引用事件载荷不完整");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
    }
}
