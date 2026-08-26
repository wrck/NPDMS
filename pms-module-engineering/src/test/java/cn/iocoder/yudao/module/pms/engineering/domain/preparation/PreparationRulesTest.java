package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparationRulesTest {

    private static final String VALID_CATALOG = """
            {"schemaVersion":1,"catalogCode":"PRE_02_SITE_SURVEY","catalogVersion":1,
             "commonFields":[{"fieldCode":"siteCondition","fieldType":"TEXT","required":true,"maxLength":1000,"sortOrder":10}],
             "forms":[{"formCode":"POWER","formVersion":1},{"formCode":"NETWORK_PORT","formVersion":1},
                      {"formCode":"FIBER","formVersion":1},{"formCode":"CABINET","formVersion":1},
                      {"formCode":"NETWORK_CABLE","formVersion":1},{"formCode":"OPTICAL_MODULE","formVersion":1}]}
            """;

    @Test
    void loadsFixedCatalogAndFreezesIndependentSchema() {
        MutableConfigApi configApi = new MutableConfigApi(VALID_CATALOG);
        FixedSurveyFormCatalogProvider provider = new FixedSurveyFormCatalogProvider(configApi);

        String frozen = provider.freezeSchema("POWER", 1);
        configApi.value = VALID_CATALOG.replace("siteCondition", "changedCondition");

        FixedSurveyFormRules.FrozenSchema parsed = FixedSurveyFormRules.parseFrozen(frozen);
        assertEquals("POWER", parsed.formCode());
        assertEquals("siteCondition", parsed.fields().getFirst().fieldCode());
        assertEquals("{\"siteCondition\":\"机房供电正常\"}",
                FixedSurveyFormRules.validateAndNormalizeValue(frozen, "{\"siteCondition\":\"机房供电正常\"}"));
    }

    @Test
    void rejectsMissingInvalidVersionUnknownTypeAndUnknownProperties() {
        assertThrows(ServiceException.class, () -> new FixedSurveyFormCatalogProvider(key -> null).load());
        assertThrows(ServiceException.class, () -> provider(VALID_CATALOG.replace("\"catalogVersion\":1", "\"catalogVersion\":2")).load());
        assertThrows(ServiceException.class, () -> provider(VALID_CATALOG.replace("\"fieldType\":\"TEXT\"", "\"fieldType\":\"SCRIPT\"")).load());
        assertThrows(ServiceException.class, () -> provider(VALID_CATALOG.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"script\":\"x\"")).load());
        assertThrows(ServiceException.class, () -> provider(VALID_CATALOG.replace("\"formCode\":\"POWER\"", "\"formCode\":\"UNKNOWN\"")).load());
    }

    @Test
    void validatesRequiredLengthAndSelectionValuesFromFrozenSchema() {
        var fields = List.of(
                new FixedSurveyFormCatalog.FieldDefinition("note", "TEXT", true, 5, null, 10),
                new FixedSurveyFormCatalog.FieldDefinition("mode", "SINGLE_SELECT", true, null, List.of("A", "B"), 20),
                new FixedSurveyFormCatalog.FieldDefinition("tags", "MULTI_SELECT", true, null, List.of("X", "Y"), 30));
        String schema = FixedSurveyFormRules.freeze(1,
                new FixedSurveyFormCatalog.FormDefinition("POWER", 1), fields);

        assertEquals("{\"note\":\"ok\",\"mode\":\"A\",\"tags\":[\"Y\",\"X\"]}",
                FixedSurveyFormRules.validateAndNormalizeValue(schema,
                        "{\"tags\":[\"Y\",\"X\"],\"mode\":\"A\",\"note\":\"ok\"}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"mode\":\"A\"}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"note\":\"  \u3000\",\"mode\":\"A\",\"tags\":[\"X\"]}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"note\":\"ok\",\"mode\":\"A\",\"tags\":[]}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"note\":\"123456\",\"mode\":\"A\"}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"note\":\"ok\",\"mode\":\"C\"}"));
        assertThrows(ServiceException.class, () -> FixedSurveyFormRules.validateAndNormalizeValue(schema, "{\"note\":\"ok\",\"mode\":\"A\",\"script\":true}"));
    }

    @Test
    void enforcesPreparationAndItemStateAxes() {
        PreparationStateRules.requirePreparationTransition("DRAFT", "PENDING_CONFIRMATION");
        PreparationStateRules.requirePreparationTransition("PENDING_CONFIRMATION", "CONFIRMED");
        PreparationStateRules.requirePreparationTransition("CONFIRMED", "RETURNED");
        PreparationStateRules.requireItemConfirmationTransition(
                "PENDING_CONFIRMATION", "REQUIRED", "PENDING", "CONFIRMED");
        PreparationStateRules.requireApplicabilityTransition(
                "PENDING_CONFIRMATION", "NOT_APPLICABLE_PENDING", "NOT_APPLICABLE_CONFIRMED");

        assertThrows(ServiceException.class,
                () -> PreparationStateRules.requirePreparationTransition("DRAFT", "CONFIRMED"));
        assertThrows(ServiceException.class,
                () -> PreparationStateRules.requireItemConfirmationTransition("DRAFT", "REQUIRED", "PENDING", "CONFIRMED"));
        assertThrows(ServiceException.class,
                () -> PreparationStateRules.requireApplicabilityTransition("CONFIRMED", "REQUIRED", "NOT_APPLICABLE_PENDING"));
    }

    @Test
    void aggregatesOnlyResolvedApplicableAndNotApplicableItems() {
        assertTrue(PreparationStateRules.allItemsConfirmed(List.of(
                new PreparationStateRules.ItemState("REQUIRED", "CONFIRMED"),
                new PreparationStateRules.ItemState("NOT_APPLICABLE_CONFIRMED", "CONFIRMED"))));
        assertFalse(PreparationStateRules.allItemsConfirmed(List.of(
                new PreparationStateRules.ItemState("REQUIRED", "PENDING"))));
        assertFalse(PreparationStateRules.allItemsConfirmed(List.of(
                new PreparationStateRules.ItemState("NOT_APPLICABLE_PENDING", "CONFIRMED"))));
        assertFalse(PreparationStateRules.allItemsConfirmed(List.of()));
    }

    private static FixedSurveyFormCatalogProvider provider(String value) {
        return new FixedSurveyFormCatalogProvider(key -> value);
    }

    private static final class MutableConfigApi implements ConfigApi {
        private String value;

        private MutableConfigApi(String value) {
            this.value = value;
        }

        @Override
        public String getConfigValueByKey(String key) {
            return value;
        }
    }
}
