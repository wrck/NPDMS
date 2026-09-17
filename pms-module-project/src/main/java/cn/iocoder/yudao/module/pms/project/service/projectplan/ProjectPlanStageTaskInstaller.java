package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskTreePathMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_VERSION_CONFLICT;

/** Installs project-owned stages, tasks and current WBS; never operates on an Owner business record. */
@Service
@RequiredArgsConstructor
public class ProjectPlanStageTaskInstaller {
    private final ProjectPlanProjectionMapper projections;
    private final ProjectStageInstanceMapper stageRows;
    private final ProjectTaskInstanceMapper taskRows;
    private final ProjectTaskTreePathMapper paths;
    private final ProjectTaskExecutionContractMapper taskContracts;
    private final ProjectStageExecutionContractMapper stageContracts;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectNodeExecutionMapper executions;
    private final TaskExecutionContractFactory taskFactory;
    private final ProjectRuntimeGraphFreezer stageFactory;
    private final cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi satisfactionTemplates;

    public record Request(ProjectMasterDO project, Long newPlanId, Long newTaskStateMachineRevisionId,
                          TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                          ProjectPlanExecutionPlanner.Plan plan, List<ProjectNodeExecutionDO> rounds,
                          List<ProjectStageInstanceDO> stages, List<ProjectTaskInstanceDO> tasks,
                          Long actorId, LocalDateTime occurredAt) { }
    public record Installation(List<ProjectNodeExecutionMapper.PlanRebase> continuing,
                               List<ProjectNodeExecutionMapper.PlanRetirement> removed, boolean tasksChanged) { }

