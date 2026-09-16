package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Definition;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EntityExtensionValidationTest {
    @Test
    void draftMayBeIncompleteButCompletionRequiresValue() {
        var schema = List.of(new Definition("extra_detail", "补充说明", EntityField.Type.TEXT, true, 10, List.of()));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("extra_detail", null);
        assertDoesNotThrow(() -> EntityExtensionValidation.values(schema, values, false));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, values, true));
        values.put("extra_detail", "已补充");
        assertDoesNotThrow(() -> EntityExtensionValidation.values(schema, values, true));
    }

    @Test
    void definitionCannotShadowBusinessField() {
        var fixed = List.of(new EntityField("projectId", EntityField.Type.NUMBER, true));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.definitions(
                List.of(new Definition("projectId", "项目", EntityField.Type.TEXT, false, null, List.of())), fixed));
        assertDoesNotThrow(() -> EntityExtensionValidation.definitions(
                List.of(new Definition("extra_detail", "说明", EntityField.Type.TEXT, false, null, List.of())), fixed));
    }

    @Test
    void rejectUnknownFieldsIncorrectTypesAndOutOfRangeChoices() {
        var schema = List.of(new Definition("extra_choices", "选项", EntityField.Type.TEXT_LIST, false, null, List.of("A", "B")));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("projectId", 2), false));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("extra_choices", "A"), false));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("extra_choices", List.of("C")), false));
        assertDoesNotThrow(() -> EntityExtensionValidation.values(schema, Map.of("extra_choices", List.of("A", "B")), false));
    }

    @Test
    void dateAndFiniteNumberAreTypedBusinessValues() {
        var schema = List.of(new Definition("extra_date", "日期", EntityField.Type.DATE, false, null, List.of()),
                new Definition("extra_amount", "数量", EntityField.Type.NUMBER, false, null, List.of()));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("extra_date", "2026-02-30"), false));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("extra_amount", Double.NaN), false));
        assertThrows(ServiceException.class, () -> EntityExtensionValidation.values(schema, Map.of("extra_amount", "12"), false));
        assertDoesNotThrow(() -> EntityExtensionValidation.values(schema, Map.of("extra_date", "2026-02-28", "extra_amount", 12), true));
    }
}
