package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessConfigurationApi.Configuration;
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

/** Ordinary writes reuse frozen Owner configuration; explicit project entries still validate their selected node. */
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
        Frozen frozen = frozen(projectId, snapshot);
        if (requested != null) requireSelection(projectId, requested);
        if (frozen.independent()) {
            if (requested == null) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            var selected = requested.stage() != null
                    ? bindings.inspectStage(new ProjectWorkBindingStageFactQuery(projectId, requested.stage().stageId(), ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS))
                    : bindings.inspectTask(new ProjectWorkBindingTaskFactQuery(projectId, requested.task().taskId(), ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS));
            if (!sameTarget(projectId, selected)
                    || !Objects.equals(requested.stage() == null ? null : requested.stage().stageId(), selected.projectStageId())
                    || !Objects.equals(requested.task() == null ? null : requested.task().taskId(), selected.projectTaskId()))
                throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            return selected;
        }
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

    /** Configuration availability only; callers must still check current Owner permission, state and versions. */
    public boolean canUseFrozenConfiguration(Long projectId, String snapshot) {
        try { return frozen(projectId, snapshot) != null; }
        catch (RuntimeException invalid) { return false; }
    }

    /** Historical node identity is provenance, not a requirement that the originating round remains writable. */
    public Frozen frozen(Long projectId, String snapshot) {
        Frozen frozen;
        try { frozen = snapshot == null ? null : JsonUtils.parseObject(snapshot, Frozen.class); }
        catch (RuntimeException invalid) { throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID); }
        requireFrozenIdentity(frozen);
        if (frozen.businessOrigin() != null) {
            if (!Objects.equals(projectId, frozen.businessOrigin().projectId())) throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            return frozen;
        }
        if (frozen.moduleForm() != null) {
            RequirementAnalysisConfiguration.require(frozen.moduleForm(), frozen.moduleForm().tenantId(), projectId);
            return frozen;
        }
        if (frozen.configuration() != null) {
            RequirementAnalysisConfiguration.require(frozen.configuration(), frozen.configuration().tenantId(), projectId);
            return frozen;
        }
        var source = frozen.binding();
        var target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        if (projectId == null || projectId <= 0 || !Objects.equals(projectId, source.projectId())
                || !Objects.equals(target.workBindingTypeCode(), source.workBindingTypeCode())
                || !Objects.equals(target.targetContextCode(), source.targetContextCode())
                || !Objects.equals(target.targetObjectType(), source.targetObjectType())
                || !Objects.equals(target.targetObjectKey(), source.targetObjectKey()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
        return frozen;
    }

    private void requireSelection(Long projectId, ProjectBusinessExecutionSelection requested) {
        if (requested == null || (requested.task() == null) == (requested.stage() == null)
                || !Objects.equals(projectId, requested.task() != null ? requested.task().projectId() : requested.stage().projectId()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
    }

    private boolean sameTarget(Long projectId, ProjectWorkBindingFact current) {
        var target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        return current != null && Objects.equals(projectId, current.projectId())
                && Objects.equals(target.workBindingTypeCode(), current.workBindingTypeCode())
                && Objects.equals(target.targetContextCode(), current.targetContextCode())
                && Objects.equals(target.targetObjectType(), current.targetObjectType())
                && Objects.equals(target.targetObjectKey(), current.targetObjectKey());
    }

    private boolean sameBusinessBinding(ProjectWorkBindingFact origin, ProjectWorkBindingFact current) {
        return sameTarget(origin.projectId(), current);
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
        if (frozen.businessOrigin() != null) {
            var origin = frozen.businessOrigin();
            if (origin.tenantId() == null || origin.tenantId() <= 0 || origin.projectId() == null || origin.projectId() <= 0
                    || frozen.binding() != null || frozen.execution() != null || frozen.stageExecution() != null
                    || frozen.configuration() != null || frozen.moduleForm() != null)
                throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            return;
        }
        if (frozen.moduleForm() != null) {
            if (frozen.binding() != null || frozen.execution() != null || frozen.stageExecution() != null || frozen.configuration() != null)
                throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            RequirementAnalysisConfiguration.require(frozen.moduleForm(), frozen.moduleForm().tenantId(), frozen.moduleForm().projectId());
            return;
        }
        if (frozen.configuration() != null) {
            if (frozen.binding() != null || frozen.execution() != null || frozen.stageExecution() != null)
                throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
            RequirementAnalysisConfiguration.require(frozen.configuration(), frozen.configuration().tenantId(), frozen.configuration().projectId());
            return;
        }
        boolean stage = isStage(frozen.binding());
        if (stage ? frozen.execution() != null || frozen.stageExecution() == null
                || !Objects.equals(stageQuery(frozen.binding()), frozen.stageExecution().query())
                : frozen.stageExecution() != null || frozen.execution() == null
                || !Objects.equals(query(frozen.binding()), frozen.execution().query()))
            throw exception(REQUIREMENT_ANALYSIS_WORK_BINDING_INVALID);
    }

    public record BusinessOrigin(Long tenantId, Long projectId) { }
    public static Frozen independent(Long tenantId, Long projectId) {
        return new Frozen(null, null, null, null, null, new BusinessOrigin(tenantId, projectId));
    }

    public record Frozen(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution, ProjectStageExecutionContext stageExecution,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            Configuration configuration,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            RequirementAnalysisConfiguration.ModuleForm moduleForm,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            BusinessOrigin businessOrigin) {
        public Frozen(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution, ProjectStageExecutionContext stageExecution,
                Configuration configuration, RequirementAnalysisConfiguration.ModuleForm moduleForm) {
            this(binding, execution, stageExecution, configuration, moduleForm, null);
        }
        public Frozen(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution, ProjectStageExecutionContext stageExecution) {
            this(binding, execution, stageExecution, null, null);
        }
        public Frozen(ProjectWorkBindingFact binding, ProjectTaskExecutionContext execution, ProjectStageExecutionContext stageExecution, Configuration configuration) {
            this(binding, execution, stageExecution, configuration, null);
        }
        public boolean independent() { return businessOrigin != null || moduleForm != null || configuration != null; }
        public Long configurationTenantId() { return businessOrigin != null ? businessOrigin.tenantId() : moduleForm != null ? moduleForm.tenantId() : configuration.tenantId(); }
        public Long projectTemplateId() { return businessOrigin != null ? null : moduleForm != null ? null : configuration == null ? binding.projectTemplateId() : configuration.projectTemplateId(); }
        public Long templateRevisionId() { return businessOrigin != null ? null : moduleForm != null ? null : configuration == null ? binding.templateRevisionId() : configuration.templateRevisionId(); }
        public Long formRevisionId() { return businessOrigin != null ? null : moduleForm != null ? moduleForm.revisionId() : configuration == null ? binding.dynamicFormTemplateRevisionId()
                : RequirementAnalysisConfiguration.form(configuration.parameters()).revisionId(); }
    }
}
