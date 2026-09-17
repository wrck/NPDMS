package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.TaskBusinessLinksQuery;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus;
import cn.iocoder.yudao.module.pms.project.service.projectclosureguard.ProjectClosureGuardService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import cn.iocoder.yudao.module.pms.project.service.stagegate.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


/** Checks are evidence only: no task completion, stage advance, legacy final acceptance or project exit. */
@Service @RequiredArgsConstructor
public class NormalClosureCheckService implements cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureCheckApi {
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper closureProjects;
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper projects;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectTaskBusinessLinkMapper links;
    private final ProjectRuntimeGraphResolver graphs;
    private final ProjectStageGateProviderRegistry gates;
    private final ProjectTaskBusinessService business;
    private final TaskBusinessCompletionEvaluator evaluator;
    private final ProjectClosureGuardService descendantGuard;
    private final NormalClosureAccess access;

    public record Evaluation(String policyJson, List<Check> checks, String evidence,
                             String sourceVector, String sourceDigest,
                             ProjectRuntimeGraphResolver.Resolution graph) {
        public boolean passed() { return policyJson != null && !checks.isEmpty() && checks.stream().allMatch(Check::passed); }
    }

    /** 与 ACC 侧 Views.Check 同构的检查证据行（跨模块经 ClosureCheck DTO 传递）。 */
    public record Check(String code, boolean passed, String reason, Long subjectId) {}

