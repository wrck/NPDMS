package cn.iocoder.yudao.module.system.api.audit.dto;

/** 只包含脱敏明细的业务审计命令。 */
public record BusinessAuditCommand(long tenantId, Long actorId, String operationCode, String resourceType,
                                   Long resourceId, String decisionCode, String correlationId,
                                   String redactedDetailJson) {
}
