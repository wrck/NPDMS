package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.Decision;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.IdempotencyScope;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectCreationPlatformFactService.SuccessFacts;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;

/** 服务经理人工确认的幂等、授权与审计应用入口。 */
@Service
public class ProjectManagerAssignmentApplicationService {

    public static final String ASSIGN_SCOPE = "POST:/pms/projects/{id}/actions/assign-manager";
    private static final String ASSIGN_PERMISSION = "pms:project:assign";

    @Resource
    private ProjectCreationPlatformFactService platformFactService;
    @Resource
    private ProjectManualCreationService projectService;
    @Resource
    private PermissionCommonApi permissionApi;

    public AssignServiceManagerResult assign(AssignServiceManagerCommand command, Actor actor) {
        validate(command, actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), ASSIGN_PERMISSION)) {
            throw exception(FORBIDDEN);
        }
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
        detail.put("officeId", command.officeId());
        detail.put("locationId", command.locationId());
        detail.put("newVersion", result.version());
        return new SuccessFacts("PROJECT_SERVICE_MANAGER_ASSIGN", "Project",
                String.valueOf(result.projectId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                "ProjectServiceManagerAssigned", JsonUtils.toJsonString(result));
    }

    private void validate(AssignServiceManagerCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.expectedVersion() == null
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null
                || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("服务经理确认命令不完整");
        }
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
