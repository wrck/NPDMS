package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Short, transactional admission coordination. LiteFlow owns rule evaluation, not persistent state. */
@Service
@RequiredArgsConstructor
public class ProjectStageAdmissionService {
    @jakarta.annotation.Resource
    private ProjectRuleTimerScheduler timers;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectStageInstanceMapper stages;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuntimeRuleEvaluator evaluator;
    private final ProjectRuleCompiler compiler;
    private final OperationAuditApi audit;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper executions;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper plans;

    public record StageAdmission(Long stageId, String nodeKey, RuleEvaluation.Outcome outcome,
                                 String reasonCode, boolean activated) { }

    /** Internal command invoked after an authorized project/business change; not a user rule override. */
    @Transactional(rollbackFor = Exception.class)
    public List<StageAdmission> activateEligible(Long projectId, Long actorId, String correlationId) {
        return activate(projectId, actorId, correlationId, null);
    }

    /** A runtime reevaluation or version/round-checked timer admits only its target in this transaction. */
    @Transactional(rollbackFor = Exception.class)
    public List<StageAdmission> activateStage(Long projectId, Long actorId, String correlationId, Long stageId) {
        if (stageId == null) throw new IllegalArgumentException("ADMISSION_STAGE_REQUIRED");
        return activate(projectId, actorId, correlationId, stageId);
    }

