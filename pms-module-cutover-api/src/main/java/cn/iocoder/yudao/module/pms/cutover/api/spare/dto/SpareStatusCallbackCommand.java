package cn.iocoder.yudao.module.pms.cutover.api.spare.dto;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.module.pms.cutover.api.spare.dto.SpareCallbackContractRules.*;

public record SpareStatusCallbackCommand(
        String eventId,
        Long tenantId,
        String externalSystemCode,
        String externalApplicationNo,
        Long statusVersion,
        String externalStatusRaw,
        Map<String, Object> statusSnapshot,
        LocalDateTime externalOccurredAt,
        String correlationId) {

    public SpareStatusCallbackCommand {
        text(eventId, 128, "eventId");
        positive(tenantId, "tenantId");
        text(externalSystemCode, 64, "externalSystemCode");
        text(externalApplicationNo, 128, "externalApplicationNo");
        positive(statusVersion, "statusVersion");
        text(externalStatusRaw, 128, "externalStatusRaw");
        statusSnapshot = jsonObject(statusSnapshot, "statusSnapshot");
        text(correlationId, 128, "correlationId");
    }
}
