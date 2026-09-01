package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;

public record CreateCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId,
                                            Integer expectedTaskVersion, Long expectedProjectScopeVersion,
                                            String editMode, CutoverPlanFilePort.FileFact expectedFileFact,
                                            Boolean ownershipConfirmed, String idempotencyKey,
                                            String correlationId) {
}
