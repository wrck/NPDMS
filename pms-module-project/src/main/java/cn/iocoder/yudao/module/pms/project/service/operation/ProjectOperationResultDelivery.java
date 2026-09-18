package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.Set;

/** Delivery ACK is independent of business completion. The existing durable Outbox owns retries. */
@Service
@RequiredArgsConstructor
public class ProjectOperationResultDelivery {
    private final ProjectOperationResultFanout fanout;
    private final ProjectOperationNodeResultProcessor processor;
    @jakarta.annotation.Resource
    private org.springframework.beans.factory.ObjectProvider<ProjectResultSubscriptionDelivery> subscriptions;
    public static Set<String> eventTypes() {
        var types = new java.util.HashSet<>(ProjectResultSubscriptionDelivery.eventTypes());
        types.add(BusinessOperationResultEvent.EVENT_TYPE); types.add(ProjectResultTargetEvent.EVENT_TYPE);
        return Set.copyOf(types);
    }
    public boolean deliver(PlatformOutboxMessageDTO message) {
        if (!Objects.equals(message.tenantId(),TenantContextHolder.getRequiredTenantId())) throw new IllegalArgumentException("RESULT_TENANT_INVALID");
        if (ProjectResultSubscriptionDelivery.eventTypes().contains(message.eventType())) return subscriptions.getObject().deliver(message);
        if (BusinessOperationResultEvent.EVENT_TYPE.equals(message.eventType())) {
            var source = JsonUtils.parseObject(message.payload(),BusinessOperationResultEvent.class);
            source.requireEnvelope(message.eventId(),message.tenantId()); fanout.accept(source); return true;
        }
        if (ProjectResultTargetEvent.EVENT_TYPE.equals(message.eventType())) {
            var target = JsonUtils.parseObject(message.payload(),ProjectResultTargetEvent.class);
            if (!Objects.equals(message.eventId(),target.eventId()) || !Objects.equals(message.tenantId(),target.source().tenantId()))
                throw new IllegalArgumentException("RESULT_TARGET_ENVELOPE_INVALID");
            processor.process(target); return true;
        }
        throw new IllegalArgumentException("RESULT_EVENT_TYPE_UNSUPPORTED");
    }
}
