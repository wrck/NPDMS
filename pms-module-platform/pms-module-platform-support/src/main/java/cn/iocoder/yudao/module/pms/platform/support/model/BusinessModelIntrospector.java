package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 继承字段发现：读取允许公开的祖先与子类业务字段，合并校验信息并解析持久化列名。
 * 字段冲突、错误泛型和不支持类型给出定位到类/字段的错误；基类控制字段不进入目录。
 */
public final class BusinessModelIntrospector {

    public record IntrospectedField(String code, EntityField.Type type, boolean required,
                                    String column, Field property, Class<?> declaringClass) {
    }

    private static final ClassValue<List<IntrospectedField>> CACHE = new ClassValue<>() {
        @Override
        protected List<IntrospectedField> computeValue(Class<?> type) {
            return discover(type, false);
        }
    };

    private static final ClassValue<List<IntrospectedField>> AGGREGATE_CACHE = new ClassValue<>() {
        @Override protected List<IntrospectedField> computeValue(Class<?> type) { return discover(type, true); }
    };
    /** Opt-in public aggregate fields; ordinary persistence discovery continues to exclude child-table properties. */
    public static List<IntrospectedField> aggregateFields(Class<?> entityType) { return AGGREGATE_CACHE.get(entityType); }

    private BusinessModelIntrospector() {
    }

    public static List<IntrospectedField> businessFields(Class<?> entityType) {
        return CACHE.get(entityType);
    }

    public static Map<String, Object> readValues(Object entity, List<IntrospectedField> fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (IntrospectedField field : fields) {
                values.put(field.code(), field.property().get(entity));
            }
        } catch (IllegalAccessException e) {
            throw new BusinessContractException("FIELD_INACCESSIBLE", "字段不可读: " + e.getMessage());
        }
        return values;
    }

    public static String requireColumn(List<IntrospectedField> fields, String fieldCode) {
        return fields.stream().filter(f -> f.code().equals(fieldCode)).findFirst()
                .orElseThrow(() -> new BusinessContractException("FIELD_NOT_OPEN", "字段未在目录开放: " + fieldCode))
                .column();
    }

    private static List<IntrospectedField> discover(Class<?> entityType, boolean includeAggregateFields) {
        if (!BaseBusinessEntity.class.isAssignableFrom(entityType)) {
            throw new BusinessContractException("ENTITY_NOT_UNIFIED",
                    "实体未继承统一业务基类: " + entityType.getName());
        }
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> level = entityType; level != null && level != BaseBusinessEntity.class; level = level.getSuperclass()) {
            chain.add(0, level);
        }
        Map<String, IntrospectedField> fields = new LinkedHashMap<>();
        for (Class<?> level : chain) {
            for (Field property : level.getDeclaredFields()) {
                if (Modifier.isStatic(property.getModifiers()) || property.isSynthetic()
                        || Modifier.isTransient(property.getModifiers())
                        || property.isAnnotationPresent(JsonIgnore.class)
                        && !property.isAnnotationPresent(BusinessModelField.class)) {
                    continue;
                }
                TableField tableField = property.getAnnotation(TableField.class);
                if (tableField != null && !tableField.exist()
                        && (!includeAggregateFields || !property.isAnnotationPresent(BusinessModelField.class))) {
                    continue;
                }
                String code = property.getName();
                if (fields.containsKey(code)) {
                    throw new BusinessContractException("FIELD_SHADOWED",
                            "业务字段被遮蔽: " + code + " 同时定义于 "
                                    + fields.get(code).declaringClass().getName() + " 与 " + level.getName());
                }
                if (!property.trySetAccessible()) {
                    throw new BusinessContractException("FIELD_INACCESSIBLE", level.getName() + "#" + code);
                }
                fields.put(code, new IntrospectedField(code, mapType(property, level, code),
                        property.isAnnotationPresent(jakarta.validation.constraints.NotNull.class)
                                || property.isAnnotationPresent(jakarta.validation.constraints.NotBlank.class),
                        column(code, tableField), property, level));
            }
        }
        return List.copyOf(fields.values());
    }

    private static EntityField.Type mapType(Field property, Class<?> owner, String code) {
        Class<?> type = property.getType();
        if (type == String.class) return EntityField.Type.TEXT;
        if (type == Long.class || type == Integer.class || type == BigDecimal.class) return EntityField.Type.NUMBER;
        if (type == Boolean.class) return EntityField.Type.BOOLEAN;
        if (type == LocalDate.class) return EntityField.Type.DATE;
        if (type == LocalDateTime.class) return EntityField.Type.DATETIME;
        if (type == List.class) {
            Type generic = property.getGenericType();
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getActualTypeArguments()[0] instanceof Class<?> item) {
                if (item == String.class) return EntityField.Type.TEXT_LIST;
                if (!item.getName().startsWith("java.")) return EntityField.Type.OBJECT_LIST;
            }
            throw new BusinessContractException("UNSUPPORTED_FIELD_TYPE",
                    owner.getName() + "#" + code + " 的 List 泛型不受支持");
        }
        throw new BusinessContractException("UNSUPPORTED_FIELD_TYPE",
                owner.getName() + "#" + code + " 类型不受支持: " + type.getName());
    }

    private static String column(String code, TableField tableField) {
        if (tableField != null && !tableField.value().isEmpty()) return tableField.value();
        StringBuilder column = new StringBuilder();
        for (char c : code.toCharArray()) {
            if (Character.isUpperCase(c)) {
                column.append('_').append(Character.toLowerCase(c));
            } else {
                column.append(c);
            }
        }
        String name = column.toString();
        return name.toUpperCase(Locale.ROOT).equals(name) ? name.toLowerCase(Locale.ROOT) : name;
    }
}