    /** The authorized caller holds the project lock and has checked BOTH impact and execution plans. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Installation install(Request request) {
        if (!request.plan().issues().isEmpty()) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        Long tenant = request.project().getTenantId(), project = request.project().getId();
        String actor = request.actorId().toString();
        var current = request.rounds().stream().collect(Collectors.toMap(ProjectNodeExecutionDO::getNodeKey, Function.identity()));
        var oldTasks = request.tasks().stream().collect(Collectors.toMap(ProjectTaskInstanceDO::getId, Function.identity()));
        var oldStages = request.stages().stream().collect(Collectors.toMap(ProjectStageInstanceDO::getId, Function.identity()));
        var stageDefinitions = request.after().getStages().stream().collect(Collectors.toMap(TemplateExecutionSnapshot.StageContract::getCode,Function.identity()));
        var taskDefinitions = request.after().getTasks().stream().collect(Collectors.toMap(TemplateExecutionSnapshot.TaskContract::getCode,Function.identity()));
        var previousDefinitions = request.before().getTasks().stream().collect(Collectors.toMap(TemplateExecutionSnapshot.TaskContract::getNodeKey,Function.identity()));
        var ids = request.after().getTasks().stream().map(node -> current.containsKey(node.getNodeKey())
                ? current.get(node.getNodeKey()).getNodeInstanceId() : IdWorker.getId()).toList().iterator();
        var hierarchy = ProjectPlanTaskHierarchy.resolve(request.before(), request.after(), request.rounds(), request.tasks());
        if (!hierarchy.issues().isEmpty()) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var content = hierarchy.content();
        var desired = TemplateInstantiator.instantiateWithTaskStateMachines(content,project,taskId -> {
            var existing = oldTasks.get(taskId);
            return existing == null ? request.newTaskStateMachineRevisionId() : existing.getStateMachineRevisionId();
        },ids::next);
        var retired = new ArrayList<ProjectNodeExecutionMapper.PlanRetirement>();
        var continuing = new ArrayList<ProjectNodeExecutionMapper.PlanRebase>();
        var currentStageContracts = graph.selectContracts(new ProjectRuntimeGraphQuery(tenant,project)).stream()
                .collect(Collectors.toMap(ProjectStageExecutionContractDO::getStageId,Function.identity()));
        boolean tasksChanged = false;

        // Remove child tasks first; stage retirement must still reject any surviving started work.
        for (var change : request.plan().changes().stream().filter(change -> change.action()==ProjectPlanExecutionPlanner.Action.RETIRE_UNSTARTED)
                .sorted(Comparator.comparing(ProjectPlanExecutionPlanner.Change::nodeKind).reversed()).toList()) {
            var old = current.get(change.nodeKey());
            if ("TASK".equals(change.nodeKind())) {
                var row = oldTasks.get(change.nodeInstanceId());
                var contract = taskContracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenant,row.getId()));
                requireContract(old,contract == null ? null : contract.getId());
                requireOne(projections.retireUnstartedTask(new ProjectPlanProjectionMapper.NodeProjectionChange(tenant,project,row.getId(),row.getVersion(),actor)));
                requireOne(projections.closeTaskContract(closure(request,row.getId(),contract.getId(),contract.getVersion())));
                tasksChanged = true;
            } else {
                var row = oldStages.get(change.nodeInstanceId());
                var contract = currentStageContracts.get(row.getId());
                requireContract(old,contract == null ? null : contract.getId());
                requireOne(projections.retireUnstartedStage(new ProjectPlanProjectionMapper.NodeProjectionChange(tenant,project,row.getId(),row.getVersion(),actor)));
                requireOne(projections.closeStageContract(closure(request,row.getId(),contract.getId(),contract.getVersion())));
            }
            retired.add(new ProjectNodeExecutionMapper.PlanRetirement(tenant,project,old.getId(),old.getVersion(),old.getPlanVersionId(),actor));
        }
        // Free only renamed CURRENT codes, inside this transaction; archived rows retain their original code.
        for (var stage : desired.getStages()) {
            var node = stageDefinitions.get(stage.getCode());
            var oldRound = current.get(node.getNodeKey());
            stage.setId(oldRound == null ? IdWorker.getId() : oldRound.getNodeInstanceId()); stage.setTenantId(tenant);
            // Match the defaults used by initial inserts; clearing optional design values must not write SQL NULL to required columns.
            if (stage.getSortOrder() == null) stage.setSortOrder(0);
            var old = oldStages.get(stage.getId());
            if (old != null) {
                stage.setGraphVersion(old.getGraphVersion());
                if (!Objects.equals(old.getCode(),stage.getCode()))
                    requireOne(projections.stageCodeForRename(new ProjectPlanProjectionMapper.NodeProjectionChange(tenant,project,old.getId(),old.getVersion(),actor)));
            }
        }
        for (var task : desired.getTasks()) {
            task.setTenantId(tenant); task.setVersion(0);
            var old = oldTasks.get(task.getId());
            if (old != null) {
                var prior = previousDefinitions.get(taskDefinitions.get(task.getCode()).getNodeKey());
                preserveUnchangedTaskFields(prior,taskDefinitions.get(task.getCode()),old,task);
                if (!Objects.equals(old.getCode(),task.getCode()))
                    requireOne(projections.taskCodeForRename(new ProjectPlanProjectionMapper.NodeProjectionChange(tenant,project,old.getId(),old.getVersion(),actor)));
            }
            if (task.getSortOrder() == null) task.setSortOrder(0);
            if (task.getPriority() == null) task.setPriority(2);
            if (old == null || !Objects.equals(old.getSatisfactionTiming(),task.getSatisfactionTiming())) {
                if (old != null && old.getActualStartTime() != null) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
                cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectTaskSatisfactionSnapshot.freeze(request.project(),task,satisfactionTemplates);
            } else {
                task.setAccSatisfactionTemplateId(old.getAccSatisfactionTemplateId()); task.setTemplateRevisionId(old.getTemplateRevisionId());
                task.setTemplateVersion(old.getTemplateVersion()); task.setSatisfactionRuleVersion(old.getSatisfactionRuleVersion());
                task.setSatisfactionThreshold(old.getSatisfactionThreshold());
            }
        }
        String snapshot = JsonUtils.toJsonString(request.after());
        for (var stage : desired.getStages()) {
            var node = stageDefinitions.get(stage.getCode());
            var old = oldStages.get(stage.getId());
            if (old == null) requireOne(stageRows.insert(stage));
            else if (!stageMetadata(old).equals(stageMetadata(stage)))
                requireOne(projections.updateStageDefinition(new ProjectPlanProjectionMapper.StageDefinitionUpdate(tenant,project,old.getId(),old.getVersion(),stage,actor)));
            var round = current.get(node.getNodeKey());
            var contract = currentStageContracts.get(stage.getId());
            if (round != null) requireContract(round,contract == null ? null : contract.getId());
            if (round == null || !ended(round)) {
                var next = stageFactory.createContract(tenant,project,node,stage,snapshot,request.occurredAt());
                if (contract == null || !sameStageBinding(contract,next)) {
                    if (contract != null) {
                        requireOne(projections.closeStageContract(closure(request,stage.getId(),contract.getId(),contract.getVersion())));
                        next.setBindingVersion(contract.getBindingVersion()+1);
                    }
                    requireOne(stageContracts.insert(next)); contract = next;
                }
                installExecution(request,node.getNodeKey(),"STAGE",stage.getId(),contract.getId(),round,continuing);
            }
        }
        var bindings = content.getTasks().stream().collect(Collectors.toMap(node -> node.getSourceNodeKey(),Function.identity()));
        for (var task : desired.getTasks()) {
            var node = taskDefinitions.get(task.getCode());
            var old = oldTasks.get(task.getId());
            if (old == null) { requireOne(taskRows.insert(task)); tasksChanged = true; }
            else if (!taskMetadata(old).equals(taskMetadata(task))) {
                requireOne(projections.updateTaskDefinition(new ProjectPlanProjectionMapper.TaskDefinitionUpdate(tenant,project,old.getId(),old.getVersion(),task,actor)));
                tasksChanged = true;
            }
            var round = current.get(node.getNodeKey());
            if (round != null && ended(round)) continue;
            var contract = old == null ? null : taskContracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenant,task.getId()));
            if (round != null) requireContract(round,contract == null ? null : contract.getId());
            var next = taskFactory.create(task.getId(),null,bindings.get(node.getNodeKey()),request.occurredAt()); next.setTenantId(tenant);
            if (contract == null || !sameTaskBinding(contract,next)) {
                if (contract != null) {
                    if (sameTaskWorkBinding(contract,next)) next.setApprovalInstanceId(contract.getApprovalInstanceId());
                    requireOne(projections.closeTaskContract(closure(request,task.getId(),contract.getId(),contract.getVersion())));
                    next.setContractVersion(contract.getContractVersion()+1);
                }
                requireOne(taskContracts.insert(next)); contract = next;
            }
            installExecution(request,node.getNodeKey(),"TASK",task.getId(),contract.getId(),round,continuing);
        }
        if (tasksChanged) {
            projections.deleteCurrentTaskPaths(new ProjectPlanScopeQuery(tenant,project));
            for (var path : desired.getTaskTreePaths()) { path.setTenantId(tenant); requireOne(paths.insert(path)); }
            requireOne(projections.advanceTaskTreeVersion(new ProjectPlanProjectionMapper.TaskTreeVersionUpdate(tenant,project,request.project().getTaskTreeVersion(),actor)));
        }
        return new Installation(List.copyOf(continuing),List.copyOf(retired),tasksChanged);
    }

    private void installExecution(Request request,String key,String kind,Long instanceId,Long contractId,ProjectNodeExecutionDO old,
                                  List<ProjectNodeExecutionMapper.PlanRebase> continuing) {
        var project = request.project();
        if (old != null) {
            continuing.add(new ProjectNodeExecutionMapper.PlanRebase(project.getTenantId(),project.getId(),old.getId(),old.getVersion(),
                    old.getPlanVersionId(),request.newPlanId(),old.getContractId(),contractId,request.actorId().toString()));
            return;
        }
        var next = new ProjectNodeExecutionDO(); next.setId(IdWorker.getId()); next.setTenantId(project.getTenantId()); next.setProjectId(project.getId());
        next.setPlanVersionId(request.newPlanId()); next.setNodeKey(key); next.setNodeKind(kind); next.setNodeInstanceId(instanceId); next.setContractId(contractId);
        next.setRoundNo(executions.selectNextRoundNo(new ProjectNodeExecutionMapper.NodeRoundSequence(project.getTenantId(),project.getId(),key)));
        next.setCurrentMarker(1); next.setStatus("PENDING"); next.setVersion(0); next.setCreator(request.actorId().toString()); next.setUpdater(request.actorId().toString());
        next.setCreateTime(request.occurredAt()); next.setUpdateTime(request.occurredAt()); requireOne(executions.insert(next));
    }
    private ProjectPlanProjectionMapper.ContractClosure closure(Request request,Long instance,Long contract,Integer version) {
        return new ProjectPlanProjectionMapper.ContractClosure(request.project().getTenantId(),request.project().getId(),instance,contract,version,request.occurredAt(),request.actorId().toString());
    }
    private boolean ended(ProjectNodeExecutionDO round) { return Set.of("DONE","TERMINATED").contains(round.getStatus()); }
    private void requireOne(int affected) { if (affected != 1) throw exception(PROJECT_PLAN_VERSION_CONFLICT); }
    private void requireContract(ProjectNodeExecutionDO round,Long contract) {
        if (!Objects.equals(round.getContractId(),contract)) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
    }
    private boolean jsonEquals(String left,String right) {
        if (Objects.equals(left,right)) return true;
        return left != null && right != null && Objects.equals(JsonUtils.parseTree(left),JsonUtils.parseTree(right));
    }
    private boolean sameStageBinding(ProjectStageExecutionContractDO old,ProjectStageExecutionContractDO next) {
        return Objects.equals(old.getBindingType(),next.getBindingType()) && declarationEquals(old.getBindingSnapshot(),next.getBindingSnapshot())
                && declarationEquals(old.getPermissionSnapshot(),next.getPermissionSnapshot());
    }
    private boolean sameTaskBinding(ProjectTaskExecutionContractDO old,ProjectTaskExecutionContractDO next) {
        return sameTaskWorkBinding(old,next) && Objects.equals(old.getPermissionPolicyRef(),next.getPermissionPolicyRef())
                && Objects.equals(old.getGateRef(),next.getGateRef()) && declarationEquals(old.getPermissionSnapshot(),next.getPermissionSnapshot());
    }
    private boolean declarationEquals(String left,String right) {
        if (Objects.equals(left,right)) return true;
        return Objects.equals(withoutProvenance(left),withoutProvenance(right));
    }
    private tools.jackson.databind.JsonNode withoutProvenance(String value) {
        if (value == null) return null;
        var declaration = JsonUtils.parseTree(value);
        if (declaration.isObject()) ((tools.jackson.databind.node.ObjectNode)declaration).remove("sourceRevisionId");
        return declaration;
    }
    private boolean sameTaskWorkBinding(ProjectTaskExecutionContractDO old,ProjectTaskExecutionContractDO next) {
        return Objects.equals(Arrays.asList(old.getWorkBindingTypeCode(),old.getTargetContextCode(),old.getTargetObjectType(),old.getTargetObjectKey(),old.getComponentKey(),old.getDynamicFormRevisionId()),
                Arrays.asList(next.getWorkBindingTypeCode(),next.getTargetContextCode(),next.getTargetObjectType(),next.getTargetObjectKey(),next.getComponentKey(),next.getDynamicFormRevisionId()))
                && jsonEquals(old.getBindingParameterSnapshot(),next.getBindingParameterSnapshot())
                && jsonEquals(old.getBindingViewSnapshot(),next.getBindingViewSnapshot());
    }
    private List<?> stageMetadata(ProjectStageInstanceDO row) {
        return Arrays.asList(row.getCode(),row.getName(),row.getSortOrder(),row.getEntryCriteria(),row.getExitCriteria(),row.getStartNode(),row.getTerminalNode());
    }
    private List<?> taskMetadata(ProjectTaskInstanceDO row) {
        return Arrays.asList(row.getCode(),row.getName(),row.getStageCode(),row.getParentTaskCode(),row.getParentTaskId(),row.getRootTaskId(),row.getTreeDepth(),row.getPriority(),row.getSortOrder(),row.getEstimatedHours(),row.getDescription(),row.getSatisfactionTiming());
    }
    private void preserveUnchangedTaskFields(TemplateExecutionSnapshot.TaskContract before,TemplateExecutionSnapshot.TaskContract after,
                                             ProjectTaskInstanceDO actual,ProjectTaskInstanceDO desired) {
        if (Objects.equals(before.getName(),after.getName())) desired.setName(actual.getName());
        if (Objects.equals(before.getDescription(),after.getDescription())) desired.setDescription(actual.getDescription());
        if (Objects.equals(before.getPriority(),after.getPriority())) desired.setPriority(actual.getPriority());
        if (Objects.equals(before.getSortOrder(),after.getSortOrder())) desired.setSortOrder(actual.getSortOrder());
        if (Objects.equals(before.getEstimatedHours(),after.getEstimatedHours())) desired.setEstimatedHours(actual.getEstimatedHours());
        if (Objects.equals(before.getSatisfactionTiming(),after.getSatisfactionTiming())) desired.setSatisfactionTiming(actual.getSatisfactionTiming());
    }
}
