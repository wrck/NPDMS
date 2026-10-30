package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessModelIntrospectorTest {

    @SuppressWarnings("unused")
    static abstract class SampleBase extends BaseBusinessEntity {

        @NotNull
        private String baseCode;

        private LocalDateTime baseTime;

        public String getBaseCode() {
            return baseCode;
        }

        public LocalDateTime getBaseTime() {
            return baseTime;
        }
    }

    @SuppressWarnings("unused")
    static class SampleEntity extends SampleBase {

        private String displayName;

        private BigDecimal amount;

        private Boolean enabled;

        private LocalDate effectiveDate;

        private List<String> tags;

        private List<SampleItem> items;

        @TableField("custom_status")
        private String status;

        @TableField(exist = false)
        private String notPersisted;

        @JsonIgnore
        private String hidden;

        private transient String transientField;

        private static String CONSTANT = "static";

        public String getDisplayName() {
            return displayName;
        }
    }

    static class SampleItem {
    }

    static class ShadowEntity extends SampleBase {

        @SuppressWarnings("unused")
        private String baseCode;
    }

    @SuppressWarnings("unused")
    static class UnsupportedEntity extends BaseBusinessEntity {

        private Map<String, String> payload;
    }

    @SuppressWarnings("unused")
    static class RawListEntity extends BaseBusinessEntity {

        private List rawItems;
    }

    static class ForeignEntity {
    }

    @Test
    void discoversInheritedFieldsWithTypesColumnsAndRequired() {
        List<BusinessModelIntrospector.IntrospectedField> fields =
                BusinessModelIntrospector.businessFields(SampleEntity.class);

        assertEquals(List.of("baseCode", "baseTime", "displayName", "amount", "enabled",
                "effectiveDate", "tags", "items", "status"), fields.stream()
                .map(BusinessModelIntrospector.IntrospectedField::code).toList());
        assertEquals(EntityField.Type.TEXT, typeOf(fields, "baseCode"));
        assertEquals(EntityField.Type.DATETIME, typeOf(fields, "baseTime"));
        assertEquals(EntityField.Type.TEXT, typeOf(fields, "displayName"));
        assertEquals(EntityField.Type.NUMBER, typeOf(fields, "amount"));
        assertEquals(EntityField.Type.BOOLEAN, typeOf(fields, "enabled"));
        assertEquals(EntityField.Type.DATE, typeOf(fields, "effectiveDate"));
        assertEquals(EntityField.Type.TEXT_LIST, typeOf(fields, "tags"));
        assertEquals(EntityField.Type.OBJECT_LIST, typeOf(fields, "items"));
        assertTrue(field(fields, "baseCode").required());
        assertEquals("base_code", field(fields, "baseCode").column());
        assertEquals("display_name", field(fields, "displayName").column());
        assertEquals("custom_status", field(fields, "status").column());
        assertEquals(SampleBase.class, field(fields, "baseCode").declaringClass());
        assertEquals(SampleEntity.class, field(fields, "displayName").declaringClass());
    }

    @Test
    void skipsStaticTransientIgnoredAndNonPersistentFields() {
        List<String> codes = BusinessModelIntrospector.businessFields(SampleEntity.class).stream()
                .map(BusinessModelIntrospector.IntrospectedField::code).toList();

        assertFalse(codes.contains("notPersisted"));
        assertFalse(codes.contains("hidden"));
        assertFalse(codes.contains("transientField"));
        assertFalse(codes.contains("CONSTANT"));
        assertFalse(codes.contains("id"));
        assertFalse(codes.contains("version"));
    }

    @Test
    void readsValuesByFieldCode() {
        SampleEntity entity = new SampleEntity();
        setField(entity, "baseCode", "BASE-1");
        setField(entity, "displayName", "样例");
        setField(entity, "amount", new BigDecimal("12.50"));

        Map<String, Object> values = BusinessModelIntrospector.readValues(entity,
                BusinessModelIntrospector.businessFields(SampleEntity.class));

        assertEquals("BASE-1", values.get("baseCode"));
        assertEquals("样例", values.get("displayName"));
        assertEquals(new BigDecimal("12.50"), values.get("amount"));
        assertEquals(9, values.size());
    }

    @Test
    void resolvesColumnAndRejectsUnknownFieldCode() {
        List<BusinessModelIntrospector.IntrospectedField> fields =
                BusinessModelIntrospector.businessFields(SampleEntity.class);

        assertEquals("effective_date", BusinessModelIntrospector.requireColumn(fields, "effectiveDate"));
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> BusinessModelIntrospector.requireColumn(fields, "missingField"));
        assertEquals("FIELD_NOT_OPEN", ex.getErrorCode());
    }

    @Test
    void rejectsShadowedBusinessField() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> BusinessModelIntrospector.businessFields(ShadowEntity.class));
        assertEquals("FIELD_SHADOWED", ex.getErrorCode());
    }

    @Test
    void rejectsUnsupportedFieldTypes() {
        BusinessContractException mapField = assertThrows(BusinessContractException.class,
                () -> BusinessModelIntrospector.businessFields(UnsupportedEntity.class));
        assertEquals("UNSUPPORTED_FIELD_TYPE", mapField.getErrorCode());

        BusinessContractException rawList = assertThrows(BusinessContractException.class,
                () -> BusinessModelIntrospector.businessFields(RawListEntity.class));
        assertEquals("UNSUPPORTED_FIELD_TYPE", rawList.getErrorCode());
    }

    @Test
    void rejectsEntityOutsideUnifiedHierarchy() {
        BusinessContractException ex = assertThrows(BusinessContractException.class,
                () -> BusinessModelIntrospector.businessFields(ForeignEntity.class));
        assertEquals("ENTITY_NOT_UNIFIED", ex.getErrorCode());
    }

    private static EntityField.Type typeOf(List<BusinessModelIntrospector.IntrospectedField> fields, String code) {
        return field(fields, code).type();
    }

    private static BusinessModelIntrospector.IntrospectedField field(
            List<BusinessModelIntrospector.IntrospectedField> fields, String code) {
        return fields.stream().filter(f -> f.code().equals(code)).findFirst().orElseThrow();
    }

    private static void setField(Object entity, String name, Object value) {
        try {
            for (Class<?> type = entity.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(entity, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    // 继续向父类查找
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
