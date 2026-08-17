package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 四维匹配器（BR-4 / PM-03 规则4）
 * <p>
 * 按签约方式/项目类别/实施方式/重大项目级别独立条件匹配生效模板：
 * 候选维度为 null 表示不限；来源维度缺失（null）只能命中"不限"候选。
 * 同优先级多匹配或无匹配时返回冲突清单，不静默选模。
 */
public final class TemplateMatcher {

    private TemplateMatcher() {
    }

    /**
     * 执行匹配：唯一命中返回 MATCHED；无匹配/同优先级多匹配返回冲突清单。
     *
     * @param candidates         生效模板候选（各自携带最新已发布版本的四维条件）
     * @param signingMethod      签约方式（可空=未映射/缺失）
     * @param projectCategory    项目类别（可空=缺失）
     * @param implementationMethod 实施方式（可空=未映射/缺失）
     * @param majorProjectLevel  重大项目级别（可空=未映射/缺失）
     */
    public static TemplateMatchResult match(List<TemplateMatchCandidate> candidates,
                                            String signingMethod, String projectCategory,
                                            String implementationMethod, String majorProjectLevel) {
        List<TemplateMatchCandidate> matches = new ArrayList<>();
        if (candidates != null) {
            for (TemplateMatchCandidate candidate : candidates) {
                if (candidate == null) {
                    continue;
                }
                if (dimensionMatches(candidate.getSigningMethod(), signingMethod)
                        && dimensionMatches(candidate.getProjectCategory(), projectCategory)
                        && dimensionMatches(candidate.getImplementationMethod(), implementationMethod)
                        && dimensionMatches(candidate.getMajorProjectLevel(), majorProjectLevel)) {
                    matches.add(candidate);
                }
            }
        }
        if (matches.isEmpty()) {
            return TemplateMatchResult.noMatch("无匹配的生效模板：请核对四维条件或来源属性映射");
        }
        int minPriority = matches.stream()
                .map(TemplateMatchCandidate::getMatchPriority)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min().orElse(Integer.MAX_VALUE);
        List<TemplateMatchCandidate> top = new ArrayList<>();
        for (TemplateMatchCandidate candidate : matches) {
            Integer priority = candidate.getMatchPriority();
            if (priority != null && priority == minPriority) {
                top.add(candidate);
            }
        }
        if (top.size() == 1) {
            return TemplateMatchResult.matched(top.get(0));
        }
        List<String> conflicts = new ArrayList<>();
        conflicts.add("同优先级【" + minPriority + "】多匹配，需人工处理：");
        for (TemplateMatchCandidate candidate : top) {
            conflicts.add("模板【" + candidate.getCode() + "】" + candidate.getName());
        }
        return TemplateMatchResult.multiMatch(conflicts);
    }

    /**
     * 维度匹配：候选 null=不限（恒匹配）；候选有值时须与来源值精确相等（来源 null 不匹配有值候选）。
     */
    private static boolean dimensionMatches(String candidateValue, String sourceValue) {
        if (candidateValue == null) {
            return true;
        }
        return candidateValue.equals(sourceValue);
    }
}
