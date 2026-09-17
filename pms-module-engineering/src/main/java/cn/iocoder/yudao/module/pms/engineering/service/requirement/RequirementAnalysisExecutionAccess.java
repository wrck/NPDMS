package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingStageFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFactRevalidationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID;

/** SOL freezes its originating node round in the existing execution-contract snapshot, not a second rules source. */
@Service
@RequiredArgsConstructor
public class RequirementAnalysisExecutionAccess {
    private final ProjectNodeExecutionApi executions;
    private final ProjectWorkBindingFactApi bindings;
    private final ProjectBusinessExecutionApi businessExecutions;

    public Frozen lockCurrent(ProjectWorkBindingFact binding) {
        return lockCurrent(binding, null, null);
    }

    public Frozen lockCurrent(ProjectWorkBindingFact binding, EntityActor actor, Long targetRevisionId) {
        if (isStage(binding)) {
            var observed = executions.inspectStage(stageQuery(binding));
            businessExecutions.lockForWrite(writeRequest(binding, actor, targetRevisionId,
                    new ProjectBusinessExecutionSelection(null, observed)));
            // The shared guard begins real stage handling in this transaction; freeze its updated version.
            return new Frozen(binding, null, executions.inspectStage(stageQuery(binding)));
        }
        var observed = executions.inspect(query(binding));
        businessExecutions.lockForWrite(writeRequest(binding, actor, targetRevisionId,
                new ProjectBusinessExecutionSelection(observed, null)));
        return new Frozen(binding, observed, null);
    }

    private ProjectBusinessExecutionApi.WriteRequest writeRequest(ProjectWorkBindingFact binding, EntityActor actor,
            Long targetRevisionId, ProjectBusinessExecutionSelection observed) {
        if (actor == null) return new ProjectBusinessExecutionApi.WriteRequest(binding.projectId(),
                "SOL", "REQUIREMENT_ANALYSIS", observed);
        return ProjectOwnerOperationScope.writeRequest(actor.tenantId(), actor.userId(), binding.projectId(),
                "SOL", "REQUIREMENT_ANALYSIS", targetRevisionId == null ? null : targetRevisionId.toString(), observed);
    }

