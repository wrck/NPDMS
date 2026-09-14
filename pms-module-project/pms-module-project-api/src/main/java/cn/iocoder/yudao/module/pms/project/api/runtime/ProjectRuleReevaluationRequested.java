package cn.iocoder.yudao.module.pms.project.api.runtime;

import java.util.UUID;

/** A wakeup to re-read versioned facts, never a command to advance a node from a cached result. */
public record ProjectRuleReevaluationRequested(String eventId, Long tenantId, Long projectId,
                                               Long actorId, String correlationId) {
    public static final String EVENT_TYPE = "ProjectRuleReevaluationRequested";

    public static ProjectRuleReevaluationRequested create(Long tenantId, Long projectId, Long actorId, String correlationId) {
        return new ProjectRuleReevaluationRequested("project-rules:" + UUID.randomUUID(), tenantId,
                projectId, actorId, correlationId);
    }
}
