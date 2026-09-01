package cn.iocoder.yudao.module.pms.project.api.systemqualification.dto;

/** 内部命令锁定后的当前项目资格事实。 */
public record ProjectSystemQualificationFact(
        Long projectId,
        Long currentManagerUserId,
        String lifecycleStatus,
        String currentStage,
        Integer currentProjectVersion,
        Long currentParticipantFactVersion,
        Long currentTreeVersion) {
}
