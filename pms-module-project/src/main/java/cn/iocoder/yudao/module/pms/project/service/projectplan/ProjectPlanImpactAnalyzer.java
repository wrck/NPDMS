package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.Issue;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** Project-specific impact mapping only. The existing compiler/LiteFlow still owns rule semantics. */
@Component
public class ProjectPlanImpactAnalyzer {
    public record Change(String nodeKey, String nodeKind, String name, String action,
                         boolean started, boolean completed, List<String> effects) { }
    public record Impact(List<Change> changes, List<String> changedRuleKeys, List<Issue> issues) { }
    private record Node(String key, String kind, String name, String stageCode, JsonNode value, Object binding,
                        String admission, String completion, String exit) { }

    public Impact analyze(TemplateExecutionSnapshot before, TemplateExecutionSnapshot after,
                          List<ProjectNodeExecutionDO> rounds, List<ProjectTaskInstanceDO> tasks) {
        Map<String, Node> oldNodes = nodes(before), newNodes = nodes(after);
        Set<String> changedRules = new LinkedHashSet<>(before.getRulePrograms().keySet());
        changedRules.addAll(after.getRulePrograms().keySet());
        changedRules.removeIf(key -> Objects.equals(before.getRulePrograms().get(key), after.getRulePrograms().get(key)));
        Map<String, ProjectNodeExecutionDO> current = new HashMap<>();
        rounds.forEach(round -> current.put(round.getNodeKey(), round));
        Set<Long> startedTaskIds = new HashSet<>();
        tasks.stream().filter(task -> task.getActualStartTime() != null
                || Set.of("IN_PROGRESS", "PENDING_ACCEPT", "DONE", "CLOSED").contains(task.getStatus()))
                .forEach(task -> startedTaskIds.add(task.getId()));
        Set<String> startedKeys = new HashSet<>();
        rounds.stream().filter(round -> round.getStartedAt() != null || Set.of("DONE", "TERMINATED").contains(round.getStatus())
                || ("TASK".equals(round.getNodeKind()) && startedTaskIds.contains(round.getNodeInstanceId())))
                .forEach(round -> startedKeys.add(round.getNodeKey()));
        Set<String> stagesWithWork = new HashSet<>();
        for (var task : tasks) if (startedTaskIds.contains(task.getId())) stagesWithWork.add(task.getStageCode());
        for (String key : startedKeys) {
            var node = oldNodes.get(key);
            if (node != null && "TASK".equals(node.kind())) stagesWithWork.add(node.stageCode());
        }
        List<Change> changes = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>(oldNodes.keySet()); keys.addAll(newNodes.keySet());
        for (String key : keys) {
            Node old = oldNodes.get(key), next = newNodes.get(key);
            Node node = next == null ? old : next;
            boolean changed = old == null || next == null || !Objects.equals(old.value(), next.value());
            List<String> effects = new ArrayList<>();
            if (old != null && next != null) {
                if (ruleChanged(old.admission(), next.admission(), changedRules)) effects.add("准入规则变化");
                if (ruleChanged(old.completion(), next.completion(), changedRules)) effects.add("完成规则变化");
                if (ruleChanged(old.exit(), next.exit(), changedRules)) effects.add("退出规则变化");
            }
            if (!changed && effects.isEmpty()) continue;
            boolean started = startedKeys.contains(key);
            var round = current.get(key);
            boolean completed = round != null && Set.of("DONE", "TERMINATED").contains(round.getStatus());
            if (next == null && (started || ("STAGE".equals(old.kind()) && stagesWithWork.contains(old.stageCode()))))
                issues.add(new Issue("nodes." + key, "STARTED_NODE_DELETE_FORBIDDEN", "已开始节点或包含已开始工作的阶段不能直接删除"));
            if (old != null && next != null && !old.kind().equals(next.kind()))
                issues.add(new Issue("nodes." + key, "NODE_KIND_IMMUTABLE", "已有节点不能改变要素类型；请使用独立的新节点"));
            if (started && old != null && next != null) {
                if ("TASK".equals(old.kind()) && !Objects.equals(old.value().path("satisfactionTiming"),next.value().path("satisfactionTiming")))
                    issues.add(new Issue("nodes." + key + ".satisfactionTiming", "STARTED_BINDING_IMMUTABLE", "已开始工作不得改变满意度办理配置"));
                if (!sameBinding(old.binding(), next.binding()))
                    issues.add(new Issue("nodes." + key + ".binding", "STARTED_BINDING_IMMUTABLE", "已开始工作不得改变业务绑定"));
                if ("TASK".equals(old.kind()) && !Objects.equals(stageIdentity(before, old.stageCode()), stageIdentity(after, next.stageCode())))
                    issues.add(new Issue("nodes." + key + ".stageCode", "STARTED_STAGE_IMMUTABLE", "已开始任务不得改变所属阶段"));
            }
            if (completed) effects.add("已完成结果和原轮次保留，不重新计算历史");
            else if (next != null) effects.add(started ? "保留本轮办理证据，后续按新计划规则判断" : "未开始节点按生效计划重新判定");
            changes.add(new Change(key, node.kind(), node.name(), old == null ? "ADD" : next == null ? "REMOVE" : "UPDATE",
                    started, completed, List.copyOf(effects)));
        }
        if (ruleChanged(before.getClosureRuleKey(), after.getClosureRuleKey(), changedRules))
            changes.add(new Change("$project", "PROJECT", "项目收口", "UPDATE", false, false, List.of("生效后按新的收口条件判断；不附加固定审批")));
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        collectDependents(before, dependents); collectDependents(after, dependents);
        Set<String> affected = new LinkedHashSet<>(changes.stream().map(Change::nodeKey).toList());
        ArrayDeque<String> pending = new ArrayDeque<>(affected);
        while (!pending.isEmpty()) {
            String source = pending.removeFirst();
            for (String target : dependents.getOrDefault(source, Set.of())) {
                if (!affected.add(target)) continue;
                pending.addLast(target);
                Node node = newNodes.getOrDefault(target, oldNodes.get(target));
                var round = current.get(target);
                boolean completed = round != null && Set.of("DONE", "TERMINATED").contains(round.getStatus());
                changes.add(new Change(target, node == null ? "PROJECT" : node.kind(), node == null ? "项目收口" : node.name(),
                        "REEVALUATE", startedKeys.contains(target), completed,
                        List.of(completed ? "关联节点变化；本轮已完成结果仍保留" : "受关联节点变化影响，生效后重新判定，不自动扩大返工范围")));
            }
        }
        return new Impact(List.copyOf(changes), List.copyOf(changedRules), List.copyOf(issues));
    }

