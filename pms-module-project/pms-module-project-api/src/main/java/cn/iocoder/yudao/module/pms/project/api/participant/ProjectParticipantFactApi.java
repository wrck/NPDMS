package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;

/** PROJ项目资格与当前参与人只读权威事实。 */
public interface ProjectParticipantFactApi {

    String ROLE_PROJECT_MANAGER = "PROJECT_MANAGER";
    String ROLE_SERVICE_MANAGER_L1 = "SERVICE_MANAGER_L1";
    String ROLE_SERVICE_MANAGER_L2 = "SERVICE_MANAGER_L2";

    ProjectParticipantFact inspect(ProjectParticipantFactQuery query);

    ProjectParticipantFact lockAndRevalidate(ProjectParticipantFactRevalidationQuery query);

}
