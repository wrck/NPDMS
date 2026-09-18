package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;

/** 只绑定调用方代码指定的原命令类型；既不加载客户端类名，也不修改共享Mapper。 */
public final class ProjectOperationInput {
    private ProjectOperationInput() { }

    public static ObjectNode object(JsonNode input) {
        if (input == null || !input.isObject()) throw invalid();
        if (input.has("execution") || input.has("tenantId")) throw invalid();
        return (ObjectNode) input;
    }

    public static void fields(JsonNode input, Set<String> allowed) {
        for (String name : object(input).propertyNames()) {
            if (!allowed.contains(name)) throw invalid();
        }
    }

    public static <T> T read(ObjectMapper mapper, JsonNode input, Class<T> commandType) {
        return read(mapper, input, commandType, Set.of());
    }

    /** 已有传输字段由Owner适配器明确取出校验；只从副本移除，不改写幂等原意图。 */
    public static <T> T read(ObjectMapper mapper, JsonNode input, Class<T> commandType, Set<String> transportFields) {
        ObjectNode value = object(input).deepCopy();
        transportFields.forEach(value::remove);
        try {
            return mapper.readerFor(commandType)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .readValue(value);
        } catch (tools.jackson.core.JacksonException failure) {
            // 不将输入值、Java类型或包含敏感字段的原异常向客户端回显。
            throw invalid();
        }
    }

    public static String optionalText(JsonNode input, String field) {
        JsonNode value = object(input).get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("BUSINESS_INPUT_INVALID");
    }
}
