package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Current gate status is a projection; frozen plan rules and Owner facts decide each use. */
@Service
@RequiredArgsConstructor
public class ProjectGateRuleService {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateInstanceMapper gates;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuntimeRuleEvaluator rules;
    private final OperationAuditApi audit;

    public record Result(RuleEvaluation evaluation, String gateSnapshot) { }
    private record Reference(String type, String code, String revision) { }

    // External calls join task completion or create one gate transaction in the coordinator.
    // https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
    @Transactional(rollbackFor = Exception.class)
    public Result evaluate(Long projectId, String gateCode, Long actorId, String correlationId) {
        return evaluate(projectId, gateCode, actorId, correlationId, true);
    }

    /** Same frozen rules and live facts, without updating the projection or recording a business operation. */
    @Transactional(rollbackFor = Exception.class)
    public RuleEvaluation inspect(Long projectId, String gateCode) {
        return evaluate(projectId, gateCode, null, null, false).evaluation();
    }

    private Result evaluate(Long projectId, String gateCode, Long actorId, String correlationId, boolean persist) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        String ref = "project:" + projectId + ":gate:" + gateCode;
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || !"ACTIVE".equals(project.getLifecycleStatus())) return unknown(ref, "PROJECT_NOT_ACTIVE");
        var query = new ProjectRuntimeGraphQuery(tenantId, projectId);
        var rows = graph.selectGatesForUpdate(query).stream().filter(g -> Objects.equals(gateCode, g.getGateCode())).toList();
        if (rows.size() != 1) return unknown(ref, "GATE_INSTANCE_UNAVAILABLE");
        var gate = rows.getFirst();
        if (!Objects.equals(gate.getTenantId(), tenantId) || !Objects.equals(gate.getProjectId(), projectId)
                || gate.getId() == null || gate.getVersion() == null) return unknown(ref, "GATE_INSTANCE_UNAVAILABLE");
        ref = "plan:" + project.getActivePlanVersionId() + ":gate:" + gate.getId() + ":version:" + gate.getVersion();
        RuleEvaluation result;
        try {
            var plan = plans.selectEffective(new ProjectPlanScopeQuery(tenantId, projectId));
            if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId()))
                throw new IllegalStateException("PROJECT_PLAN_VERSION_UNAVAILABLE");
            var snapshot = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
            var definitions = snapshot.getGates().stream().filter(g -> gateCode.equals(g.getCode())).toList();
            if (definitions.size() != 1) throw new IllegalStateException("GATE_PLAN_DEFINITION_UNAVAILABLE");
            var definition = definitions.getFirst();
            var program = snapshot.getRulePrograms().get(definition.getConditionRuleKey());
            if (program == null || !Objects.equals(gate.getStageCode(), definition.getStageCode())
                    || !Objects.equals(gate.getGateType(), definition.getGateType()))
                throw new IllegalStateException("GATE_PLAN_DEFINITION_UNAVAILABLE");
            var refs = references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(tenantId, List.of(gate.getId())));
            var expected = definition.getReferences().stream().map(r -> new Reference(r.getRefType(), r.getRefCode(), r.getRefVersion())).toList();
            var actual = refs.stream().map(r -> new Reference(r.getRefType(), r.getRefCode(), r.getRefVersion())).toList();
            if (expected.isEmpty() || expected.size() != actual.size() || !new HashSet<>(expected).equals(new HashSet<>(actual))
                    || refs.stream().anyMatch(r -> !Objects.equals(r.getTenantId(), tenantId) || !Objects.equals(r.getGateId(), gate.getId())
                    || r.getId() == null || r.getVersion() == null))
                throw new IllegalStateException("GATE_REFERENCE_VERSION_UNAVAILABLE");
            var stages = graph.selectStagesForUpdate(query).stream().filter(s -> gate.getStageCode().equals(s.getCode())).toList();
            if (stages.size() != 1) throw new IllegalStateException("GATE_STAGE_UNAVAILABLE");
            result = rules.evaluate(ref + ":rule:" + definition.getConditionRuleKey(), program,
                    new ProjectRuntimeRuleEvaluator.Facts(project, stages.getFirst(), graph.selectTasksForUpdate(query),
                            List.of(gate), refs, false));
        } catch (RuntimeException unavailable) {
            // Do not persist exception messages, business values, or stale success as a fallback.
            result = unknown(ref, "GATE_RULE_OR_FACT_UNAVAILABLE").evaluation();
        }
        if (!persist) return new Result(result, null);
        String status = result.matched() ? "PASSED" : "PENDING";
        int version = gate.getVersion();
        if (!status.equals(gate.getStatus())) {
            if (gates.updateStatusIfMatch(new ProjectGateStatusUpdate(tenantId, gate.getId(), version,
                    gate.getStatus(), status, actorId == null ? "project-rules" : actorId.toString())) != 1)
                throw new IllegalStateException("GATE_EVALUATION_VERSION_CONFLICT");
            audit.record(tenantId, actorId == null ? 0L : actorId, correlationId, "PROJECT_GATE_RULE_EVALUATED", "ProjectGate", gate.getId().toString(), "SUCCESS",
                    Map.of("projectId", projectId, "planVersionId", project.getActivePlanVersionId(), "gateId", gate.getId(),
                            "previousStatus", gate.getStatus(), "status", status, "previousVersion", version, "version", version + 1,
                            "evaluation", result));
            version++;
        }
        return new Result(result, gateCode + ":" + status + ":" + version);
    }

    private static Result unknown(String ref, String reason) {
        return new Result(new RuleEvaluation(ref, RuleEvaluation.Outcome.UNKNOWN, reason, List.of(), List.of(), List.of()), null);
    }
}
