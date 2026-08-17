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
    /** 冲突/未命中说明清单（人工处理） */
    private List<String> conflicts = new ArrayList<>();

    public static TemplateMatchResult matched(TemplateMatchCandidate candidate) {
        TemplateMatchResult result = new TemplateMatchResult();
        result.setOutcome(Outcome.MATCHED);
        result.setMatched(candidate);
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
}
