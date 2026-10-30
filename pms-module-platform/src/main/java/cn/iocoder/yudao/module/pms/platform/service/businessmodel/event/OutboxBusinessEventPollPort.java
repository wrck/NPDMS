package cn.iocoder.yudao.module.pms.platform.service.businessmodel.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventKind;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventPollPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.event.BusinessEventRecord;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.PlatformOutboxDeliveryMapper;
import tools.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一业务事件受控读取默认实现：按事件类型读取待处理 Outbox 事件，
 * 不改变事件状态；消费进度与去重由消费方检查点管理。
 * 事件读取是平台内部系统观察行为，跨租户读取、由事件自身携带租户身份。
 */
@Component
public class OutboxBusinessEventPollPort implements BusinessEventPollPort {

    private final PlatformOutboxDeliveryMapper outboxMapper;

    public OutboxBusinessEventPollPort(PlatformOutboxDeliveryMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Override
    public List<BusinessEventRecord> pollPending(String eventType, int limit, long afterSequence) {
        // 事件读取是平台内部系统观察行为：跨租户读取待处理事件（忽略租户追加条件），
        // 租户边界由事件载荷与消费方检查点保持。
        List<PlatformOutboxEventDO> rows = TenantUtils.executeIgnore(() ->
                outboxMapper.selectPendingByEventType(eventType, Math.max(1, limit), afterSequence));
        return rows.stream().map(OutboxBusinessEventPollPort::toRecord).toList();
    }

    private static BusinessEventRecord toRecord(PlatformOutboxEventDO row) {
        Map<String, Object> payload = JsonUtils.parseObject(row.getPayload(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> source = payload == null ? Map.of()
                : (Map<String, Object>) payload.getOrDefault("sourceRef", Map.of());
        EntityRef sourceRef = new EntityRef(
                source.get("tenantId") instanceof Number tenant ? tenant.longValue() : row.getTenantId(),
                String.valueOf(source.getOrDefault("ownerModule", "")),
                String.valueOf(source.getOrDefault("entityType", row.getAggregateType())),
                source.get("entityId") instanceof Number entityId ? entityId.longValue()
                        : Long.valueOf(row.getAggregateKey()));
        String kindToken = row.getEventType().replace("pms.business.", "").toUpperCase(Locale.ROOT);
        BusinessEventKind kind;
        try {
            kind = BusinessEventKind.valueOf(kindToken);
        } catch (IllegalArgumentException ex) {
            kind = BusinessEventKind.CHANGED;
        }
        return new BusinessEventRecord(row.getEventId(), sourceRef,
                kind, String.valueOf(payload == null ? null : payload.get("orderBasis")),
                payload == null ? null : (String) payload.get("causationId"), payload,
                row.getId());
    }
}
