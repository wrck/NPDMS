package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.ZoneId;

/** 固定providerKey到唯一Owner实现的注册表。 */
@Component
public class ProjectStageGateProviderRegistry {

    private final Map<String, ProjectStageGateFactProviderApi> providers;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectNodeExecutionMapper executions;

    public ProjectStageGateProviderRegistry(List<ProjectStageGateFactProviderApi> implementations,
                                           ProjectRuntimeGraphMapper graph, ProjectNodeExecutionMapper executions) {
        this.graph = graph;
        this.executions = executions;
        Map<String, ProjectStageGateFactProviderApi> resolved = new LinkedHashMap<>();
        for (ProjectStageGateFactProviderApi implementation : implementations) {
            if (implementation == null || implementation.providerKeys() == null
                    || implementation.providerKeys().isEmpty()) {
                throw new IllegalStateException("stage gate provider keys must not be empty");
            }
            for (String providerKey : implementation.providerKeys()) {
                if (providerKey == null || providerKey.isBlank()
                        || resolved.putIfAbsent(providerKey, implementation) != null) {
                    throw new IllegalStateException("duplicate or blank stage gate provider: " + providerKey);
                }
            }
        }
        this.providers = Map.copyOf(resolved);
    }

    public boolean hasProvider(String providerKey) {
        return providers.containsKey(providerKey);
    }

    public ProjectStageGateFact lockAndRevalidate(String providerKey, ProjectStageGateFactQuery query) {
        ProjectStageGateFactProviderApi provider = providers.get(providerKey);
        if (provider == null) {
            throw new IllegalStateException("stage gate provider unavailable: " + providerKey);
        }
        if (ProjectStageGateFactProviderApi.PROVIDER_BPM_APPROVAL.equals(providerKey)
                || ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS.equals(providerKey)) {
            var scoped = currentStageRound(query);
            if (scoped == null) return new ProjectStageGateFact(providerKey, query.refType(), "UNKNOWN", "UNKNOWN",
                    "UNKNOWN", ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, "APPROVAL_STAGE_ROUND_UNAVAILABLE");
            query = scoped;
        }
        ProjectStageGateFact fact = provider.lockAndRevalidate(query);
        if (fact == null || !providerKey.equals(fact.providerKey())) {
            throw new IllegalStateException("stage gate provider returned mismatched fact: " + providerKey);
        }
        return fact;
    }

    private ProjectStageGateFactQuery currentStageRound(ProjectStageGateFactQuery query) {
        var stages = graph.selectStages(new ProjectRuntimeGraphQuery(query.tenantId(), query.projectId())).stream()
                .filter(stage -> Objects.equals(stage.getStageCode(), query.currentStageCode())
                        && Objects.equals(stage.getTenantId(), query.tenantId())
                        && Objects.equals(stage.getProjectId(), query.projectId())).toList();
        if (stages.size() != 1 || stages.getFirst().getId() == null) return null;
        var rounds = executions.selectCurrent(new ProjectPlanScopeQuery(query.tenantId(), query.projectId())).stream()
                .filter(round -> "STAGE".equals(round.getNodeKind())
                        && Objects.equals(round.getNodeInstanceId(), stages.getFirst().getId())
                        && Objects.equals(round.getTenantId(), query.tenantId())
                        && Objects.equals(round.getProjectId(), query.projectId())
                        && Integer.valueOf(1).equals(round.getCurrentMarker())).toList();
        if (rounds.size() != 1 || rounds.getFirst().getId() == null || rounds.getFirst().getCreateTime() == null) return null;
        // Completion after rework is not sufficient: the process must also have started in this round.
        return query.forStageRound(rounds.getFirst().getCreateTime().atZone(ZoneId.systemDefault()).toInstant());
    }
}
