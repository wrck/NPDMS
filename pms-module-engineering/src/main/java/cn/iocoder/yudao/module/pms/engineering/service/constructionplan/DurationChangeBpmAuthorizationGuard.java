package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeProcessQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_BPM_ASSOCIATION_INVALID;

/** BPM终态进入SOL写事务前的主体、功能权限、项目范围与当前角色守卫。 */
@Service
@RequiredArgsConstructor
public class DurationChangeBpmAuthorizationGuard {

    static final String PERMISSION_APPROVE = "pms:construction-plan:duration-approve";
    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> SERVICE_MANAGER_ROLES = Set.of(
            ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1,
            ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L2);
    private static final Set<String> PROJECT_MANAGER_ROLE =
            Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanChangeMapper changeMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;

    AuthorizationContext authorize(String processInstanceId,
                                   DurationChangeBpmResultService.TerminalResult result) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (processInstanceId == null || processInstanceId.isBlank()
                || actorId == null || actorId <= 0) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        ConstructionPlanChangeDO change = changeMapper.selectByProcessInstanceId(
                new ConstructionPlanChangeProcessQuery(tenantId, processInstanceId));
        if (change == null || !Objects.equals(change.getTenantId(), tenantId)) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }
        ConstructionPlanDO plan = planMapper.selectById(
                new ConstructionPlanLockQuery(tenantId, change.getPlanId()));
        if (plan == null || !Objects.equals(plan.getTenantId(), tenantId)) {
            throw exception(DURATION_CHANGE_BPM_ASSOCIATION_INVALID);
        }

        Set<String> requiredRoles;
        String permission;
        if (result == DurationChangeBpmResultService.TerminalResult.CANCEL) {
            if (!Objects.equals(actorId, change.getApplicantUserId())) {
                throw exception(FORBIDDEN);
            }
            requiredRoles = PROJECT_MANAGER_ROLE;
            permission = ConstructionPlanApplicationService.PERMISSION_MANAGE;
        } else {
            if (!Objects.equals(actorId, change.getApproverUserId())
                    || Objects.equals(actorId, change.getApplicantUserId())) {
                throw exception(FORBIDDEN);
            }
            requiredRoles = SERVICE_MANAGER_ROLES;
            permission = PERMISSION_APPROVE;
        }
        if (!permissionApi.hasAnyPermissions(actorId, permission)) {
            throw exception(FORBIDDEN);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorId, plan.getProjectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || !scope.fullProjectIds().contains(plan.getProjectId())) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
        ProjectParticipantFact current = participantFactApi.inspect(new ProjectParticipantFactQuery(
                plan.getProjectId(), actorId, requiredRoles, LocalDateTime.now()));
        if (current == null || !Objects.equals(current.projectId(), plan.getProjectId())
                || !Objects.equals(current.userId(), actorId)
                || current.projectVersion() == null) {
            throw exception(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID);
        }
        participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                plan.getProjectId(), actorId, current.projectVersion(), ACTIVE, null, requiredRoles));
        return new AuthorizationContext(tenantId, actorId, plan.getId(), plan.getProjectId(),
                change.getId(), current.projectVersion());
    }

    record AuthorizationContext(Long tenantId, Long actorId, Long planId, Long projectId,
                                Long changeId, Integer projectVersion) {
    }

}
