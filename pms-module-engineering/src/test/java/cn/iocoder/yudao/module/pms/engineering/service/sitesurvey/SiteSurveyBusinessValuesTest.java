package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import static org.junit.jupiter.api.Assertions.*;

class SiteSurveyBusinessValuesTest {
    @Test void acceptsStructuredChoicesFalseAndClearValues() {
        assertDoesNotThrow(() -> SiteSurveyBusinessValues.validate(Map.of("extra_powerTypes", List.of("AC", "DC"),
                "extra_materialMatches", false, "extra_selectedMaterials", List.of()), 1L));
    }
    @Test void rejectsUnknownChoicesAndTextMasqueradingAsBoolean() {
        assertThrows(RuntimeException.class, () -> SiteSurveyBusinessValues.validate(Map.of("extra_requiredEndDate", "2026-02-30"), 1L));
        assertThrows(RuntimeException.class, () -> SiteSurveyBusinessValues.validate(Map.of("extra_powerTypes", List.of("UNKNOWN")), 1L));
        assertThrows(RuntimeException.class, () -> SiteSurveyBusinessValues.validate(Map.of("extra_cabinetReady", "false"), 1L));
        assertThrows(RuntimeException.class, () -> SiteSurveyBusinessValues.validate(Map.of("extra_selectedMaterials", List.of(Map.of("sn", "A", "projectId", 2))), 1L));
    }
    @Test void completeTemplateUsesTheRealSharedSchemaParserAndPreservesOriginalEntityFields() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.isDirectory(root.resolve("yudao-ui"))) root = root.getParent();
        var document = JsonUtils.parseTree(Files.readString(root.resolve("yudao-ui/yudao-ui-admin-vue3/src/views/pms/engineering/site-survey/business-template.json")));
        var schema = new DynamicFormSchemaService().parseAndValidate(document.get("formConfJson").toString(), document.get("formRulesJson").toString(), "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38");
        assertTrue(schema.ordinaryFieldKeys().containsAll(SiteSurveyFormPolicyProvider.FIELDS));
        assertTrue(schema.ordinaryFieldKeys().containsAll(List.of("extra_powerTypes", "extra_powerEnvironments", "extra_networkPortTypes", "extra_railTrayRequired", "extra_materialMatches", "extra_selectedMaterials", "extra_requiredEndDate")));
        assertFalse(schema.ordinaryFieldKeys().contains("outsourceRequired"));
    }
}
