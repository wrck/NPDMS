package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.Decision;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.IdempotencyScope;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.SuccessFacts;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectServiceManagerAssignedPayload;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ORGANIZATION_SCOPE_INVALID;

/** 服务经理人工确认的幂等、授权与审计应用入口。 */
@Service
public class ProjectManagerAssignmentApplicationService {

    public static final String ASSIGN_SCOPE = "POST:/pms/projects/{id}/actions/assign-manager";
    public static final String ASSIGNMENT_NOTIFY_TEMPLATE = "pms_project_service_manager_assigned";
    @Resource
    private PlatformCommandExecutionApi platformFactService;
    @Resource
    private ProjectManualCreationService projectService;
    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectCreationAuthorizationService authorizationService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private OrganizationScopeApi organizationScopeApi;
    @Resource
    private AssetLocationApi assetLocationApi;
    @Resource
    private ProjectSiteApplicationService projectSiteService;
    @Resource
    private ProjectAuthorizationGuard projectAuthorizationGuard;

    @Transactional(rollbackFor = Exception.class)
    public AssignServiceManagerResult assign(AssignServiceManagerCommand command, Actor actor) {
        validate(command, actor);
        authorizationService.assertCanAssign(actor.actorId());
        projectAuthorizationGuard.assertCanAssign(
                new ProjectAuthorizationGuard.Actor(actor.tenantId(), actor.actorId()), command.projectId());
        validateBusinessScope(command, actor);
        var execution = platformFactService.execute(
                new IdempotencyScope(actor.tenantId(), ASSIGN_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), AssignServiceManagerResult.class,
                () -> projectService.assignServiceManager(command),
                result -> successFacts(command, actor, result));
        if (execution.decision() == Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        return execution.response();
    }

    private SuccessFacts successFacts(AssignServiceManagerCommand command, Actor actor,
                                      AssignServiceManagerResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", result.projectId());
        detail.put("assignmentId", result.assignmentId());
        detail.put("levelCode", command.levelCode());
        detail.put("assignmentType", command.assignmentType());
        detail.put("siteId", command.siteId());
        detail.put("departmentId", command.departmentId());
        detail.put("departmentCode", command.departmentCode());
        detail.put("changeReason", command.changeReason());
        detail.put("previousPrimaryManagerId", result.previousPrimaryManagerId());
        if (result.previousPrimaryManagerId() != null) {
            detail.put("previousPrimaryEffectiveTo", result.effectiveFrom());
        }
        detail.put("currentPrimaryManagerId", result.currentPrimaryManagerId());
        detail.put("effectiveFrom", result.effectiveFrom());
        detail.put("operatorId", actor.actorId());
        detail.put("newVersion", result.version());
        Map<String, Object> templateParams = new LinkedHashMap<>();
        templateParams.put("projectId", result.projectId());
        templateParams.put("assignmentId", result.assignmentId());
        templateParams.put("levelCode", command.levelCode());
        templateParams.put("assignmentType", command.assignmentType());
        templateParams.put("effectiveFrom", result.effectiveFrom());
        ProjectServiceManagerAssignedPayload payload = new ProjectServiceManagerAssignedPayload(
                result.assignmentId(), result.projectId(), command.managerId(), ASSIGNMENT_NOTIFY_TEMPLATE,
                Map.copyOf(templateParams), command.assignmentType(), command.levelCode(), result.effectiveFrom());
        return new SuccessFacts("PROJECT_SERVICE_MANAGER_ASSIGN", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectServiceManagerAssigned", JsonUtils.toJsonString(payload));
    }

    private void validate(AssignServiceManagerCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.expectedVersion() == null
                || command.managerId() == null
                || command.departmentId() == null
                || command.departmentCode() == null || command.departmentCode().isBlank()
                || !("L1".equals(command.levelCode()) || "L2".equals(command.levelCode()))
                || !("PRIMARY".equals(command.assignmentType())
                || "COLLABORATOR".equals(command.assignmentType()))
                || ("L2".equals(command.levelCode()) && command.siteId() == null)
                || command.changeReason() == null || command.changeReason().isBlank()
                || command.changeReason().trim().length() > 500
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null
                || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("服务经理确认命令不完整");
        }
    }

    private void validateBusinessScope(AssignServiceManagerCommand command, Actor actor) {
        ProjectMasterDO project = projectMasterMapper.selectById(command.projectId());
        if (project == null || !java.util.Objects.equals(project.getTenantId(), actor.tenantId())
                || project.getCompanyId() == null) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "项目或项目公司范围不存在");
        }
        var projectSites = projectSiteService.getActiveSites(command.projectId());
        boolean levelTwo = "L2".equals(command.levelCode());
        if (projectSites.isEmpty()) {
            if (command.siteId() != null) {
                throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "待维护地点项目不接受站点责任范围");
            }
        } else {
            boolean projectContainsSite = command.siteId() != null && projectSites.stream()
                    .anyMatch(site -> command.siteId().equals(site.getSiteId()));
            if ((levelTwo || command.siteId() != null) && !projectContainsSite) {
                throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "站点不在项目当前实施范围内");
            }
            if (command.siteId() != null) {
                assetLocationApi.getSite(command.siteId(), null);
            }
        }
        if (levelTwo && command.siteId() == null) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "L2服务经理必须指定当前项目站点");
        }
        adminUserApi.validateUser(command.managerId());
        DeptRespDTO department = deptApi.getDeptByCode(command.departmentCode());
        if (department == null || !java.util.Objects.equals(department.getId(), command.departmentId())) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "办事处部门不存在或已停用");
        }
        if (!organizationScopeApi.hasScope(command.managerId(), project.getCompanyId(), command.departmentId())) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "候选服务经理不具备项目公司与办事处的联合范围");
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
