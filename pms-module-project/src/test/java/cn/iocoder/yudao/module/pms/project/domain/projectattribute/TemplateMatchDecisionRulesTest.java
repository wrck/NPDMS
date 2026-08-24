package cn.iocoder.yudao.module.pms.project.domain.projectattribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateMatchDecisionRulesTest {

    @Test
    void manualAttributesRejectMajorLevelAndTreeCategoryCodes() {
        assertThrows(IllegalArgumentException.class, () ->
                TemplateMatchDecisionRules.requireManualCreationAttributes(
                        new ProjectAttributeSnapshot("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", "A")));
        assertThrows(IllegalArgumentException.class, () ->
                TemplateMatchDecisionRules.requireManualCreationAttributes(
                        new ProjectAttributeSnapshot("DIRECT_SIGN", "MAIN", "DIRECT_SERVICE", null)));
    }

    @Test
    void manualAttributesAreTrimmedAndMajorLevelStoredAsNull() {
        ProjectAttributeSnapshot result = TemplateMatchDecisionRules.requireManualCreationAttributes(
                new ProjectAttributeSnapshot(" DIRECT_SIGN ", " GENERAL ", " DIRECT_SERVICE ", " "));

        assertEquals("DIRECT_SIGN", result.signingMethod());
        assertEquals("GENERAL", result.projectCategory());
        assertEquals("DIRECT_SERVICE", result.implementationMode());
        assertEquals(null, result.majorProjectLevel());
    }

    @Test
    void impactResultOnlyComparesUniqueCandidateWithFrozenRevision() {
        assertEquals(TemplateMatchDecisionRules.IMPACT_NONE,
                TemplateMatchDecisionRules.impactResult(impactDecision("UNIQUE", 11L), 11L));
        assertEquals(TemplateMatchDecisionRules.IMPACT_CHANGED,
                TemplateMatchDecisionRules.impactResult(impactDecision("UNIQUE", 12L), 11L));
        assertEquals(TemplateMatchDecisionRules.MATCH_NO_MATCH,
                TemplateMatchDecisionRules.impactResult(impactDecision("NO_MATCH", null), 11L));
        assertEquals(TemplateMatchDecisionRules.MATCH_MULTIPLE,
                TemplateMatchDecisionRules.impactResult(impactDecision("MULTIPLE_MATCHES", null), 11L));
    }

    @Test
    void impactDecisionForbidsDecisionModeAndMatchedFieldsForConflict() {
        assertThrows(IllegalArgumentException.class, () ->
                TemplateMatchDecisionRules.validateImpactDecision(new TemplateMatchDecision(
                        "NO_MATCH", "digest", "v1", "AUTO_UNIQUE", null, null, null)));
        assertThrows(IllegalArgumentException.class, () ->
                TemplateMatchDecisionRules.validateImpactDecision(new TemplateMatchDecision(
                        "MULTIPLE_MATCHES", "digest", "v1", null, 1L, 2L, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                TemplateMatchDecisionRules.validateImpactDecision(new TemplateMatchDecision(
                        "NO_MATCH", "digest", "v1", null, 1L, null, null)));
    }

    private TemplateMatchDecision impactDecision(String result, Long revisionId) {
        return new TemplateMatchDecision(result, "digest", "v1", null,
                revisionId == null ? null : 1L, revisionId, revisionId == null ? null : 1);
    }
}
