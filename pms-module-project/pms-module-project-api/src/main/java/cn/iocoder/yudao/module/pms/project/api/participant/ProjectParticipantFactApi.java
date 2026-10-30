package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;

/** PROJ项目资格与当前参与人只读权威事实。 */
public interface ProjectParticipantFactApi {

    /** Stable PROJ denial code; callers may try another independently verified authority only for this result. */
    int SCOPE_DENIED_ERROR_CODE = 1_014_024_033;

    String ROLE_PROJECT_MANAGER = ProjectMemberRoles.PROJECT_MANAGER;
    String ROLE_SERVICE_MANAGER = ProjectMemberRoles.SERVICE_MANAGER;
    String ROLE_SERVICE_MANAGER_L1 = ProjectMemberRoles.LEGACY_SERVICE_MANAGER_L1;
    String ROLE_SERVICE_MANAGER_L2 = ProjectMemberRoles.LEGACY_SERVICE_MANAGER_L2;

    ProjectParticipantFact inspect(ProjectParticipantFactQuery query);

    ProjectParticipantFact lockAndRevalidate(ProjectParticipantFactRevalidationQuery query);

    /** Confirmed active member qualification; requires an explicit subject. Caller still checks permissions and project scope. */
    ProjectParticipantFact inspectAuthorization(ProjectParticipantFactQuery query);

    /** Revalidates the same confirmed membership, project version/state and original participant lock order. */
    ProjectParticipantFact lockAndRevalidateAuthorization(ProjectParticipantFactRevalidationQuery query);

}
