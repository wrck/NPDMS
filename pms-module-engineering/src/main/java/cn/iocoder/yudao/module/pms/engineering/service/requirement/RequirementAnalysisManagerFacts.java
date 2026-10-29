package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import java.time.LocalDateTime;
import java.util.Set;

/** 超管操作权与项目真实经理资格分离；不把超管伪造成项目成员。 */
final class RequirementAnalysisManagerFacts {
    private RequirementAnalysisManagerFacts() { }

    static ProjectParticipantFact inspect(ProjectParticipantFactApi participants, PermissionApi permissions,
                                          Long projectId, Long actorId) {
        boolean administrator = permissions.hasAnyRoles(actorId, RoleCodeEnum.SUPER_ADMIN.getCode());
        var selection = new ProjectParticipantFactQuery(projectId, administrator ? null : actorId,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), LocalDateTime.now());
        // Administrator power is independent; member-based operation qualification must be confirmed.
        return administrator ? participants.inspect(selection) : participants.inspectAuthorization(selection);
    }
}
