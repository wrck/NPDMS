package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Rules belong to the effective project plan; the immutable binding contract keeps business link identity. */
@Service
@RequiredArgsConstructor
public class ProjectTaskPlanCompletionService {
    @jakarta.annotation.Resource
    private ProjectTaskApprovalService approvals;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuntimeRuleEvaluator facts;
    private final ProjectRuleEvaluationService rules;
    private final ProjectRuleCompiler compiler;
    private final ProjectTaskBusinessService business;
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executionApi;
    private final cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService gateRules;

    public record Result(RuleEvaluation completion, RuleEvaluation exit, Map<String,Object> evidence, RuleEvaluation gate) {
        public Result(RuleEvaluation completion, RuleEvaluation exit, Map<String,Object> evidence) {
            this(completion, exit, evidence, null);
        }
        public boolean matched() { return completion.matched() && exit.matched() && (gate == null || gate.matched()); }
        public List<String> unmet() {
            List<String> codes = new ArrayList<>();
            if (!completion.matched()) codes.add(completion.reasonCode() == null ? "COMPLETION_NOT_MATCHED" : completion.reasonCode());
            if (!exit.matched()) codes.add(exit.reasonCode() == null ? "EXIT_NOT_MATCHED" : exit.reasonCode());
            if (gate != null && !gate.matched()) codes.add(gate.reasonCode() == null ? "GATE_NOT_PASSED" : gate.reasonCode());
            return List.copyOf(codes);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result evaluate(TaskActionCommand command, ProjectMasterDO project, ProjectTaskInstanceDO task,
                           ProjectTaskExecutionContractDO binding, TaskWorkbenchActor actor) {
        String reference = "plan:" + project.getActivePlanVersionId() + ":task:" + task.getId();
        if (!Objects.equals(binding.getId(),command.executionContractId())
                || !Objects.equals(binding.getContractVersion(),command.contractVersion()))
            return unknown(reference,"EXECUTION_CONTRACT_VERSION_MISMATCH");
        return evaluateInternal(project, task, binding, actor.tenantId(), () -> {
            String expected = command.expectedBusinessFactVersion();
            if (expected == null || !expected.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("BUSINESS_FACT_VERSION_REQUIRED");
            var result = business.lockAndRevalidateLinkedFacts(task.getId(), actor.tenantId(), actor.actorId(), actor.correlationId(), expected);
            if (result == null || !expected.equals(result.factVersion()))
                throw new IllegalArgumentException("BUSINESS_FACT_VERSION_MISMATCH");
            return new TaskBusinessCompletionFacts(result, true);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Result evaluateAutomatically(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                        ProjectTaskExecutionContractDO binding) {
        return evaluateInternal(project, task, binding, project.getTenantId(), () -> {
            var context = executionApi.inspect(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery(
                    project.getId(), task.getId(), binding.getId()));
            if (!context.writable()) throw new IllegalArgumentException("TASK_EXECUTION_UNAVAILABLE");
            return business.lockCompletionFacts(project.getTenantId(), context, binding);
        });
    }

    private Result evaluateInternal(ProjectMasterDO project, ProjectTaskInstanceDO task,
                                    ProjectTaskExecutionContractDO binding, Long tenantId,
                                    java.util.function.Supplier<TaskBusinessCompletionFacts> ownerReader) {
        String reference = "plan:" + project.getActivePlanVersionId() + ":task:" + task.getId();
        var scope = new ProjectPlanScopeQuery(tenantId,project.getId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(),project.getActivePlanVersionId()))
            return unknown(reference,"PROJECT_PLAN_VERSION_UNAVAILABLE");
        var rounds = executions.selectCurrentForUpdate(scope).stream().filter(round -> "TASK".equals(round.getNodeKind())
                && task.getId().equals(round.getNodeInstanceId())).toList();
        if (rounds.size()!=1) return unknown(reference,"TASK_EXECUTION_ROUND_UNAVAILABLE");
        var round = rounds.getFirst();
        reference += ":execution:" + round.getId();
        if (!plan.getId().equals(round.getPlanVersionId()) || !Objects.equals(binding.getId(),round.getContractId())
                || !Objects.equals(binding.getSourceNodeKey(),round.getNodeKey()) || !"ACTIVE".equals(round.getStatus()))
            return unknown(reference,"TASK_EXECUTION_ROUND_STALE");
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(),TemplateExecutionSnapshot.class);
        var definitions = snapshot.getTasks().stream().filter(node -> round.getNodeKey().equals(node.getNodeKey())
                && task.getTaskCode().equals(node.getCode()) && task.getStageCode().equals(node.getStageCode())).toList();
        if (definitions.size()!=1) return unknown(reference,"TASK_PLAN_DEFINITION_UNAVAILABLE");
        var node = definitions.getFirst();
        var completion = snapshot.getRulePrograms().get(node.getCompletionRuleKey());
        var exit = node.getExitRuleKey()==null || node.getExitRuleKey().isBlank() ? compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"))
                : snapshot.getRulePrograms().get(node.getExitRuleKey());
        if (completion==null || exit==null) return unknown(reference,"FROZEN_RULE_PROGRAM_REQUIRED");
        boolean nativeWork = "TASK_NATIVE".equals(binding.getWorkBindingTypeCode());
        boolean approvalWork = "APPROVAL".equals(binding.getWorkBindingTypeCode());
        if (nativeWork && round.getSubmittedAt()==null) return unknown(reference,"CURRENT_ROUND_SUBMISSION_REQUIRED");
        List<TaskBusinessLinkFact> links = List.of();
        Map<String,Object> evidence = new LinkedHashMap<>();
        if (approvalWork) {
            var approval = approvals.inspect(tenantId, project.getId(), task.getId(), binding, round.getId(), round.getStartedAt());
            if (approval.outcome() == cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.UNKNOWN)
                return unknown(reference, approval.reason());
            if (approval.outcome() != cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.SATISFIED) {
                var waiting = new RuleEvaluation(reference, RuleEvaluation.Outcome.NOT_MATCHED,
                        approval.reason(), List.of(), List.of(), List.of());
                return new Result(waiting, waiting, Map.of("completion", waiting, "exit", waiting));
            }
            evidence.put("approval", approval);
        } else if (!nativeWork) {
            var ownerResult = ownerReader.get();
            var ownerFacts = ownerResult == null ? null : ownerResult.facts();
            if (ownerFacts==null || ownerFacts.factVersion() == null || ownerFacts.links().isEmpty())
                return unknown(reference,"BUSINESS_FACT_VERSION_MISMATCH");
            if (!ownerResult.hasCompletedHandling()) {
                var waiting = new RuleEvaluation(reference, RuleEvaluation.Outcome.NOT_MATCHED,
                        "BUSINESS_HANDLING_PENDING", List.of(), List.of(), List.of());
                return new Result(waiting, waiting, Map.of("completion", waiting, "exit", waiting));
            }
            links = ownerFacts.links();
            evidence.put("aggregateFactVersion",ownerFacts.factVersion());
            evidence.put("ownerContext",binding.getTargetContextCode());
            evidence.put("objectType",binding.getTargetObjectType());
            evidence.put("links",links.stream().map(link -> Map.of("linkId",link.id(),"objectId",link.objectId(),"factVersion",link.factVersion())).toList());
            evidence.put("businessFacts", ProjectBusinessFactSourceService.freeze(round, links));
        }
        var query = new ProjectRuntimeGraphQuery(tenantId,project.getId());
        var parents = graph.selectStagesForUpdate(query).stream().filter(stage -> task.getStageCode().equals(stage.getStageCode())).toList();
        if (parents.size()!=1 || !"ACTIVE".equals(parents.getFirst().getStatus())) return unknown(reference,"TASK_STAGE_NOT_ACTIVE");
        var gates = graph.selectGatesForUpdate(query);
        var refs = gates.isEmpty() ? List.<ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(tenantId,gates.stream().map(ProjectGateInstanceDO::getId).toList()));
        var context = new ProjectRuntimeRuleEvaluator.Facts(project,parents.getFirst(),graph.selectTasksForUpdate(query),gates,refs,false);
        var ownerLinks = links;
        List<Map<String,Object>> criteria = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        RuleFact.Resolver resolver = leaf -> switch (leaf.predicate()) {
            case "WAIT_ELAPSED" -> facts.resolveRelativeTime(leaf, context, round.getId());
            case "TASK_NATIVE_STATUS" -> nativeWork ? RuleFact.known(round.getSubmittedAt()!=null) : RuleFact.unknown("NATIVE_COMPLETION_NOT_APPLICABLE");
            case "BUSINESS_FACT" -> leaf.parameters().has("sourceNodeKey") ? facts.resolveFact(leaf, context)
                    : ownerLinks.isEmpty() ? RuleFact.unknown("BUSINESS_LINK_GROUP_EMPTY")
                    : TaskBusinessCompletionEvaluator.businessFact(leaf,ownerLinks,criteria,invalid);
            default -> facts.resolveFact(leaf,context);
        };
        var completionResult = rules.evaluate(reference+":completion",completion,resolver);
        var exitResult = rules.evaluate(reference+":exit",exit,resolver);
        evidence.put("planVersionId",plan.getId()); evidence.put("executionId",round.getId());
        evidence.put("completion",completionResult); evidence.put("exit",exitResult);
        evidence.put("criteria",List.copyOf(criteria));
        RuleEvaluation gateResult = null;
        if (node.getGateRef() != null && !node.getGateRef().isBlank()) {
            var evaluated = gateRules.evaluate(project.getId(), node.getGateRef(), null, reference);
            gateResult = evaluated.evaluation();
            evidence.put("gate", gateResult);
            if (evaluated.gateSnapshot() != null) evidence.put("gateSnapshot", evaluated.gateSnapshot());
        }
        return new Result(completionResult,exitResult,Collections.unmodifiableMap(evidence),gateResult);
    }
    private Result unknown(String reference,String reason) {
        var result = new RuleEvaluation(reference,RuleEvaluation.Outcome.UNKNOWN,reason,List.of(),List.of(),List.of());
        return new Result(result,result,Map.of("completion",result,"exit",result));
    }
}
