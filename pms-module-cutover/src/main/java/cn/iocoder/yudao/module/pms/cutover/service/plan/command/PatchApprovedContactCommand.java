package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import java.time.LocalDateTime;

public record PatchApprovedContactCommand(Long tenantId, Long actorId, Long taskId, Long arrangementId,
                                          Integer expectedPlanVersion, String personName, String phone,
                                          LocalDateTime arrivalTime, String idempotencyKey, String correlationId) {
}
