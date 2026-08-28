package cn.iocoder.yudao.module.pms.project.api.participant.dto;

import java.util.Set;

/** 项目资格与当前参与人事实。 */
public record ProjectParticipantFact(
        Long projectId,
        Long userId,
        Set<String> effectiveRoleCodes,
        String assignmentType,
        String lifecycleStatus,
        String currentStage,
        Integer projectVersion,
        Long factVersion) {

    public ProjectParticipantFact {
        effectiveRoleCodes = Set.copyOf(effectiveRoleCodes);
    }

}
