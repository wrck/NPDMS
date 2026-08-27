package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequirementAnalysisWorkBindingSchemaTest {

    @Test
    void publicationFreezesEnabledDictionaryLabelsInCodeOrder() {
        String frozen = RequirementAnalysisWorkBindingSchema.freezeAndValidate(binding(),
                (dictionaryType, requested) -> List.of(
                        new RequirementAnalysisWorkBindingSchema.OptionSnapshot("B", "乙-权威"),
                        new RequirementAnalysisWorkBindingSchema.OptionSnapshot("A", "甲-权威")));

        Map<?, ?> root = JsonUtils.parseObject(frozen, Map.class);
        List<?> extensions = (List<?>) root.get("extensionItems");
        Map<?, ?> selection = (Map<?, ?>) extensions.getFirst();
        List<?> options = (List<?>) selection.get("optionSnapshot");
        assertEquals("A", ((Map<?, ?>) options.get(0)).get("code"));
        assertEquals("甲-权威", ((Map<?, ?>) options.get(0)).get("label"));
        assertEquals("B", ((Map<?, ?>) options.get(1)).get("code"));
        assertEquals(1, RequirementAnalysisWorkBindingSchema.parseFrozen(frozen).catalogVersion());
    }

    @Test
    void publicationRejectsCoreCollisionUnknownFieldsAndUnavailableDictionaryOption() {
        assertThrows(IllegalArgumentException.class,
                () -> RequirementAnalysisWorkBindingSchema.freezeAndValidate(
                        binding().replace("EXT_LEVEL", "PROJECT_BACKGROUND"), (type, options) -> options));
        assertThrows(IllegalArgumentException.class,
                () -> RequirementAnalysisWorkBindingSchema.freezeAndValidate(
                        binding().replace("\"sortOrder\":120", "\"script\":\"x\",\"sortOrder\":120"),
                        (type, options) -> options));
        assertThrows(IllegalArgumentException.class,
                () -> RequirementAnalysisWorkBindingSchema.freezeAndValidate(binding(),
                        (type, options) -> List.of(options.getFirst())));
    }

    private static String binding() {
        return "{\"schemaVersion\":1,\"catalogCode\":\"PRE_04_REQUIREMENT_ANALYSIS\","
                + "\"catalogVersion\":1,\"extensionItems\":[{\"fieldCode\":\"EXT_LEVEL\","
                + "\"fieldName\":\"扩展级别\",\"fieldTypeCode\":\"SINGLE_SELECT\","
                + "\"required\":true,\"dictionaryType\":\"pms_requirement_level\","
                + "\"optionSnapshot\":[{\"code\":\"B\",\"label\":\"旧乙\"},"
                + "{\"code\":\"A\",\"label\":\"旧甲\"}],\"sortOrder\":120}]}";
    }
}
