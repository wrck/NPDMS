package cn.iocoder.yudao.module.pms.platform.api.entity;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Cached mapping of business bean properties. It never generates SQL.
 * Inherited business properties are shared by current and revision entities.
 */
public final class EntityFieldMapping<T> {
    private static final ClassValue<List<MappedField>> FIELDS = new ClassValue<>() {
        @Override protected List<MappedField> computeValue(Class<?> type) {
            Map<String, MappedField> fields = new LinkedHashMap<>();
            for (Field property : type.getDeclaredFields()) {
                if (Modifier.isStatic(property.getModifiers()) || property.isSynthetic()
                        || property.isAnnotationPresent(JsonIgnore.class)) continue;
                var code = property.getName();
                var descriptor = new EntityField(code, typeOf(property),
                        property.isAnnotationPresent(NotBlank.class) || property.isAnnotationPresent(NotNull.class));
                if (!property.trySetAccessible()) throw new IllegalStateException("Inaccessible business field: " + code);
                fields.put(code, new MappedField(property, descriptor));
            }
            return List.copyOf(fields.values());
        }
    };
    private final Class<T> type;
    private final Map<String, MappedField> byCode;

    public EntityFieldMapping(Class<T> type) {
        this(type, type);
    }

    /** Use the business entity's declared properties, including when the target is its revision subclass. */
    public EntityFieldMapping(Class<T> type, Class<? super T> businessType) {
        this.type = Objects.requireNonNull(type);
        if (!businessType.isAssignableFrom(type)) throw new IllegalArgumentException("Unrelated business type");
        Map<String, MappedField> fields = new LinkedHashMap<>();
        FIELDS.get(businessType).forEach(field -> fields.put(field.descriptor().code(), field));
        byCode = Collections.unmodifiableMap(fields);
    }

    public List<EntityField> fields() {
        return byCode.values().stream().map(MappedField::descriptor).toList();
    }

    public Map<String, Object> read(T entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        byCode.forEach((code, field) -> {
            Object value = field.read(entity);
            if (field.descriptor().type() == EntityField.Type.OBJECT_LIST && value instanceof List<?> items) {
                value = items.stream().map(this::readChild).toList();
            }
            values.put(code, value);
        });
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<String, Object> readChild(Object child) {
        return new EntityFieldMapping(child.getClass()).read(child);
    }

    public void write(T entity, Map<String, Object> patch) {
        if (patch == null || !byCode.keySet().containsAll(patch.keySet())) {
            throw new IllegalArgumentException("Patch contains undeclared business fields");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        patch.forEach((code, value) -> {
            var field = byCode.get(code);
            requireType(field.descriptor().type(), value);
            validateChildren(field, value);
            properties.put(field.property().getName(), value);
        });
        // Convert before mutation, and copy only submitted, explicitly exposed properties.
        T converted = BeanUtils.toBean(properties, type);
        patch.keySet().forEach(code -> {
            var field = byCode.get(code);
            field.write(entity, field.read(converted));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateChildren(MappedField field, Object value) {
        if (field.descriptor().type() != EntityField.Type.OBJECT_LIST || value == null) return;
        if (!(field.property().getGenericType() instanceof ParameterizedType listType)
                || !(listType.getActualTypeArguments()[0] instanceof Class<?> childType)) {
            throw new IllegalArgumentException("Child rows require a concrete business type");
        }
        var mapping = new EntityFieldMapping(childType);
        for (Object child : (List<?>) value) {
            Map<String, Object> values;
            if (child instanceof Map<?, ?> map) values = (Map<String, Object>) map;
            else if (childType.isInstance(child)) values = mapping.read(child);
            else throw new IllegalArgumentException("Invalid child business value");
            mapping.write(BeanUtils.toBean(Map.of(), childType), values);
        }
    }

    private static EntityField.Type typeOf(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) return EntityField.Type.TEXT;
        if (type == Boolean.class || type == boolean.class) return EntityField.Type.BOOLEAN;
        if (Number.class.isAssignableFrom(type) || type == int.class || type == long.class) return EntityField.Type.NUMBER;
        if (type == LocalDate.class) return EntityField.Type.DATE;
        if (type == LocalDateTime.class) return EntityField.Type.DATETIME;
        if (List.class.isAssignableFrom(type)) {
            return field.getGenericType().getTypeName().equals("java.util.List<java.lang.String>")
                    ? EntityField.Type.TEXT_LIST : EntityField.Type.OBJECT_LIST;
        }
        throw new IllegalArgumentException("Unsupported business field type: " + field);
    }

    private static void requireType(EntityField.Type type, Object value) {
        if (value == null) return;
        boolean valid = switch (type) {
            case TEXT -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case NUMBER -> value instanceof Number;
            case DATE -> value instanceof LocalDate || value instanceof String text && LocalDate.parse(text) != null;
            case DATETIME -> value instanceof LocalDateTime || value instanceof String text && LocalDateTime.parse(text) != null;
            case TEXT_LIST -> value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
            case OBJECT_LIST -> value instanceof List<?>;
        };
        if (!valid) throw new IllegalArgumentException("Business field value has an incompatible type");
    }

    private record MappedField(Field property, EntityField descriptor) {
        Object read(Object entity) {
            try { return property.get(entity); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
        void write(Object entity, Object value) {
            try { property.set(entity, value); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }
}
