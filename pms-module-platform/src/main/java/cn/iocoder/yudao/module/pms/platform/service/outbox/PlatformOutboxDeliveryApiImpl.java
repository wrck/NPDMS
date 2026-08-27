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

/** Outbox投递状态管理。 */
@Service
public class PlatformOutboxDeliveryApiImpl implements PlatformOutboxDeliveryApi {

    static final int MAX_BATCH_SIZE = 100;

    @Resource
    private PlatformOutboxDeliveryMapper mapper;

    @Override
    @Transactional
    public List<PlatformOutboxMessageDTO> claimDue(PlatformOutboxClaimQuery query) {
        if (query == null || query.eventType() == null || query.eventType().isBlank()
                || query.dueAt() == null || query.limit() <= 0 || query.limit() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Outbox领取条件不完整或批次超过上限");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DueOutboxListQuery dalQuery = DueOutboxListQuery.builder()
                .tenantId(tenantId)
                .eventType(query.eventType())
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
        mapper.markDeliveredIfPending(new OutboxDeliveryUpdateQuery(
                TenantContextHolder.getRequiredTenantId(), eventId, expectedRetryCount));
    }

    @Override
    @Transactional
    public void scheduleRetry(String eventId, int expectedRetryCount, LocalDateTime nextRetryTime) {
        validateCas(eventId, expectedRetryCount);
        if (nextRetryTime == null) {
            throw new IllegalArgumentException("Outbox下次重试时间不能为空");
        }
        mapper.scheduleRetryIfPending(new OutboxRetryUpdateQuery(
                TenantContextHolder.getRequiredTenantId(), eventId, expectedRetryCount, nextRetryTime));
    }

    private static void validateCas(String eventId, int expectedRetryCount) {
        if (eventId == null || eventId.isBlank() || expectedRetryCount < 0) {
            throw new IllegalArgumentException("Outbox CAS条件不完整");
        }
    }
}
