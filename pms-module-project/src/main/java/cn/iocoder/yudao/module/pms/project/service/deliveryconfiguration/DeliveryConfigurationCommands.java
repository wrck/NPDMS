package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationErrors.*;

/** PM-03: platform-owned transactional idempotency/audit, without invented business events. */
@Component
@RequiredArgsConstructor
public class DeliveryConfigurationCommands {
    private final PlatformCommandExecutionApi api;

    public <T> T execute(String action, String key, Object intent, Class<T> type, Supplier<T> operation) {
        if (key == null || key.isBlank() || key.length() > 128) throw exception(INVALID, "Idempotency-Key");
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null) throw exception(INVALID, "authenticated actor required");
        var result = api.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                TenantContextHolder.getRequiredTenantId(), action, actor, key), digest(intent), type, operation,
                response -> new PlatformCommandExecutionApi.SuccessFacts(action, "DELIVERY_CONFIGURATION",
                        String.valueOf(response), key, JsonUtils.toJsonString(intent), null, null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(IDEMPOTENCY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || result.response() == null)
            throw exception(IN_PROGRESS);
        return result.response();
    }

    public static int version(String value) {
        if (value == null || !(value.matches("[0-9]+") || value.matches("\"[0-9]+\"")))
            throw exception(INVALID, "If-Match");
        try {
            return Integer.parseInt(value.startsWith("\"") ? value.substring(1, value.length() - 1) : value);
        } catch (NumberFormatException ex) { throw exception(INVALID, "If-Match"); }
    }

    public static String digest(Object intent) {
        JsonNode node = JsonUtils.parseObject(JsonUtils.toJsonString(intent), JsonNode.class);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(node).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static String canonical(JsonNode node) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>(node.propertyNames()); Collections.sort(keys);
            List<String> fields = new ArrayList<>();
            for (String key : keys) fields.add(JsonUtils.toJsonString(key) + ":" + canonical(node.get(key)));
            return "{" + String.join(",", fields) + "}";
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>(); for (JsonNode child : node) values.add(canonical(child));
            return "[" + String.join(",", values) + "]";
        }
        return node.toString();
    }
}
