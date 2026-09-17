package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.*;

/** Maps version-local node identities to current Owner instances; no business-table access or state transitions. */
@Component
public class ProjectPlanDeliverablePlanner {
    public record Change(String nodeKey, String action, Long instanceId, Integer expectedVersion,
                         String fromCode, String toCode) { }
    public record Plan(List<Change> changes, List<DeliverablePlanChange> writes, List<Issue> issues) { }

    public Plan plan(Long projectId, TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                     DeliverablePlanState state) {
        var oldNodes = before.getDeliverables().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.DeliverableContract::getNodeKey, Function.identity()));
        var newNodes = after.getDeliverables().stream().collect(Collectors.toMap(
                TemplateExecutionSnapshot.DeliverableContract::getNodeKey, Function.identity()));
        Map<String, DeliverableView> current = new HashMap<>();
        List<Issue> issues = new ArrayList<>();
        for (var row : state.definitions()) {
            if (!Objects.equals(projectId, row.projectId()) || row.id() == null || row.version() == null
                    || current.putIfAbsent(row.deliverableCode(), row) != null)
                issues.add(issue("$deliverables", "DELIVERABLE_RUNTIME_IDENTITY_CONFLICT", "交付件实例身份或版本不一致"));
        }
        if (!issues.isEmpty()) return new Plan(List.of(), List.of(), List.copyOf(issues));
        var changes = new ArrayList<Change>();
        var writes = new ArrayList<DeliverablePlanChange>();
        Set<Long> managed = new HashSet<>();
        for (var old : before.getDeliverables()) {
            // The effective version's code locates its current projection; source asset IDs are never runtime identity.
            var row = current.get(old.getCode());
            if (row == null) {
                issues.add(issue(old.getNodeKey(), "DELIVERABLE_RUNTIME_MISSING", "有效计划交付件缺少业务实例，不能通过改版静默重建"));
                continue;
            }
            managed.add(row.id());
            var next = newNodes.get(old.getNodeKey());
            if (next == null) {
                if (!state.retirableIds().contains(row.id()))
                    issues.add(issue(old.getNodeKey(), "DELIVERABLE_HANDLING_HISTORY_PROTECTED", "交付件已办理、存在来源记录或被业务任务引用，不能直接删除"));
                changes.add(new Change(old.getNodeKey(), "REMOVE", row.id(), row.version(), row.deliverableCode(), null));
                writes.add(new DeliverablePlanChange(row.id(), row.version(), null));
                continue;
            }
            var desired = new DeliverableDefinition(next.getCode(),
                    choose(old.getName(), next.getName(), row.name()),
                    choose(old.getStageCode(), next.getStageCode(), row.stageCode()),
                    choose(old.getTaskCode(), next.getTaskCode(), row.taskCode()),
                    Boolean.TRUE.equals(choose(old.getRequired(), next.getRequired(), row.required())), row.sourceDefinitionId());
            if (!desired.equals(definition(row))) {
                changes.add(new Change(old.getNodeKey(), "UPDATE", row.id(), row.version(), row.deliverableCode(), next.getCode()));
                writes.add(new DeliverablePlanChange(row.id(), row.version(), desired));
            }
        }
        for (var next : after.getDeliverables()) {
            var occupant = current.get(next.getCode());
            if (occupant != null && !managed.contains(occupant.id()))
                issues.add(issue(next.getNodeKey(), "DELIVERABLE_CODE_OCCUPIED", "编码已被计划外的业务交付件使用，不能覆盖该记录"));
            if (!oldNodes.containsKey(next.getNodeKey())) {
                changes.add(new Change(next.getNodeKey(), "ADD", null, null, null, next.getCode()));
                writes.add(new DeliverablePlanChange(null, null, new DeliverableDefinition(next.getCode(), next.getName(),
                        next.getStageCode(), next.getTaskCode(), Boolean.TRUE.equals(next.getRequired()), null)));
            }
        }
        return new Plan(List.copyOf(changes), List.copyOf(writes), List.copyOf(issues));
    }

    private static <T> T choose(T before, T after, T actual) { return Objects.equals(before, after) ? actual : after; }
    private static DeliverableDefinition definition(DeliverableView row) {
        return new DeliverableDefinition(row.deliverableCode(), row.name(), row.stageCode(), row.taskCode(),
                Boolean.TRUE.equals(row.required()), row.sourceDefinitionId());
    }
    private static Issue issue(String key, String code, String message) {
        return new Issue("nodes." + key, code, message);
    }
}
