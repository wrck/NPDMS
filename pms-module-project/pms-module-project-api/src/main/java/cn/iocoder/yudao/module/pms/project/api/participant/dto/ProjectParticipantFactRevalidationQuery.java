package cn.iocoder.yudao.module.pms.project.api.participant.dto;

import java.util.Set;

/** SOL写入前的PROJ锁定重验请求；tenantId只取受信调用上下文。 */
public record ProjectParticipantFactRevalidationQuery(
        Long projectId,
        Long userId,
        Integer expectedProjectVersion,
        String requiredLifecycleStatus,
        String requiredCurrentStage,
        Set<String> requiredRoleCodes) {

    public ProjectParticipantFactRevalidationQuery {
        if (requiredRoleCodes != null) {
            requiredRoleCodes = Set.copyOf(requiredRoleCodes);
        }
    }

}