    /** Caller has already locked root/project and revalidated current PM + MANAGE scope. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Evaluation evaluateLocked(ProjectMasterDO project, long treeVersion, Long actorId, String correlationId) {
        String policyJson = project.getClosurePolicySnapshot();
        List<Check> checks = new ArrayList<>();
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("projectId", project.getId()); source.put("projectVersion", project.getVersion());
        source.put("treeVersion", treeVersion); source.put("policy", policyJson);
        source.put("taskTreeVersion", project.getTaskTreeVersion()); source.put("taskProgressVersion", project.getTaskProgressVersion());
        add(checks, "PROJECT_ACTIVE", "ACTIVE".equals(project.getLifecycleStatus()), project.getId());
        if (!"ACTIVE".equals(project.getLifecycleStatus())) return result(policyJson, checks, source, null);

        var guard = descendantGuard.evaluate(project.getId(), treeVersion,
                new ProjectClosureGuardService.Actor(project.getTenantId(), actorId, correlationId));
        add(checks, "DESCENDANTS_CLOSED", guard.allowed(), project.getId());
        source.put("descendantGuard", Map.of("allowed", guard.allowed(), "treeVersion", guard.treeVersion(),
                "blockers", guard.blockers().stream().sorted(Comparator.comparing(b -> b.projectId())).toList(),
                "pendingProgressProjects", guard.pendingProgressProjects().stream().sorted().toList()));
        var query = new cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper.ProjectQuery(project.getTenantId(), project.getId());
        List<ProjectTaskInstanceDO> tasks = closureProjects.selectTasksForUpdate(query);
        Map<Long, ProjectTaskExecutionContractDO> current = new LinkedHashMap<>();
        // Lock all local tasks/contracts/links before any business Owner. Never lock closure before project.
        for (var task : tasks) {
            var contract = contracts.selectCurrentByTaskIdForUpdate(
                    new CurrentTaskExecutionContractLockQuery(project.getTenantId(), task.getId()));
            if (contract != null) current.put(task.getId(), contract);
            links.selectActiveForUpdate(new TaskBusinessLinksQuery(project.getTenantId(), project.getId(), task.getId()));
        }
        var graph = graphs.resolve(project);
        add(checks, "TERMINAL_STAGE", graph.terminal(), graph.current().getId());
        add(checks, "STAGE_COMPLETION", graph.completion() == ConditionStatus.SATISFIED, graph.current().getId());
        source.put("stage", List.of(graph.current().getId(), graph.current().getVersion(), graph.current().getGraphVersion(),
                graph.current().getDefinitionRevisionId(), graph.current().getCode()));
        List<Object> gateEvidence = new ArrayList<>();
        for (var gate : graph.gates()) {
            var references = graph.references().stream().filter(r -> Objects.equals(r.getGateId(), gate.getId())).toList();
            add(checks, "EXIT_GATE_REFERENCES", !references.isEmpty(), gate.getId());
            for (var ref : references) {
                String key = ProjectStageReadinessService.providerKey(ref.getRefType());
                if (key == null || !gates.hasProvider(key)) {
                    add(checks, "EXIT_GATE_OWNER_UNAVAILABLE", false, ref.getId());
                    continue;
                }
                var fact = gates.lockAndRevalidate(key, new ProjectStageGateFactQuery(project.getTenantId(), project.getId(),
                        gate.getStageCode(), gate.getId(), gate.getGateCode(), gate.getVersion(), ref.getId(), ref.getVersion(),
                        ref.getRefType(), ref.getRefCode(), ref.getRefVersion(), null));
                add(checks, "EXIT_GATE_SATISFIED", fact.outcome() == ProjectStageGateOutcome.SATISFIED, ref.getId());
                gateEvidence.add(List.of(gate.getId(), gate.getVersion(), ref.getId(), ref.getVersion(), fact));
            }
        }
        source.put("gates", gateEvidence);
        add(checks, "ALL_TASKS_DONE", tasks.stream().allMatch(t -> "DONE".equals(t.getStatus())), project.getId());
        List<Object> taskEvidence = new ArrayList<>();
        for (var task : tasks) {
            Map<String, Object> taskSource = new LinkedHashMap<>();
            taskSource.put("taskId", task.getId()); taskSource.put("version", task.getVersion());
            taskSource.put("status", task.getStatus());
            taskEvidence.add(taskSource);
            var contract = current.get(task.getId());
            boolean validContract = contract != null && Objects.equals(contract.getTenantId(), project.getTenantId())
                    && Objects.equals(contract.getProjectTaskId(), task.getId()) && contract.getEffectiveTo() == null
                    && contract.getContractVersion() != null && contract.getContractVersion() > 0;
            add(checks, "TASK_CONTRACT_CURRENT", validContract, task.getId());
            if (!validContract) continue;
            taskSource.put("contractId", contract.getId()); taskSource.put("contractVersion", contract.getContractVersion());
            taskSource.put("completionRule", contract.getCompletionRuleSnapshot());
            taskSource.put("definitionSnapshot", contract.getDefinitionSnapshot());
            if (contract.getWorkBindingTypeCode() != null && cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessBindingHostProvider.TYPES.contains(contract.getWorkBindingTypeCode())) {
                var before = business.inspectLinkedFactsSnapshot(task.getId(), project.getTenantId(), actorId, correlationId);
                var locked = business.lockAndRevalidateLinkedFacts(task.getId(), project.getTenantId(), actorId,
                        correlationId, before.factVersion());
                var value = evaluator.evaluate(contract, locked.factVersion(), locked.links());
                add(checks, "TASK_BUSINESS_FACTS", value.satisfied(), task.getId());
                taskSource.put("businessEvidence", value.evidence());
            } else {
                // Native DONE is valid only for an explicitly frozen native completion contract.
                boolean nativeContract = "TASK_NATIVE".equals(contract.getWorkBindingTypeCode())
                        && "TASK_NATIVE_STATUS".equals(contract.getCompletionRuleTypeCode());
                add(checks, "TASK_NATIVE_CONTRACT", nativeContract, task.getId());
            }
        }
        source.put("tasks", taskEvidence);
        return result(policyJson, checks, source, graph);
    }

    private static void add(List<Check> checks, String code, boolean passed, Long id) {
        checks.add(new Check(code, passed, passed ? null : code, id));
    }
    private static Evaluation result(String policyJson, List<Check> checks, Map<String, Object> source,
                                     ProjectRuntimeGraphResolver.Resolution graph) {
        String vector = canonical(JsonUtils.parseObject(JsonUtils.toJsonString(source), tools.jackson.databind.JsonNode.class));
        return new Evaluation(policyJson, List.copyOf(checks), JsonUtils.toJsonString(checks), vector, digest(vector), graph);
    }
    static String canonical(tools.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>(node.propertyNames()); Collections.sort(keys);
            return "{" + keys.stream().map(key -> JsonUtils.toJsonString(key) + ":" + canonical(node.get(key)))
                    .collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (node.isArray()) {
            List<String> items = new ArrayList<>(); node.forEach(item -> items.add(canonical(item)));
            return "[" + String.join(",", items) + "]";
        }
        return node.toString();
    }
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    // ===== ProjectClosureCheckApi 实现（跨模块契约；ACC 正常闭环消费）=====

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ClosureEvaluation evaluateLocked(ClosureCheckCommand command) {
        var project = access.lockProject(command.projectId(), command.tenantId());
        if (!java.util.Objects.equals(command.expectedProjectVersion(), project.getVersion()))
            throw closureFailure("CLOSURE_PROJECT_VERSION_CONFLICT");
        var evaluation = evaluateLocked(project, command.treeVersion(), command.actorId(), command.correlationId());
        var current = evaluation.graph() == null ? null : evaluation.graph().current();
        return new ClosureEvaluation(evaluation.passed(), evaluation.policyJson(),
                evaluation.checks().stream().map(c -> new ClosureCheck(c.code(), c.passed(), c.reason(), c.subjectId())).toList(),
                evaluation.evidence(), evaluation.sourceVector(), evaluation.sourceDigest(),
                current == null ? null : current.getId(), current == null ? null : current.getVersion(),
                evaluation.graph() != null && evaluation.graph().terminal());
    }

    @Override
    public GraphOverview inspectGraph(Long tenantId, Long projectId) {
        var project = projects.selectById(projectId);
        if (project == null || !java.util.Objects.equals(project.getTenantId(), tenantId))
            throw closureFailure("CLOSURE_PROJECT_NOT_FOUND");
        var graph = graphs.inspect(project);
        boolean done = closureProjects.selectTasks(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper.ProjectQuery(tenantId, projectId))
                .stream().allMatch(t -> "DONE".equals(t.getStatus()));
        return new GraphOverview(graph.terminal(), String.valueOf(graph.completion()), graph.current().getId(), done);
    }

    private static cn.iocoder.yudao.framework.common.exception.ServiceException closureFailure(String reason) {
        return new cn.iocoder.yudao.framework.common.exception.ServiceException(
                cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED.getCode(), reason);
    }
}
