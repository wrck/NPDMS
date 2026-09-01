package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import tools.jackson.databind.JsonNode;

public record SaveCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId,
                                          Integer expectedTaskVersion, Integer expectedPlanVersion, JsonNode content,
                                          String idempotencyKey, String correlationId) {
    /** 仅保留既有测试夹具源码兼容；保存时使用草稿冻结范围水位。 */
    @Deprecated
    public SaveCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId, Integer expectedTaskVersion,
                                       Integer expectedPlanVersion, Long ignoredProjectScopeVersion, JsonNode content,
                                       String idempotencyKey, String correlationId) {
        this(tenantId, actorId, taskId, expectedTaskVersion, expectedPlanVersion, content,
                idempotencyKey, correlationId);
    }
}
