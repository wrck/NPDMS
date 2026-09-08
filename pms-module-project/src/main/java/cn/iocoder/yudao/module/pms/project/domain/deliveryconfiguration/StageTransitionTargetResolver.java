package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PM-03 / F-PROJ-009 AC-03：只读目标解析，不执行谓词、访问数据库或写阶段/闭环事实。
 * 调用者负责精确修订及 Owner 事实的有效性；缺失事实与 UNAVAILABLE 均不可判定。
 * 当前阶段所有非默认出边参与判定，其他阶段的未知事实不参与本次决策。
 * 解析成功不代表完成规则、准出/准入门禁或授权已通过，更不等于阶段已推进。
 */
public final class StageTransitionTargetResolver {

    private StageTransitionTargetResolver() {
    }

    public enum ConditionStatus {
        SATISFIED, UNSATISFIED, UNAVAILABLE
    }

    public record ConditionFact(Long revisionId, ConditionStatus status) {
    }

    public enum Status {
        RESOLVED, TERMINAL, NO_MATCH, AMBIGUOUS, UNAVAILABLE, INVALID_GRAPH, INVALID_INPUT
    }

    /** 仅 RESOLVED 携带目标和选中关系；候选编码稳定排序，失败包含对象/字段定位。 */
    public record Result(Status status, String targetStageCode, String transitionCode,
                         List<String> candidateTransitionCodes,
                         List<StageTransitionGraphValidator.Failure> failures) {
        public Result {
            candidateTransitionCodes = List.copyOf(candidateTransitionCodes);
            failures = List.copyOf(failures);
        }
    }

    /**
     * 使用列表接收事实，以便拒绝重复修订而非让 Map 的覆盖顺序决定结果。
     * 无条件图也须显式传空事实列表；缺失的已引用修订不会当作 UNSATISFIED。
     */
    public static Result resolve(StageTransitionGraph graph, String currentStageCode, List<ConditionFact> facts) {
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(graph);
        if (!failures.isEmpty()) {
            return rejected(Status.INVALID_GRAPH, List.of(), failures);
        }
        if (currentStageCode == null || graph.stages().stream()
                .noneMatch(stage -> stage.stageCode().equals(currentStageCode))) {
            return rejected(Status.INVALID_INPUT, List.of(), List.of(new StageTransitionGraphValidator.Failure(
                    "UNKNOWN_CURRENT_STAGE", "currentStageCode", "当前阶段未配置于关系图中：" + currentStageCode)));
        }
        Map<Long, ConditionStatus> statuses = new HashMap<>();
        failures = validateFacts(facts, statuses);
        if (!failures.isEmpty()) {
            return rejected(Status.INVALID_INPUT, List.of(), failures);
        }
        StageTransitionGraph.Stage current = graph.stages().stream()
                .filter(stage -> stage.stageCode().equals(currentStageCode)).findFirst().orElseThrow();
        if (current.normalClosure()) {
            return new Result(Status.TERMINAL, null, null, List.of(), List.of());
        }

        List<StageTransitionDefinition> outgoing = graph.transitions().stream()
                .filter(edge -> edge.fromStageCode().equals(currentStageCode))
                .sorted(Comparator.comparing(StageTransitionDefinition::transitionCode)).toList();
        List<StageTransitionDefinition> matched = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        failures = new ArrayList<>();
        StageTransitionDefinition defaultEdge = null;
        for (StageTransitionDefinition edge : outgoing) {
            if (edge.defaultBranch()) {
                defaultEdge = edge;
                continue;
            }
            ConditionStatus status = edge.conditionRevisionId() == null ? ConditionStatus.SATISFIED
                    : statuses.getOrDefault(edge.conditionRevisionId(), ConditionStatus.UNAVAILABLE);
            if (status == ConditionStatus.UNAVAILABLE) {
                unavailable.add(edge.transitionCode());
                failures.add(new StageTransitionGraphValidator.Failure("CONDITION_UNAVAILABLE",
                        "transitions[" + edge.transitionCode() + "].conditionRevisionId",
                        "条件修订 " + edge.conditionRevisionId() + " 的事实缺失、未知或失效"));
            } else if (status == ConditionStatus.SATISFIED) {
                matched.add(edge);
            }
        }
        // 不短路选中高优先级命中；任何参与判定的未知均阻止选择和默认回退。
        if (!unavailable.isEmpty()) {
            return rejected(Status.UNAVAILABLE, unavailable, failures);
        }
        if (!matched.isEmpty()) {
            int priority = matched.stream().mapToInt(StageTransitionDefinition::priority).min().orElseThrow();
            List<StageTransitionDefinition> winners = matched.stream()
                    .filter(edge -> edge.priority() == priority).toList();
            if (winners.size() == 1) {
                return resolved(winners.getFirst());
            }
            return rejected(Status.AMBIGUOUS, winners.stream()
                    .map(StageTransitionDefinition::transitionCode).toList(), winners.stream()
                    .map(edge -> new StageTransitionGraphValidator.Failure("AMBIGUOUS_PRIORITY",
                            "transitions[" + edge.transitionCode() + "].priority",
                            "最高优先级 " + priority + " 同时命中多个关系，不允许推进")).toList());
        }
        if (defaultEdge != null) {
            return resolved(defaultEdge);
        }
        return rejected(Status.NO_MATCH, outgoing.stream().map(StageTransitionDefinition::transitionCode).toList(),
                List.of(new StageTransitionGraphValidator.Failure("NO_MATCH", "stages[" + currentStageCode
                        + "].outgoing", "所有非默认条件均明确不满足，且未配置默认分支")));
    }

    private static List<StageTransitionGraphValidator.Failure> validateFacts(List<ConditionFact> facts,
                                                                            Map<Long, ConditionStatus> statuses) {
        List<StageTransitionGraphValidator.Failure> failures = new ArrayList<>();
        if (facts == null) {
            return List.of(new StageTransitionGraphValidator.Failure("REQUIRED", "facts", "条件事实集合不能为空引用"));
        }
        for (int i = 0; i < facts.size(); i++) {
            ConditionFact fact = facts.get(i);
            String path = "facts[" + i + "]";
            if (fact == null) {
                failures.add(new StageTransitionGraphValidator.Failure("REQUIRED", path, "条件事实不能为空"));
                continue;
            }
            if (fact.revisionId() == null || fact.revisionId() <= 0) {
                failures.add(new StageTransitionGraphValidator.Failure("INVALID_CONDITION_REVISION",
                        path + ".revisionId", "条件事实必须使用正数修订 ID"));
            } else if (statuses.containsKey(fact.revisionId())) {
                failures.add(new StageTransitionGraphValidator.Failure("DUPLICATE_CONDITION_FACT",
                        path + ".revisionId", "同一条件修订不得提供重复事实：" + fact.revisionId()));
            } else {
                statuses.put(fact.revisionId(), fact.status());
            }
            if (fact.status() == null) {
                failures.add(new StageTransitionGraphValidator.Failure("REQUIRED", path + ".status",
                        "条件事实必须明确为 SATISFIED、UNSATISFIED 或 UNAVAILABLE"));
            }
        }
        return failures;
    }

    private static Result resolved(StageTransitionDefinition edge) {
        return new Result(Status.RESOLVED, edge.toStageCode(), edge.transitionCode(),
                List.of(edge.transitionCode()), List.of());
    }

    private static Result rejected(Status status, List<String> candidates,
                                   List<StageTransitionGraphValidator.Failure> failures) {
        return new Result(status, null, null, candidates, failures);
    }
}
