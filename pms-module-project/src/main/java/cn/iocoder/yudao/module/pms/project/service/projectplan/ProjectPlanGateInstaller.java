package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcessQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TemplateInstantiator;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PLAN_VERSION_CONFLICT;

/** Installs current gate definitions; preserves retired references and audits invalidation of old results. */
@Service
@RequiredArgsConstructor
public class ProjectPlanGateInstaller {
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateInstanceMapper gates;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectPlanProjectionMapper projections;
    private final OperationAuditApi audit;
    private final ProjectStageGateProcessOwnerApi processes;
    public record Change(String nodeKey, String action, Long instanceId, Integer expectedVersion,
                         String fromCode, String toCode, boolean reevaluationRequired) { }
    public record Write(Change change, ProjectGateInstanceDO definition, String previousStatus,
                        List<ProjectGateReferenceInstanceDO> retired, List<ProjectGateReferenceInstanceDO> added) { }
    public record Plan(List<Change> changes, List<Write> writes, List<Issue> issues) { }
    private record Reference(String type, String code, String version) { }

    /** Caller holds the authorized project lock; reads do not evaluate or alter gate results. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Plan inspect(ProjectPlanScopeQuery scope, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after) {
        var rows = graph.selectGatesForUpdate(new ProjectRuntimeGraphQuery(scope.tenantId(), scope.projectId()));
        var refs = rows.isEmpty() ? List.<ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(scope.tenantId(), rows.stream().map(ProjectGateInstanceDO::getId).toList()));
        var plan = plan(scope, before, after, rows, refs);
        if (!plan.issues().isEmpty()) return plan;
        Map<String, Set<Long>> affected = new LinkedHashMap<>();
        for (var write : plan.writes()) {
            var source = rows.stream().filter(row -> Objects.equals(row.getId(), write.change().instanceId())).findFirst().orElse(null);
            if (source == null) continue;
            boolean changesOwner = write.definition() == null
                    || !Objects.equals(source.getStageCode(), write.definition().getStageCode())
                    || !Objects.equals(source.getGateType(), write.definition().getGateType());
            var protectedRefs = (changesOwner ? refs.stream().filter(ref -> Objects.equals(ref.getGateId(), source.getId()))
                    : write.retired().stream()).filter(ref -> "PROCESS".equals(ref.getRefType()) || "APPROVAL".equals(ref.getRefType()))
                    .map(ProjectGateReferenceInstanceDO::getId).collect(Collectors.toSet());
            if (!protectedRefs.isEmpty()) affected.put(write.change().nodeKey(), protectedRefs);
        }
        if (affected.isEmpty()) return plan;
        List<Issue> issues = new ArrayList<>();
        try {
            var running = processes.inspectRunning(new ProjectStageGateRunningProcessQuery(scope.tenantId(), scope.projectId()));
            if (running == null) throw new IllegalStateException("BPM_ACTIVITY_UNAVAILABLE");
            affected.forEach((nodeKey, ids) -> {
                if (running.stream().anyMatch(process -> ids.contains(process.gateReferenceId())))
                    issues.add(issue(nodeKey, "RUNNING_GATE_PROCESS_CHANGE_FORBIDDEN", "门禁流程仍在办理，须先完成或明确终止，不能删除引用或改变归属"));
            });
        } catch (RuntimeException unavailable) {
            affected.keySet().forEach(nodeKey -> issues.add(issue(nodeKey, "GATE_PROCESS_ACTIVITY_UNAVAILABLE", "无法确认门禁流程是否仍在办理，暂不能变更其执行引用")));
        }
        return new Plan(plan.changes(), plan.writes(), List.copyOf(issues));
    }

    Plan plan(ProjectPlanScopeQuery scope, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
              List<ProjectGateInstanceDO> current, List<ProjectGateReferenceInstanceDO> refs) {
        var oldNodes = before.getGates().stream().collect(Collectors.toMap(TemplateExecutionSnapshot.GateContract::getNodeKey, Function.identity()));
        var nextNodes = after.getGates().stream().collect(Collectors.toMap(TemplateExecutionSnapshot.GateContract::getNodeKey, Function.identity()));
        Map<String, ProjectGateInstanceDO> rows = new HashMap<>();
        List<Issue> issues = new ArrayList<>();
        for (var row : current) {
            if (!Objects.equals(scope.tenantId(), row.getTenantId()) || !Objects.equals(scope.projectId(), row.getProjectId())
                    || row.getId() == null || row.getVersion() == null || rows.putIfAbsent(row.getGateCode(), row) != null)
                issues.add(issue("$gates", "GATE_RUNTIME_CONFLICT", "门禁实例身份或版本不一致"));
        }
        Set<Long> gateIds = current.stream().map(ProjectGateInstanceDO::getId).collect(Collectors.toSet());
        for (var ref : refs) if (!Objects.equals(scope.tenantId(), ref.getTenantId()) || !gateIds.contains(ref.getGateId())
                || ref.getId() == null || ref.getVersion() == null)
            issues.add(issue("$gates", "GATE_REFERENCE_RUNTIME_CONFLICT", "门禁引用身份或版本不一致"));
        if (!issues.isEmpty()) return new Plan(List.of(), List.of(), List.copyOf(issues));
        var content = TemplateInstantiator.instantiateGates(after.toRuntimeContent(), scope.projectId());
        var desired = content.getGates().stream().collect(Collectors.toMap(ProjectGateInstanceDO::getGateCode, Function.identity()));
        Map<Long, List<ProjectGateReferenceInstanceDO>> oldRefs = refs.stream().collect(Collectors.groupingBy(ProjectGateReferenceInstanceDO::getGateId));
        Set<Long> managed = new HashSet<>();
        List<Write> writes = new ArrayList<>();
        for (var old : before.getGates()) {
            var row = rows.get(old.getCode());
            if (row == null) {
                issues.add(issue(old.getNodeKey(), "GATE_RUNTIME_MISSING", "有效计划门禁缺少实例，不能通过改版静默重建"));
                continue;
            }
            managed.add(row.getId());
            var previousRefs = oldRefs.getOrDefault(row.getId(), List.of());
            var next = nextNodes.get(old.getNodeKey());
            if (next == null) {
                if (!"PENDING".equals(row.getStatus()))
                    issues.add(issue(old.getNodeKey(), "EVALUATED_GATE_DELETE_FORBIDDEN", "门禁已有判定结果，不能直接删除"));
                writes.add(new Write(new Change(old.getNodeKey(), "REMOVE", row.getId(), row.getVersion(), row.getGateCode(), null, false), null, row.getStatus(), previousRefs, List.of()));
                continue;
            }
            var target = desired.get(next.getCode());
            target.setName(choose(old.getName(), next.getName(), row.getName()));
            target.setDescription(choose(old.getDescription(), next.getDescription(), row.getDescription()));
            var desiredRefs = content.getGateReferencesByGateCode().getOrDefault(next.getCode(), List.of());
            Set<Reference> previousValues = values(previousRefs), nextValues = values(desiredRefs);
            // Process business keys must not be reassigned to another stage; retain old reference rows as history.
            boolean changesStage = !Objects.equals(row.getStageCode(), target.getStageCode())
                    || !Objects.equals(stageKey(before, row.getStageCode()), stageKey(after, target.getStageCode()));
            var retired = previousRefs.stream().filter(ref -> changesStage || !nextValues.contains(value(ref))).toList();
            var added = desiredRefs.stream().filter(ref -> changesStage || !previousValues.contains(value(ref))).toList();
            boolean changedJudgment = !retired.isEmpty() || !added.isEmpty()
                    || !Objects.equals(row.getGateType(), target.getGateType()) || !Objects.equals(row.getStageCode(), target.getStageCode());
            if (!metadata(row).equals(metadata(target)) || changedJudgment)
                writes.add(new Write(new Change(old.getNodeKey(), "UPDATE", row.getId(), row.getVersion(), row.getGateCode(), target.getGateCode(), changedJudgment),
                        target, row.getStatus(), retired, added));
        }
        for (var next : after.getGates()) {
            var occupant = rows.get(next.getCode());
            if (occupant != null && !managed.contains(occupant.getId()))
                issues.add(issue(next.getNodeKey(), "GATE_CODE_OCCUPIED", "编码被计划外门禁占用，不能覆盖该记录"));
            if (!oldNodes.containsKey(next.getNodeKey()))
                writes.add(new Write(new Change(next.getNodeKey(), "ADD", null, null, null, next.getCode(), true), desired.get(next.getCode()), null,
                        List.of(), content.getGateReferencesByGateCode().getOrDefault(next.getCode(), List.of())));
        }
        return new Plan(writes.stream().map(Write::change).toList(), List.copyOf(writes), List.copyOf(issues));
    }

    /** Same transaction as plan activation, version checks, audit and its dedicated reevaluation Outbox event. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void install(ProjectPlanScopeQuery scope, Plan plan, Long actorId, String correlationId) {
        if (!plan.issues().isEmpty()) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        for (var write : plan.writes()) if (write.definition() == null)
            requireOne(projections.retirePendingGate(token(scope, write.change(), actorId)));
        for (var write : plan.writes()) if ("UPDATE".equals(write.change().action())
                && !Objects.equals(write.change().fromCode(), write.change().toCode()))
            requireOne(projections.gateCodeForRename(token(scope, write.change(), actorId)));
        for (var write : plan.writes()) {
            Long gateId = write.change().instanceId();
            if (write.definition() != null) {
                if (gateId == null) {
                    var row = write.definition(); gateId = IdWorker.getId(); row.setId(gateId); row.setTenantId(scope.tenantId());
                    row.setProjectId(scope.projectId()); row.setVersion(0); row.setCreator(actorId.toString()); row.setUpdater(actorId.toString());
                    requireOne(gates.insert(row));
                } else requireOne(projections.updateGateDefinition(new ProjectPlanProjectionMapper.GateDefinitionUpdate(
                        scope.tenantId(), scope.projectId(), gateId, write.change().expectedVersion(), write.definition(), actorId.toString())));
            }
            for (var ref : write.retired()) requireOne(projections.retireGateReference(new ProjectPlanProjectionMapper.GateReferenceRetirement(
                    scope.tenantId(), scope.projectId(), gateId, ref.getId(), ref.getVersion(), actorId.toString())));
            for (var ref : write.added()) {
                ref.setGateId(gateId); ref.setTenantId(scope.tenantId()); ref.setVersion(0); ref.setCreator(actorId.toString()); ref.setUpdater(actorId.toString());
                requireOne(references.insert(ref));
            }
            if ("UPDATE".equals(write.change().action()) && write.change().reevaluationRequired() && !"PENDING".equals(write.previousStatus())) {
                requireOne(gates.updateStatusIfMatch(new ProjectGateStatusUpdate(scope.tenantId(), gateId,
                        write.change().expectedVersion()+1, write.previousStatus(), "PENDING", actorId.toString())));
                audit.record(scope.tenantId(), actorId, correlationId, "PROJECT_GATE_PLAN_RESULT_INVALIDATED", "ProjectGate", gateId.toString(), "SUCCESS",
                        Map.of("projectId", scope.projectId(), "gateId", gateId, "previousStatus", write.previousStatus(),
                                "previousVersion", write.change().expectedVersion(), "currentVersion", write.change().expectedVersion()+2,
                                "retiredReferenceIds", write.retired().stream().map(ProjectGateReferenceInstanceDO::getId).toList()));
            }
        }
    }

    private static Reference value(ProjectGateReferenceInstanceDO ref) { return new Reference(ref.getRefType(), ref.getRefCode(), ref.getRefVersion()); }
    private static String stageKey(TemplateExecutionSnapshot snapshot, String code) {
        return snapshot.getStages().stream().filter(stage -> Objects.equals(stage.getCode(), code))
                .map(TemplateExecutionSnapshot.StageContract::getNodeKey).findFirst().orElse(null);
    }
    private static Set<Reference> values(List<ProjectGateReferenceInstanceDO> refs) { return refs.stream().map(ProjectPlanGateInstaller::value).collect(Collectors.toSet()); }
    private static List<?> metadata(ProjectGateInstanceDO row) { return Arrays.asList(row.getGateCode(), row.getName(), row.getStageCode(), row.getGateType(), row.getDescription(), row.getValidationSummary()); }
    private static <T> T choose(T before, T after, T actual) { return Objects.equals(before, after) ? actual : after; }
    private static Issue issue(String key, String code, String message) { return new Issue("nodes." + key, code, message); }
    private ProjectPlanProjectionMapper.NodeProjectionChange token(ProjectPlanScopeQuery scope, Change change, Long actor) {
        return new ProjectPlanProjectionMapper.NodeProjectionChange(scope.tenantId(), scope.projectId(), change.instanceId(), change.expectedVersion(), actor.toString());
    }
    private void requireOne(int count) { if (count != 1) throw exception(PROJECT_PLAN_VERSION_CONFLICT); }
}
