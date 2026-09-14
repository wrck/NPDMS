package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectStageBusinessQueryService {
    private final ProjectManualCreationService projects;
    private final ProjectRuntimeGraphMapper graph;
    private final BusinessViewQueryApi views;
    private final List<StageBusinessViewProvider> providers;
    private final ProjectNodeExecutionApi executions;
    private final ProjectStageApprovalService approvals;

    public StageBusinessContext getContext(Long projectId, String stageCode, ProjectAccessActor actor) {
        // The existing project query is the scope authority, including super-admin and tenant rules.
        var project = projects.getProject(projectId, actor);
        var query = new ProjectRuntimeGraphQuery(actor.tenantId(), projectId);
        var stages = graph.selectStages(query).stream().filter(s -> stageCode.equals(s.getStageCode())).toList();
        if (stages.size() != 1) return unavailable(projectId, stageCode, null, null, "STAGE_NOT_FOUND");
        var stage = stages.getFirst();
        var contracts = graph.selectContracts(query).stream().filter(c -> Objects.equals(c.getStageId(), stage.getId())).toList();
        if (contracts.size() != 1) return unavailable(projectId, stageCode, stage, null, "STAGE_CONTRACT_NOT_FROZEN");
        var contract = contracts.getFirst();
        if (!Objects.equals(stage.getTenantId(), actor.tenantId()) || !Objects.equals(stage.getProjectId(), projectId)
                || !Objects.equals(contract.getTenantId(), actor.tenantId()) || !Objects.equals(contract.getProjectId(), projectId)
                || stage.getGraphVersion() == null || !Objects.equals(stage.getGraphVersion(), contract.getGraphVersion())
                || contract.getSourceNodeKey() == null || contract.getSourceNodeKey().isBlank()
                || contract.getEffectiveTo() != null)
            return unavailable(projectId, stageCode, stage, contract, "STAGE_CONTRACT_STALE");
        try {
            var snapshot = JsonUtils.parseObject(contract.getDefinitionSnapshot(), TemplateExecutionSnapshot.class);
            var frozenNodes = snapshot.getStages().stream().filter(node ->
                    contract.getSourceNodeKey().equals(node.getNodeKey()) && stageCode.equals(node.getCode())).toList();
            if (frozenNodes.size() != 1)
                return unavailable(projectId, stageCode, stage, contract, "STAGE_CONTRACT_STALE");
            var frozen = frozenNodes.getFirst();
            var binding = frozen.getBinding();
            if (!Objects.equals(json(binding), JsonUtils.parseTree(contract.getBindingSnapshot()))
                    || !Objects.equals(binding == null ? "STAGE_NATIVE" : binding.getType(), contract.getBindingType())
                    || !Objects.equals(json(frozen.getPermission()), JsonUtils.parseTree(contract.getPermissionSnapshot()))
                    || !Objects.equals(frozen.getCompletionRule(), JsonUtils.parseTree(contract.getCompletionRuleSnapshot())))
                return unavailable(projectId, stageCode, stage, contract, "STAGE_BINDING_SNAPSHOT_MISMATCH");
            if ("STAGE_NATIVE".equals(contract.getBindingType())) {
                return new StageBusinessContext(projectId, stage.getId(), stageCode, contract.getId(),
                        contract.getBindingVersion(), contract.getBindingType(), null, null, Set.of(), true, null, null, null);
            }
            if ("APPROVAL".equals(contract.getBindingType())) {
                var execution = executions.inspectStage(new ProjectStageExecutionQuery(projectId, stage.getId(), contract.getId()));
                var approval = approvals.view(actor.tenantId(), execution, binding);
                boolean readonly = !"ACTIVE".equals(project.getLifecycleStatus()) || !"ACTIVE".equals(stage.getStatus())
                        || !execution.writable() || approval.current().outcome()
                        == cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.UNKNOWN;
                // Approval routing is independent of business-page registration. BPM retains per-operation authorization.
                return new StageBusinessContext(projectId, stage.getId(), stageCode, contract.getId(),
                        contract.getBindingVersion(), contract.getBindingType(), null, null,
                        readonly ? Set.of("QUERY") : Set.of("QUERY", "APPROVAL"), readonly,
                        approval.current().outcome() == cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.UNKNOWN
                                ? approval.current().reason() : null, execution, approval);
            }
            if (binding == null || binding.getBusinessViewSnapshot() == null)
                return unavailable(projectId, stageCode, stage, contract, "VIEW_NOT_FROZEN");
            var frozenView = JsonUtils.convertObject(binding.getBusinessViewSnapshot(), BusinessViewRevision.class);
            if (frozenView.id() == null || frozenView.id() <= 0)
                return unavailable(projectId, stageCode, stage, contract, "VIEW_NOT_FROZEN");
            var view = views.getRevision(new BusinessViewQueryApi.Query(frozenView.id(),
                    BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
            String owner = binding.getTargetContextCode();
            String type = binding.getTargetObjectType();
            if (view == null || !sameViewContract(frozenView, view)
                    || !Objects.equals(owner, view.ownerContext()) || !Objects.equals(type, view.entityType())
                    || !Objects.equals(binding.getComponentKey(), view.componentKey())
                    || !Set.of("PUBLISHED", "DISABLED").contains(view.status()))
                return unavailable(projectId, stageCode, stage, contract, "VIEW_IDENTITY_MISMATCH");
            var matches = providers.stream().filter(p -> owner.equals(p.ownerContext()) && type.equals(p.objectType())).toList();
            if (matches.size() != 1)
                return unavailable(projectId, stageCode, stage, contract, "STAGE_OWNER_PROVIDER_UNAVAILABLE");
            String strategy = binding.getParameters() == null ? "" : binding.getParameters().path("instanceResolutionStrategy").asText();
            if (!Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE").contains(strategy))
                return unavailable(projectId, stageCode, stage, contract, "STAGE_ENTER_COMMAND_NOT_CONNECTED");
            ProjectStageExecutionContext execution = null;
            try {
                execution = executions.inspectStage(new ProjectStageExecutionQuery(projectId, stage.getId(), contract.getId()));
            } catch (RuntimeException unavailableExecution) {
                // Frozen history remains queryable, but unavailable/current-plan-mismatched execution cannot grant a write.
            }
            var result = matches.getFirst().inspectStage(new StageBusinessViewProvider.Context(actor.tenantId(), actor.actorId(),
                    projectId, stage.getId(), binding.getTargetObjectKey(), strategy, execution));
            if (result == null || !result.allowedActions().contains("QUERY"))
                return unavailable(projectId, stageCode, stage, contract, "OWNER_CONTEXT_FORBIDDEN");
            boolean readonly = "READ_ONLY_AGGREGATE".equals(strategy) || "DISABLED".equals(view.status())
                    || !"ACTIVE".equals(project.getLifecycleStatus()) || !"ACTIVE".equals(stage.getStatus())
                    || execution == null || !execution.writable();
            return new StageBusinessContext(projectId, stage.getId(), stageCode, contract.getId(), contract.getBindingVersion(),
                    contract.getBindingType(), strategy, view, readonly ? Set.of("QUERY") : result.allowedActions(), readonly,
                    execution == null && "ACTIVE".equals(stage.getStatus()) ? "STAGE_EXECUTION_UNAVAILABLE" : null, execution, null);
        } catch (RuntimeException unavailable) {
            return unavailable(projectId, stageCode, stage, contract, "STAGE_BINDING_UNAVAILABLE");
        }
    }

    private static tools.jackson.databind.JsonNode json(Object value) {
        return JsonUtils.parseTree(JsonUtils.toJsonString(value));
    }

    private static boolean sameViewContract(BusinessViewRevision frozen, BusinessViewRevision actual) {
        // Registry disablement is allowed to make a frozen view read-only; its execution contract cannot drift.
        return Objects.equals(frozen.id(), actual.id()) && Objects.equals(frozen.revisionNo(), actual.revisionNo())
                && Objects.equals(frozen.ownerContext(), actual.ownerContext()) && Objects.equals(frozen.entityType(), actual.entityType())
                && Objects.equals(frozen.viewSource(), actual.viewSource())
                && Objects.equals(frozen.componentKey(), actual.componentKey()) && Objects.equals(frozen.componentVersion(), actual.componentVersion())
                && Objects.equals(frozen.dynamicFormRevisionId(), actual.dynamicFormRevisionId())
                && Objects.equals(frozen.contextSchema(), actual.contextSchema()) && Objects.equals(frozen.supportedActions(), actual.supportedActions())
                && Objects.equals(frozen.queryProviderKey(), actual.queryProviderKey())
                && Objects.equals(frozen.commandProviderKey(), actual.commandProviderKey())
                && Objects.equals(frozen.permissionProviderKey(), actual.permissionProviderKey());
    }

    private StageBusinessContext unavailable(Long projectId, String code, ProjectStageInstanceDO stage,
            ProjectStageExecutionContractDO contract, String reason) {
        return new StageBusinessContext(projectId, stage == null ? null : stage.getId(), code,
                contract == null ? null : contract.getId(), contract == null ? null : contract.getBindingVersion(),
                contract == null ? null : contract.getBindingType(), null, null, Set.of(), true, reason, null, null);
    }
}
