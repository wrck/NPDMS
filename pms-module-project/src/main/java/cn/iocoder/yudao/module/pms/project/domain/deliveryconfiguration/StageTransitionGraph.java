package cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * PM-03 / F-PROJ-009：现有模板版本内的图值对象，不是第二个模板根。
 * 保留非法输入供纯校验器逐项定位，构造时不以 NPE 代替校验；集合防御性复制且只读。
 * 开始/正常收口是显式配置，不从阶段编号或集合顺序推导。
 */
public record StageTransitionGraph(List<Stage> stages, List<StageTransitionDefinition> transitions) {

    public StageTransitionGraph {
        stages = immutableCopy(stages);
        transitions = immutableCopy(transitions);
    }

    public record Stage(String stageCode, Boolean start, Boolean normalClosure) {
    }

    /** 前置视图只从同一组关系派生；非法图/未知阶段不得静默解释为空视图。 */
    public List<StageTransitionDefinition> incoming(String stageCode) {
        validateViewInput(stageCode);
        return transitions.stream().filter(edge -> edge.toStageCode().equals(stageCode))
                .sorted(Comparator.comparing(StageTransitionDefinition::transitionCode)).toList();
    }

    /** 后置视图只从同一组关系派生，排序仅用于稳定展示，不决定运行目标。 */
    public List<StageTransitionDefinition> outgoing(String stageCode) {
        validateViewInput(stageCode);
        return transitions.stream().filter(edge -> edge.fromStageCode().equals(stageCode))
                .sorted(Comparator.comparing(StageTransitionDefinition::transitionCode)).toList();
    }

    private void validateViewInput(String stageCode) {
        List<StageTransitionGraphValidator.Failure> failures = StageTransitionGraphValidator.validate(this);
        if (!failures.isEmpty()) {
            throw new IllegalArgumentException("graph: " + failures);
        }
        if (stageCode == null || stages.stream().noneMatch(stage -> stage.stageCode().equals(stageCode))) {
            throw new IllegalArgumentException("stageCode: 阶段未配置于关系图中：" + stageCode);
        }
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
