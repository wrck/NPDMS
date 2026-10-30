package cn.iocoder.yudao.module.pms.cutover.service.task.command;

import lombok.Builder;

@Builder
public record ReviseCutoverTaskCommand(Long tenantId, Long actorId, Long taskId, Long expectedTaskVersion,
                                      Long expectedProjectScopeVersion, String targetStage, String reason, Long configurationRevisionId,
                                      String idempotencyKey, String correlationId) implements CutoverTaskMutation { }
