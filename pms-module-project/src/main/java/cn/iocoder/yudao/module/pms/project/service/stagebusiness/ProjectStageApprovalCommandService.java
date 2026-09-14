package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.ApprovalWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectStageApprovalCommandService {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectStageBusinessQueryService contexts;
    private final ProjectStageApprovalService approvals;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final PlatformCommandExecutionApi commands;

    public record Command(Long projectId, String stageCode, ProjectStageExecutionContext execution,
                          ProjectNodeApprovalApi.Submission approval) { }

    public ProjectNodeApprovalApi.Fact start(Command command, Long actorId, String key) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command == null || command.projectId() == null || command.projectId() <= 0 || command.execution() == null
                || !Objects.equals(command.projectId(),command.execution().projectId())
                || command.stageCode() == null || command.stageCode().isBlank() || command.stageCode().length() > 32
                || key == null || key.isBlank() || key.length() > 128) throw exception(BAD_REQUEST);
        authorize(tenantId,command.projectId(),actorId);
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,"PROJECT_STAGE_APPROVAL",actorId,key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), ProjectNodeApprovalApi.Fact.class,
                () -> startOnce(command,tenantId,actorId,key), fact -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_STAGE_APPROVAL_STARTED","ProjectNodeExecution",command.execution().executionId().toString(),
                        "stage-approval:" + key,JsonUtils.toJsonString(Map.of("projectId",command.projectId(),
                        "executionId",command.execution().executionId(),"processInstanceId",fact.processInstanceId(),
                        "definitionId",fact.definitionId())),List.of()));
        // BPM's dedicated listener already emits the reevaluation Outbox event in this same transaction.
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null)
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }

    private ProjectNodeApprovalApi.Fact startOnce(Command command, Long tenantId, Long actorId, String key) {
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId,command.projectId()));
        if (project == null || !Objects.equals(tenantId,project.getTenantId()) || !"ACTIVE".equals(project.getLifecycleStatus()))
            throw exception(PROJECT_VERSION_CONFLICT);
        authorize(tenantId,command.projectId(),actorId);
        var current = contexts.getContext(command.projectId(),command.stageCode(),new ProjectAccessActor(tenantId,actorId));
        if (!Objects.equals(command.execution(),current.execution())) throw exception(PROJECT_VERSION_CONFLICT);
        if (!"APPROVAL".equals(current.bindingType()) || current.readonly() || current.recoverableError() != null
                || !current.ownerActions().contains("APPROVAL") || current.approval() == null
                || current.approval().current().outcome() != ProjectNodeApprovalApi.Outcome.NOT_SATISFIED
                || !List.of("NOT_STARTED","REJECTED","CANCELLED").contains(current.approval().current().status()))
            throw exception(BAD_REQUEST);
        var definition = current.approval();
        return approvals.start(tenantId,current.execution(),new ApprovalWorkBindingSchema.Definition(definition.definitionKey(),definition.definitionId()),
                actorId,key,command.approval());
    }

    private void authorize(Long tenantId, Long projectId, Long actorId) {
        if (actorId == null || actorId <= 0 || !permissions.hasAnyPermissions(actorId,"pms:project:update")) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId,actorId,projectId,ProjectScopeApi.ACTION_EDIT));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId))
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
    }
}
