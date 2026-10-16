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

    @Override public Set<String> documentTargets(DocumentTargetsQuery query) {
        Long projectId = query.projectId();
        String sourceCode = query.sourceCode();
        var project = require(projects.selectById(projectId));
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || project.getActivePlanVersionId() == null) return Set.of();
        var plan = plans.selectById(project.getActivePlanVersionId());
        if (plan == null || !Objects.equals(plan.getProjectId(), projectId)
                || !Objects.equals(plan.getTenantId(), project.getTenantId()) || !"EFFECTIVE".equals(plan.getStatus()))
            throw new IllegalStateException("DELIVERABLE_FROZEN_PLAN_UNAVAILABLE");
        Set<String> codes = new HashSet<>();
        var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
        cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO origin = null;
        if (query.originTaskId() != null) {
            var matches = graph.selectTasks(new ProjectRuntimeGraphQuery(project.getTenantId(), projectId)).stream()
                    .filter(task -> Objects.equals(task.getId(), query.originTaskId())).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("DOCUMENT_ORIGIN_TASK_UNAVAILABLE");
            origin = matches.getFirst();
            var originTask = origin;
            boolean valid = snapshot.getTasks().stream().anyMatch(task -> Objects.equals(task.getCode(), originTask.getCode())
                    && Objects.equals(task.getStageCode(), originTask.getStageCode()) && task.getBinding() != null
                    && "PAGE".equals(task.getBinding().getType()) && task.getBinding().getParameters() != null
                    && Set.of("/pms/engineering/execution/imp-configuration", "/pms/engineering/execution/imp-joint-test")
                        .contains(task.getBinding().getParameters().path("routePath").asText()));
            if (!"AST.DEVICE_CONFIGURATION_LOG".equals(sourceCode) || !valid)
                throw new IllegalArgumentException("DOCUMENT_ORIGIN_TASK_BINDING_MISMATCH");
        }
        for (var definition : snapshot.getDeliverables()) {
            if (definition.getConfiguration() == null) continue;
            if (origin != null && (!Objects.equals(origin.getCode(), definition.getTaskCode())
                    || !Objects.equals(origin.getStageCode(), definition.getStageCode()))) continue;
            if ("PLN.STAGE_PLAN.CONSTRUCTION_PLAN_APPROVED".equals(sourceCode)
                    && definition.getConfiguration().path("allowedSources").isArray()
                    && java.util.stream.StreamSupport.stream(definition.getConfiguration().path("allowedSources").spliterator(), false)
                        .anyMatch(source -> "BUSINESS_RESULT".equals(source.asText()))
                    && snapshot.getTasks().stream().anyMatch(task -> Objects.equals(task.getCode(), definition.getTaskCode())
                        && Objects.equals(task.getStageCode(), definition.getStageCode()) && task.getBinding() != null
                        && "PLN".equals(task.getBinding().getTargetContextCode())
                        && "STAGE_PLAN".equals(task.getBinding().getTargetObjectType())
                        && referencesDeliverable(task.getCompletionRule(), definition.getCode()))) codes.add(definition.getCode());
            if ("IMP.INSTALLATION.INSTALLATION_COMPLETED".equals(sourceCode)
                    && definition.getConfiguration().path("allowedSources").isArray()
                    && java.util.stream.StreamSupport.stream(definition.getConfiguration().path("allowedSources").spliterator(), false)
                        .anyMatch(source -> "BUSINESS_RESULT".equals(source.asText()))
                    && snapshot.getTasks().stream().anyMatch(task -> Objects.equals(task.getCode(), definition.getTaskCode())
                        && Objects.equals(task.getStageCode(), definition.getStageCode()) && task.getBinding() != null
                        && "PAGE".equals(task.getBinding().getType()) && task.getBinding().getParameters() != null
                        && "/pms/engineering/execution/imp-installation".equals(task.getBinding().getParameters().path("routePath").asText())
                        && referencesDeliverable(task.getCompletionRule(), definition.getCode()))) codes.add(definition.getCode());
            if ("AST.DEVICE_CONFIGURATION_LOG".equals(sourceCode)
                    && definition.getConfiguration().path("allowedSources").isArray()
                    && java.util.stream.StreamSupport.stream(definition.getConfiguration().path("allowedSources").spliterator(), false)
                        .anyMatch(source -> "UPLOAD".equals(source.asText()))
                    && snapshot.getTasks().stream().anyMatch(task -> Objects.equals(task.getCode(), definition.getTaskCode())
                        && Objects.equals(task.getStageCode(), definition.getStageCode()) && task.getBinding() != null
                        && "PAGE".equals(task.getBinding().getType()) && task.getBinding().getParameters() != null
                        && ("/pms/engineering/execution/imp-configuration".equals(task.getBinding().getParameters().path("routePath").asText())
                            || query.originTaskId() != null && "/pms/engineering/execution/imp-joint-test".equals(task.getBinding().getParameters().path("routePath").asText()))
                        && referencesDeliverable(task.getCompletionRule(), definition.getCode()))) codes.add(definition.getCode());
            for (var source : definition.getConfiguration().path("automaticSources")) {
                if (sourceCode.equals(source.asText())) codes.add(definition.getCode());
            }
        }
        return Set.copyOf(codes);
    }

    private boolean referencesDeliverable(tools.jackson.databind.JsonNode rule, String code) {
        if (rule == null) return false;
        if ("DELIVERABLE".equals(rule.path("predicate").asText()) && code.equals(rule.path("parameters").path("refCode").asText())) return true;
        for (var child : rule.path("rules")) if (referencesDeliverable(child, code)) return true;
        return false;
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
