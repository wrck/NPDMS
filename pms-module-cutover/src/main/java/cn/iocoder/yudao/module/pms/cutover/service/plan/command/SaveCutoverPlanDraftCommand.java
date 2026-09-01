package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import tools.jackson.databind.JsonNode;

public record SaveCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId,
                                          Integer expectedTaskVersion, Integer expectedPlanVersion,
                                          Long expectedProjectScopeVersion, JsonNode content,
                                          String idempotencyKey, String correlationId) {
}
