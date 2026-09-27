package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一业务事件端口平台实现：CHANGED/FORMED/INVALIDATED 事件写入平台 Outbox，
 * 与调用方业务事务同事务提交；消费失败不重执行领域命令。
 */
@Component
public class OutboxBusinessEventPort implements BusinessEventPort {

    private final PlatformTransactionalOutboxWriter outboxWriter;

    public OutboxBusinessEventPort(PlatformTransactionalOutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void append(BusinessEventRecord event) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()
                || event.sourceRef() == null || event.kind() == null) {
            throw new IllegalArgumentException("统一业务事件事实不完整");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.eventId());
        payload.put("kind", event.kind().name());
        payload.put("sourceRef", event.sourceRef());
        payload.put("orderBasis", event.orderBasis());
        payload.put("causationId", event.causationId());
        if (event.payload() != null) {
            payload.putAll(event.payload());
        }
        outboxWriter.write(event.sourceRef().tenantId(),
                new PlatformCommandExecutionApi.BusinessEvent(event.eventId(),
                        eventType(event.kind()), JsonUtils.toJsonString(payload)),
                event.sourceRef().entityType(), event.sourceRef().entityId().toString(),
                LocalDateTime.now());
    }

    private static String eventType(BusinessEventKind kind) {
        return "pms.business." + kind.name().toLowerCase(java.util.Locale.ROOT);
    }
}
