package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectLifecycleStageFactApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Standard lifecycle summary, not the identity or authorization of the single active node. */
@Service
@RequiredArgsConstructor
public class ProjectCurrentStageService implements ProjectLifecycleStageFactApi {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectPlanProjectionMapper projections;

    /** Joins the stage/plan/round transition transaction; no read endpoint repairs state. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Integer synchronize(Long projectId) {
        var project = projects.selectByIdForUpdate(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("PROJECT_NOT_FOUND");
        if (project.getActivePlanVersionId() == null || !"ACTIVE".equals(project.getLifecycleStatus())) return project.getVersion();
        var active = activeBindings(project);
        if (active.isEmpty() || Objects.equals(active.first(), project.getCurrentStage())) return project.getVersion();
        if (projections.updateCurrentStage(new ProjectPlanProjectionMapper.CurrentStageUpdate(project.getTenantId(),
                projectId, project.getActivePlanVersionId(), project.getVersion(), active.first(), "project-rules")) != 1)
            throw new IllegalStateException("PROJECT_CURRENT_STAGE_VERSION_CONFLICT");
        return project.getVersion() + 1;
    }

    /** A later parallel stage must not be blocked by the earliest lifecycle summary. */
    @Override
    public boolean isActive(Query query) {
        if (query == null || query.projectId() == null || query.expectedProjectVersion() == null
                || query.lifecycleStage() == null || !query.lifecycleStage().matches("S[0-6]"))
            throw new IllegalArgumentException("PROJECT_LIFECYCLE_QUERY_INVALID");
        var project = projects.selectById(query.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(project.getVersion(), query.expectedProjectVersion()))
            throw new IllegalStateException("PROJECT_LIFECYCLE_FACT_STALE");
        return isActive(project, query.lifecycleStage());
    }

    public boolean isActive(ProjectMasterDO project, String lifecycleStage) {
        if (project.getActivePlanVersionId() == null) return Objects.equals(project.getCurrentStage(), lifecycleStage);
        return "ACTIVE".equals(project.getLifecycleStatus()) && activeBindings(project).contains(lifecycleStage);
    }

    private TreeSet<String> activeBindings(ProjectMasterDO project) {
        if (!Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("PROJECT_TENANT_MISMATCH");
        var plan = plans.selectEffective(new ProjectPlanScopeQuery(project.getTenantId(), project.getId()));
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId()))
            throw new IllegalStateException("PROJECT_PLAN_VERSION_UNAVAILABLE");
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        var result = new TreeSet<String>();
        for (var stage : graph.selectStages(new ProjectRuntimeGraphQuery(project.getTenantId(), project.getId()))) {
            if (!"ACTIVE".equals(stage.getStatus())) continue;
            var definitions = snapshot.getStages().stream().filter(node -> Objects.equals(node.getCode(), stage.getCode())).toList();
            if (definitions.size() != 1 || definitions.getFirst().getLifecycleStage() == null
                    || !Set.of("S0", "S1", "S2", "S3", "S4", "S5", "S6").contains(definitions.getFirst().getLifecycleStage()))
                throw new IllegalStateException("STAGE_LIFECYCLE_BINDING_UNAVAILABLE");
            result.add(definitions.getFirst().getLifecycleStage());
        }
        return result;
    }
}
