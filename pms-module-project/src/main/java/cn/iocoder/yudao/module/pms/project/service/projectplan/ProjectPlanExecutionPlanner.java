package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import org.springframework.stereotype.Component;

import java.util.*;

/** Version-to-runtime identity mapping. This is not a rule evaluator or a state-transition command. */
@Component
public class ProjectPlanExecutionPlanner {
    public enum Action { CREATE, REBASE_CURRENT, PRESERVE_HISTORY, RETIRE_UNSTARTED }
    public record Change(String nodeKey, String nodeKind, String name, Action action,
                         Long nodeInstanceId, Long executionId, Integer executionVersion, Integer nodeVersion, Integer roundNo,
                         Long sourcePlanVersionId, String fromCode, String toCode) { }
    public record Plan(List<Change> changes, List<Issue> issues) { }
    private record Node(String key, String kind, String name, String code) { }

    public Plan plan(Long effectivePlanId, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                     List<ProjectNodeExecutionDO> rounds, List<ProjectStageInstanceDO> stages,
                     List<ProjectTaskInstanceDO> tasks) {
        Map<String, Node> oldNodes = nodes(before), nextNodes = nodes(after);
        Map<String, ProjectNodeExecutionDO> current = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();
        for (var round : rounds) {
            if (current.putIfAbsent(round.getNodeKey(), round) != null)
                issues.add(issue(round.getNodeKey(), "DUPLICATE_CURRENT_EXECUTION", "同一节点存在多个当前执行，不能生效"));
            if (!oldNodes.containsKey(round.getNodeKey()))
                issues.add(issue(round.getNodeKey(), "EXECUTION_OUTSIDE_EFFECTIVE_PLAN", "当前执行不属于有效计划，不能按资产编号或业务编码猜测身份"));
        }
        Set<Long> managedTasks = new HashSet<>();
        rounds.stream().filter(round -> "TASK".equals(round.getNodeKind())).forEach(round -> managedTasks.add(round.getNodeInstanceId()));
        for (var task : tasks) {
            if (!managedTasks.contains(task.getId()))
                issues.add(new Issue("tasks." + task.getId(), "PROJECT_PLAN_UNTRACKED_TASK",
                        "存在未纳入有效计划的任务，不能在改版时丢弃其任务树或办理记录"));
        }
        List<Change> changes = new ArrayList<>();
        for (var old : oldNodes.values()) {
            var round = current.get(old.key());
            if (round == null) {
                issues.add(issue(old.key(), "CURRENT_EXECUTION_MISSING", "有效计划节点缺少当前执行，不能通过改版静默重建"));
                continue;
            }
            boolean ended = Set.of("DONE", "TERMINATED").contains(round.getStatus());
            if (!Objects.equals(old.kind(), round.getNodeKind()) || round.getId() == null
                    || round.getVersion() == null || round.getRoundNo() == null || round.getRoundNo() < 1
                    || round.getContractId() == null || round.getNodeInstanceId() == null
                    || !Integer.valueOf(1).equals(round.getCurrentMarker())
                    || !Set.of("PENDING", "ACTIVE", "DONE", "TERMINATED").contains(round.getStatus())
                    || (!ended && !Objects.equals(effectivePlanId, round.getPlanVersionId()))) {
                issues.add(issue(old.key(), "CURRENT_EXECUTION_STALE", "当前执行身份、轮次或有效计划版本不一致"));
                continue;
            }
            // A completed round may intentionally remain attached to an older plan.
            if (!projectionMatches(old, round, stages, tasks)) {
                issues.add(issue(old.key(), "NODE_RUNTIME_PROJECTION_STALE", "节点执行与当前阶段或任务实例不一致"));
                continue;
            }
            if (ended && (round.getEndedAt() == null || round.getResultSnapshot() == null)) {
                issues.add(issue(old.key(), "ENDED_EXECUTION_EVIDENCE_MISSING", "已结束执行缺少历史结果，不能以新规则补造"));
                continue;
            }
            var next = nextNodes.get(old.key());
            if (next == null && (ended || round.getStartedAt() != null
                    || ("TASK".equals(old.kind()) && tasks.stream().anyMatch(task ->
                        Objects.equals(task.getId(), round.getNodeInstanceId()) && started(task)))
                    || ("STAGE".equals(old.kind()) && tasks.stream().anyMatch(task ->
                        Objects.equals(task.getStageCode(), old.code()) && started(task))))) {
                issues.add(issue(old.key(), "STARTED_NODE_DELETE_FORBIDDEN", "已开始节点或包含已开始工作的阶段不能直接删除"));
                continue;
            }
            Action action = ended ? Action.PRESERVE_HISTORY : next == null ? Action.RETIRE_UNSTARTED : Action.REBASE_CURRENT;
            changes.add(new Change(old.key(), old.kind(), next == null ? old.name() : next.name(), action,
                    round.getNodeInstanceId(), round.getId(), round.getVersion(), projectionVersion(old,round,stages,tasks), round.getRoundNo(),
                    round.getPlanVersionId(), old.code(), next == null ? null : next.code()));
        }
        for (var next : nextNodes.values()) {
            if (!oldNodes.containsKey(next.key()))
                changes.add(new Change(next.key(), next.kind(), next.name(), Action.CREATE,
                        null, null, null, null, null, null, null, next.code()));
        }
        if (issues.isEmpty()) issues.addAll(ProjectPlanTaskHierarchy.resolve(before, after, rounds, tasks).issues());
        return new Plan(List.copyOf(changes), List.copyOf(issues));
    }

