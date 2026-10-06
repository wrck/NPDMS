package cn.iocoder.yudao.module.pms.platform.support.model;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Conversion at the declaration boundary, including collection element types erased by reflection. */
public final class DeclaredBusinessFieldValues {
    private DeclaredBusinessFieldValues() {
    }

    public static Object convert(Object value, BusinessModelIntrospector.IntrospectedField field) {
        if (value == null) return null;
        try {
            Class<?> type = field.property().getType();
            return switch (field.type()) {
                case TEXT -> {
                    if (!(value instanceof String)) throw new IllegalArgumentException();
                    yield value;
                }
                case NUMBER -> {
                    if (!(value instanceof Number) && !(value instanceof String)) throw new IllegalArgumentException();
                    BigDecimal number = new BigDecimal(value.toString());
                    if (type == Long.class) yield number.longValueExact();
                    if (type == Integer.class) yield number.intValueExact();
                    yield number;
                }
                case BOOLEAN -> {
                    if (value instanceof Boolean) yield value;
                    if (!(value instanceof String text)
                            || !"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text))
                        throw new IllegalArgumentException();
                    yield Boolean.parseBoolean(text);
                }
                case DATE -> value instanceof LocalDate ? value : LocalDate.parse((String) value);
                case DATETIME -> value instanceof LocalDateTime ? value
                        : LocalDateTime.parse(((String) value).replace(' ', 'T'));
                case TEXT_LIST -> {
                    if (!(value instanceof List<?> items) || items.stream().anyMatch(item -> !(item instanceof String)))
                        throw new IllegalArgumentException();
                    yield new ArrayList<>(items);
                }
                case OBJECT_LIST -> {
                    if (!(value instanceof List<?> items)) throw new IllegalArgumentException();
                    Class<?> itemType = (Class<?>) ((ParameterizedType) field.property().getGenericType()).getActualTypeArguments()[0];
                    List<Object> converted = new ArrayList<>();
                    for (Object item : items) {
                        if (itemType.isInstance(item)) converted.add(item);
                        else if (item instanceof Map<?, ?>) converted.add(JsonUtils.parseObject(JsonUtils.toJsonString(item), itemType));
                        else throw new IllegalArgumentException();
                    }
                    yield converted;
                }
            };
        } catch (RuntimeException invalid) {
            throw new BusinessContractException("FIELD_VALUE_INVALID", "字段值非法: " + field.code());
        }
    }
}
