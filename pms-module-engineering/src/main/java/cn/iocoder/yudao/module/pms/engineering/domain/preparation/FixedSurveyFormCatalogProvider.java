package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_FIXED_FORM_CATALOG_INVALID;

@Component
@RequiredArgsConstructor
public class FixedSurveyFormCatalogProvider {

    public static final String CONFIG_KEY = "pms.sol.preparation.site-survey.form-catalog.v1";
    public static final int CATALOG_VERSION = 1;
    public static final String CATALOG_CODE = "PRE_02_SITE_SURVEY";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "catalogCode", "catalogVersion", "commonFields", "forms");
    private static final Set<String> FIELD_FIELDS = Set.of(
            "fieldCode", "fieldType", "required", "maxLength", "options", "sortOrder");
    private static final Set<String> FORM_FIELDS = Set.of("formCode", "formVersion");
    private static final Set<String> BASELINE_FORMS = Set.of(
            "POWER", "NETWORK_PORT", "FIBER", "CABINET", "NETWORK_CABLE", "OPTICAL_MODULE");

    private final ConfigApi configApi;

    public FixedSurveyFormCatalog load() {
        String value = configApi.getConfigValueByKey(CONFIG_KEY);
        if (value == null || value.isBlank()) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        try {
            JsonNode root = JsonUtils.parseTree(value);
            requireObjectWithOnly(root, ROOT_FIELDS);
            FixedSurveyFormCatalog catalog = JsonUtils.parseObject(value, FixedSurveyFormCatalog.class);
            validateCatalog(root, catalog);
            return catalog;
        } catch (RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw ex;
            }
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
    }

    public String freezeSchema(String formCode, Integer formVersion) {
        FixedSurveyFormCatalog catalog = load();
        FixedSurveyFormCatalog.FormDefinition form = catalog.forms().stream()
                .filter(candidate -> candidate.formCode().equals(formCode)
                        && candidate.formVersion().equals(formVersion))
                .findFirst()
                .orElseThrow(() -> exception(PREPARATION_FIXED_FORM_CATALOG_INVALID));
        return FixedSurveyFormRules.freeze(catalog.schemaVersion(), form, catalog.commonFields());
    }

    private static void validateCatalog(JsonNode root, FixedSurveyFormCatalog catalog) {
        if (catalog == null || !Integer.valueOf(1).equals(catalog.schemaVersion())
                || !CATALOG_CODE.equals(catalog.catalogCode())
                || !Integer.valueOf(CATALOG_VERSION).equals(catalog.catalogVersion())
                || catalog.commonFields() == null || catalog.commonFields().isEmpty()
                || catalog.forms() == null || catalog.forms().isEmpty()) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        JsonNode fieldsNode = root.get("commonFields");
        JsonNode formsNode = root.get("forms");
        if (fieldsNode == null || !fieldsNode.isArray() || formsNode == null || !formsNode.isArray()
                || fieldsNode.size() != catalog.commonFields().size() || formsNode.size() != catalog.forms().size()) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        Set<String> fieldCodes = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (int index = 0; index < catalog.commonFields().size(); index++) {
            requireObjectWithOnly(fieldsNode.get(index), FIELD_FIELDS);
            FixedSurveyFormCatalog.FieldDefinition field = catalog.commonFields().get(index);
            FixedSurveyFormRules.validateDefinition(field);
            if (!fieldCodes.add(field.fieldCode()) || !sortOrders.add(field.sortOrder())) {
                throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
            }
        }
        Set<String> formCodes = new HashSet<>();
        for (int index = 0; index < catalog.forms().size(); index++) {
            requireObjectWithOnly(formsNode.get(index), FORM_FIELDS);
            FixedSurveyFormCatalog.FormDefinition form = catalog.forms().get(index);
            if (form == null || form.formCode() == null || form.formCode().isBlank()
                    || !Integer.valueOf(1).equals(form.formVersion()) || !formCodes.add(form.formCode())) {
                throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
            }
        }
        if (!formCodes.equals(BASELINE_FORMS)) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
    }

    private static void requireObjectWithOnly(JsonNode node, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        Set<String> actual = new HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!allowedFields.containsAll(actual)) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
    }
}
