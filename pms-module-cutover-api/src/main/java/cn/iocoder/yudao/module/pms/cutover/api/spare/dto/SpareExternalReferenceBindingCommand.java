package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareCallbackContractRules.*;

public record SpareExternalReferenceBindingCommand(
        String eventId,
        Long tenantId,
        String platformRequestId,
        String externalSystemCode,
        String externalRequestId,
        String externalApplicationNo,
        LocalDateTime occurredAt,
        String correlationId) {

    public SpareExternalReferenceBindingCommand {
        text(eventId, 128, "eventId");
        positive(tenantId, "tenantId");
        text(platformRequestId, 128, "platformRequestId");
        text(externalSystemCode, 64, "externalSystemCode");
        text(externalRequestId, 128, "externalRequestId");
        text(externalApplicationNo, 128, "externalApplicationNo");
        if (occurredAt == null) throw invalid("occurredAt");
        text(correlationId, 128, "correlationId");
    }
}
