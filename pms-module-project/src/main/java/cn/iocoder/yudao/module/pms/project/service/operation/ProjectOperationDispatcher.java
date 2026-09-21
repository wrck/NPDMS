package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationCommand;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationResult;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Selects the frozen operation version; the executor retains all authorization and transaction checks. */
@Service
@RequiredArgsConstructor
public class ProjectOperationDispatcher {
    private final ProjectOperationContextResolver contexts;
    private final ProjectControlledOperationExecutor executor;
    private final ProjectOperationAdapters adapters;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;

    public ProjectOperationResult execute(String code, ProjectOperationCommand command) {
        ProjectControlledOperationExecutor.validate(command);
        var context = contexts.resolve(command.projectId(), command.nodeKind(), command.nodeId(), null);
        var task = command.execution().task();
        var stage = command.execution().stage();
        Long planId = task != null ? task.planVersionId() : stage.planVersionId();
        Long executionId = task != null ? task.executionId() : stage.executionId();
        if (planId == null || planId <= 0 || executionId == null || executionId <= 0)
            throw exception(BAD_REQUEST, "OPERATION_CONTEXT_MISMATCH");
        // Resolve the original immutable version even after a rework/rebase, so receipt recovery never chooses latest.
        var plan = plans.selectById(planId);
        var round = executions.selectById(executionId);
        if (plan == null || round == null || !Objects.equals(context.tenantId(), plan.getTenantId())
                || !Objects.equals(command.projectId(), plan.getProjectId())
                || !Set.of("EFFECTIVE", "SUPERSEDED").contains(plan.getStatus())
                || !Objects.equals(context.tenantId(), round.getTenantId()) || !Objects.equals(command.projectId(), round.getProjectId())
                || !Objects.equals(command.nodeKind(), round.getNodeKind()) || !Objects.equals(command.nodeId(), round.getNodeInstanceId()))
            throw exception(BAD_REQUEST, "OPERATION_CONTEXT_MISMATCH");
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var bindings = "TASK".equals(command.nodeKind())
                ? snapshot.getTasks().stream().filter(node -> Objects.equals(round.getNodeKey(), node.getNodeKey()))
                    .map(TemplateExecutionSnapshot.TaskContract::getBinding).toList()
                : snapshot.getStages().stream().filter(node -> Objects.equals(round.getNodeKey(), node.getNodeKey()))
                    .map(TemplateExecutionSnapshot.StageContract::getBinding).toList();
        if (bindings.size() != 1 || bindings.getFirst() == null || bindings.getFirst().getOperationContract() == null)
            throw exception(BAD_REQUEST, "OPERATION_NOT_BOUND");
        var matches = FrozenOperationContract.read(bindings.getFirst().getOperationContract()).declaration().operations()
                .stream().filter(operation -> operation.operationCode().equals(code)).toList();
        if (matches.size() != 1) throw exception(BAD_REQUEST, "OPERATION_NOT_BOUND");
        int version = matches.getFirst().operationVersion();
        return executor.execute(code, version, command, current -> adapters.require(code, version).invoke(code, current));
    }
}
