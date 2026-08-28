package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementAnalysisWorkBindingSchemaTest {

    @Test
    void publicationAcceptsOnlyTheExactV2DynamicFormRevisionSnapshot() {
        RequirementAnalysisWorkBindingSchema.ParsedBinding binding =
                RequirementAnalysisWorkBindingSchema.parseForPublication(binding());

        assertEquals(700L, binding.dynamicFormTemplateId());
        assertEquals(701L, binding.dynamicFormTemplateRevisionId());
        assertEquals(3, binding.dynamicFormRevisionNo());
        assertEquals(9, binding.dynamicFormRevisionFactVersion());
        assertEquals(binding(), RequirementAnalysisWorkBindingSchema.toSnapshot(binding));
    }

    @Test
    void runtimeParsesTheSameFrozenRevisionWithoutLegacyCatalogFields() {
        RequirementAnalysisWorkBindingSchema.ParsedBinding binding =
                RequirementAnalysisWorkBindingSchema.parseFrozen(binding());

        assertEquals(701L, binding.dynamicFormTemplateRevisionId());
        assertThrows(IllegalArgumentException.class, () -> RequirementAnalysisWorkBindingSchema.parseFrozen(
                "{\"schemaVersion\":1,\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\","
                        + "\"catalogVersion\":1,\"extensionItems\":[]}"));
    }

    @Test
    void rejectsMissingUnknownAndNonPositiveRevisionFacts() {
        assertThrows(IllegalArgumentException.class, () -> RequirementAnalysisWorkBindingSchema.parseForPublication(
                binding().replace(",\"dynamicFormRevisionFactVersion\":9", "")));
        assertThrows(IllegalArgumentException.class, () -> RequirementAnalysisWorkBindingSchema.parseForPublication(
                binding().replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"catalogCode\":\"legacy\"")));
        assertThrows(IllegalArgumentException.class, () -> RequirementAnalysisWorkBindingSchema.parseForPublication(
                binding().replace("\"dynamicFormRevisionNo\":3", "\"dynamicFormRevisionNo\":0")));
        assertThrows(IllegalArgumentException.class, () -> RequirementAnalysisWorkBindingSchema.parseForPublication(
                binding().replace("\"dynamicFormTemplateRevisionId\":701", "\"dynamicFormTemplateRevisionId\":-1")));
    }

    private static String binding() {
        return "{\"schemaVersion\":2,\"dynamicFormTemplateId\":700,"
                + "\"dynamicFormTemplateRevisionId\":701,\"dynamicFormRevisionNo\":3,"
                + "\"dynamicFormRevisionFactVersion\":9}";
    }
}
