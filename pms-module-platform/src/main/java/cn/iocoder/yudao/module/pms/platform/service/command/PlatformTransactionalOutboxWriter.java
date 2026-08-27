package cn.iocoder.yudao.module.pms.platform.service.command;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class PlatformTransactionalOutboxWriter {

    static final String STATUS_PENDING = "PENDING";

    private final PlatformOutboxEventMapper outboxMapper;

    public PlatformTransactionalOutboxWriter(PlatformOutboxEventMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void write(Long tenantId, PlatformCommandExecutionApi.BusinessEvent event,
                      String aggregateType, String aggregateKey, LocalDateTime occurredAt) {
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
        if (outboxMapper.insert(outbox) != 1) {
            throw new IllegalStateException("平台Outbox事件写入失败");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
