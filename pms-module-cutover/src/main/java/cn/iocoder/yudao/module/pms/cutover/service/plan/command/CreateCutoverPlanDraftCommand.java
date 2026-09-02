package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;

public record CreateCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId,
                                            Integer expectedTaskVersion,
                                            String editMode, CutoverPlanFilePort.FileFact expectedFileFact,
                                            Boolean ownershipConfirmed, String idempotencyKey,
                                            String correlationId) {
    /** 仅保留既有测试夹具源码兼容；范围水位不再接受HTTP输入。 */
    @Deprecated
    public CreateCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId, Integer expectedTaskVersion,
                                         Long ignoredProjectScopeVersion, String editMode,
                                         CutoverPlanFilePort.FileFact expectedFileFact, Boolean ownershipConfirmed,
                                         String idempotencyKey, String correlationId) {
        this(tenantId, actorId, taskId, expectedTaskVersion, editMode, expectedFileFact,
                ownershipConfirmed, idempotencyKey, correlationId);
    }
}
