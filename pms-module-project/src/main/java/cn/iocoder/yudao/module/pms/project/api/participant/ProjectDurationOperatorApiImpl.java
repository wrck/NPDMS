package cn.iocoder.yudao.module.pms.project.api.participant;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectDurationOperatorApiImpl implements ProjectDurationOperatorApi {
    private final ProjectMasterMapper projects;
    private final ProjectMemberAssignmentMapper members;
    private final ProjectManagementQualificationApi qualifications;

    @Override
    public Fact inspect(Query query) {
        validate(query);
        var project=projects.selectById(query.projectId());
        requireProject(project,query);
        return fact(project,query,qualifications.inspect(qualificationQuery(query)));
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Fact lockInitialAndRevalidate(RevalidationQuery query) {
        Fact result = lockAndRevalidate(query);
        if (!"CONFIRMED_PROJECT_PM".equals(result.basis())
                && members.selectCurrentDurationProjectManagersForUpdate(
                    new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.DurationCurrentProjectManagerQuery(
                            query.subject().tenantId(), query.subject().projectId())).isEmpty()) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return result;
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Fact lockAndRevalidate(RevalidationQuery query) {
        if (query==null || query.expectedProjectVersion()==null || query.expectedProjectVersion()<0) throw exception(PROJECT_VERSION_CONFLICT);
        validate(query.subject());
        var project=projects.selectByIdForUpdate(query.subject().projectId());
        requireProject(project,query.subject());
        if (!Objects.equals(project.getVersion(),query.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        return fact(project,query.subject(),qualifications.lockCurrent(new ProjectManagementQualificationApi.CurrentRevalidation(
                qualificationQuery(query.subject()),query.expectedProjectVersion(),"ACTIVE",null)));
    }

    private static ProjectManagementQualificationApi.Query qualificationQuery(Query query) {
        return new ProjectManagementQualificationApi.Query(query.tenantId(),query.projectId(),query.userId());
    }
    private static Fact fact(ProjectMasterDO project,Query query,ProjectManagementQualificationApi.Fact qualification) {
        if (qualification == null) throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        String basis = qualification.source() == ProjectManagementQualificationApi.Source.REGIONAL_MANAGER
                ? "REGIONAL_SERVICE_MANAGER" : qualification.member().effectiveRoleCodes().contains(ProjectMemberRoles.PROJECT_MANAGER)
                    ? "CONFIRMED_PROJECT_PM" : "CONFIRMED_PROJECT_MANAGEMENT_MEMBER";
        return new Fact(project.getId(),query.userId(),project.getLifecycleStatus(),project.getVersion(),basis);
    }
    private static void validate(Query query) {
        if (query==null || query.tenantId()==null || query.userId()==null || query.userId()<=0
                || query.projectId()==null || query.projectId()<=0
                || !Objects.equals(query.userId(),cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId())
                || !Objects.equals(query.tenantId(),TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
    private static void requireProject(ProjectMasterDO project,Query query) {
        if (project==null || !Objects.equals(project.getTenantId(),query.tenantId()) || !"ACTIVE".equals(project.getLifecycleStatus()))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
}