    private boolean projectionMatches(Node node, ProjectNodeExecutionDO round,
                                      List<ProjectStageInstanceDO> stages, List<ProjectTaskInstanceDO> tasks) {
        if ("STAGE".equals(node.kind())) {
            var matches = stages.stream().filter(stage -> Objects.equals(stage.getId(), round.getNodeInstanceId())).toList();
            return matches.size() == 1 && Objects.equals(node.code(), matches.getFirst().getCode())
                    && Objects.equals(round.getStatus(), matches.getFirst().getStatus());
        }
        var matches = tasks.stream().filter(task -> Objects.equals(task.getId(), round.getNodeInstanceId())).toList();
        if (matches.size() != 1 || !Objects.equals(node.code(), matches.getFirst().getCode())) return false;
        return switch (round.getStatus()) {
            case "PENDING" -> Set.of("PENDING_ASSIGN", "PENDING_START").contains(matches.getFirst().getStatus());
            case "ACTIVE" -> Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(matches.getFirst().getStatus())
                    || (round.getAdmittedAt() != null && round.getStartedAt() == null
                        && Set.of("PENDING_ASSIGN", "PENDING_START").contains(matches.getFirst().getStatus()));
            case "DONE" -> "DONE".equals(matches.getFirst().getStatus());
            case "TERMINATED" -> "CLOSED".equals(matches.getFirst().getStatus());
            default -> false;
        };
    }

    private Map<String, Node> nodes(TemplateExecutionSnapshot snapshot) {
        Map<String, Node> result = new LinkedHashMap<>();
        snapshot.getStages().forEach(node -> result.put(node.getNodeKey(), new Node(node.getNodeKey(), "STAGE", node.getName(), node.getCode())));
        snapshot.getTasks().forEach(node -> result.put(node.getNodeKey(), new Node(node.getNodeKey(), "TASK", node.getName(), node.getCode())));
        return result;
    }
    private Integer projectionVersion(Node node,ProjectNodeExecutionDO round,List<ProjectStageInstanceDO> stages,List<ProjectTaskInstanceDO> tasks) {
        return "STAGE".equals(node.kind())
                ? stages.stream().filter(row -> Objects.equals(row.getId(),round.getNodeInstanceId())).findFirst().orElseThrow().getVersion()
                : tasks.stream().filter(row -> Objects.equals(row.getId(),round.getNodeInstanceId())).findFirst().orElseThrow().getVersion();
    }
    private boolean started(ProjectTaskInstanceDO task) {
        return task.getActualStartTime() != null || Set.of("IN_PROGRESS", "PENDING_ACCEPT", "DONE", "CLOSED").contains(task.getStatus());
    }
    private Issue issue(String key, String code, String message) { return new Issue("nodes." + key, code, message); }
}
