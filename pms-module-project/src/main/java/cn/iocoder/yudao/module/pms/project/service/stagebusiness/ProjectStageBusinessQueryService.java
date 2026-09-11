package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.FrozenDefinitions;
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
                || stage.getDefinitionRevisionId() == null || !Objects.equals(stage.getDefinitionRevisionId(), contract.getDefinitionRevisionId())
                || contract.getEffectiveTo() != null)
            return unavailable(projectId, stageCode, stage, contract, "STAGE_CONTRACT_STALE");
        try {
            var frozen = new FrozenDefinitions(contract.getDefinitionSnapshot());
            var binding = frozen.require(contract.getWorkBindingRevisionId(), "WORK_BINDING");
            frozen.require(contract.getPermissionPolicyRevisionId(), "PERMISSION_POLICY");
            frozen.require(contract.getCompletionRuleRevisionId(), "COMPLETION_RULE");
            if (!binding.equals(JsonUtils.parseTree(contract.getBindingSnapshot()))
                    || !Objects.equals(binding.path("bindingType").asText(), contract.getBindingType()))
                return unavailable(projectId, stageCode, stage, contract, "STAGE_BINDING_SNAPSHOT_MISMATCH");
            if ("STAGE_NATIVE".equals(contract.getBindingType())) {
                return new StageBusinessContext(projectId, stage.getId(), stageCode, contract.getId(),
                        contract.getBindingVersion(), contract.getBindingType(), null, null, Set.of(), true, null);
            }
            var revision = binding.path("businessViewRevisionId");
            if (!revision.asText().matches("[1-9][0-9]*"))
                return unavailable(projectId, stageCode, stage, contract, "VIEW_NOT_FROZEN");
            var view = views.getRevision(new BusinessViewQueryApi.Query(Long.valueOf(revision.asText()),
                    BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
            String owner = binding.path("targetContextCode").asText();
            String type = binding.path("targetObjectType").asText();
            if (view == null || !Objects.equals(view.id(), Long.valueOf(revision.asText()))
                    || !owner.equals(view.ownerContext()) || !type.equals(view.entityType())
                    || !Set.of("PUBLISHED", "DISABLED").contains(view.status()))
                return unavailable(projectId, stageCode, stage, contract, "VIEW_IDENTITY_MISMATCH");
            var matches = providers.stream().filter(p -> owner.equals(p.ownerContext()) && type.equals(p.objectType())).toList();
            if (matches.size() != 1)
                return unavailable(projectId, stageCode, stage, contract, "STAGE_OWNER_PROVIDER_UNAVAILABLE");
            String strategy = binding.path("instanceResolutionStrategy").asText();
            if (!Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE").contains(strategy))
                return unavailable(projectId, stageCode, stage, contract, "STAGE_ENTER_COMMAND_NOT_CONNECTED");
            var result = matches.getFirst().inspectStage(new StageBusinessViewProvider.Context(actor.tenantId(), actor.actorId(),
                    projectId, stage.getId(), binding.path("targetObjectKey").asText(), strategy));
            if (result == null || !result.allowedActions().contains("QUERY"))
                return unavailable(projectId, stageCode, stage, contract, "OWNER_CONTEXT_FORBIDDEN");
            boolean readonly = "READ_ONLY_AGGREGATE".equals(strategy) || "DISABLED".equals(view.status())
                    || !"ACTIVE".equals(project.getLifecycleStatus());
            return new StageBusinessContext(projectId, stage.getId(), stageCode, contract.getId(), contract.getBindingVersion(),
                    contract.getBindingType(), strategy, view, readonly ? Set.of("QUERY") : result.allowedActions(), readonly, null);
        } catch (RuntimeException unavailable) {
            return unavailable(projectId, stageCode, stage, contract, "STAGE_BINDING_UNAVAILABLE");
        }
    }

    private StageBusinessContext unavailable(Long projectId, String code, ProjectStageInstanceDO stage,
            ProjectStageExecutionContractDO contract, String reason) {
        return new StageBusinessContext(projectId, stage == null ? null : stage.getId(), code,
                contract == null ? null : contract.getId(), contract == null ? null : contract.getBindingVersion(),
                contract == null ? null : contract.getBindingType(), null, null, Set.of(), true, reason);
    }
}
