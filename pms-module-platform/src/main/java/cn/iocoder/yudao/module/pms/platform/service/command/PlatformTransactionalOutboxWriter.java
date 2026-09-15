package cn.iocoder.yudao.module.pms.platform.service.command;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class PlatformTransactionalOutboxWriter implements PlatformBusinessEventApi {

    static final String STATUS_PENDING = "PENDING";

    private final PlatformOutboxEventMapper outboxMapper;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public PlatformTransactionalOutboxWriter(PlatformOutboxEventMapper outboxMapper,
            org.springframework.context.ApplicationEventPublisher publisher) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void append(String aggregateType, String aggregateKey, PlatformCommandExecutionApi.BusinessEvent event) {
        appendAt(aggregateType, aggregateKey, event, LocalDateTime.now());
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void appendAt(String aggregateType, String aggregateKey, PlatformCommandExecutionApi.BusinessEvent event, LocalDateTime notBefore) {
        if (notBefore == null) throw new IllegalArgumentException("平台业务事件投递时间不能为空");
        if (event == null || event.eventId() == null || event.eventId().length() > 64)
            throw new IllegalArgumentException("平台业务事件标识无效");
        var payload = JsonUtils.parseTree(event.eventPayload());
        if (payload == null || !payload.isObject() || !event.eventId().equals(payload.path("eventId").asText()))
            throw new IllegalArgumentException("平台业务事件标识与载荷不一致");
        write(TenantContextHolder.getRequiredTenantId(), event, aggregateType, aggregateKey, LocalDateTime.now(), notBefore);
    }

    @Transactional(rollbackFor = Exception.class)
    public void write(Long tenantId, PlatformCommandExecutionApi.BusinessEvent event,
                      String aggregateType, String aggregateKey, LocalDateTime occurredAt) {
        write(tenantId, event, aggregateType, aggregateKey, occurredAt, null);
    }

    private void write(Long tenantId, PlatformCommandExecutionApi.BusinessEvent event,
                       String aggregateType, String aggregateKey, LocalDateTime occurredAt, LocalDateTime notBefore) {
        if (tenantId == null || tenantId < 0 || event == null || isBlank(event.eventId())
                || isBlank(event.eventType()) || isBlank(event.eventPayload())
                || isBlank(aggregateType) || isBlank(aggregateKey) || occurredAt == null) {
            throw new IllegalArgumentException("平台Outbox事件事实不完整");
        }
        PlatformOutboxEventDO outbox = new PlatformOutboxEventDO();
        outbox.setTenantId(tenantId);
        outbox.setEventId(event.eventId());
        outbox.setEventType(event.eventType());
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateKey(aggregateKey);
        outbox.setPayload(event.eventPayload());
        outbox.setStatus(STATUS_PENDING);
        outbox.setOccurredAt(occurredAt);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(notBefore);
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("平台Outbox事件写入失败");
        }
        publisher.publishEvent(new cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended(
                new cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO(
                        event.eventId(), event.eventType(), event.eventPayload(), 0, tenantId, occurredAt), notBefore));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
