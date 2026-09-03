package cn.iocoder.yudao.module.pms.platform.service.outbox;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.PlatformOutboxDeliveryMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.DueOutboxListQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxDeliveryUpdateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxRetryUpdateQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 受控业务事件集合的Outbox投递状态管理。 */
@Service
public class PlatformOutboxDeliveryApiImpl implements PlatformOutboxDeliveryApi {

    static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "ProjectServiceManagerAssigned", "TaskAssigned", "TaskCompleted",
            "FileVersionCommitted", "FileReferenceAttached", "FileReferenceDetached", "FileArchived",
            "DeviceAssigned", "AcceptanceReportVersionChanged", "SatisfactionTaskCreated",
            "SatisfactionResultVersionChanged", "ImplementationEvidencePublished");
    static final int MAX_BATCH_SIZE = 100;

    @Resource
    private PlatformOutboxDeliveryMapper mapper;

    @Override
    @Transactional
    public List<PlatformOutboxMessageDTO> claimDue(PlatformOutboxClaimQuery query) {
        if (query == null || query.dueAt() == null || query.limit() <= 0 || query.limit() > MAX_BATCH_SIZE
                || query.eventTypes() == null || query.eventTypes().isEmpty()
                || !SUPPORTED_EVENT_TYPES.containsAll(query.eventTypes())) {
            throw new IllegalArgumentException("Outbox领取条件不完整或批次超过上限");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DueOutboxListQuery dalQuery = DueOutboxListQuery.builder()
                .tenantId(tenantId)
                .eventTypes(query.eventTypes())
                .dueAt(query.dueAt())
                .limit(query.limit())
                .build();
        return mapper.selectDueForUpdate(dalQuery).stream()
                .map(event -> new PlatformOutboxMessageDTO(event.getEventId(), event.getEventType(),
                        event.getPayload(), event.getRetryCount(), event.getTenantId(), event.getOccurredAt()))
                .toList();
    }

    @Override
    @Transactional
    public void markDelivered(String eventId, int expectedRetryCount) {
        validateCas(eventId, expectedRetryCount);
        if (mapper.markDeliveredIfPending(new OutboxDeliveryUpdateQuery(
                TenantContextHolder.getRequiredTenantId(), eventId, expectedRetryCount)) != 1) {
            throw new IllegalStateException("OUTBOX_DELIVERY_CAS_CONFLICT");
        }
    }

    @Override
    @Transactional
    public void scheduleRetry(String eventId, int expectedRetryCount, LocalDateTime nextRetryTime) {
        validateCas(eventId, expectedRetryCount);
        if (nextRetryTime == null) {
            throw new IllegalArgumentException("Outbox下次重试时间不能为空");
        }
        if (mapper.scheduleRetryIfPending(new OutboxRetryUpdateQuery(
                TenantContextHolder.getRequiredTenantId(), eventId, expectedRetryCount, nextRetryTime)) != 1) {
            throw new IllegalStateException("OUTBOX_RETRY_CAS_CONFLICT");
        }
    }

    private static void validateCas(String eventId, int expectedRetryCount) {
        if (eventId == null || eventId.isBlank() || expectedRetryCount < 0) {
            throw new IllegalArgumentException("Outbox CAS条件不完整");
        }
    }
}
