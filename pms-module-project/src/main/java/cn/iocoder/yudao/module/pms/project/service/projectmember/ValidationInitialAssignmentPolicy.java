package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard.Actor;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** 本次隔离验证库的首次指派资格，不是 PROJECT_MANAGE，也不持久化授权。 */
@Component
@RequiredArgsConstructor
public class ValidationInitialAssignmentPolicy {
    public static final String ENABLED_PROPERTY = "pms.validation.initial-assignment.enabled";
    public static final String DATASOURCE_URL_PROPERTY = "spring.datasource.dynamic.datasource.master.url";
    private static final String DATABASE_PATH = "/npdms_template_core_20260910";
    private final Environment environment;
    private final PermissionApi permissionApi;
    private final ProjectMemberAssignmentMapper memberMapper;

    public boolean permitsCandidates(Actor actor, ProjectMasterDO project) {
        return permitsAssignment(actor, project, false, false);
    }

    /** 写入者须在原事务的根锁及项目锁下重验；只允许补齐缺失主责，不能借此改派。 */
    public boolean permitsAssignment(Actor actor, ProjectMasterDO project,
                                     boolean assignsServiceManager, boolean assignsProjectManager) {
        if (!validationDatabaseEnabled() || actor == null || actor.actorId() == null || actor.actorId() <= 0
                || actor.tenantId() == null || actor.tenantId() < 0 || project == null
                || !Objects.equals(actor.tenantId(), project.getTenantId())
                || !Objects.equals(actor.tenantId(), TenantContextHolder.getTenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus()) || !"S0".equals(project.getCurrentStage())
                || !"UNASSIGNED".equals(project.getAssignmentStatus())) return false;
        var login = SecurityFrameworkUtils.getLoginUser();
        if (login == null || !Objects.equals(login.getId(), actor.actorId())
                || !Objects.equals(login.getTenantId(), actor.tenantId())
                || !Objects.equals(login.getUserType(), UserTypeEnum.ADMIN.getValue())
                || !permissionApi.hasAnyRoles(actor.actorId(), "super_admin")) return false;
        var members = memberMapper.selectActiveForAssignmentState(
                new ProjectAssignmentStateQuery(project.getId(), LocalDateTime.now()));
        boolean serviceManager = members.stream().anyMatch(member ->
                Objects.equals(member.getTenantId(), actor.tenantId())
                        && Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2").contains(member.getMemberRole())
                        && (member.getAssignmentType() == null || "PRIMARY".equals(member.getAssignmentType())));
        boolean projectManager = project.getManagerId() != null;
        return !(serviceManager && projectManager)
                && !(assignsServiceManager && serviceManager) && !(assignsProjectManager && projectManager);
    }

    private boolean validationDatabaseEnabled() {
        if (!Boolean.parseBoolean(environment.getProperty(ENABLED_PROPERTY, "false"))) return false;
        String url = environment.getProperty(DATASOURCE_URL_PROPERTY, "");
        if (!url.startsWith("jdbc:mysql://")) return false;
        try {
            URI parsed = URI.create(url.substring("jdbc:".length()));
            return parsed.getHost() != null && parsed.getUserInfo() == null && parsed.getFragment() == null
                    && DATABASE_PATH.equals(parsed.getRawPath());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
