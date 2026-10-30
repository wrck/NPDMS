package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessErrors.failure;

/** Explicit refresh reuses the normal automatic completion command; reads remain side-effect free. */
@Service
@RequiredArgsConstructor
public class ProjectTaskBusinessRefreshService {
    private final TaskBusinessAccess access;
    private final ProjectTaskRuntimeMapper tasks;
    private final ProjectTaskExecutionContractMapper contracts;
    private final TaskBusinessProviderRegistry providers;
    private final ProjectNodeExecutionApi executions;
    private final ProjectTaskBusinessAssociationService associations;
    private final ProjectTaskLifecycleService lifecycle;
    private final OperationAuditApi audit;

    public record Command(Long taskId, Integer expectedTaskVersion, Integer expectedContractVersion) { }

    @Transactional(rollbackFor = Exception.class)
    public ProjectTaskLifecycleService.AutomaticResult refresh(Command command, TaskWorkbenchActor actor) {
        if (command == null || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || command.expectedContractVersion() == null || command.expectedContractVersion() < 1)
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        var visible = access.read(command.taskId(), actor.tenantId(), actor.actorId());
        var context = new Context(actor.tenantId(), actor.actorId(), visible.getProjectId(), command.taskId(), actor.correlationId());
        var project = tasks.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(actor.tenantId(), context.projectId()));
        var task = tasks.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(actor.tenantId(), context.projectId(), command.taskId()));
        if (task == null || !Objects.equals(task.getId(), command.taskId())
                || !access.executableLocked(task, project, context))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(actor.tenantId(), command.taskId()));
        if (contract == null || !Objects.equals(contract.getTenantId(), actor.tenantId())
                || !Objects.equals(contract.getProjectTaskId(), command.taskId()))
            throw failure("CONTRACT_NOT_FOUND");
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion())
                || !Objects.equals(contract.getContractVersion(), command.expectedContractVersion()))
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        if (!Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(task.getStatus()))
            throw failure("CURRENT_TASK_EXECUTION_REQUIRED");
        var binding = TaskBusinessBinding.parse(contract);
        if (binding.unavailableReason() != null) throw failure(binding.unavailableReason());
        if ("READ_ONLY_AGGREGATE".equals(binding.instanceResolutionStrategy()))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var execution = executions.inspect(new ProjectTaskExecutionQuery(context.projectId(), command.taskId(), contract.getId()));
        if (execution == null || !execution.writable()) throw failure("CURRENT_TASK_EXECUTION_REQUIRED");
        executions.lockAndRevalidate(execution);
        var provider = providers.require(binding.ownerContext(), binding.objectType());
        if (!provider.inspectContext(context).contains("QUERY")) throw failure("OWNER_CONTEXT_FORBIDDEN");
        associations.synchronize(context.projectId(), command.taskId(), actor.correlationId());
        var result = lifecycle.completeFromBusinessResult(context.projectId(), command.taskId(), actor.correlationId());
        audit.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PROJECT_TASK_BUSINESS_REFRESH",
                "ProjectTask", command.taskId().toString(), "SUCCESS",
                Map.of("executionId", execution.executionId(), "completed", result.completed(), "unknown", result.unknown()));
        return result;
    }
}