    private List<StageAdmission> activate(Long projectId, Long actorId, String correlationId, Long stageId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !Objects.equals(project.getTenantId(), tenantId))
            throw new IllegalArgumentException("PROJECT_NOT_FOUND");
        if (!"ACTIVE".equals(project.getLifecycleStatus())) return List.of();
        if (project.getActivePlanVersionId() == null)
            return List.of(new StageAdmission(null, null, RuleEvaluation.Outcome.UNKNOWN, "PROJECT_PLAN_NOT_INITIALIZED", false));
        var plan = plans.selectEffective(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery(tenantId, projectId));
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId()))
            return List.of(new StageAdmission(null, null, RuleEvaluation.Outcome.UNKNOWN, "PROJECT_PLAN_VERSION_UNAVAILABLE", false));
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var query = new ProjectRuntimeGraphQuery(tenantId, projectId);
        var nodes = graph.selectStagesForUpdate(query);
        var contracts = graph.selectContracts(query);
        var tasks = graph.selectTasksForUpdate(query);
        var gates = graph.selectGatesForUpdate(query);
        var gateReferences = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(tenantId,
                        gates.stream().map(gate -> gate.getId()).toList()));
        List<StageAdmission> results = new ArrayList<>();
        for (var stage : nodes) {
            if (stageId != null && !stageId.equals(stage.getId())) continue;
            if (!"PENDING".equals(stage.getStatus())) continue;
            var matching = contracts.stream().filter(contract -> Objects.equals(contract.getStageId(), stage.getId())).toList();
            if (matching.size() != 1) {
                results.add(new StageAdmission(stage.getId(), null, RuleEvaluation.Outcome.UNKNOWN, "STAGE_CONTRACT_UNAVAILABLE", false));
                continue;
            }
            var contract = matching.getFirst();
            String version = "plan:" + plan.getId() + ":stage:" + stage.getId() + ":admission";
            RuleEvaluation evaluation;
            try {
                RuleProgram program = admission(stage, contract, tenantId, projectId, snapshot);
                evaluation = evaluator.evaluate(version, program, new ProjectRuntimeRuleEvaluator.Facts(
                        project, stage, tasks, gates, gateReferences, false));
            } catch (RuntimeException unavailable) {
                results.add(new StageAdmission(stage.getId(), contract.getSourceNodeKey(), RuleEvaluation.Outcome.UNKNOWN,
                        "STAGE_ADMISSION_DEFINITION_UNAVAILABLE", false));
                continue;
            }
            // A failed rule blocks only this node. Persistence/audit failures still roll back the transaction.
            if (evaluation.matched()) {
                if (stages.updateStatusIfMatch(new ProjectStageStatusUpdate(tenantId, projectId, stage.getId(),
                        stage.getVersion(), "PENDING", "ACTIVE", actorId == null ? "project-rules" : actorId.toString(), occurredAt)) != 1)
                var occurredAt = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
                    throw new IllegalStateException("STAGE_ACTIVATION_VERSION_CONFLICT");
                if (executions.activateIfPending(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.Activation(
                        tenantId, projectId, project.getActivePlanVersionId(), stage.getId(), "STAGE", occurredAt)) != 1)
                    throw new IllegalStateException("STAGE_EXECUTION_ROUND_CONFLICT");
                timers.scheduleFromNode(projectId, "STAGE", stage.getId());
                audit.record(tenantId, actorId == null ? 0L : actorId, correlationId, "PROJECT_STAGE_ACTIVATED", "PROJECT_STAGE", stage.getId().toString(),
                        "SUCCESS", Map.of("projectId", projectId, "stageId", stage.getId(), "nodeKey", contract.getSourceNodeKey(),
                                "graphVersion", contract.getGraphVersion(), "contractId", contract.getId(), "ruleVersionRef", version,
                                "conditions", evaluation.conditions(), "components", evaluation.steps()));
            }
            results.add(new StageAdmission(stage.getId(), contract.getSourceNodeKey(), evaluation.outcome(),
                    evaluation.reasonCode(), evaluation.matched()));
        }
        return List.copyOf(results);
    }

    private RuleProgram admission(ProjectStageInstanceDO stage, ProjectStageExecutionContractDO contract,
                                  Long tenantId, Long projectId, TemplateExecutionSnapshot snapshot) {
        if (!Objects.equals(stage.getTenantId(), tenantId) || !Objects.equals(stage.getProjectId(), projectId)
                || !Objects.equals(contract.getTenantId(), tenantId) || !Objects.equals(contract.getProjectId(), projectId)
                || stage.getGraphVersion() == null || !Objects.equals(stage.getGraphVersion(), contract.getGraphVersion())
                || contract.getEffectiveTo() != null || contract.getSourceNodeKey() == null)
            throw new IllegalArgumentException("STAGE_CONTRACT_STALE");
        var definitions = snapshot.getStages().stream().filter(node -> contract.getSourceNodeKey().equals(node.getNodeKey())
                && stage.getCode().equals(node.getCode())).toList();
        if (definitions.size() != 1) throw new IllegalArgumentException("STAGE_DEFINITION_UNAVAILABLE");
        String key = definitions.getFirst().getAdmissionRuleKey();
        if (key == null || key.isBlank())
            return compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));
        RuleProgram program = snapshot.getRulePrograms().get(key);
        if (program == null) throw new IllegalArgumentException("ADMISSION_PROGRAM_NOT_FROZEN");
        return program;
    }

    /** Called under the project's command lock before starting template-managed task work. */
    @Transactional(rollbackFor = Exception.class)
    public boolean taskMayStart(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project,
                                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO task,
                                cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO taskContract) {
        var result = taskAdmissionFact(project, task, taskContract);
        return result.available() && Boolean.TRUE.equals(result.value());
    }

    @Transactional(rollbackFor = Exception.class)
    public RuleFact taskAdmissionFact(
            cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project,
            cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO task,
            cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO taskContract) {
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || !Objects.equals(project.getId(), task.getProjectId())
                || !Objects.equals(project.getTenantId(), TenantContextHolder.getRequiredTenantId())) return admissionUnavailable();
        var query = new ProjectRuntimeGraphQuery(project.getTenantId(), project.getId());
        var parents = graph.selectStagesForUpdate(query).stream().filter(stage -> Objects.equals(stage.getCode(), task.getStageCode())).toList();
        if (parents.size() != 1) return admissionUnavailable();
        if (!"ACTIVE".equals(parents.getFirst().getStatus())) return RuleFact.known(false);
        var parent = parents.getFirst();
        var matching = graph.selectContracts(query).stream().filter(contract -> Objects.equals(contract.getStageId(), parent.getId())).toList();
        if (matching.size() != 1 || taskContract.getSourceNodeKey() == null) return admissionUnavailable();
        var contract = matching.getFirst();
        try {
            if (!Objects.equals(contract.getGraphVersion(), parent.getGraphVersion()) || contract.getEffectiveTo() != null) return admissionUnavailable();
            var plan = plans.selectEffective(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery(project.getTenantId(), project.getId()));
            if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId())) return admissionUnavailable();
            var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
            var definitions = snapshot.getTasks().stream().filter(node -> taskContract.getSourceNodeKey().equals(node.getNodeKey())
                    && task.getCode().equals(node.getCode()) && task.getStageCode().equals(node.getStageCode())).toList();
            if (definitions.size() != 1) return admissionUnavailable();
            String key = definitions.getFirst().getAdmissionRuleKey();
            if (key == null || key.isBlank()) return RuleFact.known(true);
            var program = snapshot.getRulePrograms().get(key);
            if (program == null) return admissionUnavailable();
            var gates = graph.selectGatesForUpdate(query);
            var gateRefs = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                    : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(project.getTenantId(),
                            gates.stream().map(gate -> gate.getId()).toList()));
            var result = evaluator.evaluate("plan:" + plan.getId() + ":task:" + task.getId() + ":admission",
                    program, new ProjectRuntimeRuleEvaluator.Facts(project, parent, graph.selectTasksForUpdate(query), gates, gateRefs, false));
            return result.outcome() == RuleEvaluation.Outcome.UNKNOWN ? admissionUnavailable()
                    : RuleFact.known(result.matched());
        } catch (RuntimeException unavailable) {
            return admissionUnavailable();
        }
    }

    private static RuleFact admissionUnavailable() {
        return RuleFact.unknown("TASK_ADMISSION_UNAVAILABLE");
    }
}
