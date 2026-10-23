package cn.iocoder.yudao.module.pms.integration.deviceops;

import cn.iocoder.yudao.module.pms.integration.api.deviceops.dto.DeviceOpsDispatchCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Shared command contract for server dispatch and authorized desktop execution. */
final class DacCollectionPayloadFactory {
    private DacCollectionPayloadFactory() { }

    record Input(DeviceOpsDispatchCommand command, DacDeviceOpsGatewayProperties properties,
                 String namespace, String callbackUrl) { }

    static Map<String, Object> build(Input input) {
        var command = input.command();
        var properties = input.properties();
        boolean callback = input.callbackUrl() != null && !input.callbackUrl().isBlank();
        boolean hasProject = command.projectId() != null && !command.projectId().isBlank();
        if (callback && (!hasProject || command.deviceId() == null || command.deviceId().isBlank()))
            throw new IllegalArgumentException("回调地址需要项目与设备上下文");
        Map<String, Object> context = new LinkedHashMap<>();
        if (hasProject) context.put("project", Map.of("namespace", input.namespace(), "projectKey", command.projectId()));
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("deviceKey", command.deviceId());
        device.put("deviceName", command.deviceName());
        context.put("device", device);
        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("protocol", protocol(command.protocol()));
        connection.put("host", command.host());
        connection.put("port", command.port());
        connection.put("username", command.temporaryUsername());
        connection.put("authenticationType", "PASSWORD");
        connection.put("executionMode", "SHELL");
        connection.put("password", command.temporarySecret());
        if ("TELNET".equals(command.protocol())) connection.put("telnetPrompts", Map.of(
                "login", "(?i)(login|username)\\s*:\\s*$", "password", "(?i)password\\s*:\\s*$",
                "command", "[>#\\$]\\s*$", "lineEnding", "AUTO"));
        connection.put("connectTimeoutSeconds", Math.max(1, properties.getDeviceConnectTimeoutSeconds()));
        if ("SAVED_CREDENTIAL".equals(command.credentialMode())) {
            connection.clear();
            connection.put("savedConnectionId", command.savedConnectionId());
            connection.put("savedConnectionVersion", command.savedConnectionVersion());
        }
        String content = String.join("\n", command.commands());
        if (!sha(content).equalsIgnoreCase(command.templateHash())) throw new IllegalArgumentException("DAC_TEMPLATE_HASH_MISMATCH");
        Map<String, Object> script = new LinkedHashMap<>();
        script.put("source", "EXTERNAL_DELIVERED");
        script.put("key", "plt-" + command.templateId());
        script.put("version", command.templateVersion());
        script.put("content", content);
        script.put("sha256", command.templateHash());
        script.put("policy", "EXECUTION_ONLY");
        script.put("parserType", properties.getParserType());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("namespace", input.namespace());
        body.put("context", context);
        body.put("connection", connection);
        body.put("script", script);
        body.put("externalRequestId", command.platformTaskId());
        body.put("activityType", properties.getActivityType());
        if (callback) body.put("callbackUrl", input.callbackUrl());
        body.put("commandTimeoutSeconds", timeout(input));
        body.put("parseTimeoutSeconds", Math.max(1, properties.getParseTimeoutSeconds()));
        body.put("leaseGraceSeconds", 0L);
        if (command.executionOptions() != null) body.put("executionOptions", command.executionOptions());
        return body;
    }

    static long timeout(Input input) {
        var command = input.command();
        var properties = input.properties();
        if (command.executionOptions() != null && "PRECHECK".equals(command.executionOptions().purpose()))
            return Math.max(1, properties.getDeviceConnectTimeoutSeconds());
        boolean manual = ("IMP".equals(command.sourceContext()) && Set.of("Configuration", "JointTest").contains(command.sourceObjectType()))
                || ("PLT".equals(command.sourceContext()) && "CollectionCenter".equals(command.sourceObjectType()))
                || ("CUT".equals(command.sourceContext()) && "CUTOVER_CHECKLIST_ITEM".equals(command.sourceObjectType()))
                || (command.executionOptions() != null && "INSPECTION".equals(command.executionOptions().purpose()));
        long seconds = manual ? properties.getManualExecutionTimeoutSeconds() : properties.getCommandTimeoutSeconds();
        if (seconds < 1 || (!manual && seconds > DacDeviceOpsGateway.COMMAND_TIMEOUT_LIMIT_SECONDS))
            throw new IllegalArgumentException("DAC_COMMAND_TIMEOUT_INVALID");
        return seconds;
    }

    static String protocol(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SSH", "SSH2" -> "SSH2";
            case "TELNET" -> "TELNET";
            default -> throw new IllegalArgumentException("不支持的采集协议");
        };
    }

    private static String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException failure) { throw new IllegalStateException(failure); }
    }
}
