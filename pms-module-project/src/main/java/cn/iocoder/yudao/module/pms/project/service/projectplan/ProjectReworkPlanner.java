package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Selection and impact only. No rule evaluation, business mutation or implicit dependent-node rework. */
@Component
@RequiredArgsConstructor
public class ProjectReworkPlanner {
    private final ProjectPlanImpactAnalyzer impacts;

    public record Node(String nodeKey, String nodeKind, String code, String name, String stageCode) { }
    public record Target(Node node, Long executionId, Integer executionVersion, Integer roundNo, boolean selected) { }
    public record Blocker(String nodeKey, String code, String message) { }
    public record Plan(List<Target> targets, Set<String> affectedNodeKeys, List<Blocker> blockers) {
        public boolean applicable() { return blockers.isEmpty() && !targets.isEmpty(); }
    }

    public Plan plan(TemplateExecutionSnapshot snapshot, List<ProjectNodeExecutionDO> rounds, List<String> selectedKeys) {
        var nodes = nodes(snapshot);
        Map<String, ProjectNodeExecutionDO> current = new HashMap<>();
        for (var round : rounds) {
            if (current.putIfAbsent(round.getNodeKey(), round) != null) throw new IllegalStateException("DUPLICATE_CURRENT_EXECUTION");
        }
        List<Blocker> blockers = new ArrayList<>();
        LinkedHashSet<String> selected = new LinkedHashSet<>(selectedKeys == null ? List.of() : selectedKeys);
        if (selected.isEmpty() || selectedKeys.size() != selected.size())
            blockers.add(new Blocker("$selection", "REWORK_SELECTION_INVALID", "请选择不重复的已结束节点"));
        LinkedHashSet<String> targets = new LinkedHashSet<>(selected);
        for (String key : selected) {
            Node node = nodes.get(key);
            var round = current.get(key);
            if (node == null || round == null) {
                blockers.add(new Blocker(key, "REWORK_NODE_UNAVAILABLE", "节点不在当前有效计划或缺少当前执行"));
                continue;
            }
            if (!Set.of("DONE", "TERMINATED").contains(round.getStatus())) {
                blockers.add(new Blocker(key, "REWORK_NODE_NOT_ENDED", "本轮尚未结束；已开始的工作须先完成或明确终止"));
                continue;
            }
            if ("TASK".equals(node.nodeKind())) {
                var parents = nodes.values().stream().filter(parent -> "STAGE".equals(parent.nodeKind())
                        && Objects.equals(node.stageCode(), parent.stageCode())).toList();
                if (parents.size() != 1 || current.get(parents.getFirst().nodeKey()) == null)
                    blockers.add(new Blocker(key, "REWORK_STAGE_UNAVAILABLE", "所属阶段缺少当前执行"));
                else {
                    String parentKey = parents.getFirst().nodeKey();
                    if (Set.of("DONE", "TERMINATED").contains(current.get(parentKey).getStatus())) targets.add(parentKey);
                }
            }
        }
        // Reopening a finished stage must not abandon inconsistent, still-running work inside it.
        for (String key : targets) {
            Node node = nodes.get(key);
            if (node == null || !"STAGE".equals(node.nodeKind())) continue;
            for (Node child : nodes.values()) {
                var round = current.get(child.nodeKey());
                if ("TASK".equals(child.nodeKind()) && Objects.equals(node.stageCode(), child.stageCode())
                        && round != null && round.getStartedAt() != null
                        && !Set.of("DONE", "TERMINATED").contains(round.getStatus()))
                    blockers.add(new Blocker(child.nodeKey(), "REWORK_STAGE_HAS_RUNNING_WORK", "阶段仍有运行中的任务，不能替换其执行轮次"));
            }
        }
        List<Target> result = targets.stream().filter(key -> nodes.containsKey(key) && current.containsKey(key))
                .map(key -> new Target(nodes.get(key), current.get(key).getId(), current.get(key).getVersion(),
                        current.get(key).getRoundNo(), selected.contains(key)))
                .sorted(Comparator.comparing((Target target) -> target.node().nodeKind()).thenComparing(target -> target.node().nodeKey()))
                .toList();
        return new Plan(result, impacts.dependentNodeKeys(snapshot, targets), List.copyOf(blockers));
    }

    public Map<String, Node> nodes(TemplateExecutionSnapshot snapshot) {
        Map<String, Node> result = new LinkedHashMap<>();
        snapshot.getStages().forEach(node -> result.put(node.getNodeKey(), new Node(node.getNodeKey(), "STAGE", node.getCode(), node.getName(), node.getCode())));
        snapshot.getTasks().forEach(node -> result.put(node.getNodeKey(), new Node(node.getNodeKey(), "TASK", node.getCode(), node.getName(), node.getStageCode())));
        return Collections.unmodifiableMap(result);
    }
}
