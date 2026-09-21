package cn.iocoder.yudao.module.pms.project.service.deliverable;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectDeliverableRuleService implements ProjectDeliverableRuleApi {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuleCompiler compiler;
    private final ObjectProvider<ProjectRuntimeRuleEvaluator> evaluator;
    private final ThreadLocal<Set<String>> evaluating = ThreadLocal.withInitial(HashSet::new);

    @Override public Context read(Long projectId, String code) {
        return context(require(projects.selectById(projectId)), code);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Context lock(Long projectId, String code) {
        var project = lockProject(projectId);
        var query = new ProjectRuntimeGraphQuery(project.getTenantId(), projectId);
        graph.selectStagesForUpdate(query);
        graph.selectTasksForUpdate(query);
        var gates = graph.selectGatesForUpdate(query);
        if (!gates.isEmpty()) references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(
                project.getTenantId(), gates.stream().map(gate -> gate.getId()).toList()));
        return context(project, code);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Decision evaluate(Long projectId, String code) {
        String key = TenantContextHolder.getRequiredTenantId() + ":" + projectId + ":" + code;
        Set<String> active = evaluating.get();
        if (!active.add(key)) return new Decision(false, "DELIVERABLE_RULE_CYCLE", "{}");
        try {
            var context = lock(projectId, code);
            var project = lockProject(projectId);
            var query = new ProjectRuntimeGraphQuery(project.getTenantId(), projectId);
            var stages = graph.selectStagesForUpdate(query).stream()
                    .filter(stage -> Objects.equals(context.stageCode(), stage.getCode())).toList();
            if (stages.size() != 1) return new Decision(false, "DELIVERABLE_STAGE_UNAVAILABLE", "{}");
            var gates = graph.selectGatesForUpdate(query);
            var refs = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                    : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(project.getTenantId(),
                    gates.stream().map(gate -> gate.getId()).toList()));
            var facts = new ProjectRuntimeRuleEvaluator.Facts(project, stages.getFirst(),
                    graph.selectTasksForUpdate(query), gates, refs, false);
            var program = compiler.compile(context.configuration().path("confirmationRule"));
            var result = evaluator.getObject().evaluate("plan:" + context.planVersionId() + ":deliverable:" + code, program, facts);
            boolean matched = result.outcome() == cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.MATCHED;
            return new Decision(matched, matched ? "DELIVERABLE_RULE_SATISFIED"
                    : result.reasonCode() == null ? "DELIVERABLE_RULE_NOT_SATISFIED" : result.reasonCode(), JsonUtils.toJsonString(result));
        } finally {
            active.remove(key);
            if (active.isEmpty()) evaluating.remove();
        }
    }

    private ProjectMasterDO lockProject(Long id) {
        var observed = require(projects.selectById(id));
        Long rootId = observed.getRootId() == null ? id : observed.getRootId();
        var root = require(projects.selectByIdForUpdate(rootId));
        var project = Objects.equals(rootId, id) ? root : require(projects.selectByIdForUpdate(id));
        if (!Objects.equals(rootId, project.getRootId() == null ? id : project.getRootId()))
            throw new IllegalStateException("DELIVERABLE_PROJECT_TREE_CHANGED");
        return project;
    }

    private Context context(ProjectMasterDO project, String code) {
        var plan = project.getActivePlanVersionId() == null ? null : plans.selectById(project.getActivePlanVersionId());
        if (plan == null || !Objects.equals(plan.getProjectId(), project.getId())
                || !Objects.equals(plan.getTenantId(), project.getTenantId()) || !"EFFECTIVE".equals(plan.getStatus()))
            throw new IllegalStateException("DELIVERABLE_FROZEN_PLAN_UNAVAILABLE");
        var definitions = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot()).getDeliverables().stream()
                .filter(item -> Objects.equals(item.getCode(), code)).toList();
        if (definitions.size() != 1 || definitions.getFirst().getConfiguration() == null)
            throw new IllegalStateException("DELIVERABLE_FROZEN_CONFIGURATION_UNAVAILABLE");
        var definition = definitions.getFirst();
        return new Context(project.getId(), plan.getId(), project.getLifecycleStatus(), project.getManagerId(),
                definition.getStageCode(), definition.getTaskCode(), definition.getConfiguration().deepCopy());
    }

    private ProjectMasterDO require(ProjectMasterDO project) {
        if (project == null || !Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || Boolean.TRUE.equals(project.getDeleted())) throw new IllegalArgumentException("DELIVERABLE_PROJECT_UNAVAILABLE");
        return project;
    }
}
