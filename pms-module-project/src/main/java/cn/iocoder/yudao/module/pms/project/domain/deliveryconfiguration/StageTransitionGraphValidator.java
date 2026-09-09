package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PM-03 / F-PROJ-009 AC-02、AC-03；SDS08 单一模板增量的纯结构校验。
 * 空失败列表仅表示图结构有效，不证明条件事实、引用发布状态、门禁或权限有效。
 */
public final class StageTransitionGraphValidator {

    private StageTransitionGraphValidator() {
    }

    /** path 定位输入对象及字段；code 是本领域组件的错误类型，不是公开 REST 错误码。 */
    public record Failure(String code, String path, String message) {
    }

    public static List<Failure> validate(StageTransitionGraph graph) {
        List<Failure> failures = new ArrayList<>();
        if (graph == null) {
            return List.of(new Failure("REQUIRED", "graph", "阶段图不能为空"));
        }
        validateFields(graph, failures);
        // 字段/引用损坏时不运行图算法，避免 NPE 或把缺失引用解释为合法边。
        if (!failures.isEmpty()) {
            return List.copyOf(failures);
        }

        Map<String, List<StageTransitionDefinition>> outgoing = new HashMap<>();
        for (StageTransitionGraph.Stage stage : graph.stages()) {
            outgoing.put(stage.stageCode(), new ArrayList<>());
        }
        for (StageTransitionDefinition edge : graph.transitions()) {
            outgoing.get(edge.fromStageCode()).add(edge);
        }
        List<StageTransitionGraph.Stage> starts = graph.stages().stream()
                .filter(StageTransitionGraph.Stage::start).toList();
        if (starts.size() != 1) {
            failures.add(new Failure("START_COUNT", "stages.start", "必须显式配置唯一 S0 开始阶段"));
        }
        for (StageTransitionGraph.Stage stage : starts) {
            if (!"S0".equals(stage.stageCode())) {
                failures.add(new Failure("START_NOT_S0", stagePath(stage) + ".start", "开始阶段必须是 S0"));
            }
        }
        if (graph.stages().stream().noneMatch(StageTransitionGraph.Stage::normalClosure)) {
            failures.add(new Failure("MISSING_CLOSURE", "stages.normalClosure", "必须显式配置正常收口阶段"));
        }
        for (StageTransitionGraph.Stage stage : graph.stages()) {
            List<StageTransitionDefinition> edges = outgoing.get(stage.stageCode());
            if (stage.normalClosure() && !edges.isEmpty()) {
                failures.add(new Failure("CLOSURE_HAS_OUTGOING", stagePath(stage) + ".normalClosure",
                        "正常收口阶段不得有出向关系"));
            }
            if (!stage.normalClosure() && edges.isEmpty()) {
                failures.add(new Failure("MISSING_OUTGOING", stagePath(stage) + ".outgoing",
                        "非收口阶段至少需要一条出向关系"));
            }
            validateBranches(edges, failures);
        }

        Map<String, Integer> colors = new HashMap<>();
        for (StageTransitionGraph.Stage stage : graph.stages()) {
            detectCycles(stage.stageCode(), outgoing, colors, failures);
        }
        if (starts.size() == 1) {
            Set<String> reachable = new HashSet<>();
            collectReachable(starts.getFirst().stageCode(), outgoing, reachable);
            for (StageTransitionGraph.Stage stage : graph.stages()) {
                if (!reachable.contains(stage.stageCode())) {
                    failures.add(new Failure("UNREACHABLE", stagePath(stage) + ".stageCode",
                            "阶段无法从开始阶段到达"));
                }
            }
            if (graph.stages().stream().noneMatch(stage -> stage.normalClosure()
                    && reachable.contains(stage.stageCode()))) {
                failures.add(new Failure("NO_REACHABLE_CLOSURE", "stages.normalClosure",
                        "开始阶段必须可达正常收口阶段"));
            }
        }
        return List.copyOf(failures);
    }

