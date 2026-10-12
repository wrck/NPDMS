package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_MEMBER_ROLE_INVALID;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.*;

/** 正常成员与已批准历史迁入共享SYSTEM资格条件；项目动作授权仍由各自Owner执行。 */
public final class ProjectMemberQualification {
    private ProjectMemberQualification() { }

    public static ActiveUserSelectionApi.Query query(ProjectMasterDO project, String role,
            int pageNo, int pageSize, String keyword, Set<Long> userIds) {
        String systemRole = switch (role) {
            case TEAM_MEMBER, PROJECT_MANAGER -> PROJECT_MANAGER;
            case SALES_REPRESENTATIVE -> SALES_REPRESENTATIVE;
            case SERVICE_MANAGER -> SERVICE_MANAGER;
            default -> throw exception(PROJECT_MEMBER_ROLE_INVALID);
        };
        var qualification = PROJECT_MANAGER.equals(role)
                ? new ActiveUserSelectionApi.Qualification(project.getCompanyId(), null, null, PROJECT_MANAGER) : null;
        return new ActiveUserSelectionApi.Query(pageNo, pageSize, keyword, userIds, systemRole, qualification);
    }
}
