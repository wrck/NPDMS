package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Set;

/** 传输确认只表示该页已持久化；业务或节点完成由后续正式证据判定负责。 */
@Service
@RequiredArgsConstructor
public class ProjectResultSubscriptionDelivery {
    private final ProjectResultSubscriptionWorker worker;
    private final ProjectResultSubscriptionFanout fanout;

    public static Set<String> eventTypes() {
        return Set.of(BusinessResultChange.EVENT_TYPE, ResultSubscriptionWakeup.EVENT_TYPE, ResultSubscriptionFanoutEvent.EVENT_TYPE);
    }
    public boolean deliver(PlatformOutboxMessageDTO message) {
        if (message == null || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !eventTypes().contains(message.eventType())) throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
        JsonNode json = JsonUtils.getObjectMapper().readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(message.payload());
        if (json == null || !json.isObject() || !json.path("eventVersion").isIntegralNumber()
                || !"1".equals(json.path("eventVersion").asText()) || !Objects.equals(message.eventId(), json.path("eventId").asText()))
            throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
        if (ResultSubscriptionWakeup.EVENT_TYPE.equals(message.eventType())) {
            requireNumbers(json, "tenantId", "projectId", "subscriptionId", "planVersionId", "executionId", "contractId");
            var event = read(message.payload(), ResultSubscriptionWakeup.class);
            if (!Objects.equals(message.tenantId(), event.tenantId())) throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
            worker.process(event);
        } else if (BusinessResultChange.EVENT_TYPE.equals(message.eventType())) {
            requireNumbers(json, "sequence"); requireNumbers(json.path("channel"), "id", "tenantId", "projectId");
            var event = read(message.payload(), BusinessResultChange.class);
            if (!Objects.equals(message.tenantId(), event.channel().tenantId())) throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
            fanout.accept(event, 0);
        } else {
            requireNumbers(json, "afterSubscriptionId"); requireNumbers(json.path("source"), "sequence", "eventVersion");
            requireNumbers(json.path("source").path("channel"), "id", "tenantId", "projectId");
            var event = read(message.payload(), ResultSubscriptionFanoutEvent.class);
            if (!Objects.equals(message.tenantId(), event.source().channel().tenantId())) throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
            fanout.accept(event.source(), event.afterSubscriptionId());
        }
        return true;
    }

    private void requireNumbers(JsonNode document, String... fields) {
        for (String field : fields) {
            var token = document.path(field);
            if (!token.isIntegralNumber() || !token.canConvertToLong())
                throw new IllegalArgumentException("SUBSCRIPTION_ENVELOPE_INVALID");
        }
    }
    private <T> T read(String payload, Class<T> type) {
        return JsonUtils.getObjectMapper().readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(payload);
    }
}