    private boolean ruleChanged(String old, String next, Set<String> changed) {
        return !Objects.equals(old, next) || (next != null && changed.contains(next));
    }
    private String stageIdentity(TemplateExecutionSnapshot snapshot, String code) {
        return snapshot.getStages().stream().filter(node -> Objects.equals(code, node.getCode()))
                .map(TemplateExecutionSnapshot.StageContract::getNodeKey).findFirst().orElse(null);
    }
    private boolean sameBinding(Object old, Object next) {
        return Objects.equals(bindingValue(old), bindingValue(next));
    }
    private JsonNode bindingValue(Object binding) {
        var value = JsonUtils.parseTree(JsonUtils.toJsonString(binding));
        if (value.isObject()) ((tools.jackson.databind.node.ObjectNode) value).remove("sourceRevisionId");
        return value;
    }
    private void collectDependents(TemplateExecutionSnapshot snapshot, Map<String, Set<String>> dependents) {
        Map<String, String> references = new HashMap<>();
        snapshot.getStages().forEach(node -> references.put("STATE:" + node.getCode() + "_COMPLETED", node.getNodeKey()));
        snapshot.getTasks().forEach(node -> references.put("TASK:" + node.getCode(), node.getNodeKey()));
        snapshot.getMilestones().forEach(node -> references.put("MILESTONE:" + node.getCode(), node.getNodeKey()));
        snapshot.getDeliverables().forEach(node -> references.put("DELIVERABLE:" + node.getCode(), node.getNodeKey()));
        var all = nodes(snapshot);
        for (var node : all.values()) {
            collectRuleDependents(snapshot, node.admission(), node.key(), references, dependents);
            collectRuleDependents(snapshot, node.completion(), node.key(), references, dependents);
            collectRuleDependents(snapshot, node.exit(), node.key(), references, dependents);
            if ("TASK".equals(node.kind())) {
                String parent = stageIdentity(snapshot, node.stageCode());
                if (parent != null) dependents.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(node.key());
            }
        }
        collectRuleDependents(snapshot, snapshot.getClosureRuleKey(), "$project", references, dependents);
    }

    /** Rework changes runtime results, not the design. Reuse the compiled dependency mapping. */
    public Set<String> dependentNodeKeys(TemplateExecutionSnapshot snapshot, Set<String> sources) {
        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        collectDependents(snapshot, dependents);
        Set<String> affected = new LinkedHashSet<>(sources);
        var pending = new ArrayDeque<>(sources);
        while (!pending.isEmpty()) {
            for (String target : dependents.getOrDefault(pending.removeFirst(), Set.of())) {
                if (affected.add(target)) pending.addLast(target);
            }
        }
        affected.removeAll(sources);
        return Collections.unmodifiableSet(affected);
    }
    private void collectRuleDependents(TemplateExecutionSnapshot snapshot, String ruleKey, String consumer,
                                       Map<String, String> references, Map<String, Set<String>> dependents) {
        if (ruleKey == null) return;
        var program = snapshot.getRulePrograms().get(ruleKey);
        if (program == null) return;
        for (var leaf : program.leaves()) {
            String source = "BUSINESS_FACT".equals(leaf.predicate()) && leaf.parameters().has("sourceNodeKey")
                    ? leaf.parameters().path("sourceNodeKey").asText()
                    : references.get(leaf.predicate() + ":" + leaf.parameters().path("refCode").asText());
            if (source != null) dependents.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(consumer);
        }
    }
    private Map<String, Node> nodes(TemplateExecutionSnapshot snapshot) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        snapshot.getStages().forEach(node -> nodes.put(node.getNodeKey(), new Node(node.getNodeKey(), "STAGE", node.getName(), node.getCode(),
                json(node), node.getBinding(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey())));
        snapshot.getTasks().forEach(node -> nodes.put(node.getNodeKey(), new Node(node.getNodeKey(), "TASK", node.getName(), node.getStageCode(),
                json(node), node.getBinding(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey())));
        snapshot.getMilestones().forEach(node -> nodes.put(node.getNodeKey(), new Node(node.getNodeKey(), "MILESTONE", node.getName(), node.getStageCode(), json(node), null, null, null, null)));
        snapshot.getDeliverables().forEach(node -> nodes.put(node.getNodeKey(), new Node(node.getNodeKey(), "DELIVERABLE", node.getName(), node.getStageCode(), json(node), null, null, null, null)));
        snapshot.getGates().forEach(node -> nodes.put(node.getNodeKey(), new Node(node.getNodeKey(), "GATE", node.getName(), node.getStageCode(), json(node), null, null, null, null)));
        return nodes;
    }
    private JsonNode json(Object value) { return JsonUtils.parseTree(JsonUtils.toJsonString(value)); }
}
