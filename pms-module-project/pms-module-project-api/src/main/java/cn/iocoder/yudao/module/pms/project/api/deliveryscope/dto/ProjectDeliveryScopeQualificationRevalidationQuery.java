package cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException.Code.INVALID_REQUEST;

/** COM交付范围写命令的冻结项目资格锁定重验请求。 */
public record ProjectDeliveryScopeQualificationRevalidationQuery(
        Long tenantId,
        Long projectId,
        Long expectedRootProjectId,
        Long actorId,
        String expectedLifecycleStatus,
        String expectedCurrentStage,
        Integer expectedProjectVersion,
        Long expectedParticipantFactVersion,
        Long expectedTreeVersion) {

    private static final Set<String> LIFECYCLES = Set.of("ACTIVE", "NORMAL_CLOSED", "EXCEPTION_CLOSED");
    private static final Set<String> STAGES = Set.of("S0", "S1", "S2", "S3", "S4", "S5", "S6");

    public ProjectDeliveryScopeQualificationRevalidationQuery {
        if (tenantId == null || tenantId <= 0 || projectId == null || projectId <= 0
                || expectedRootProjectId == null || expectedRootProjectId <= 0
                || actorId == null || actorId <= 0 || expectedLifecycleStatus == null
                || !LIFECYCLES.contains(expectedLifecycleStatus) || expectedCurrentStage == null
                || !STAGES.contains(expectedCurrentStage) || expectedProjectVersion == null
                || expectedProjectVersion < 0 || expectedParticipantFactVersion == null
                || expectedParticipantFactVersion < 0 || expectedTreeVersion == null || expectedTreeVersion <= 0
                || "NORMAL_CLOSED".equals(expectedLifecycleStatus) && !"S6".equals(expectedCurrentStage)) {
            throw new ProjectDeliveryScopeQualificationFactException(INVALID_REQUEST,
                    "冻结项目资格输入不完整或越界");
        }
    }
}
