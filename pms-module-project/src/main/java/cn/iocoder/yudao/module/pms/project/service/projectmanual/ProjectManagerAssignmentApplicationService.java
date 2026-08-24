package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService.Decision;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService.IdempotencyScope;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService.SuccessFacts;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
    @Resource
    private ProjectCommandExecutionService platformFactService;
    @Resource
    private ProjectManualCreationService projectService;
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

    public AssignServiceManagerResult assign(AssignServiceManagerCommand command, Actor actor) {
        validate(command, actor);
        authorizationService.assertCanAssign(actor.actorId());
        validateBusinessScope(command);
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
        detail.put("roleCode", command.roleCode());
        detail.put("levelCode", command.levelCode());
        detail.put("siteId", command.siteId());
        detail.put("departmentCode", command.departmentCode());
        detail.put("newVersion", result.version());
        return new SuccessFacts("PROJECT_SERVICE_MANAGER_ASSIGN", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectServiceManagerAssigned", JsonUtils.toJsonString(result));
    }

    private void validate(AssignServiceManagerCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.expectedVersion() == null
                || command.managerId() == null
                || command.departmentCode() == null || command.departmentCode().isBlank()
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null
                || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("服务经理确认命令不完整");
        }
    }

    private void validateBusinessScope(AssignServiceManagerCommand command) {
        ProjectMasterDO project = projectService.getProject(command.projectId());
        if (project == null || project.getCompanyId() == null) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "项目或项目公司范围不存在");
        }
        var projectSites = projectSiteService.getActiveSites(command.projectId());
        if (projectSites.isEmpty()) {
            if (command.siteId() != null) {
                throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "待维护地点项目不接受站点责任范围");
            }
        } else {
            boolean projectContainsSite = projectSites.stream()
                    .anyMatch(site -> command.siteId() != null && command.siteId().equals(site.getSiteId()));
            if (!projectContainsSite) {
                throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "站点不在项目当前实施范围内");
            }
            assetLocationApi.getSite(command.siteId(), null);
        }
        adminUserApi.validateUser(command.managerId());
        DeptRespDTO department = deptApi.getDeptByCode(command.departmentCode());
        if (department == null) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "办事处部门不存在或已停用");
        }
        if (!organizationScopeApi.hasScope(command.managerId(), project.getCompanyId(), department.getId())) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "候选服务经理不具备项目公司与办事处的联合范围");
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
