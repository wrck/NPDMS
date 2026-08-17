package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-4 四维匹配规则单测：唯一命中或冲突清单，不静默选模
 */
class TemplateMatcherTest {

    @Test
    void uniqueHitOnExactDimensions() {
        TemplateMatchCandidate candidate = candidate("TPL-A", 100, "DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(candidate), "DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null);
        assertEquals(TemplateMatchResult.Outcome.MATCHED, result.getOutcome());
        assertEquals("TPL-A", result.getMatched().getCode());
    }

    @Test
    void wildcardDimensionMatchesAnySourceValue() {
        // 候选维度 null=不限，来源有值仍可命中
        TemplateMatchCandidate candidate = candidate("TPL-ANY", 100, null, null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(candidate), "CHANNEL_SIGN", "ENGINEERING", "SUPERVISION", null);
        assertEquals(TemplateMatchResult.Outcome.MATCHED, result.getOutcome());
    }

    @Test
    void missingSourceDimensionOnlyMatchesWildcardCandidate() {
        // 来源维度缺失（null）只能命中"不限"候选，不得命中有值候选（不静默选模）
        TemplateMatchCandidate specific = candidate("TPL-SPECIFIC", 100, "DIRECT_SIGN", null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(specific), null, null, null, null);
        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
    }

    @Test
    void noMatchReturnsConflictInsteadOfGuess() {
        TemplateMatchCandidate candidate = candidate("TPL-A", 100, "DIRECT_SIGN", null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(candidate), "CHANNEL_SIGN", null, null, null);
        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
        assertNull(result.getMatched());
        assertTrue(!result.getConflicts().isEmpty());
    }

    @Test
    void samePriorityMultiMatchReturnsConflictList() {
        TemplateMatchCandidate first = candidate("TPL-A", 100, "DIRECT_SIGN", null, null, null);
        TemplateMatchCandidate second = candidate("TPL-B", 100, "DIRECT_SIGN", null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(first, second), "DIRECT_SIGN", null, null, null);
        assertEquals(TemplateMatchResult.Outcome.MULTI_MATCH, result.getOutcome());
        assertNull(result.getMatched());
        assertTrue(result.getConflicts().stream().anyMatch(c -> c.contains("TPL-A")));
        assertTrue(result.getConflicts().stream().anyMatch(c -> c.contains("TPL-B")));
    }

    @Test
    void smallerPriorityValueWinsOverLowerPriority() {
        TemplateMatchCandidate lower = candidate("TPL-LOW", 200, "DIRECT_SIGN", null, null, null);
        TemplateMatchCandidate higher = candidate("TPL-HIGH", 10, "DIRECT_SIGN", null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(lower, higher), "DIRECT_SIGN", null, null, null);
        assertEquals(TemplateMatchResult.Outcome.MATCHED, result.getOutcome());
        assertEquals("TPL-HIGH", result.getMatched().getCode());
    }

    @Test
    void multiMatchOnlyCountedWithinTopPriority() {
        // 最高优先级唯一命中即返回，不因次优先级候选产生冲突
        TemplateMatchCandidate top = candidate("TPL-TOP", 10, "DIRECT_SIGN", null, null, null);
        TemplateMatchCandidate second = candidate("TPL-SECOND", 10, "DIRECT_SIGN", null, null, null);
        TemplateMatchCandidate lower = candidate("TPL-LOWER", 99, "DIRECT_SIGN", null, null, null);
        TemplateMatchResult result = TemplateMatcher.match(
                List.of(top, second, lower), "DIRECT_SIGN", null, null, null);
        assertEquals(TemplateMatchResult.Outcome.MULTI_MATCH, result.getOutcome());
        // 冲突清单只包含最高优先级候选
        assertTrue(result.getConflicts().stream().noneMatch(c -> c.contains("TPL-LOWER")));
    }

    private TemplateMatchCandidate candidate(String code, int priority, String signingMethod,
                                             String projectCategory, String implementationMethod,
                                             String majorProjectLevel) {
        TemplateMatchCandidate candidate = new TemplateMatchCandidate();
        candidate.setTemplateId((long) code.hashCode());
        candidate.setCode(code);
        candidate.setName("模板-" + code);
        candidate.setMatchPriority(priority);
        candidate.setSigningMethod(signingMethod);
        candidate.setProjectCategory(projectCategory);
        candidate.setImplementationMethod(implementationMethod);
        candidate.setMajorProjectLevel(majorProjectLevel);
        return candidate;
    }
}