    public ProjectWorkBindingFact lockBinding(ProjectWorkBindingFact binding) {
        if (isStage(binding)) return bindings.lockAndRevalidateStage(new ProjectWorkBindingStageFactRevalidationQuery(
                binding.projectId(), binding.projectStageId(), binding.executionContractId(), binding.projectStageVersion(),
                binding.contractVersion(), binding.projectVersion(), ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        return bindings.lockAndRevalidate(new ProjectWorkBindingFactRevalidationQuery(binding.projectId(), binding.projectTaskId(),
                binding.executionContractId(), binding.projectTaskVersion(), binding.contractVersion(), binding.projectVersion(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
    }

    public ProjectWorkBindingFact lockRequested(Long projectId, ProjectBusinessExecutionSelection requested) {
        requireSelection(projectId, requested);
        if (requested.stage() != null) {
            executions.lockAndRevalidateStage(requested.stage());
            return bindings.inspectStage(new ProjectWorkBindingStageFactQuery(projectId, requested.stage().stageId(),
                    ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        }
        executions.lockAndRevalidate(requested.task());
        return bindings.inspectTask(new ProjectWorkBindingTaskFactQuery(projectId, requested.task().taskId(),
                ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
    }

    public boolean canCreate(ProjectWorkBindingFact binding) {
        if (binding == null) return false;
        try { return isStage(binding) ? executions.inspectStage(stageQuery(binding)).writable() : executions.inspect(query(binding)).writable(); }
        catch (RuntimeException unavailable) { return false; }
    }

    public ProjectBusinessExecutionSelection observeCurrent(ProjectWorkBindingFact binding) {
        return isStage(binding) ? new ProjectBusinessExecutionSelection(null, executions.inspectStage(stageQuery(binding)))
                : new ProjectBusinessExecutionSelection(executions.inspect(query(binding)), null);
    }

    public boolean canWrite(RequirementAnalysisRevisionDO root) {
        return canWrite(root, null);
    }

    public boolean canWrite(RequirementAnalysisRevisionDO root, ProjectBusinessExecutionSelection requested) {
        return canWrite(root == null ? null : root.getProjectId(), root == null ? null : root.getExecutionSnapshot(), requested);
    }

    public boolean canWrite(Long projectId, String snapshot, ProjectBusinessExecutionSelection requested) {
        try { return canCreate(currentBinding(projectId, snapshot, requested)); }
        catch (RuntimeException unavailable) { return false; }
    }

    public void lockForWrite(RequirementAnalysisRevisionDO root) {
        lockForWrite(root, null);
    }

    public void lockForWrite(RequirementAnalysisRevisionDO root, ProjectBusinessExecutionSelection requested) {
        var binding = currentBinding(root, requested);
        if (requested != null) lockRequested(root.getProjectId(), requested);
        lockCurrent(binding);
    }

    public String freeze(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution) {
        return freeze(new Frozen(binding, execution, null));
    }

    public String freeze(Frozen frozen) {
        requireFrozenIdentity(frozen);
        return JsonUtils.toJsonString(frozen);
    }

    public ProjectWorkBindingFact currentBinding(RequirementAnalysisRevisionDO root) {
        return currentBinding(root, null);
    }

    public ProjectWorkBindingFact currentBinding(RequirementAnalysisRevisionDO root, ProjectBusinessExecutionSelection requested) {
        return currentBinding(root == null ? null : root.getProjectId(),
                root == null ? null : root.getExecutionSnapshot(), requested);
    }

    public ProjectWorkBindingFact currentBinding(Long projectId, String snapshot, ProjectBusinessExecutionSelection requested) {
        Frozen frozen = snapshot == null ? null
                : JsonUtils.parseObject(snapshot, Frozen.class);
        requireFrozenIdentity(frozen);
        if (!Objects.equals(projectId, frozen.binding().projectId()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        if (requested != null) requireSelection(projectId, requested);
        Long stageId = requested == null ? frozen.binding().projectStageId()
                : requested.stage() == null ? null : requested.stage().stageId();
        Long taskId = requested == null ? frozen.binding().projectTaskId()
                : requested.task() == null ? null : requested.task().taskId();
        var binding = stageId != null
                ? bindings.inspectStage(new ProjectWorkBindingStageFactQuery(projectId, stageId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                : bindings.inspectTask(new ProjectWorkBindingTaskFactQuery(projectId, taskId, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
        if (!sameBusinessBinding(frozen.binding(),binding) || !Objects.equals(stageId,binding.projectStageId())
                || !Objects.equals(taskId,binding.projectTaskId())) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        // Explicit shared-node handling never rewrites the record's originating contract/round.
        return binding;
    }

    private void requireSelection(Long projectId, ProjectBusinessExecutionSelection requested) {
        if (requested == null || (requested.task() == null) == (requested.stage() == null)
                || !Objects.equals(projectId, requested.task() != null ? requested.task().projectId() : requested.stage().projectId()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
    }

    private boolean sameBusinessBinding(ProjectWorkBindingFact origin,ProjectWorkBindingFact current) {
        if (current == null || !Objects.equals(origin.projectId(),current.projectId())
                || !Objects.equals(origin.workBindingTypeCode(),current.workBindingTypeCode())
                || !Objects.equals(origin.targetContextCode(),current.targetContextCode())
                || !Objects.equals(origin.targetObjectType(),current.targetObjectType())
                || !Objects.equals(origin.targetObjectKey(),current.targetObjectKey())
                || !Objects.equals(origin.dynamicFormTemplateId(),current.dynamicFormTemplateId())
                || !Objects.equals(origin.dynamicFormTemplateRevisionId(),current.dynamicFormTemplateRevisionId())) return false;
        // Contract/plan/round versions can advance. Owner binding parameters may not silently change under an existing record.
        if (Objects.equals(origin.bindingParameterSnapshot(),current.bindingParameterSnapshot())) return true;
        return origin.bindingParameterSnapshot() != null && current.bindingParameterSnapshot() != null
                && Objects.equals(JsonUtils.parseTree(origin.bindingParameterSnapshot()),JsonUtils.parseTree(current.bindingParameterSnapshot()));
    }

    private ProjectTaskExecutionQuery query(ProjectWorkBindingFact binding) {
        return new ProjectTaskExecutionQuery(binding.projectId(), binding.projectTaskId(), binding.executionContractId());
    }

    private ProjectStageExecutionQuery stageQuery(ProjectWorkBindingFact binding) {
        return new ProjectStageExecutionQuery(binding.projectId(), binding.projectStageId(), binding.executionContractId());
    }

    private boolean isStage(ProjectWorkBindingFact binding) {
        if (binding == null || (binding.projectTaskId() == null) == (binding.projectStageId() == null))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        return binding.projectStageId() != null;
    }

    private void requireFrozenIdentity(Frozen frozen) {
        if (frozen == null) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        boolean stage = isStage(frozen.binding());
        if (stage ? frozen.execution() != null || frozen.stageExecution() == null
                || !Objects.equals(stageQuery(frozen.binding()), frozen.stageExecution().query())
                : frozen.stageExecution() != null || frozen.execution() == null
                || !Objects.equals(query(frozen.binding()), frozen.execution().query()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
    }

    public record Frozen(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution, ProjectStageExecutionContext stageExecution) { }
}