    private static void validateFields(StageTransitionGraph graph, List<Failure> failures) {
        if (graph.stages() == null || graph.stages().isEmpty()) {
            failures.add(new Failure("EMPTY_GRAPH", "stages", "阶段集合不能为空"));
        }
        if (graph.transitions() == null) {
            failures.add(new Failure("REQUIRED", "transitions", "关系集合不能为空引用；无关系使用空集合"));
        }
        if (!failures.isEmpty()) {
            return;
        }
        Set<String> stageCodes = new HashSet<>();
        for (int i = 0; i < graph.stages().size(); i++) {
            StageTransitionGraph.Stage stage = graph.stages().get(i);
            String path = "stages[" + i + "]";
            if (stage == null) {
                failures.add(new Failure("REQUIRED", path, "阶段对象不能为空"));
                continue;
            }
            if (stage.stageCode() == null || !stage.stageCode().matches("S[0-6]")) {
                failures.add(new Failure("INVALID_STAGE_CODE", path + ".stageCode", "阶段编码必须为 S0～S6"));
            } else if (!stageCodes.add(stage.stageCode())) {
                failures.add(new Failure("DUPLICATE_STAGE", path + ".stageCode",
                        "阶段编码重复：" + stage.stageCode()));
            }
            if (stage.start() == null) {
                failures.add(new Failure("REQUIRED", path + ".start", "必须显式声明是否为开始阶段"));
            }
            if (stage.normalClosure() == null) {
                failures.add(new Failure("REQUIRED", path + ".normalClosure", "必须显式声明是否为正常收口"));
            }
        }
        Set<String> transitionCodes = new HashSet<>();
        for (int i = 0; i < graph.transitions().size(); i++) {
            StageTransitionDefinition edge = graph.transitions().get(i);
            String path = "transitions[" + i + "]";
            if (edge == null) {
                failures.add(new Failure("REQUIRED", path, "关系对象不能为空"));
                continue;
            }
            if (edge.transitionCode() == null || edge.transitionCode().isBlank()) {
                failures.add(new Failure("REQUIRED", path + ".transitionCode", "关系编码不能为空"));
            } else if (!transitionCodes.add(edge.transitionCode())) {
                failures.add(new Failure("DUPLICATE_TRANSITION", path + ".transitionCode",
                        "关系编码重复：" + edge.transitionCode()));
            }
            if (!stageCodes.contains(edge.fromStageCode())) {
                failures.add(new Failure("DANGLING_SOURCE", path + ".fromStageCode", "来源阶段不在图中"));
            }
            if (!stageCodes.contains(edge.toStageCode())) {
                failures.add(new Failure("DANGLING_TARGET", path + ".toStageCode", "目标阶段不在图中"));
            }
            if (edge.fromStageCode() != null && edge.fromStageCode().equals(edge.toStageCode())) {
                failures.add(new Failure("SELF_LOOP", path + ".toStageCode", "关系不得指向自身"));
            }
            if (edge.priority() == null) {
                failures.add(new Failure("REQUIRED", path + ".priority", "必须配置优先级，数值越小越优先"));
            }
            if (edge.defaultBranch() == null) {
                failures.add(new Failure("REQUIRED", path + ".defaultBranch", "必须显式声明默认分支"));
            }
            if (edge.conditionRevisionId() != null && edge.conditionRevisionId() <= 0) {
                failures.add(new Failure("INVALID_CONDITION_REVISION", path + ".conditionRevisionId",
                        "条件必须引用正数修订 ID"));
            }
            if (Boolean.TRUE.equals(edge.defaultBranch()) && edge.conditionRevisionId() != null) {
                failures.add(new Failure("DEFAULT_HAS_CONDITION", path + ".conditionRevisionId",
                        "默认分支不得携带条件"));
            }
        }
    }

    private static void validateBranches(List<StageTransitionDefinition> edges, List<Failure> failures) {
        boolean hasDefault = false;
        Set<Integer> unconditionalPriorities = new HashSet<>();
        for (StageTransitionDefinition edge : edges) {
            if (edge.defaultBranch()) {
                if (hasDefault) {
                    failures.add(new Failure("MULTIPLE_DEFAULTS", edgePath(edge) + ".defaultBranch",
                            "同一来源最多配置一条默认分支"));
                }
                hasDefault = true;
            } else if (edge.conditionRevisionId() == null && !unconditionalPriorities.add(edge.priority())) {
                failures.add(new Failure("UNCONDITIONAL_PRIORITY_CONFLICT", edgePath(edge) + ".priority",
                        "同一来源的无条件非默认分支不得具有相同优先级"));
            }
        }
    }

    private static void collectReachable(String stageCode, Map<String, List<StageTransitionDefinition>> outgoing,
                                         Set<String> reachable) {
        if (!reachable.add(stageCode)) {
            return;
        }
        for (StageTransitionDefinition edge : outgoing.get(stageCode)) {
            collectReachable(edge.toStageCode(), outgoing, reachable);
        }
    }

    private static void detectCycles(String stageCode, Map<String, List<StageTransitionDefinition>> outgoing,
                                     Map<String, Integer> colors, List<Failure> failures) {
        if (colors.getOrDefault(stageCode, 0) != 0) {
            return;
        }
        colors.put(stageCode, 1);
        for (StageTransitionDefinition edge : outgoing.get(stageCode)) {
            if (colors.getOrDefault(edge.toStageCode(), 0) == 1) {
                failures.add(new Failure("CYCLE", edgePath(edge) + ".toStageCode", "关系构成循环"));
            } else {
                detectCycles(edge.toStageCode(), outgoing, colors, failures);
            }
        }
        colors.put(stageCode, 2);
    }

    private static String stagePath(StageTransitionGraph.Stage stage) {
        return "stages[" + stage.stageCode() + "]";
    }

    private static String edgePath(StageTransitionDefinition edge) {
        return "transitions[" + edge.transitionCode() + "]";
    }
}
