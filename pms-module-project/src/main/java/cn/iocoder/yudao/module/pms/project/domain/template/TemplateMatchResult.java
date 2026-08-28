package cn.iocoder.yudao.module.pms.project.domain.template;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 四维匹配结果：唯一命中或冲突清单（不静默选模，BR-4）
 */
@Data
public class TemplateMatchResult {

    /** 匹配结局 */
    public enum Outcome {
        /** 唯一命中 */
        MATCHED,
        /** 无匹配 */
        NO_MATCH,
        /** 同优先级多匹配 */
        MULTI_MATCH
    }

    /** 匹配结局 */
    private Outcome outcome;
    /** 唯一命中时的候选（其余结局为 null） */
    private TemplateMatchCandidate matched;
    /** 命中候选清单（MATCHED=单元素，MULTI_MATCH=同优先级候选，NO_MATCH=空；供 F-PM01 列表语义包装） */
    private List<TemplateMatchCandidate> candidates = new ArrayList<>();
    /** 冲突/未命中说明清单（人工处理） */
    private List<String> conflicts = new ArrayList<>();
    /** 候选查询水位；正式创建必须回传并由服务端重算比较 */
    private String candidateWatermark;

    public static TemplateMatchResult matched(TemplateMatchCandidate candidate) {
        TemplateMatchResult result = new TemplateMatchResult();
        result.setOutcome(Outcome.MATCHED);
        result.setMatched(candidate);
        result.getCandidates().add(candidate);
        return result;
    }

    public static TemplateMatchResult noMatch(String reason) {
        TemplateMatchResult result = new TemplateMatchResult();
        result.setOutcome(Outcome.NO_MATCH);
        result.getConflicts().add(reason);
        return result;
    }

    public static TemplateMatchResult multiMatch(List<String> conflicts) {
        TemplateMatchResult result = new TemplateMatchResult();
        result.setOutcome(Outcome.MULTI_MATCH);
        result.setConflicts(conflicts);
        return result;
    }

    public static TemplateMatchResult multiMatch(List<String> conflicts, List<TemplateMatchCandidate> candidates) {
        TemplateMatchResult result = multiMatch(conflicts);
        if (candidates != null) {
            result.setCandidates(new ArrayList<>(candidates));
        }
        return result;
    }
}
