package cn.iocoder.yudao.module.pms.commerce.service.scope;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox.CommerceOutboxEventMapper;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryScopeConflictNotifier {
    private static final String TYPE = "DELIVERY_SCOPE_CONFLICT_FROZEN";

    private final ProjectParticipantFactApi participantFactApi;
    private final CommerceOutboxEventMapper outboxMapper;

    public void request(Long tenantId, DeliveryScopeDO scope, String erpSourceVersion,
                        BigDecimal allocatedQuantity, BigDecimal effectiveQuantity,
                        LocalDateTime requestedAt) {
        Recipient recipient = resolve(scope.getProjectId(), requestedAt);
        CommerceOutboxEventDO event = new CommerceOutboxEventDO();
        event.setTenantId(tenantId);
        event.setEventId(eventId(scope, erpSourceVersion));
        event.setEventType("NotificationRequested");
        event.setAggregateType("DeliveryScope");
        event.setAggregateKey(String.valueOf(scope.getId()));
        event.setScopeVersion(scope.getAllocationVersion());
        event.setPayload(JsonUtils.toJsonString(payload(
                tenantId, scope, erpSourceVersion, allocatedQuantity, effectiveQuantity, recipient)));
        event.setStatus("PENDING");
        event.setOccurredAt(requestedAt);
        event.setRetryCount(0);
        outboxMapper.insert(event);
    }

    private Recipient resolve(Long projectId, LocalDateTime requestedAt) {
        try {
            ProjectParticipantFact fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    projectId, null, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), requestedAt));
            if (fact != null && Objects.equals(projectId, fact.projectId()) && fact.userId() != null
                    && fact.userId() > 0 && fact.projectVersion() != null && fact.factVersion() != null
                    && fact.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)) {
                return new Recipient(fact.userId(), fact.projectVersion(), fact.factVersion());
            }
        } catch (RuntimeException ignored) {
            // 业务冲突已冻结；收件人事实不可用时保留角色，等待投递侧重试解析。
        }
        return new Recipient(null, null, null);
    }

    private Map<String, Object> payload(Long tenantId, DeliveryScopeDO scope, String erpSourceVersion,
                                        BigDecimal allocatedQuantity, BigDecimal effectiveQuantity,
                                        Recipient recipient) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("notificationType", TYPE);
        value.put("tenantId", tenantId);
        value.put("projectId", scope.getProjectId());
        value.put("orderLineId", scope.getOrderLineId());
        value.put("deliveryScopeId", scope.getId());
        value.put("allocationVersion", scope.getAllocationVersion());
        value.put("erpSourceVersion", erpSourceVersion);
        value.put("allocatedQuantity", allocatedQuantity);
        value.put("effectiveQuantity", effectiveQuantity);
        value.put("conflictReason", "ERP_EFFECTIVE_QUANTITY_BELOW_CURRENT_ALLOCATION");
        value.put("recipientRole", ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        if (recipient.userId() == null) {
            value.put("recipientResolution", "RETRYABLE_ROLE");
        } else {
            value.put("recipientUserId", recipient.userId());
            value.put("recipientProjectVersion", recipient.projectVersion());
            value.put("recipientFactVersion", recipient.factVersion());
        }
        return value;
    }

    private String eventId(DeliveryScopeDO scope, String erpSourceVersion) {
        String key = TYPE + ':' + scope.getId() + ':' + scope.getAllocationVersion() + ':' + erpSourceVersion;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private record Recipient(Long userId, Integer projectVersion, Long factVersion) {
    }
}
