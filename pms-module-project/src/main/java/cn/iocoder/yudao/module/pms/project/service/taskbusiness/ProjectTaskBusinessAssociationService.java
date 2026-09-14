package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessBindingHostProvider;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Uniform automatic relationship reconciliation. Business data stays with its Owner; old links remain history. */
@Service
@RequiredArgsConstructor
public class ProjectTaskBusinessAssociationService {
    private final ProjectTaskRuntimeMapper tasks;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectTaskBusinessLinkMapper links;
    private final TaskBusinessProviderRegistry providers;
    private final OperationAuditApi audit;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;

    @Transactional(rollbackFor = Exception.class)
    public void synchronize(Long projectId, Long taskId, String correlationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = tasks.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) return;
        var task = tasks.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(tenantId, projectId, taskId));
        if (task == null || Set.of("DONE", "CLOSED", "CANCELLED", "CANCELED").contains(task.getStatus())) return;
        var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenantId, taskId));
        if (contract == null || !TaskBusinessBindingHostProvider.TYPES.contains(contract.getWorkBindingTypeCode())) return;
        var receiving = executions.selectCurrentForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery(tenantId, projectId))
                .stream().filter(row -> "TASK".equals(row.getNodeKind()) && taskId.equals(row.getNodeInstanceId())).toList();
        if (receiving.size() != 1) throw new IllegalStateException("CURRENT_TASK_EXECUTION_REQUIRED");
        if (!Objects.equals(receiving.getFirst().getContractId(), contract.getId())
                || !Objects.equals(receiving.getFirst().getPlanVersionId(), project.getActivePlanVersionId()))
            throw new IllegalStateException("CURRENT_TASK_EXECUTION_STALE");
        reconcile(new Receiver(tenantId, projectId, taskId, null, receiving.getFirst(), contract.getId(), contract.getContractVersion(),
                contract.getTargetContextCode(), contract.getTargetObjectType(), contract.getTargetObjectKey(), contract.getBindingParameterSnapshot()), correlationId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void synchronizeStage(Long projectId, Long stageId, String correlationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = tasks.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) return;
        var scope = new ProjectRuntimeGraphQuery(tenantId,projectId);
        var stages = graph.selectStagesForUpdate(scope).stream().filter(row -> stageId.equals(row.getId())).toList();
        if (stages.size()!=1) throw new IllegalStateException("CURRENT_STAGE_REQUIRED");
        var stage = stages.getFirst();
        if (!"ACTIVE".equals(stage.getStatus())) return;
        var currentContracts = graph.selectContracts(scope).stream().filter(row -> stageId.equals(row.getStageId())).toList();
        if (currentContracts.size()!=1) throw new IllegalStateException("CURRENT_STAGE_CONTRACT_REQUIRED");
        var contract = currentContracts.getFirst();
        if (!Objects.equals(stage.getTenantId(),tenantId) || !Objects.equals(stage.getProjectId(),projectId)
                || !Objects.equals(contract.getTenantId(),tenantId) || !Objects.equals(contract.getProjectId(),projectId)
                || contract.getEffectiveTo()!=null || !Objects.equals(stage.getGraphVersion(),contract.getGraphVersion()))
            throw new IllegalStateException("CURRENT_STAGE_CONTRACT_STALE");
        if (!TaskBusinessBindingHostProvider.TYPES.contains(contract.getBindingType())) return;
        var binding = JsonUtils.parseObject(contract.getBindingSnapshot(),TemplateExecutionSnapshot.BindingContract.class);
        if (binding==null || !Objects.equals(contract.getBindingType(),binding.getType()))
            throw new IllegalStateException("CURRENT_STAGE_BINDING_REQUIRED");
        var receiving = executions.selectCurrentForUpdate(new ProjectPlanScopeQuery(tenantId,projectId)).stream()
                .filter(row -> "STAGE".equals(row.getNodeKind()) && stageId.equals(row.getNodeInstanceId())).toList();
        if (receiving.size()!=1) throw new IllegalStateException("CURRENT_STAGE_EXECUTION_REQUIRED");
        var execution = receiving.getFirst();
        if (!"ACTIVE".equals(execution.getStatus()) || !Objects.equals(execution.getContractId(),contract.getId())
                || !Objects.equals(execution.getNodeKey(),contract.getSourceNodeKey())
                || !Objects.equals(execution.getPlanVersionId(),project.getActivePlanVersionId()))
            throw new IllegalStateException("CURRENT_STAGE_EXECUTION_STALE");
        reconcile(new Receiver(tenantId,projectId,null,stageId,execution,contract.getId(),contract.getBindingVersion(),
                binding.getTargetContextCode(),binding.getTargetObjectType(),binding.getTargetObjectKey(),
                JsonUtils.toJsonString(binding.getParameters())),correlationId);
    }

    private record Receiver(Long tenantId, Long projectId, Long taskId, Long stageId, ProjectNodeExecutionDO execution,
                            Long contractId, Integer contractVersion, String ownerContext, String objectType,
                            String targetObjectKey, String bindingParameters) { }

    private void reconcile(Receiver receiver, String correlationId) {
        Long tenantId = receiver.tenantId(), projectId = receiver.projectId(), taskId = receiver.taskId(), stageId = receiver.stageId();
        Long executionId = receiver.execution().getId();
        var provider = providers.require(receiver.ownerContext(), receiver.objectType());
        var context = new TaskBusinessObjectProvider.AssociationContext(tenantId, projectId,
                receiver.targetObjectKey(), receiver.bindingParameters());
        Map<String, TaskBusinessObjectProvider.AssociationCandidate> candidates = new LinkedHashMap<>();
        String cursor = null;
        while (true) {
            var page = provider.associationCandidates(context, cursor, 100);
            if (page == null || page.size() > 100) throw new IllegalStateException("OWNER_ASSOCIATION_UNAVAILABLE");
            for (var candidate : page) {
                if (candidate == null || candidate.objectId() == null || candidate.objectId().isBlank()
                        || candidate.factVersion() == null || candidate.factVersion().isBlank()
                        || candidates.putIfAbsent(candidate.objectId(), candidate) != null)
                    throw new IllegalStateException("OWNER_ASSOCIATION_INVALID");
            }
            if (page.size() < 100) break;
            cursor = page.getLast().objectId();
        }
        var query = new TaskBusinessLinksQuery(tenantId, projectId, taskId, stageId);
        var current = links.selectActiveForUpdate(query);
        // New records receive the latest execution. Old-round associations are history, not new handling evidence.
        if (!candidates.isEmpty()) {
            var previous = links.selectPreviouslyAssociatedObjectIds(new PreviousBusinessAssociationsQuery(tenantId,
                    projectId, taskId, executionId, receiver.ownerContext(), receiver.objectType(), Set.copyOf(candidates.keySet()), stageId));
            Set<String> currentInitialReferences = new HashSet<>();
            if (taskId != null && Integer.valueOf(1).equals(receiver.execution().getRoundNo())) {
                // Existing live references in the initial execution are not old-round completion evidence.
                current.stream().filter(link -> link.getNodeExecutionId() == null
                        && Objects.equals(link.getExecutionContractId(), receiver.contractId())
                        && Objects.equals(link.getContractVersion(), receiver.contractVersion())
                        && Objects.equals(link.getOwnerContext(), receiver.ownerContext())
                        && Objects.equals(link.getObjectType(), receiver.objectType()))
                        .forEach(link -> currentInitialReferences.add(link.getObjectId()));
            }
            previous.stream().filter(id -> !currentInitialReferences.contains(id)).forEach(candidates::remove);
        }
        Set<String> retained = new HashSet<>();
        List<Long> opened = new ArrayList<>(), closed = new ArrayList<>();
        var now = LocalDateTime.now();
        for (var link : current) {
            boolean sameBinding = Objects.equals(link.getExecutionContractId(), receiver.contractId())
                    && Objects.equals(link.getNodeExecutionId(), executionId)
                    && Objects.equals(link.getContractVersion(), receiver.contractVersion())
                    && Objects.equals(link.getOwnerContext(), receiver.ownerContext())
                    && Objects.equals(link.getObjectType(), receiver.objectType());
            if (sameBinding && candidates.containsKey(link.getObjectId())) { retained.add(link.getObjectId()); continue; }
            if (links.unlinkIfMatch(new TaskBusinessUnlinkUpdate(tenantId, projectId, taskId, link.getId(), link.getVersion(), 0L, now, stageId)) != 1)
                throw new IllegalStateException("AUTO_ASSOCIATION_VERSION_CONFLICT");
            closed.add(link.getId());
        }
        for (var candidate : candidates.values()) {
            if (retained.contains(candidate.objectId())) continue;
            var link = new ProjectTaskBusinessLinkDO();
            link.setId(IdWorker.getId()); link.setTenantId(tenantId); link.setProjectId(projectId); link.setTaskId(taskId);
            link.setStageId(stageId);
            link.setNodeExecutionId(executionId);
            link.setExecutionContractId(receiver.contractId()); link.setContractVersion(receiver.contractVersion());
            link.setOwnerContext(receiver.ownerContext()); link.setObjectType(receiver.objectType());
            link.setObjectId(candidate.objectId()); link.setFactVersion(candidate.factVersion());
            link.setLinkedBy(0L); link.setLinkedAt(now); link.setVersion(0); link.setCreator("0"); link.setUpdater("0");
            link.setCreateTime(now); link.setUpdateTime(now);
            if (links.insertLink(link) != 1) throw new IllegalStateException("AUTO_ASSOCIATION_WRITE_FAILED");
            opened.add(link.getId());
        }
        if (!opened.isEmpty() || !closed.isEmpty()) audit.record(tenantId, 0L, correlationId,
                stageId == null ? "PROJECT_TASK_BUSINESS_AUTO_ASSOCIATE" : "PROJECT_STAGE_BUSINESS_AUTO_ASSOCIATE",
                stageId == null ? "ProjectTask" : "ProjectStage", (stageId == null ? taskId : stageId).toString(), "SUCCESS",
                Map.of("projectId", projectId, "nodeExecutionId", executionId, "executionContractId", receiver.contractId(), "openedLinks", opened, "closedLinks", closed));
    }
}
