package cn.iocoder.yudao.module.pms.project.api.deliveryscope.dto;

import cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.api.deliveryscope.ProjectDeliveryScopeQualificationFactException.Code.OWNER_DATA_CORRUPTED;

/** PROJ锁定的当前项目经理、生命周期与ACTION_EDIT组合事实。 */
public record ProjectDeliveryScopeQualificationFact(
        Long tenantId,
        Long projectId,
        Long currentManagerUserId,
        String lifecycleStatus,
        String currentStage,
        Integer projectVersion,
        Long participantFactVersion,
        Long treeVersion) {

    private static final Set<String> LIFECYCLES = Set.of("ACTIVE", "NORMAL_CLOSED", "EXCEPTION_CLOSED");
    private static final Set<String> STAGES = Set.of("S0", "S1", "S2", "S3", "S4", "S5", "S6");

    public ProjectDeliveryScopeQualificationFact {
        if (tenantId == null || tenantId <= 0 || projectId == null || projectId <= 0
                || currentManagerUserId == null || currentManagerUserId <= 0 || lifecycleStatus == null
                || !LIFECYCLES.contains(lifecycleStatus) || currentStage == null || !STAGES.contains(currentStage)
                || projectVersion == null || projectVersion < 0 || participantFactVersion == null
                || participantFactVersion < 0 || treeVersion == null || treeVersion < 0) {
            throw new ProjectDeliveryScopeQualificationFactException(OWNER_DATA_CORRUPTED,
                    "PROJ交付范围资格事实损坏");
        }
    }
}
