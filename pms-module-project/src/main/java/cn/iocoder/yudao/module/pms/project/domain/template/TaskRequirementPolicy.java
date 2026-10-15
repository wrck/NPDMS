package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 缺省保留历史必选语义；仅显式false为按需，不改写任务状态。 */
public final class TaskRequirementPolicy {
    private TaskRequirementPolicy() { }
    public static boolean required(ProjectTaskInstanceDO task) {
        return !Boolean.FALSE.equals(task.getRequiredTask());
    }

    /** 拒绝与按需语义矛盾的新版本依赖；不把失败业务事实解释成成功。 */
    public static void validateDependencies(TemplateExecutionSnapshot snapshot) {
        Set<String> optional = snapshot.getTasks().stream().filter(task -> Boolean.FALSE.equals(task.getRequired()))
                .map(TemplateExecutionSnapshot.TaskContract::getCode).collect(Collectors.toSet());
        if (optional.isEmpty()) return;
        Map<String, String> sources = new HashMap<>();
        snapshot.getTasks().stream().filter(task -> optional.contains(task.getCode()))
                .forEach(task -> sources.put(task.getNodeKey(), task.getCode()));
        Map<String, String> deliverables = new HashMap<>();
        snapshot.getDeliverables().stream().filter(item -> optional.contains(item.getTaskCode()))
                .forEach(item -> deliverables.put(item.getCode(), item.getTaskCode()));
        for (var stage : snapshot.getStages()) {
            checkRules(new DependencyContext(snapshot, optional, sources, deliverables, null),
                    stage.getAdmissionRuleKey(), stage.getCompletionRuleKey(), stage.getExitRuleKey());
        }
        for (var task : snapshot.getTasks()) {
            checkRules(new DependencyContext(snapshot, optional, sources, deliverables, task.getCode()),
                    task.getAdmissionRuleKey(), task.getCompletionRuleKey(), task.getExitRuleKey());
        }
        var global = new DependencyContext(snapshot, optional, sources, deliverables, null);
        checkRules(global, snapshot.getClosureRuleKey());
        snapshot.getTransitions().forEach(edge -> checkRules(global, edge.getConditionRuleKey()));
        snapshot.getGates().forEach(gate -> checkRules(global, gate.getConditionRuleKey()));
    }

    private record DependencyContext(TemplateExecutionSnapshot snapshot, Set<String> optional,
            Map<String, String> sources, Map<String, String> deliverables, String taskCode) { }

    private static void checkRules(DependencyContext context, String... keys) {
        for (String key : keys) {
            if (key == null) continue;
            var rule = context.snapshot().getRulePrograms().get(key);
            if (rule == null) continue; // 普通规则引用校验负责缺失规则。
            for (var leaf : rule.leaves()) {
                String referenced = switch (leaf.predicate()) {
                    case "TASK" -> leaf.parameters().path("refCode").asText(null);
                    case "DELIVERABLE" -> context.deliverables().get(leaf.parameters().path("refCode").asText());
                    case "BUSINESS_FACT", "WAIT_ELAPSED" -> context.sources().get(leaf.parameters().path("sourceNodeKey").asText());
                    default -> null;
                };
                if (referenced != null && context.optional().contains(referenced)
                        && !Objects.equals(referenced, context.taskCode()))
                    throw new IllegalArgumentException("规则" + key + "引用按需任务" + referenced + "，会阻断其他任务或阶段；请移除该依赖");
            }
        }
    }
}
