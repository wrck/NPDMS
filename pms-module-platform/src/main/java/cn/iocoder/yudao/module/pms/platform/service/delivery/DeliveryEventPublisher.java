package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** 交付标准事件统一出口：单一变更事件类型，绑定层按动作与归属消费（P09）。 */
@Service
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    public static final String EVENT_TYPE = "pms.delivery.changed";

    private final PlatformBusinessEventApi outbox;

    public record DeliveryChangedMessage(String eventId, String ownerModule, String entityType, Long entityId,
                                         String typeCode, String action, String status, LocalDateTime occurredAt) {
    }

    public void publishMaterial(String ownerModule, String entityType, Long entityId, String typeCode,
                                String action) {
        String eventId = UUID.randomUUID().toString();
        String payload = JsonUtils.toJsonString(new DeliveryChangedMessage(eventId, ownerModule, entityType,
                entityId, typeCode, action, null, LocalDateTime.now()));
        outbox.append("DeliveryMaterial", ownerModule + ":" + entityType + ":" + entityId + ":" + typeCode,
                new PlatformCommandExecutionApi.BusinessEvent(eventId, EVENT_TYPE, payload));
    }
}
